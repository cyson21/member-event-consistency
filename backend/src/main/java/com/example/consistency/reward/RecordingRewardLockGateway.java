package com.example.consistency.reward;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class RecordingRewardLockGateway implements RewardLockGateway {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong attempts = new AtomicLong();
    private volatile String lastLockKey = "";

    @Override
    public <T> T withRewardLock(long memberId, Supplier<T> operation) {
        String key = lockKey(memberId);
        attempts.incrementAndGet();
        lastLockKey = key;
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
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

