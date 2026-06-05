package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.List;

public final class BatchExpirationScenarioRunner {

    private BatchExpirationScenarioRunner() {
    }

    public static ScenarioRunReport run(
            StrategyType strategy,
            long couponIssueId,
            BatchExpirationWinner winner
    ) {
        return toReport(BatchExpirationRunner.run(strategy, couponIssueId, winner));
    }

    public static ScenarioRunReport toReport(BatchExpirationRunResult result) {
        return new ScenarioRunReport(
                ScenarioType.BATCH_EXPIRATION,
                result.strategy(),
                InvariantChecker.evaluate(result.toInvariantResult()),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, result.acceptedCount()),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, result.completedCount()),
                        new ScenarioMetric(ScenarioMetricName.COUPON_USED_COUNT, result.usedCount()),
                        new ScenarioMetric(ScenarioMetricName.COUPON_EXPIRED_COUNT, result.expiredCount()),
                        new ScenarioMetric(
                                ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT,
                                result.terminalStateConflictCount()
                        ),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, result.rejectedCount())
                )
        );
    }
}
