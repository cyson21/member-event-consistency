package com.example.consistency.point;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class PointSpendScenarioExecutorTest {

    public static void main(String[] args) {
        executesAndPersistsNaiveNegativeBalanceEvidence();
        executesAndPersistsIdempotencyReplayEvidence();
    }

    private static void executesAndPersistsNaiveNegativeBalanceEvidence() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        PointSpendScenarioExecutor executor = new PointSpendScenarioExecutor(repository);

        ScenarioRunRecord record = executor.execute(StrategyType.NAIVE, 7201L, 1000, 700, 2);

        assertEquals(1L, record.sequence(), "first saved run gets sequence 1");
        assertEquals(ScenarioType.POINT_SPEND, record.report().scenario(), "point scenario is persisted");
        assertEquals(false, record.report().invariant().passed(), "naive invariant failure is persisted");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT), "negative balance metric is persisted");
        assertEquals(record.report(), repository.findBySequence(record.sequence()), "report is saved in repository");
    }

    private static void executesAndPersistsIdempotencyReplayEvidence() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        PointSpendScenarioExecutor executor = new PointSpendScenarioExecutor(repository);

        ScenarioRunRecord record = executor.executeWithIdempotencyKey(
                StrategyType.IDEMPOTENCY_REPLAY,
                7202L,
                1000,
                700,
                2,
                "spend-7202-001"
        );

        assertEquals(true, record.report().invariant().passed(), "idempotency replay invariant pass is persisted");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "replay metric is persisted");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "single ledger entry is persisted");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
