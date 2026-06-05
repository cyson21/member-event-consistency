package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlCouponQueueEventRecorderTest {

    public static void main(String[] args) {
        acceptedCommandWritesQueueEventWithBusinessKey();
        terminalWorkerOutcomesWriteSeparateStatuses();
        countersReadQueueEventStatuses();
    }

    private static void acceptedCommandWritesQueueEventWithBusinessKey() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlCouponQueueEventRecorder recorder = new SqlCouponQueueEventRecorder(executor);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000301");

        recorder.recordAccepted(runId, "msg-301", 94001L, 0L);

        SqlStatement statement = executor.lastStatement();
        assertContains(statement.sql(), "insert into queue_events", "accepted event writes queue_events");
        assertContains(statement.sql(), "run_id, queue_name, message_id, business_key, status, retry_count, lag_ms", "accepted event writes observability fields");
        assertEquals(runId, statement.params().get(0), "run id is bound");
        assertEquals("coupon-campaign-issue.commands", statement.params().get(1), "queue name is bound");
        assertEquals("msg-301", statement.params().get(2), "message id is bound");
        assertEquals("campaign:94001", statement.params().get(3), "campaign business key is bound");
        assertEquals("ACCEPTED", statement.params().get(4), "accepted status is bound");
        assertEquals(0, statement.params().get(5), "accepted retry count starts at zero");
        assertEquals(0L, statement.params().get(6), "accepted lag is bound");
    }

    private static void terminalWorkerOutcomesWriteSeparateStatuses() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlCouponQueueEventRecorder recorder = new SqlCouponQueueEventRecorder(executor);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000302");

        recorder.recordCompleted(runId, "msg-302", 94002L, 35L);
        recorder.recordRetried(runId, "msg-303", 94002L, 2, 50L);
        recorder.recordDlq(runId, "msg-304", 94002L, 3, 70L);

        assertQueueEvent(executor.statementAt(0), "COMPLETED", 0, 35L, "completed worker outcome");
        assertQueueEvent(executor.statementAt(1), "RETRIED", 2, 50L, "retried worker outcome");
        assertQueueEvent(executor.statementAt(2), "DLQ", 3, 70L, "DLQ worker outcome");
    }

    private static void countersReadQueueEventStatuses() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(8L);
        executor.nextLongResult(7L);
        executor.nextLongResult(2L);
        executor.nextLongResult(1L);
        SqlCouponQueueEventRecorder recorder = new SqlCouponQueueEventRecorder(executor);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000303");

        assertEquals(8L, recorder.acceptedCount(runId), "accepted count");
        assertEquals(7L, recorder.completedCount(runId), "completed count");
        assertEquals(2L, recorder.retryCount(runId), "retry count");
        assertEquals(1L, recorder.dlqCount(runId), "DLQ count");

        assertStatusCountStatement(executor.statementAt(0), "ACCEPTED", runId, "accepted count SQL");
        assertStatusCountStatement(executor.statementAt(1), "COMPLETED", runId, "completed count SQL");
        assertStatusCountStatement(executor.statementAt(2), "RETRIED", runId, "retry count SQL");
        assertStatusCountStatement(executor.statementAt(3), "DLQ", runId, "DLQ count SQL");
    }

    private static void assertQueueEvent(SqlStatement statement, String status, int retryCount, long lagMs, String message) {
        assertContains(statement.sql(), "insert into queue_events", message);
        assertEquals(status, statement.params().get(4), message + " status");
        assertEquals(retryCount, statement.params().get(5), message + " retry count");
        assertEquals(lagMs, statement.params().get(6), message + " lag");
    }

    private static void assertStatusCountStatement(SqlStatement statement, String status, UUID runId, String message) {
        assertContains(statement.sql(), "count(*) from queue_events", message);
        assertContains(statement.sql(), "where run_id = ?", message);
        assertContains(statement.sql(), "and status = ?", message);
        assertEquals(runId, statement.params().get(0), message + " run id");
        assertEquals(status, statement.params().get(1), message + " status");
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
