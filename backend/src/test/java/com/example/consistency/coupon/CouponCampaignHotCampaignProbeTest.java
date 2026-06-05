package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignHotCampaignProbeTest {

    public static void main(String[] args) {
        dbGuardProbeKeepsIssueCountAtCapacity();
        redisLockProbeKeepsCampaignScopedLockAttemptsVisible();
        rabbitMqProbeKeepsSingleLaneEvidenceExplicit();
        unsupportedStrategyIsRejected();
    }

    private static void dbGuardProbeKeepsIssueCountAtCapacity() {
        CouponCampaignHotCampaignProbeResult result = CouponCampaignHotCampaignProbe.run(
                StrategyType.DB_GUARD,
                98001L,
                3,
                9
        );

        assertEquals(true, result.invariantPassed(), "DB guard invariant");
        assertEquals(9, result.requestCount(), "request count");
        assertEquals(3L, result.issuedCount(), "issued count stops at capacity");
        assertEquals(6L, result.rejectedCount(), "capacity rejection count");
        assertEquals(0L, result.overIssueCount(), "over issue count");
        assertEquals(0L, result.redisLockAttemptCount(), "DB guard does not claim Redis lock");
        assertEquals(0L, result.rabbitMqLaneCount(), "DB guard does not claim RabbitMQ lane");
    }

    private static void redisLockProbeKeepsCampaignScopedLockAttemptsVisible() {
        CouponCampaignHotCampaignProbeResult result = CouponCampaignHotCampaignProbe.run(
                StrategyType.REDIS_LOCK_DB_GUARD,
                98002L,
                3,
                9
        );

        assertEquals(true, result.invariantPassed(), "Redis lock plus DB guard invariant");
        assertEquals(3L, result.issuedCount(), "issued count stops at capacity");
        assertEquals(6L, result.rejectedCount(), "rejection count");
        assertEquals(9L, result.redisLockAttemptCount(), "one campaign lock attempt per command");
        assertEquals("lock:coupon-campaign:98002", result.lockKey(), "campaign-specific lock key");
        assertEquals(0L, result.overIssueCount(), "DB capacity guard remains final defense");
    }

    private static void rabbitMqProbeKeepsSingleLaneEvidenceExplicit() {
        CouponCampaignHotCampaignProbeResult result = CouponCampaignHotCampaignProbe.run(
                StrategyType.RABBITMQ_DB_GUARD,
                98003L,
                3,
                9
        );

        assertEquals(true, result.invariantPassed(), "RabbitMQ plus DB guard invariant");
        assertEquals(9L, result.acceptedCount(), "accepted count");
        assertEquals(9L, result.completedCount(), "completed count");
        assertEquals(3L, result.issuedCount(), "issued count stops at capacity");
        assertEquals(6L, result.rejectedCount(), "capacity rejection count");
        assertEquals(1L, result.rabbitMqLaneCount(), "single lane evidence");
        assertEquals(true, result.completionLatencyMs() > result.acceptedLatencyMs(), "completion latency remains separate");
    }

    private static void unsupportedStrategyIsRejected() {
        try {
            CouponCampaignHotCampaignProbe.run(StrategyType.NAIVE, 98004L, 3, 9);
            throw new AssertionError("unsupported strategy should fail");
        } catch (IllegalArgumentException exception) {
            assertEquals(
                    "strategy is not supported for hot campaign probe: NAIVE",
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
