package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignLockGateway;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class RedisCouponCampaignLockGateway implements CouponCampaignLockGateway {

    private static final long WAIT_MS = 100;
    private static final long LEASE_MS = 5_000;

    private final RedissonClient redissonClient;
    private final AtomicLong attempts = new AtomicLong();
    private volatile String lastLockKey = "";

    public RedisCouponCampaignLockGateway(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T withCampaignLock(long campaignId, Supplier<T> operation) {
        String key = lockKey(campaignId);
        attempts.incrementAndGet();
        lastLockKey = key;
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_MS, LEASE_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("Redis coupon campaign lock was not acquired before DB guard");
            }
            return operation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring Redis coupon campaign lock", exception);
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

    private String lockKey(long campaignId) {
        return "lock:coupon-campaign:" + campaignId;
    }
}
