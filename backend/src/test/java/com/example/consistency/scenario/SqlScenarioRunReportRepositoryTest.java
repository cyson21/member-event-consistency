package com.example.consistency.scenario;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.List;
import java.util.UUID;

public final class SqlScenarioRunReportRepositoryTest {

    public static void main(String[] args) {
        saveWritesScenarioRunAndMetrics();
        latestSummaryUsesExecutorProvidedReport();
        latestSummarySelectsCouponMetrics();
        latestSummarySelectsPointMetrics();
    }

    private static void saveWritesScenarioRunAndMetrics() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(41L);
        SqlScenarioRunReportRepository repository = new SqlScenarioRunReportRepository(executor);

        ScenarioRunRecord record = repository.save(sampleReport(StrategyType.DB_GUARD, true, 0, 1));

        assertEquals(41L, record.sequence(), "executor-provided sequence is used");
        assertContains(executor.statementAt(0).sql(), "insert into scenario_runs", "scenario run insert is recorded");
        assertContains(executor.statementAt(0).sql(), "values (?, ?, ?, ?, ?, ?)", "scenario run insert uses bind variables");
        assertEquals(true, executor.statementAt(0).params().get(0) instanceof UUID, "scenario run id is bound as UUID");
        assertContains(executor.statementAt(1).sql(), "insert into scenario_metrics", "first metric insert is recorded");
        assertEquals(true, executor.statementAt(1).params().get(0) instanceof UUID, "metric run id is bound as UUID");
        assertContains(executor.statementAt(2).sql(), "insert into scenario_metrics", "second metric insert is recorded");
        assertEquals(3L, executor.statementCount(), "one run insert plus two metric inserts");
    }

    private static void latestSummaryUsesExecutorProvidedReport() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextReportRecord(new ScenarioRunRecord(9L, sampleReport(StrategyType.NAIVE, false, 2, 3)));
        SqlScenarioRunReportRepository repository = new SqlScenarioRunReportRepository(executor);

        ScenarioRunSummary summary = repository.latestSummary(ScenarioType.FIRST_LOGIN_REWARD, StrategyType.NAIVE);

        assertEquals(9L, summary.sequence(), "latest summary sequence comes from executor");
        assertEquals(false, summary.invariantPassed(), "summary preserves invariant status");
        assertEquals(2L, summary.metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "summary exposes stored metric");
        assertContains(executor.lastStatement().sql(), "where sr.scenario = ? and sr.strategy = ?", "latest summary is filtered");
        assertContains(executor.lastStatement().sql(), "group by sr.run_sequence", "latest summary groups metrics before ordering");
        assertContains(executor.lastStatement().sql(), "order by sr.started_at desc", "latest summary orders after grouping");
    }

    private static void latestSummarySelectsCouponMetrics() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextReportRecord(couponReportRecord());
        SqlScenarioRunReportRepository repository = new SqlScenarioRunReportRepository(executor);

        ScenarioRunSummary summary = repository.latestSummary(ScenarioType.COUPON_CAMPAIGN_ISSUE, StrategyType.RABBITMQ_DB_GUARD);

        assertEquals(0L, summary.metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "summary exposes coupon invariant metric");
        assertContains(executor.lastStatement().sql(), "COUPON_ISSUED_COUNT", "coupon issued metric is selected");
        assertContains(executor.lastStatement().sql(), "OVER_ISSUE_COUNT", "over issue metric is selected");
        assertContains(executor.lastStatement().sql(), "RABBITMQ_LANE_COUNT", "RabbitMQ lane metric is selected");
        assertContains(executor.lastStatement().sql(), "QUEUE_RETRY_COUNT", "queue retry metric is selected");
        assertContains(executor.lastStatement().sql(), "DLQ_COUNT", "DLQ metric is selected");
        assertContains(executor.lastStatement().sql(), "QUEUE_LAG_MS_P95", "queue lag metric is selected");
        assertContains(executor.lastStatement().sql(), "RABBITMQ_ACCEPTED_LATENCY_MS", "RabbitMQ accepted latency metric is selected");
        assertContains(executor.lastStatement().sql(), "RABBITMQ_COMPLETION_LATENCY_MS", "RabbitMQ completion latency metric is selected");
    }

    private static void latestSummarySelectsPointMetrics() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextReportRecord(pointReportRecord());
        SqlScenarioRunReportRepository repository = new SqlScenarioRunReportRepository(executor);

        ScenarioRunSummary summary = repository.latestSummary(ScenarioType.POINT_SPEND, StrategyType.IDEMPOTENCY_REPLAY);

        assertEquals(1L, summary.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "summary exposes point replay metric");
        assertEquals(0L, summary.metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "summary exposes hash mismatch metric");
        assertContains(executor.lastStatement().sql(), "FINAL_POINT_BALANCE", "final point balance metric is selected");
        assertContains(executor.lastStatement().sql(), "NEGATIVE_BALANCE_COUNT", "negative balance metric is selected");
        assertContains(executor.lastStatement().sql(), "POINT_LEDGER_ENTRY_COUNT", "ledger entry metric is selected");
        assertContains(executor.lastStatement().sql(), "IDEMPOTENCY_REPLAY_COUNT", "idempotency replay metric is selected");
        assertContains(executor.lastStatement().sql(), "IDEMPOTENCY_HASH_MISMATCH_COUNT", "hash mismatch metric is selected");
        assertContains(executor.lastStatement().sql(), "DB_WAIT_MS_P95", "DB wait metric is selected");
    }

    private static ScenarioRunReport sampleReport(
            StrategyType strategy,
            boolean passed,
            long duplicateCount,
            long issuedCount
    ) {
        return new ScenarioRunReport(
                ScenarioType.FIRST_LOGIN_REWARD,
                strategy,
                new InvariantResult(
                        ScenarioType.FIRST_LOGIN_REWARD,
                        strategy,
                        passed,
                        duplicateCount,
                        0,
                        0,
                        0,
                        5,
                        5,
                        0,
                        0,
                        0,
                        "sample"
                ),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.DUPLICATE_REWARD_COUNT, duplicateCount),
                        new ScenarioMetric(ScenarioMetricName.REWARD_ISSUED_COUNT, issuedCount)
                )
        );
    }

    private static ScenarioRunRecord couponReportRecord() {
        ScenarioRunReport report = new ScenarioRunReport(
                ScenarioType.COUPON_CAMPAIGN_ISSUE,
                StrategyType.RABBITMQ_DB_GUARD,
                new InvariantResult(
                        ScenarioType.COUPON_CAMPAIGN_ISSUE,
                        StrategyType.RABBITMQ_DB_GUARD,
                        true,
                        0,
                        0,
                        0,
                        0,
                        8,
                        8,
                        0,
                        0,
                        70,
                        "sample"
                ),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.COUPON_ISSUED_COUNT, 3),
                        new ScenarioMetric(ScenarioMetricName.OVER_ISSUE_COUNT, 0),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_LANE_COUNT, 1),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_LAG_MS_P95, 70),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS, 12),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS, 82)
                )
        );
        return new ScenarioRunRecord(14L, report);
    }

    private static ScenarioRunRecord pointReportRecord() {
        ScenarioRunReport report = new ScenarioRunReport(
                ScenarioType.POINT_SPEND,
                StrategyType.IDEMPOTENCY_REPLAY,
                new InvariantResult(
                        ScenarioType.POINT_SPEND,
                        StrategyType.IDEMPOTENCY_REPLAY,
                        true,
                        0,
                        0,
                        0,
                        0,
                        2,
                        2,
                        0,
                        0,
                        0,
                        "sample"
                ),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.FINAL_POINT_BALANCE, 300),
                        new ScenarioMetric(ScenarioMetricName.NEGATIVE_BALANCE_COUNT, 0),
                        new ScenarioMetric(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT, 1),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT, 1),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT, 0)
                )
        );
        return new ScenarioRunRecord(15L, report);
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
