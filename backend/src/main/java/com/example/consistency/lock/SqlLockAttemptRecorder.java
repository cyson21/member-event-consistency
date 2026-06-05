package com.example.consistency.lock;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlLockAttemptRecorder {

    private final SqlExecutor executor;

    public SqlLockAttemptRecorder(SqlExecutor executor) {
        this.executor = executor;
    }

    public void record(UUID runId, String lockKey, String ownerId, long waitMs, long leaseMs, String result) {
        executor.insert(SqlStatement.of(
                """
                insert into lock_attempts (run_id, lock_key, owner_id, wait_ms, lease_ms, result)
                values (?, ?, ?, ?, ?, ?)
                """,
                runId,
                lockKey,
                ownerId,
                waitMs,
                leaseMs,
                result
        ));
    }

    public long acquiredCount(UUID runId) {
        return countByResult(runId, "ACQUIRED");
    }

    public long timeoutCount(UUID runId) {
        return countByResult(runId, "TIMEOUT");
    }

    private long countByResult(UUID runId, String result) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from lock_attempts
                where run_id = ?
                  and result = ?
                """,
                runId,
                result
        ));
    }
}
