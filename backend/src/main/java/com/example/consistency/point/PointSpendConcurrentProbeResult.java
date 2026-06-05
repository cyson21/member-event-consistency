package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

public record PointSpendConcurrentProbeResult(
        StrategyType strategy,
        long memberId,
        long initialBalance,
        long spendAmount,
        int requestCount,
        long successfulSpendCount,
        long rejectedCount,
        long finalPointBalance,
        long negativeBalanceCount,
        long dbWaitMsP95
) {
    public boolean invariantPassed() {
        return negativeBalanceCount == 0 && finalPointBalance >= 0;
    }
}
