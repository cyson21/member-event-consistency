package com.example.consistency.web;

import org.redisson.api.RLock;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class RedissonWatchdogLockExecutor {

    private RedissonWatchdogLockExecutor() {
    }

    static <T> T execute(
            RLock lock,
            long waitMs,
            Supplier<T> operation,
            String acquisitionFailureMessage,
            String interruptionMessage
    ) {
        Objects.requireNonNull(lock, "lock must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        if (waitMs <= 0) {
            throw new IllegalArgumentException("waitMs must be positive");
        }

        boolean acquired = false;
        Throwable operationFailure = null;
        try {
            // The two-argument overload leaves lease renewal to Redisson's watchdog.
            acquired = lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException(acquisitionFailureMessage);
            }
            return operation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruptionMessage, exception);
        } catch (RuntimeException | Error failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            if (acquired) {
                releaseIfOwned(lock, operationFailure);
            }
        }
    }

    private static void releaseIfOwned(RLock lock, Throwable operationFailure) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException | Error releaseFailure) {
            if (operationFailure == null) {
                throw releaseFailure;
            }
            operationFailure.addSuppressed(releaseFailure);
        }
    }
}
