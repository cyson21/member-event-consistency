package com.example.consistency.point;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public interface PointSpendScenarioExecution {

    ScenarioRunRecord execute(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount
    );

    ScenarioRunRecord executeWithIdempotencyKey(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey
    );
}
