package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignLockGateway;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class RedisCouponCampaignLockGateway implements CouponCampaignLockGateway {

    private static final long WAIT_MS = 100;

    private final RedissonClient redissonClient;
    private final AtomicLong attempts = new AtomicLong();
    private volatile String lastLockKey = "";

    public RedisCouponCampaignLockGateway(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
    }

    @Override
    public <T> T withCampaignLock(long campaignId, Supplier<T> operation) {
        if (campaignId <= 0) {
            throw new IllegalArgumentException("campaignId must be positive");
        }
        Objects.requireNonNull(operation, "operation must not be null");
        String key = lockKey(campaignId);
        attempts.incrementAndGet();
        lastLockKey = key;
        RLock lock = redissonClient.getLock(key);
        return RedissonWatchdogLockExecutor.execute(
                lock,
                WAIT_MS,
                operation,
                "Redis coupon campaign lock was not acquired before DB guard",
                "Interrupted while acquiring Redis coupon campaign lock"
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

    private static String lockKey(long campaignId) {
        return "lock:coupon-campaign:" + campaignId;
    }
}
