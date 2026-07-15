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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCouponCampaignLockGatewayTest {

    @Test
    void watchdogManagedLockExecutesSupplierOnceAndUnlocksWhenHeldByCurrentThread() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisCouponCampaignLockGateway gateway = new RedisCouponCampaignLockGateway(redissonClient);
        AtomicInteger supplierCalls = new AtomicInteger();

        long campaignId = 101L;
        String lockKey = "lock:coupon-campaign:" + campaignId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = gateway.withCampaignLock(campaignId, () -> {
            supplierCalls.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, supplierCalls.get());
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock).isHeldByCurrentThread();
        verify(lock).unlock();
    }

    @Test
    void lockFailureThrowsIllegalStateExceptionAndDoesNotCallSupplierOrUnlock() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisCouponCampaignLockGateway gateway = new RedisCouponCampaignLockGateway(redissonClient);
        AtomicInteger supplierCalls = new AtomicInteger();

        long campaignId = 102L;
        String lockKey = "lock:coupon-campaign:" + campaignId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> gateway.withCampaignLock(campaignId, () -> {
                    supplierCalls.incrementAndGet();
                    return "ignored";
                })
        );

        assertEquals("Redis coupon campaign lock was not acquired before DB guard", exception.getMessage());
        assertEquals(0, supplierCalls.get());
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock, never()).isHeldByCurrentThread();
        verify(lock, never()).unlock();
    }

    @Test
    void supplierExceptionIsPropagatedAndLockUnlockedOnlyWhenHeldByCurrentThread() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisCouponCampaignLockGateway gateway = new RedisCouponCampaignLockGateway(redissonClient);

        long campaignId = 103L;
        String lockKey = "lock:coupon-campaign:" + campaignId;
        RuntimeException failure = new RuntimeException("campaign operation failed");

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> gateway.withCampaignLock(campaignId, () -> {
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
    void interruptedExceptionFromTryLockPropagatesAndRestoresInterruptFlag() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisCouponCampaignLockGateway gateway = new RedisCouponCampaignLockGateway(redissonClient);
        AtomicInteger supplierCalls = new AtomicInteger();
        InterruptedException interrupted = new InterruptedException("interrupted while waiting");

        long campaignId = 104L;
        String lockKey = "lock:coupon-campaign:" + campaignId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenThrow(interrupted);

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> gateway.withCampaignLock(campaignId, () -> {
                        supplierCalls.incrementAndGet();
                        return "ignored";
                    })
            );

            assertEquals("Interrupted while acquiring Redis coupon campaign lock", exception.getMessage());
            assertSame(interrupted, exception.getCause());
            assertEquals(0, supplierCalls.get());
            assertEquals(1L, gateway.attemptCount());
            assertEquals(lockKey, gateway.lastLockKey());
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
    void acquiredLockThatIsNotHeldByCurrentThreadIsNotUnlocked() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RedisCouponCampaignLockGateway gateway = new RedisCouponCampaignLockGateway(redissonClient);
        AtomicInteger supplierCalls = new AtomicInteger();

        long campaignId = 105L;
        String lockKey = "lock:coupon-campaign:" + campaignId;

        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        String result = gateway.withCampaignLock(campaignId, () -> {
            supplierCalls.incrementAndGet();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(1, supplierCalls.get());
        assertEquals(1L, gateway.attemptCount());
        assertEquals(lockKey, gateway.lastLockKey());

        verify(redissonClient).getLock(lockKey);
        verify(lock).tryLock(100, TimeUnit.MILLISECONDS);
        verify(lock).isHeldByCurrentThread();
        verify(lock, never()).unlock();
    }
}
