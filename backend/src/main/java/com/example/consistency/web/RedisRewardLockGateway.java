package com.example.consistency.web;

import com.example.consistency.reward.RewardLockGateway;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class RedisRewardLockGateway implements RewardLockGateway {

    private static final long WAIT_MS = 100;

    private final RedissonClient redissonClient;
    private final AtomicLong attempts = new AtomicLong();
    private volatile String lastLockKey = "";

    public RedisRewardLockGateway(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
    }

    @Override
    public <T> T withRewardLock(long memberId, Supplier<T> operation) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        Objects.requireNonNull(operation, "operation must not be null");
        String key = lockKey(memberId);
        attempts.incrementAndGet();
        lastLockKey = key;
        RLock lock = redissonClient.getLock(key);
        return RedissonWatchdogLockExecutor.execute(
                lock,
                WAIT_MS,
                operation,
                "Redis reward lock was not acquired before DB guard",
                "Interrupted while acquiring Redis reward lock"
        );
    }

    @Override
    public long attemptCount() {
        return attempts.get();
    }

    @Override
    public String lastLockKey() {
        return lastLockKey;
    }

    private static String lockKey(long memberId) {
        return "lock:first-login-reward:" + memberId;
    }
}
