package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignRunner {

    private CouponCampaignRunner() {
    }

    public static CouponCampaignRunResult run(StrategyType strategy, long campaignId, int capacity, int requestCount) {
        if (campaignId <= 0 || capacity < 0 || requestCount < 0) {
            throw new IllegalArgumentException("campaignId must be positive and counts must be non-negative");
        }

        return switch (strategy) {
            case NAIVE -> runNaive(strategy, campaignId, capacity, requestCount);
            case DB_GUARD -> runGuarded(strategy, campaignId, capacity, requestCount, 0, "", 0, 0, 0, 0);
            case REDIS_LOCK_DB_GUARD -> runGuarded(
                    strategy,
                    campaignId,
                    capacity,
                    requestCount,
                    requestCount,
                    "lock:coupon-campaign:" + campaignId,
                    0,
                    0,
                    0,
                    0
            );
            case RABBITMQ_DB_GUARD -> runGuarded(
                    strategy,
                    campaignId,
                    capacity,
                    requestCount,
                    0,
                    "",
                    1,
                    0,
                    0,
                    Math.max(1, requestCount - 1) * 10L
            );
            case DB_ROW_LOCK, CONDITIONAL_UPDATE, IDEMPOTENCY_REPLAY ->
                    throw new IllegalArgumentException("strategy is not supported for Coupon Campaign Issue");
        };
    }

    private static CouponCampaignRunResult runNaive(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount
    ) {
        long issuedCount = requestCount;
        long overIssueCount = Math.max(0, issuedCount - capacity);
        return new CouponCampaignRunResult(
                strategy,
                campaignId,
                capacity,
                requestCount,
                requestCount,
                requestCount,
                issuedCount,
                overIssueCount,
                0,
                0,
                "",
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    public static CouponCampaignRunResult runRabbitMqWithWorkerFailures(
            long campaignId,
            int capacity,
            int requestCount,
            int transientRetryCount,
            int dlqCount
    ) {
        if (campaignId <= 0 || capacity < 0 || requestCount < 0 || transientRetryCount < 0 || dlqCount < 0) {
            throw new IllegalArgumentException("campaignId must be positive and counts must be non-negative");
        }
        if (dlqCount > requestCount) {
            throw new IllegalArgumentException("dlqCount cannot exceed requestCount");
        }

        int processableCommandCount = requestCount - dlqCount;
        long issuedCount = Math.min(capacity, processableCommandCount);
        long rejectedCount = processableCommandCount - issuedCount;
        long queueLagMsP95 = Math.max(1, requestCount + transientRetryCount + dlqCount) * 10L;

        return new CouponCampaignRunResult(
                StrategyType.RABBITMQ_DB_GUARD,
                campaignId,
                capacity,
                requestCount,
                requestCount,
                requestCount,
                issuedCount,
                0,
                rejectedCount,
                0,
                "",
                1,
                transientRetryCount,
                dlqCount,
                queueLagMsP95,
                rabbitMqAcceptedLatencyMs(1),
                rabbitMqCompletionLatencyMs(rabbitMqAcceptedLatencyMs(1), queueLagMsP95)
        );
    }

    private static CouponCampaignRunResult runGuarded(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount,
            long redisLockAttemptCount,
            String lockKey,
            long rabbitMqLaneCount,
            long queueRetryCount,
            long dlqCount,
            long queueLagMsP95
    ) {
        long issuedCount = Math.min(capacity, requestCount);
        long rejectedCount = requestCount - issuedCount;
        long rabbitMqAcceptedLatencyMs = rabbitMqLaneCount > 0 ? rabbitMqAcceptedLatencyMs(rabbitMqLaneCount) : 0;
        long rabbitMqCompletionLatencyMs = rabbitMqLaneCount > 0
                ? rabbitMqCompletionLatencyMs(rabbitMqAcceptedLatencyMs, queueLagMsP95)
                : 0;
        return new CouponCampaignRunResult(
                strategy,
                campaignId,
                capacity,
                requestCount,
                requestCount,
                requestCount,
                issuedCount,
                0,
                rejectedCount,
                redisLockAttemptCount,
                lockKey,
                rabbitMqLaneCount,
                queueRetryCount,
                dlqCount,
                queueLagMsP95,
                rabbitMqAcceptedLatencyMs,
                rabbitMqCompletionLatencyMs
        );
    }

    private static long rabbitMqAcceptedLatencyMs(long rabbitMqLaneCount) {
        return rabbitMqLaneCount > 0 ? 12L : 0L;
    }

    private static long rabbitMqCompletionLatencyMs(long acceptedLatencyMs, long queueLagMsP95) {
        return acceptedLatencyMs + queueLagMsP95;
    }
}
