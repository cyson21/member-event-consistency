package com.example.consistency.reward;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlRewardFollowUpRecorderTest {

    public static void main(String[] args) {
        successfulIssueWritesRewardAndFakeNotificationOutboxRows();
        countersReadOutboxEventTypes();
    }

    private static void successfulIssueWritesRewardAndFakeNotificationOutboxRows() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlRewardFollowUpRecorder recorder = new SqlRewardFollowUpRecorder(executor);

        recorder.recordSuccessfulIssue(71001L, RewardType.FIRST_LOGIN);

        assertEquals(2L, executor.statementCount(), "successful issue writes two outbox rows");
        SqlStatement rewardIssued = executor.statementAt(0);
        SqlStatement fakeNotification = executor.statementAt(1);

        assertContains(rewardIssued.sql(), "insert into outbox_events", "reward-issued event uses outbox");
        assertContains(rewardIssued.sql(), "values (?, ?, ?, ?, ?::jsonb, ?)", "reward-issued event uses bind variables and jsonb cast");
        assertUuid(rewardIssued.params().get(0), "reward-issued event id is a UUID");
        assertEquals("REWARD_ISSUE", rewardIssued.params().get(1), "reward aggregate type is bound");
        assertEquals("71001:FIRST_LOGIN", rewardIssued.params().get(2), "reward aggregate id is bound");
        assertEquals("FIRST_LOGIN_REWARD_ISSUED", rewardIssued.params().get(3), "reward event type is bound");
        assertContains((String) rewardIssued.params().get(4), "\"memberId\":71001", "reward payload includes member id");
        assertContains((String) rewardIssued.params().get(4), "\"rewardType\":\"FIRST_LOGIN\"", "reward payload includes reward type");
        assertEquals("PENDING", rewardIssued.params().get(5), "reward event starts pending");

        assertContains(fakeNotification.sql(), "insert into outbox_events", "fake notification event uses outbox");
        assertUuid(fakeNotification.params().get(0), "fake notification event id is a UUID");
        assertEquals("REWARD_FOLLOW_UP", fakeNotification.params().get(1), "fake notification aggregate type is bound");
        assertEquals("71001:FIRST_LOGIN", fakeNotification.params().get(2), "fake notification aggregate id is bound");
        assertEquals("FAKE_AFTER_COMMIT_NOTIFICATION_REQUESTED", fakeNotification.params().get(3), "fake notification event type is bound");
        assertContains((String) fakeNotification.params().get(4), "\"provider\":\"LOCAL_FAKE\"", "fake notification payload stays local-only");
        assertEquals("PENDING", fakeNotification.params().get(5), "fake notification event starts pending");
    }

    private static void countersReadOutboxEventTypes() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(4L);
        executor.nextLongResult(4L);
        SqlRewardFollowUpRecorder recorder = new SqlRewardFollowUpRecorder(executor);

        assertEquals(4L, recorder.afterCommitNotificationCount(), "fake notification count comes from outbox");
        assertEquals(4L, recorder.outboxEventCount(), "outbox event count comes from outbox");

        assertContains(executor.statementAt(0).sql(), "count(*) from outbox_events", "notification count reads outbox");
        assertContains(executor.statementAt(0).sql(), "event_type = ?", "notification count filters event type");
        assertEquals("FAKE_AFTER_COMMIT_NOTIFICATION_REQUESTED", executor.statementAt(0).params().get(0), "notification event type is bound");
        assertContains(executor.statementAt(1).sql(), "count(*) from outbox_events", "outbox count reads outbox");
        assertEquals("FIRST_LOGIN_REWARD_ISSUED", executor.statementAt(1).params().get(0), "reward-issued event type is bound");
    }

    private static void assertUuid(Object value, String message) {
        if (!(value instanceof UUID)) {
            throw new AssertionError(message + " expected UUID actual=[" + value + "]");
        }
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

