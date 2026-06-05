package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public final class BatchExpirationServiceScenarioExecutorTest {

    public static void main(String[] args) {
        dbGuardServiceExecutorPersistsUseWinsRejectionReason();
        dbGuardServiceExecutorPersistsExpirationWinsRejectionReason();
        naiveServiceExecutorUsesDependencyFreeFailureEvidence();
    }

    private static void dbGuardServiceExecutorPersistsUseWinsRejectionReason() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        BatchExpirationServiceScenarioExecutor executor = new BatchExpirationServiceScenarioExecutor(
                new BatchExpirationService(new InMemoryBatchExpirationRepository()),
                reports
        );

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 13001L, BatchExpirationWinner.USER_USE);

        assertEquals(1L, reports.count(), "service executor persists one report");
        assertEquals(true, record.report().invariant().passed(), "DB guard service run passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "use wins once");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration does not win");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "expiration is rejected");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict stays zero");
        assertEquals("expiration rejected because coupon already used", record.report().invariant().message(), "rejection reason is visible");
    }

    private static void dbGuardServiceExecutorPersistsExpirationWinsRejectionReason() {
        BatchExpirationServiceScenarioExecutor executor = new BatchExpirationServiceScenarioExecutor(
                new BatchExpirationService(new InMemoryBatchExpirationRepository()),
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(
                StrategyType.DB_GUARD,
                13002L,
                BatchExpirationWinner.BATCH_EXPIRATION
        );

        assertEquals(true, record.report().invariant().passed(), "expiration-wins run passes invariant");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "use does not win");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration wins once");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "use is rejected");
        assertEquals("use rejected because coupon already expired", record.report().invariant().message(), "rejection reason is visible");
    }

    private static void naiveServiceExecutorUsesDependencyFreeFailureEvidence() {
        BatchExpirationServiceScenarioExecutor executor = new BatchExpirationServiceScenarioExecutor(
                new BatchExpirationService(new InMemoryBatchExpirationRepository()),
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.NAIVE, 13003L, BatchExpirationWinner.USER_USE);

        assertEquals(false, record.report().invariant().passed(), "naive path still fails invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "use terminal state is recorded");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration terminal state is recorded");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT), "terminal conflict is visible");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
