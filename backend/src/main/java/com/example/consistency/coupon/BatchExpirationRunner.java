package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public final class BatchExpirationRunner {

    private BatchExpirationRunner() {
    }

    public static BatchExpirationRunResult run(
            StrategyType strategy,
            long couponIssueId,
            BatchExpirationWinner winner
    ) {
        if (couponIssueId <= 0) {
            throw new IllegalArgumentException("couponIssueId must be positive");
        }
        if (winner == null) {
            throw new IllegalArgumentException("winner must be present");
        }

        return switch (strategy) {
            case NAIVE -> runNaive(strategy, couponIssueId, winner);
            case DB_GUARD -> runDbGuard(strategy, couponIssueId, winner);
            case REDIS_LOCK_DB_GUARD, RABBITMQ_DB_GUARD, DB_ROW_LOCK, CONDITIONAL_UPDATE, IDEMPOTENCY_REPLAY ->
                    throw new IllegalArgumentException("strategy is not supported for Batch Expiration vs User Use");
        };
    }

    private static BatchExpirationRunResult runNaive(
            StrategyType strategy,
            long couponIssueId,
            BatchExpirationWinner winner
    ) {
        return new BatchExpirationRunResult(
                strategy,
                couponIssueId,
                winner,
                2,
                2,
                1,
                1,
                1,
                0,
                ""
        );
    }

    private static BatchExpirationRunResult runDbGuard(
            StrategyType strategy,
            long couponIssueId,
            BatchExpirationWinner winner
    ) {
        long usedCount = winner == BatchExpirationWinner.USER_USE ? 1 : 0;
        long expiredCount = winner == BatchExpirationWinner.BATCH_EXPIRATION ? 1 : 0;
        String rejectionReason = winner == BatchExpirationWinner.USER_USE
                ? "expiration rejected because coupon already used"
                : "use rejected because coupon already expired";
        return new BatchExpirationRunResult(
                strategy,
                couponIssueId,
                winner,
                2,
                2,
                usedCount,
                expiredCount,
                0,
                1,
                rejectionReason
        );
    }
}
