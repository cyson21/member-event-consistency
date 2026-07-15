package com.example.consistency.web;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonWatchdogLockExecutorTest {

    @Test
    void usesWatchdogManagedTryLockOverloadAndUnlocksOwnedLock() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(75L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = RedissonWatchdogLockExecutor.execute(
                lock,
                75L,
                () -> "done",
                "not acquired",
                "interrupted"
        );

        assertEquals("done", result);
        verify(lock).tryLock(75L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void preservesOperationFailureWhenUnlockAlsoFails() throws InterruptedException {
        RLock lock = mock(RLock.class);
        IllegalStateException operationFailure = new IllegalStateException("operation failed");
        IllegalStateException unlockFailure = new IllegalStateException("unlock failed");
        when(lock.tryLock(75L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        org.mockito.Mockito.doThrow(unlockFailure).when(lock).unlock();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> RedissonWatchdogLockExecutor.execute(
                        lock,
                        75L,
                        () -> {
                            throw operationFailure;
                        },
                        "not acquired",
                        "interrupted"
                )
        );

        assertSame(operationFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(unlockFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void propagatesUnlockFailureWhenOperationSucceeded() throws InterruptedException {
        RLock lock = mock(RLock.class);
        IllegalStateException unlockFailure = new IllegalStateException("unlock failed");
        when(lock.tryLock(75L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        org.mockito.Mockito.doThrow(unlockFailure).when(lock).unlock();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> RedissonWatchdogLockExecutor.execute(
                        lock,
                        75L,
                        () -> "done",
                        "not acquired",
                        "interrupted"
                )
        );

        assertSame(unlockFailure, thrown);
    }

    @Test
    void restoresInterruptAndDoesNotAttemptUnlockWhenAcquisitionWasInterrupted() throws InterruptedException {
        RLock lock = mock(RLock.class);
        InterruptedException interruption = new InterruptedException("stop");
        when(lock.tryLock(75L, TimeUnit.MILLISECONDS)).thenThrow(interruption);

        try {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> RedissonWatchdogLockExecutor.execute(
                            lock,
                            75L,
                            () -> "ignored",
                            "not acquired",
                            "interrupted"
                    )
            );

            assertEquals("interrupted", thrown.getMessage());
            assertSame(interruption, thrown.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            verify(lock, never()).isHeldByCurrentThread();
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }
}
