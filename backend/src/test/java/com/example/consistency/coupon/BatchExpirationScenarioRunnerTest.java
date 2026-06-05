package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class BatchExpirationScenarioRunnerTest {

    public static void main(String[] args) {
        naiveScenarioAllowsUseAndExpirationAndFailsInvariant();
        dbGuardScenarioAllowsOneTerminalStateAndReportsRejectedExpiration();
    }

    private static void naiveScenarioAllowsUseAndExpirationAndFailsInvariant() {
        ScenarioRunReport report = BatchExpirationScenarioRunner.run(
                StrategyType.NAIVE,
                12001L,
                BatchExpirationWinner.USER_USE
        );

        assertEquals(ScenarioType.BATCH_EXPIRATION, report.scenario(), "scenario is fixed");
        assertEquals(StrategyType.NAIVE, report.strategy(), "strategy is captured");
        assertEquals(false, report.invariant().passed(), "naive use/expiration race must fail invariant");
        assertEquals(2L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "use and expiration are accepted");
        assertEquals(2L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
        assertEquals(1L, report.metricValue(ScenarioMetricName.COUPON_USED_COUNT), "use terminal state is recorded");
        assertEquals(1L, report.metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration terminal state is recorded");
        assertEquals(1L, report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict is visible");
        assertEquals(0L, report.metricValue(ScenarioMetricName.REJECTED_COUNT), "naive path does not reject either terminal transition");
    }

    private static void dbGuardScenarioAllowsOneTerminalStateAndReportsRejectedExpiration() {
        BatchExpirationRunResult result = BatchExpirationRunner.run(
                StrategyType.DB_GUARD,
                12002L,
                BatchExpirationWinner.USER_USE
        );
        ScenarioRunReport report = BatchExpirationScenarioRunner.toReport(result);

        assertEquals(ScenarioType.BATCH_EXPIRATION, report.scenario(), "scenario is fixed");
        assertEquals(true, report.invariant().passed(), "guarded use/expiration race must pass invariant");
        assertEquals(1L, report.metricValue(ScenarioMetricName.COUPON_USED_COUNT), "use wins once");
        assertEquals(0L, report.metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration does not also win");
        assertEquals(0L, report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict stays zero");
        assertEquals(1L, report.metricValue(ScenarioMetricName.REJECTED_COUNT), "batch expiration is rejected");
        assertEquals(
                "expiration rejected because coupon already used",
                result.rejectionReason(),
                "guarded path reports visible rejection reason"
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
