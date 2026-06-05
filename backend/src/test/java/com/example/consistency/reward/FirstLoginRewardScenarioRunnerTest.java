package com.example.consistency.reward;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardScenarioRunnerTest {

    public static void main(String[] args) {
        naiveScenarioReportShowsBrokenInvariant();
        dbGuardScenarioReportShowsZeroDuplicates();
        redisLockScenarioReportIncludesLockAttemptsAndOutboxMetrics();
    }

    private static void naiveScenarioReportShowsBrokenInvariant() {
        ScenarioRunReport report = FirstLoginRewardScenarioRunner.run(StrategyType.NAIVE, 3001L, 6);

        assertEquals(ScenarioType.FIRST_LOGIN_REWARD, report.scenario(), "scenario is fixed");
        assertEquals(StrategyType.NAIVE, report.strategy(), "strategy is captured");
        assertEquals(false, report.invariant().passed(), "naive scenario must fail invariant");
        assertEquals(5L, report.metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate count is recorded");
        assertEquals(6L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count is recorded");
        assertEquals(6L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
    }

    private static void dbGuardScenarioReportShowsZeroDuplicates() {
        ScenarioRunReport report = FirstLoginRewardScenarioRunner.run(StrategyType.DB_GUARD, 3002L, 6);

        assertEquals(true, report.invariant().passed(), "DB guard scenario must pass invariant");
        assertEquals(1L, report.metricValue(ScenarioMetricName.REWARD_ISSUED_COUNT), "exactly one reward is issued");
        assertEquals(0L, report.metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate count stays zero");
        assertEquals(1L, report.metricValue(ScenarioMetricName.OUTBOX_EVENT_COUNT), "outbox metric is recorded");
    }

    private static void redisLockScenarioReportIncludesLockAttemptsAndOutboxMetrics() {
        ScenarioRunReport report = FirstLoginRewardScenarioRunner.run(StrategyType.REDIS_LOCK_DB_GUARD, 3003L, 6);

        assertEquals(true, report.invariant().passed(), "Redis lock plus DB guard scenario must pass invariant");
        assertEquals(6L, report.metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT), "lock attempts are recorded");
        assertEquals(0L, report.metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "DB guard remains the duplicate defense");
        assertEquals(1L, report.metricValue(ScenarioMetricName.AFTER_COMMIT_NOTIFICATION_COUNT), "fake after-commit metric is recorded");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}

