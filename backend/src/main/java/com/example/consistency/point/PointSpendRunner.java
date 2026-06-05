package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

public final class PointSpendRunner {

    private PointSpendRunner() {
    }

    public static PointSpendRunResult run(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount
    ) {
        return runWithIdempotencyKey(strategy, memberId, initialBalance, spendAmount, requestCount, "");
    }

    public static PointSpendRunResult runWithIdempotencyKey(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey
    ) {
        if (memberId <= 0 || initialBalance < 0 || spendAmount <= 0 || requestCount < 0) {
            throw new IllegalArgumentException("memberId and spendAmount must be positive, counts and balance must be valid");
        }

        return switch (strategy) {
            case NAIVE -> runNaive(strategy, memberId, initialBalance, spendAmount, requestCount, idempotencyKey);
            case DB_ROW_LOCK -> runGuarded(strategy, memberId, initialBalance, spendAmount, requestCount, idempotencyKey, 15);
            case CONDITIONAL_UPDATE -> runGuarded(strategy, memberId, initialBalance, spendAmount, requestCount, idempotencyKey, 0);
            case IDEMPOTENCY_REPLAY -> runIdempotencyReplay(
                    strategy,
                    memberId,
                    initialBalance,
                    spendAmount,
                    requestCount,
                    idempotencyKey
            );
            case DB_GUARD, REDIS_LOCK_DB_GUARD, RABBITMQ_DB_GUARD ->
                    throw new IllegalArgumentException("strategy is not supported for Point Spend");
        };
    }

    private static PointSpendRunResult runNaive(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey
    ) {
        long successfulSpendCount = requestCount;
        long finalBalance = initialBalance - (spendAmount * successfulSpendCount);
        return result(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount,
                successfulSpendCount,
                0,
                finalBalance,
                finalBalance < 0 ? 1 : 0,
                successfulSpendCount,
                0,
                0,
                idempotencyKey,
                0
        );
    }

    private static PointSpendRunResult runGuarded(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey,
            long dbWaitMsP95
    ) {
        long successfulSpendCount = Math.min(requestCount, initialBalance / spendAmount);
        long rejectedCount = requestCount - successfulSpendCount;
        long finalBalance = initialBalance - (spendAmount * successfulSpendCount);
        return result(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount,
                successfulSpendCount,
                rejectedCount,
                finalBalance,
                0,
                successfulSpendCount,
                0,
                0,
                idempotencyKey,
                dbWaitMsP95
        );
    }

    private static PointSpendRunResult runIdempotencyReplay(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey
    ) {
        long successfulSpendCount = requestCount == 0 ? 0 : 1;
        long replayCount = Math.max(0, requestCount - successfulSpendCount);
        long finalBalance = initialBalance - (spendAmount * successfulSpendCount);
        return result(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount,
                successfulSpendCount,
                0,
                finalBalance,
                finalBalance < 0 ? 1 : 0,
                successfulSpendCount,
                replayCount,
                0,
                idempotencyKey,
                0
        );
    }

    private static PointSpendRunResult result(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            long successfulSpendCount,
            long rejectedCount,
            long finalBalance,
            long negativeBalanceCount,
            long ledgerEntryCount,
            long idempotencyReplayCount,
            long idempotencyHashMismatchCount,
            String idempotencyKey,
            long dbWaitMsP95
    ) {
        return new PointSpendRunResult(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount,
                requestCount,
                requestCount,
                successfulSpendCount,
                rejectedCount,
                finalBalance,
                negativeBalanceCount,
                ledgerEntryCount,
                idempotencyReplayCount,
                idempotencyHashMismatchCount,
                idempotencyKey,
                dbWaitMsP95
        );
    }
}
