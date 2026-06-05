package com.example.consistency.reward;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardScenarioExecutorTest {

    public static void main(String[] args) {
        executesAndStoresNaiveScenarioReport();
        executesAndStoresGuardedScenarioReport();
    }

    private static void executesAndStoresNaiveScenarioReport() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        FirstLoginRewardScenarioExecutor executor = new FirstLoginRewardScenarioExecutor(repository);

        ScenarioRunRecord record = executor.execute(StrategyType.NAIVE, 4001L, 5);

        assertEquals(1L, record.sequence(), "stored run has sequence");
        assertEquals(false, record.report().invariant().passed(), "naive run persists broken invariant");
        assertEquals(4L, record.report().metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate count is persisted");
        assertEquals(1L, repository.count(), "repository contains executed run");
    }

    private static void executesAndStoresGuardedScenarioReport() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        FirstLoginRewardScenarioExecutor executor = new FirstLoginRewardScenarioExecutor(repository);

        ScenarioRunRecord record = executor.execute(StrategyType.REDIS_LOCK_DB_GUARD, 4002L, 5);

        assertEquals(true, record.report().invariant().passed(), "guarded run persists passing invariant");
        assertEquals(5L, record.report().metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT), "lock attempts are persisted");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate count is persisted");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}

