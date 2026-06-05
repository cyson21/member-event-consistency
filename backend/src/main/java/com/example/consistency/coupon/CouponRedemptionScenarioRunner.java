package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.List;

public final class CouponRedemptionScenarioRunner {

    private CouponRedemptionScenarioRunner() {
    }

    public static ScenarioRunReport run(StrategyType strategy, long couponIssueId, int requestCount) {
        return toReport(CouponRedemptionRunner.run(strategy, couponIssueId, requestCount));
    }

    public static ScenarioRunReport runWithIdempotency(
            StrategyType strategy,
            long couponIssueId,
            String idempotencyKey,
            String firstRequestHash,
            String retryRequestHash
    ) {
        return toReport(CouponRedemptionRunner.runWithIdempotency(
                strategy,
                couponIssueId,
                idempotencyKey,
                firstRequestHash,
                retryRequestHash
        ));
    }

    public static ScenarioRunReport toReport(CouponRedemptionRunResult result) {
        return new ScenarioRunReport(
                ScenarioType.COUPON_REDEMPTION,
                result.strategy(),
                InvariantChecker.evaluate(result.toInvariantResult()),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, result.acceptedCount()),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, result.completedCount()),
                        new ScenarioMetric(ScenarioMetricName.COUPON_USED_COUNT, result.usedCount()),
                        new ScenarioMetric(ScenarioMetricName.DOUBLE_USE_COUNT, result.doubleUseCount()),
                        new ScenarioMetric(
                                ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT,
                                result.terminalStateConflictCount()
                        ),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, result.rejectedCount()),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT, result.idempotencyReplayCount()),
                        new ScenarioMetric(
                                ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT,
                                result.idempotencyHashMismatchCount()
                        )
                )
        );
    }
}
