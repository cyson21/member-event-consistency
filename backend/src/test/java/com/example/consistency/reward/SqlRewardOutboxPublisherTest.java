package com.example.consistency.reward;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlRewardOutboxPublisherTest {

    public static void main(String[] args) {
        publishedTransitionOnlyMovesPendingRows();
        failedTransitionRecordsTerminalFailureAndRetryCount();
        statusCountersReadOutboxStates();
    }

    private static void publishedTransitionOnlyMovesPendingRows() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(true);
        SqlRewardOutboxPublisher publisher = new SqlRewardOutboxPublisher(executor);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000101");

        boolean published = publisher.markPublished(eventId);

        assertEquals(true, published, "published transition returns update result");
        SqlStatement statement = executor.lastStatement();
        assertContains(statement.sql(), "update outbox_events", "publish transition updates outbox");
        assertContains(statement.sql(), "set status = ?", "publish transition binds terminal status");
        assertContains(statement.sql(), "where event_id = ?", "publish transition targets event id");
        assertContains(statement.sql(), "and status = 'PENDING'", "publish transition only moves pending rows");
        assertEquals("PUBLISHED", statement.params().get(0), "published status is bound");
        assertEquals(eventId, statement.params().get(1), "event id is bound");
    }

    private static void failedTransitionRecordsTerminalFailureAndRetryCount() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(true);
        SqlRewardOutboxPublisher publisher = new SqlRewardOutboxPublisher(executor);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000102");

        boolean failed = publisher.markFailed(eventId, "LOCAL_FAKE_PROVIDER_ERROR");

        assertEquals(true, failed, "failed transition returns update result");
        SqlStatement statement = executor.lastStatement();
        assertContains(statement.sql(), "update outbox_events", "failure transition updates outbox");
        assertContains(statement.sql(), "retry_count = retry_count + 1", "failure transition increments retry count");
        assertContains(statement.sql(), "where event_id = ?", "failure transition targets event id");
        assertContains(statement.sql(), "and status = 'PENDING'", "failure transition only moves pending rows");
        assertEquals("FAILED", statement.params().get(0), "failed status is bound");
        assertContains((String) statement.params().get(1), "LOCAL_FAKE_PROVIDER_ERROR", "failure payload is local-only");
        assertEquals(eventId, statement.params().get(2), "event id is bound");
    }

    private static void statusCountersReadOutboxStates() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(3L);
        executor.nextLongResult(2L);
        executor.nextLongResult(1L);
        SqlRewardOutboxPublisher publisher = new SqlRewardOutboxPublisher(executor);

        assertEquals(3L, publisher.pendingCount(), "pending count");
        assertEquals(2L, publisher.publishedCount(), "published count");
        assertEquals(1L, publisher.failedCount(), "failed count");

        assertStatusCountStatement(executor.statementAt(0), "PENDING", "pending count SQL");
        assertStatusCountStatement(executor.statementAt(1), "PUBLISHED", "published count SQL");
        assertStatusCountStatement(executor.statementAt(2), "FAILED", "failed count SQL");
    }

    private static void assertStatusCountStatement(SqlStatement statement, String expectedStatus, String message) {
        assertContains(statement.sql(), "count(*) from outbox_events", message);
        assertContains(statement.sql(), "where status = ?", message);
        assertEquals(expectedStatus, statement.params().get(0), message + " status bind");
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
