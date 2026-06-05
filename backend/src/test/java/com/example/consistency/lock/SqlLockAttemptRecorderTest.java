package com.example.consistency.lock;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlLockAttemptRecorderTest {

    public static void main(String[] args) {
        successfulAttemptWritesScopedLockEvidence();
        timeoutAttemptWritesFailedResultWithoutBroadMemberLock();
        countersReadAttemptResultsByRunId();
    }

    private static void successfulAttemptWritesScopedLockEvidence() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlLockAttemptRecorder recorder = new SqlLockAttemptRecorder(executor);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000401");

        recorder.record(runId, "lock:first-login-reward:93001", "api-1", 12L, 1000L, "ACQUIRED");

        SqlStatement statement = executor.lastStatement();
        assertContains(statement.sql(), "insert into lock_attempts", "lock attempt insert");
        assertContains(statement.sql(), "run_id, lock_key, owner_id, wait_ms, lease_ms, result", "lock attempt fields");
        assertEquals(runId, statement.params().get(0), "run id is bound");
        assertEquals("lock:first-login-reward:93001", statement.params().get(1), "reward-scoped lock key is bound");
        assertEquals("api-1", statement.params().get(2), "owner id is bound");
        assertEquals(12L, statement.params().get(3), "wait is bound");
        assertEquals(1000L, statement.params().get(4), "lease is bound");
        assertEquals("ACQUIRED", statement.params().get(5), "result is bound");
    }

    private static void timeoutAttemptWritesFailedResultWithoutBroadMemberLock() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlLockAttemptRecorder recorder = new SqlLockAttemptRecorder(executor);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000402");

        recorder.record(runId, "lock:coupon-campaign:94001", "worker-1", 250L, 500L, "TIMEOUT");

        SqlStatement statement = executor.lastStatement();
        assertEquals("lock:coupon-campaign:94001", statement.params().get(1), "campaign-scoped lock key is bound");
        assertEquals("TIMEOUT", statement.params().get(5), "timeout result is bound");
        assertNotContains((String) statement.params().get(1), "lock:member:", "broad member lock is not recorded");
    }

    private static void countersReadAttemptResultsByRunId() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(3L);
        executor.nextLongResult(1L);
        SqlLockAttemptRecorder recorder = new SqlLockAttemptRecorder(executor);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000403");

        assertEquals(3L, recorder.acquiredCount(runId), "acquired count");
        assertEquals(1L, recorder.timeoutCount(runId), "timeout count");

        assertStatusCountStatement(executor.statementAt(0), runId, "ACQUIRED", "acquired count SQL");
        assertStatusCountStatement(executor.statementAt(1), runId, "TIMEOUT", "timeout count SQL");
    }

    private static void assertStatusCountStatement(SqlStatement statement, UUID runId, String result, String message) {
        assertContains(statement.sql(), "count(*) from lock_attempts", message);
        assertContains(statement.sql(), "where run_id = ?", message);
        assertContains(statement.sql(), "and result = ?", message);
        assertEquals(runId, statement.params().get(0), message + " run id");
        assertEquals(result, statement.params().get(1), message + " result");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertNotContains(String actual, String unexpected, String message) {
        if (actual.contains(unexpected)) {
            throw new AssertionError(message + " unexpected fragment=[" + unexpected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
