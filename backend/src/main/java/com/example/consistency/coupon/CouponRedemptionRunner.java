package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionRunner {

    private CouponRedemptionRunner() {
    }

    public static CouponRedemptionRunResult run(StrategyType strategy, long couponIssueId, int requestCount) {
        if (couponIssueId <= 0 || requestCount < 0) {
            throw new IllegalArgumentException("couponIssueId must be positive and requestCount must be non-negative");
        }

        return switch (strategy) {
            case NAIVE -> runNaive(strategy, couponIssueId, requestCount);
            case DB_GUARD -> runDbGuard(strategy, couponIssueId, requestCount);
            case REDIS_LOCK_DB_GUARD, RABBITMQ_DB_GUARD, DB_ROW_LOCK, CONDITIONAL_UPDATE, IDEMPOTENCY_REPLAY ->
                    throw new IllegalArgumentException("strategy is not supported for Coupon Redemption / Usage");
        };
    }

    public static CouponRedemptionRunResult runWithIdempotency(
            StrategyType strategy,
            long couponIssueId,
            String idempotencyKey,
            String firstRequestHash,
            String retryRequestHash
    ) {
        if (strategy != StrategyType.IDEMPOTENCY_REPLAY) {
            throw new IllegalArgumentException("strategy must be IDEMPOTENCY_REPLAY for Coupon Redemption idempotency evidence");
        }
        if (couponIssueId <= 0 || idempotencyKey.isBlank() || firstRequestHash.isBlank() || retryRequestHash.isBlank()) {
            throw new IllegalArgumentException("couponIssueId must be positive and idempotency inputs must be present");
        }

        boolean sameRequestHash = firstRequestHash.equals(retryRequestHash);
        return new CouponRedemptionRunResult(
                strategy,
                couponIssueId,
                2,
                2,
                2,
                1,
                0,
                0,
                sameRequestHash ? 0 : 1,
                sameRequestHash ? 1 : 0,
                sameRequestHash ? 0 : 1
        );
    }

    private static CouponRedemptionRunResult runNaive(
            StrategyType strategy,
            long couponIssueId,
            int requestCount
    ) {
        long usedCount = requestCount;
        long duplicateTerminalAttempts = Math.max(0, usedCount - 1);
        return new CouponRedemptionRunResult(
                strategy,
                couponIssueId,
                requestCount,
                requestCount,
                requestCount,
                usedCount,
                duplicateTerminalAttempts,
                duplicateTerminalAttempts,
                0,
                0,
                0
        );
    }

    private static CouponRedemptionRunResult runDbGuard(
            StrategyType strategy,
            long couponIssueId,
            int requestCount
    ) {
        long usedCount = Math.min(1, requestCount);
        long rejectedCount = requestCount - usedCount;
        return new CouponRedemptionRunResult(
                strategy,
                couponIssueId,
                requestCount,
                requestCount,
                requestCount,
                usedCount,
                0,
                0,
                rejectedCount,
                0,
                0
        );
    }
}
