package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public record BatchExpirationRunResult(
        StrategyType strategy,
        long couponIssueId,
        BatchExpirationWinner winner,
        long acceptedCount,
        long completedCount,
        long usedCount,
        long expiredCount,
        long terminalStateConflictCount,
        long rejectedCount,
        String rejectionReason
) {

    public InvariantResult toInvariantResult() {
        return new InvariantResult(
                ScenarioType.BATCH_EXPIRATION,
                strategy,
                terminalStateConflictCount == 0,
                0,
                0,
                0,
                0,
                terminalStateConflictCount,
                0,
                acceptedCount,
                completedCount,
                0,
                0,
                0,
                rejectionReason.isBlank() ? "batch-expiration run" : rejectionReason
        );
    }
}
