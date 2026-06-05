package com.example.consistency.point;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class PointSpendScenarioRunnerTest {

    public static void main(String[] args) {
        naiveScenarioReportShowsNegativeBalanceInvariantFailure();
        rowLockScenarioReportKeepsBalanceNonNegative();
        conditionalUpdateScenarioReportRejectsOverspendWithoutLockMetric();
        idempotencyReplayScenarioReportAvoidsDoubleSpendForSameKey();
    }

    private static void naiveScenarioReportShowsNegativeBalanceInvariantFailure() {
        ScenarioRunReport report = PointSpendScenarioRunner.run(StrategyType.NAIVE, 7001L, 1000, 700, 2);

        assertEquals(ScenarioType.POINT_SPEND, report.scenario(), "scenario is fixed");
        assertEquals(StrategyType.NAIVE, report.strategy(), "strategy is captured");
        assertEquals(false, report.invariant().passed(), "naive point spend must fail invariant");
        assertEquals(2L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count is recorded");
        assertEquals(2L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
        assertEquals(-400L, report.metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "naive path can go negative");
        assertEquals(1L, report.metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT), "negative balance count is recorded");
        assertEquals(2L, report.metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "naive path writes both ledger entries");
    }

    private static void rowLockScenarioReportKeepsBalanceNonNegative() {
        ScenarioRunReport report = PointSpendScenarioRunner.run(StrategyType.DB_ROW_LOCK, 7002L, 1000, 700, 2);

        assertEquals(true, report.invariant().passed(), "row lock scenario must pass invariant");
        assertEquals(300L, report.metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "balance stops after one spend");
        assertEquals(0L, report.metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT), "negative balance count stays zero");
        assertEquals(1L, report.metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "only successful spend is recorded");
        assertEquals(1L, report.metricValue(ScenarioMetricName.REJECTED_COUNT), "insufficient balance rejection is recorded");
        assertEquals(true, report.metricValue(ScenarioMetricName.DB_WAIT_MS_P95) > 0, "row lock wait metric is recorded");
    }

    private static void conditionalUpdateScenarioReportRejectsOverspendWithoutLockMetric() {
        ScenarioRunReport report = PointSpendScenarioRunner.run(StrategyType.CONDITIONAL_UPDATE, 7003L, 1000, 700, 2);

        assertEquals(true, report.invariant().passed(), "conditional update scenario must pass invariant");
        assertEquals(300L, report.metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "balance remains non-negative");
        assertEquals(0L, report.metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT), "negative balance count stays zero");
        assertEquals(0L, report.metricValue(ScenarioMetricName.DB_WAIT_MS_P95), "conditional update does not claim row-lock wait evidence");
    }

    private static void idempotencyReplayScenarioReportAvoidsDoubleSpendForSameKey() {
        PointSpendRunResult result = PointSpendRunner.runWithIdempotencyKey(
                StrategyType.IDEMPOTENCY_REPLAY,
                7004L,
                1000,
                700,
                2,
                "spend-7004-001"
        );
        ScenarioRunReport report = PointSpendScenarioRunner.toReport(result);

        assertEquals(true, report.invariant().passed(), "idempotency replay scenario must pass invariant");
        assertEquals(300L, report.metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "same idempotency key spends once");
        assertEquals(1L, report.metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "ledger entry is not duplicated");
        assertEquals(1L, report.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "replay is recorded");
        assertEquals("spend-7004-001", result.idempotencyKey(), "idempotency key is preserved");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
