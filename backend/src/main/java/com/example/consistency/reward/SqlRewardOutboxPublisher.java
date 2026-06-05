package com.example.consistency.reward;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlRewardOutboxPublisher {

    private final SqlExecutor executor;

    public SqlRewardOutboxPublisher(SqlExecutor executor) {
        this.executor = executor;
    }

    public boolean markPublished(UUID eventId) {
        return executor.insert(SqlStatement.of(
                """
                update outbox_events
                set status = ?,
                    updated_at = now()
                where event_id = ?
                  and status = 'PENDING'
                """,
                "PUBLISHED",
                eventId
        ));
    }

    public boolean markFailed(UUID eventId, String reason) {
        return executor.insert(SqlStatement.of(
                """
                update outbox_events
                set status = ?,
                    payload = jsonb_set(payload, '{localFailureReason}', to_jsonb(?::text), true),
                    retry_count = retry_count + 1,
                    next_attempt_at = null,
                    updated_at = now()
                where event_id = ?
                  and status = 'PENDING'
                """,
                "FAILED",
                reason,
                eventId
        ));
    }

    public long pendingCount() {
        return countByStatus("PENDING");
    }

    public long publishedCount() {
        return countByStatus("PUBLISHED");
    }

    public long failedCount() {
        return countByStatus("FAILED");
    }

    private long countByStatus(String status) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from outbox_events
                where status = ?
                """,
                status
        ));
    }
}
