package com.example.consistency.scenario;

import java.util.List;

public final class ScenarioRunReportRepositoryTest {

    public static void main(String[] args) {
        savesAndFindsScenarioRunReports();
        summarizesLatestReportByScenarioAndStrategy();
    }

    private static void savesAndFindsScenarioRunReports() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        ScenarioRunReport report = sampleReport(StrategyType.DB_GUARD, true, 0, 1);

        ScenarioRunRecord saved = repository.save(report);

        assertEquals(1L, saved.sequence(), "first saved run gets sequence 1");
        assertEquals(report, repository.findBySequence(saved.sequence()), "saved report can be loaded by sequence");
        assertEquals(1L, repository.count(), "count tracks stored reports");
    }

    private static void summarizesLatestReportByScenarioAndStrategy() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        repository.save(sampleReport(StrategyType.DB_GUARD, true, 0, 1));
        repository.save(sampleReport(StrategyType.DB_GUARD, true, 0, 1));
        repository.save(sampleReport(StrategyType.NAIVE, false, 3, 4));

        ScenarioRunSummary summary = repository.latestSummary(ScenarioType.FIRST_LOGIN_REWARD, StrategyType.NAIVE);

        assertEquals(3L, summary.sequence(), "latest matching report sequence is returned");
        assertEquals(false, summary.invariantPassed(), "summary captures invariant status");
        assertEquals(3L, summary.metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "summary exposes metrics");
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
                        4,
                        4,
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

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}

