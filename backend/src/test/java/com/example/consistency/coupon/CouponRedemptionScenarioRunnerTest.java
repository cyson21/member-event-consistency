package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionScenarioRunnerTest {

    public static void main(String[] args) {
        naiveScenarioAllowsDoubleUseAndFailsInvariant();
        dbGuardScenarioAllowsOneTerminalStateAndRejectsRaces();
        idempotencyReplayScenarioUsesCouponOnceAndReportsReplay();
        idempotencyReplayScenarioRejectsRequestHashMismatch();
    }

    private static void naiveScenarioAllowsDoubleUseAndFailsInvariant() {
        ScenarioRunReport report = CouponRedemptionScenarioRunner.run(StrategyType.NAIVE, 10001L, 2);

        assertEquals(ScenarioType.COUPON_REDEMPTION, report.scenario(), "scenario is fixed");
        assertEquals(StrategyType.NAIVE, report.strategy(), "strategy is captured");
        assertEquals(false, report.invariant().passed(), "naive redemption path must fail invariant");
        assertEquals(2L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count is recorded");
        assertEquals(2L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
        assertEquals(2L, report.metricValue(ScenarioMetricName.COUPON_USED_COUNT), "naive path double-uses the coupon");
        assertEquals(1L, report.metricValue(ScenarioMetricName.DOUBLE_USE_COUNT), "double-use count is recorded");
        assertEquals(1L, report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict count is recorded");
    }

    private static void dbGuardScenarioAllowsOneTerminalStateAndRejectsRaces() {
        ScenarioRunReport report = CouponRedemptionScenarioRunner.run(StrategyType.DB_GUARD, 10002L, 2);

        assertEquals(ScenarioType.COUPON_REDEMPTION, report.scenario(), "scenario is fixed");
        assertEquals(true, report.invariant().passed(), "DB guard redemption scenario must pass invariant");
        assertEquals(1L, report.metricValue(ScenarioMetricName.COUPON_USED_COUNT), "guarded path uses coupon once");
        assertEquals(0L, report.metricValue(ScenarioMetricName.DOUBLE_USE_COUNT), "double-use count stays zero");
        assertEquals(0L, report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict count stays zero");
        assertEquals(1L, report.metricValue(ScenarioMetricName.REJECTED_COUNT), "racing transition is rejected");
        assertEquals(0L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "first slice has no replay");
        assertEquals(0L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "first slice has no hash mismatch");
    }

    private static void idempotencyReplayScenarioUsesCouponOnceAndReportsReplay() {
        ScenarioRunReport report = CouponRedemptionScenarioRunner.runWithIdempotency(
                StrategyType.IDEMPOTENCY_REPLAY,
                10003L,
                "redeem-10003",
                "member=501|coupon=10003",
                "member=501|coupon=10003"
        );

        assertEquals(ScenarioType.COUPON_REDEMPTION, report.scenario(), "scenario is fixed");
        assertEquals(true, report.invariant().passed(), "idempotency replay must preserve one coupon use");
        assertEquals(2L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "original and retry are accepted by the local harness");
        assertEquals(2L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
        assertEquals(1L, report.metricValue(ScenarioMetricName.COUPON_USED_COUNT), "same-key retry uses coupon once");
        assertEquals(0L, report.metricValue(ScenarioMetricName.DOUBLE_USE_COUNT), "same-key retry does not double-use");
        assertEquals(0L, report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "same-key retry has no terminal conflict");
        assertEquals(1L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "same-key retry is reported as replay");
        assertEquals(0L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "same hash has no mismatch");
    }

    private static void idempotencyReplayScenarioRejectsRequestHashMismatch() {
        ScenarioRunReport report = CouponRedemptionScenarioRunner.runWithIdempotency(
                StrategyType.IDEMPOTENCY_REPLAY,
                10004L,
                "redeem-10004",
                "member=501|coupon=10004",
                "member=502|coupon=10004"
        );

        assertEquals(true, report.invariant().passed(), "hash mismatch is rejected before an additional coupon use");
        assertEquals(2L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "original and retry are accepted by the local harness");
        assertEquals(2L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
        assertEquals(1L, report.metricValue(ScenarioMetricName.COUPON_USED_COUNT), "mismatched retry does not use coupon again");
        assertEquals(0L, report.metricValue(ScenarioMetricName.DOUBLE_USE_COUNT), "mismatched retry does not double-use");
        assertEquals(0L, report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "mismatched retry has no terminal conflict");
        assertEquals(0L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "mismatched retry is not a replay");
        assertEquals(1L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "mismatched retry is reported");
        assertEquals(1L, report.metricValue(ScenarioMetricName.REJECTED_COUNT), "mismatched retry is rejected");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
