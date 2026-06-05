package com.example.consistency.point;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.List;

public final class PointSpendScenarioRunner {

    private PointSpendScenarioRunner() {
    }

    public static ScenarioRunReport run(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount
    ) {
        return toReport(PointSpendRunner.run(strategy, memberId, initialBalance, spendAmount, requestCount));
    }

    public static ScenarioRunReport toReport(PointSpendRunResult result) {
        return new ScenarioRunReport(
                ScenarioType.POINT_SPEND,
                result.strategy(),
                InvariantChecker.evaluate(result.toInvariantResult()),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, result.acceptedCount()),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, result.completedCount()),
                        new ScenarioMetric(ScenarioMetricName.FINAL_POINT_BALANCE, result.finalBalance()),
                        new ScenarioMetric(ScenarioMetricName.NEGATIVE_BALANCE_COUNT, result.negativeBalanceCount()),
                        new ScenarioMetric(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT, result.ledgerEntryCount()),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, result.rejectedCount()),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT, result.idempotencyReplayCount()),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT, result.idempotencyHashMismatchCount()),
                        new ScenarioMetric(ScenarioMetricName.DB_WAIT_MS_P95, result.dbWaitMsP95())
                )
        );
    }
}
