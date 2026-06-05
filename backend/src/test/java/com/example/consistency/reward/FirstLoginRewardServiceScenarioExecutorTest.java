package com.example.consistency.reward;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardServiceScenarioExecutorTest {

    public static void main(String[] args) {
        dbGuardPersistsReportFromServiceDecisions();
        redisLockPathRecordsRewardScopedLockAttempts();
    }

    private static void dbGuardPersistsReportFromServiceDecisions() {
        InMemoryRewardIssueRepository rewards = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        FirstLoginRewardService service = new FirstLoginRewardService(
                rewards,
                followUps,
                new RecordingRewardLockGateway()
        );
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        FirstLoginRewardServiceScenarioExecutor executor = new FirstLoginRewardServiceScenarioExecutor(service, reports);

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 91001L, 2);

        assertEquals(1L, record.sequence(), "first saved report sequence");
        assertEquals(true, record.report().invariant().passed(), "DB guard report invariant");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted requests");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.COMPLETED_COUNT), "completed requests");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REWARD_ISSUED_COUNT), "issued rewards");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate rewards");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.AFTER_COMMIT_NOTIFICATION_COUNT), "fake notification count");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.OUTBOX_EVENT_COUNT), "outbox count");
        assertEquals(1L, reports.count(), "report persisted once");
    }

    private static void redisLockPathRecordsRewardScopedLockAttempts() {
        InMemoryRewardIssueRepository rewards = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        FirstLoginRewardService service = new FirstLoginRewardService(
                rewards,
                followUps,
                new RecordingRewardLockGateway()
        );
        FirstLoginRewardServiceScenarioExecutor executor = new FirstLoginRewardServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.REDIS_LOCK_DB_GUARD, 91002L, 2);

        assertEquals(true, record.report().invariant().passed(), "Redis plus DB guard report invariant");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT), "reward lock attempts");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REWARD_ISSUED_COUNT), "issued rewards under lock");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate rewards under lock");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
