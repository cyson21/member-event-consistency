package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlCouponQueueEventRecorder {

    private static final String QUEUE_NAME = "coupon-campaign-issue.commands";

    private final SqlExecutor executor;

    public SqlCouponQueueEventRecorder(SqlExecutor executor) {
        this.executor = executor;
    }

    public void recordAccepted(UUID runId, String messageId, long campaignId, long lagMs) {
        insertEvent(runId, messageId, campaignId, "ACCEPTED", 0, lagMs);
    }

    public void recordCompleted(UUID runId, String messageId, long campaignId, long lagMs) {
        insertEvent(runId, messageId, campaignId, "COMPLETED", 0, lagMs);
    }

    public void recordRetried(UUID runId, String messageId, long campaignId, int retryCount, long lagMs) {
        insertEvent(runId, messageId, campaignId, "RETRIED", retryCount, lagMs);
    }

    public void recordDlq(UUID runId, String messageId, long campaignId, int retryCount, long lagMs) {
        insertEvent(runId, messageId, campaignId, "DLQ", retryCount, lagMs);
    }

    public long acceptedCount(UUID runId) {
        return countByStatus(runId, "ACCEPTED");
    }

    public long completedCount(UUID runId) {
        return countByStatus(runId, "COMPLETED");
    }

    public long retryCount(UUID runId) {
        return countByStatus(runId, "RETRIED");
    }

    public long dlqCount(UUID runId) {
        return countByStatus(runId, "DLQ");
    }

    private void insertEvent(UUID runId, String messageId, long campaignId, String status, int retryCount, long lagMs) {
        executor.insert(SqlStatement.of(
                """
                insert into queue_events (run_id, queue_name, message_id, business_key, status, retry_count, lag_ms)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                QUEUE_NAME,
                messageId,
                "campaign:" + campaignId,
                status,
                retryCount,
                lagMs
        ));
    }

    private long countByStatus(UUID runId, String status) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from queue_events
                where run_id = ?
                  and status = ?
                """,
                runId,
                status
        ));
    }
}
