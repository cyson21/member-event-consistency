package com.example.consistency.reward;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlRewardFollowUpRecorder implements RewardFollowUpRecorder {

    private static final String REWARD_ISSUED_EVENT = "FIRST_LOGIN_REWARD_ISSUED";
    private static final String FAKE_NOTIFICATION_EVENT = "FAKE_AFTER_COMMIT_NOTIFICATION_REQUESTED";

    private final SqlExecutor executor;

    public SqlRewardFollowUpRecorder(SqlExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void recordSuccessfulIssue(long memberId, RewardType rewardType) {
        recordRewardIssued(memberId, rewardType);
        recordFakeAfterCommitNotification(memberId, rewardType);
    }

    public void recordRewardIssued(long memberId, RewardType rewardType) {
        String aggregateId = memberId + ":" + rewardType.name();
        String payload = "{\"memberId\":" + memberId
                + ",\"rewardType\":\"" + rewardType.name()
                + "\",\"provider\":\"LOCAL_FAKE\"}";
        insertOutboxEvent("REWARD_ISSUE", aggregateId, REWARD_ISSUED_EVENT, payload);
    }

    public void recordFakeAfterCommitNotification(long memberId, RewardType rewardType) {
        String aggregateId = memberId + ":" + rewardType.name();
        String payload = "{\"memberId\":" + memberId
                + ",\"rewardType\":\"" + rewardType.name()
                + "\",\"provider\":\"LOCAL_FAKE\"}";
        insertOutboxEvent("REWARD_FOLLOW_UP", aggregateId, FAKE_NOTIFICATION_EVENT, payload);
    }

    @Override
    public long afterCommitNotificationCount() {
        return countByEventType(FAKE_NOTIFICATION_EVENT);
    }

    @Override
    public long outboxEventCount() {
        return countByEventType(REWARD_ISSUED_EVENT);
    }

    private void insertOutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        executor.insert(SqlStatement.of(
                """
                insert into outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload, status)
                values (?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                "PENDING"
        ));
    }

    private long countByEventType(String eventType) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from outbox_events
                where event_type = ?
                """,
                eventType
        ));
    }
}
