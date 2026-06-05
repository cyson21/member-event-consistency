package com.example.consistency.web;

import com.example.consistency.reward.RewardLockGateway;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class RedisRewardLockGateway implements RewardLockGateway {

    private static final long WAIT_MS = 100;
    private static final long LEASE_MS = 5_000;

    private final RedissonClient redissonClient;
    private final AtomicLong attempts = new AtomicLong();
    private volatile String lastLockKey = "";

    public RedisRewardLockGateway(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T withRewardLock(long memberId, Supplier<T> operation) {
        String key = lockKey(memberId);
        attempts.incrementAndGet();
        lastLockKey = key;
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_MS, LEASE_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("Redis reward lock was not acquired before DB guard");
            }
            return operation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring Redis reward lock", exception);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public long attemptCount() {
        return attempts.get();
    }

    @Override
    public String lastLockKey() {
        return lastLockKey;
    }

    private String lockKey(long memberId) {
        return "lock:first-login-reward:" + memberId;
    }
}
