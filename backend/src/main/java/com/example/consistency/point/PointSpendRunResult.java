package com.example.consistency.point;

import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public record PointSpendRunResult(
        StrategyType strategy,
        long memberId,
        long initialBalance,
        long spendAmount,
        long requestedCount,
        long acceptedCount,
        long completedCount,
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

    public InvariantResult toInvariantResult() {
        return new InvariantResult(
                ScenarioType.POINT_SPEND,
                strategy,
                negativeBalanceCount == 0,
                0,
                0,
                negativeBalanceCount,
                0,
                acceptedCount,
                completedCount,
                dbWaitMsP95,
                0,
                0,
                "point-spend run"
        );
    }
}
