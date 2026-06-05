package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionServiceScenarioExecutorTest {

    public static void main(String[] args) {
        dbGuardServiceExecutorPersistsSingleTerminalUse();
        idempotencyReplayServiceExecutorReportsReplay();
        idempotencyReplayServiceExecutorReportsRequestHashMismatch();
    }

    private static void dbGuardServiceExecutorPersistsSingleTerminalUse() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        CouponRedemptionServiceScenarioExecutor executor = new CouponRedemptionServiceScenarioExecutor(
                new CouponRedemptionService(new InMemoryCouponRedemptionRepository()),
                reports
        );

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 10005L, 2);

        assertEquals(1L, reports.count(), "service executor persists one report");
        assertEquals(true, record.report().invariant().passed(), "DB guard service run passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "coupon is used once");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "second terminal transition is rejected");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.DOUBLE_USE_COUNT), "double use stays zero");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict stays zero");
    }

    private static void idempotencyReplayServiceExecutorReportsReplay() {
        CouponRedemptionServiceScenarioExecutor executor = new CouponRedemptionServiceScenarioExecutor(
                new CouponRedemptionService(new InMemoryCouponRedemptionRepository()),
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.executeWithIdempotency(
                StrategyType.IDEMPOTENCY_REPLAY,
                10006L,
                "redeem-10006",
                "member=501|coupon=10006",
                "member=501|coupon=10006"
        );

        assertEquals(true, record.report().invariant().passed(), "same request replay passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "coupon is used once");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "same request retry is replayed");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "same request retry has no mismatch");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "same request retry is not rejected");
    }

    private static void idempotencyReplayServiceExecutorReportsRequestHashMismatch() {
        CouponRedemptionServiceScenarioExecutor executor = new CouponRedemptionServiceScenarioExecutor(
                new CouponRedemptionService(new InMemoryCouponRedemptionRepository()),
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.executeWithIdempotency(
                StrategyType.IDEMPOTENCY_REPLAY,
                10007L,
                "redeem-10007",
                "member=501|coupon=10007",
                "member=502|coupon=10007"
        );

        assertEquals(true, record.report().invariant().passed(), "mismatch is rejected before double use");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "coupon is used once");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "mismatch is not replayed");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "mismatch is reported");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "mismatch retry is rejected");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
