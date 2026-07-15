package com.example.consistency.web;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRewardLockGatewayTest {

    @Test
    void rejectsInvalidMemberIdBeforeResolvingRedisLock() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gateway.withRewardLock(-1L, () -> "ignored")
        );

        assertEquals("memberId must be positive", exception.getMessage());
        assertEquals(0L, gateway.attemptCount());
        verifyNoInteractions(redissonClient);
    }

    @Test
    void rejectsNullOperationBeforeCountingAttempt() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> gateway.withRewardLock(9101L, null)
        );

        assertEquals("operation must not be null", exception.getMessage());
        assertEquals(0L, gateway.attemptCount());
        verifyNoInteractions(redissonClient);
    }

    @Test
    void watchdogManagedLockExecutesSupplierAndUnlocksWhenHeldByCurrentThread() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);
        AtomicInteger attempts = new AtomicInteger();

        long memberId = 9101L;
        String lockKey = "lock:first-login-reward:" + memberId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = gateway.withRewardLock(memberId, () -> {
            attempts.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, attempts.get());
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock).isHeldByCurrentThread();
        verify(lock).unlock();
    }

    @Test
    void lockFailureThrowsIllegalStateExceptionAndSkipsSupplierAndUnlock() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);
        AtomicInteger attempts = new AtomicInteger();

        long memberId = 9102L;
        String lockKey = "lock:first-login-reward:" + memberId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> gateway.withRewardLock(memberId, () -> {
                    attempts.incrementAndGet();
                    return "nope";
                })
        );

        assertEquals("Redis reward lock was not acquired before DB guard", exception.getMessage());
        assertEquals(0, attempts.get());
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock, never()).isHeldByCurrentThread();
        verify(lock, never()).unlock();
    }

    @Test
    void runtimeExceptionFromSupplierIsPropagatedAndLockIsReleasedIfHeldByCurrentThread() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);

        long memberId = 9103L;
        String lockKey = "lock:first-login-reward:" + memberId;
        RuntimeException failure = new RuntimeException("operation failed");

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> gateway.withRewardLock(memberId, () -> {
                    throw failure;
                })
        );

        assertSame(failure, exception);
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock).isHeldByCurrentThread();
        verify(lock).unlock();
    }

    @Test
    void interruptedExceptionFromTryLockIsMappedAndInterruptFlagIsRestored() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);
        AtomicInteger attempts = new AtomicInteger();

        long memberId = 9104L;
        String lockKey = "lock:first-login-reward:" + memberId;
        InterruptedException interrupted = new InterruptedException("interrupted during lock");

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenThrow(interrupted);

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> gateway.withRewardLock(memberId, () -> {
                        attempts.incrementAndGet();
                        return "ignored";
                    })
            );

            assertEquals("Interrupted while acquiring Redis reward lock", exception.getMessage());
            assertSame(interrupted, exception.getCause());
            assertEquals(1L, gateway.attemptCount());
            assertEquals(lockKey, gateway.lastLockKey());
            assertEquals(0, attempts.get());
            assertTrue(Thread.currentThread().isInterrupted());

            verify(redissonClient).getLock(lockKey);
            verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
            verify(lock, never()).isHeldByCurrentThread();
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void acquiredLockThatIsNotOwnedByCurrentThreadShouldNotUnlock() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisRewardLockGateway gateway = new RedisRewardLockGateway(redissonClient);
        AtomicInteger attempts = new AtomicInteger();

        long memberId = 9105L;
        String lockKey = "lock:first-login-reward:" + memberId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        String result = gateway.withRewardLock(memberId, () -> {
            attempts.incrementAndGet();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(1, attempts.get());
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock).isHeldByCurrentThread();
        verify(lock, never()).unlock();
    }
}
