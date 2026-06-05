package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardConcurrentProbeTest {

    public static void main(String[] args) {
        dbGuardProbeRejectsConcurrentDuplicateRewards();
        redisLockProbeUsesRewardScopedLockAndDbGuard();
        unsupportedStrategyIsRejected();
    }

    private static void dbGuardProbeRejectsConcurrentDuplicateRewards() {
        FirstLoginRewardConcurrentProbeResult result = FirstLoginRewardConcurrentProbe.run(
                StrategyType.DB_GUARD,
                93011L,
                8
        );

        assertEquals(true, result.invariantPassed(), "DB guard invariant");
        assertEquals(8, result.requestCount(), "DB guard request count");
        assertEquals(1L, result.rewardIssuedCount(), "DB guard issued count");
        assertEquals(7L, result.rejectedCount(), "DB guard rejected count");
        assertEquals(0L, result.duplicateRewardCount(), "DB guard duplicate count");
        assertEquals(0L, result.redisLockAttemptCount(), "DB guard lock attempts");
        assertEquals(1L, result.afterCommitNotificationCount(), "DB guard fake notification count");
        assertEquals(1L, result.outboxEventCount(), "DB guard outbox count");
    }

    private static void redisLockProbeUsesRewardScopedLockAndDbGuard() {
        FirstLoginRewardConcurrentProbeResult result = FirstLoginRewardConcurrentProbe.run(
                StrategyType.REDIS_LOCK_DB_GUARD,
                93012L,
                8
        );

        assertEquals(true, result.invariantPassed(), "Redis plus DB guard invariant");
        assertEquals(1L, result.rewardIssuedCount(), "Redis plus DB guard issued count");
        assertEquals(7L, result.rejectedCount(), "Redis plus DB guard rejected count");
        assertEquals(0L, result.duplicateRewardCount(), "Redis plus DB guard duplicate count");
        assertEquals(8L, result.redisLockAttemptCount(), "Redis plus DB guard lock attempts");
        assertEquals("lock:first-login-reward:93012", result.lockKey(), "reward-scoped lock key");
        assertEquals(1L, result.afterCommitNotificationCount(), "Redis plus DB guard fake notification count");
        assertEquals(1L, result.outboxEventCount(), "Redis plus DB guard outbox count");
    }

    private static void unsupportedStrategyIsRejected() {
        try {
            FirstLoginRewardConcurrentProbe.run(StrategyType.RABBITMQ_DB_GUARD, 93013L, 2);
            throw new AssertionError("unsupported strategy should fail");
        } catch (IllegalArgumentException exception) {
            assertEquals(
                    "strategy is not supported for concurrent First Login Reward probe: RABBITMQ_DB_GUARD",
                    exception.getMessage(),
                    "unsupported strategy message"
            );
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
