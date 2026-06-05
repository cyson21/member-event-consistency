package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

public record PointSpendCommand(
        long memberId,
        long spendAmount,
        StrategyType strategy,
        String idempotencyKey,
        String requestHash
) {
}

