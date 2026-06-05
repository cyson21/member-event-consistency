package com.example.consistency.persistence;

import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class JdbcSqlExecutor implements SqlExecutor {

    private final DataSource dataSource;

    public JdbcSqlExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean insert(SqlStatement statement) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
            bind(preparedStatement, statement);
            return preparedStatement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to execute insert", exception);
        }
    }

    @Override
    public long insertReturningLong(SqlStatement statement) {
        return queryLong(statement);
    }

    @Override
    public long queryLong(SqlStatement statement) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
            bind(preparedStatement, statement);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query long", exception);
        }
    }

    @Override
    public ScenarioRunRecord queryLatestScenarioRun(SqlStatement statement) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
            bind(preparedStatement, statement);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Scenario run not found");
                }
                return hydrateScenarioRun(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query scenario run", exception);
        }
    }

    private void bind(PreparedStatement preparedStatement, SqlStatement statement) throws SQLException {
        for (int i = 0; i < statement.params().size(); i++) {
            preparedStatement.setObject(i + 1, statement.params().get(i));
        }
    }

    private ScenarioRunRecord hydrateScenarioRun(ResultSet resultSet) throws SQLException {
        String rawId = resultSet.getString("id");
        UUID id = rawId == null ? null : UUID.fromString(rawId);
        long sequence = resultSet.getLong("run_sequence");
        ScenarioType scenario = ScenarioType.valueOf(resultSet.getString("scenario"));
        StrategyType strategy = StrategyType.valueOf(resultSet.getString("strategy"));
        boolean passed = "PASSED".equals(resultSet.getString("status"));

        long acceptedCount = resultSet.getLong("accepted_count");
        long completedCount = resultSet.getLong("completed_count");
        long duplicateRewardCount = resultSet.getLong("duplicate_reward_count");
        long rewardIssuedCount = resultSet.getLong("reward_issued_count");
        long couponIssuedCount = resultSet.getLong("coupon_issued_count");
        long overIssueCount = resultSet.getLong("over_issue_count");
        long rejectedCount = resultSet.getLong("rejected_count");
        long finalPointBalance = resultSet.getLong("final_point_balance");
        long negativeBalanceCount = resultSet.getLong("negative_balance_count");
        long pointLedgerEntryCount = resultSet.getLong("point_ledger_entry_count");
        long idempotencyReplayCount = resultSet.getLong("idempotency_replay_count");
        long idempotencyHashMismatchCount = resultSet.getLong("idempotency_hash_mismatch_count");
        long dbWaitMsP95 = resultSet.getLong("db_wait_ms_p95");
        long redisLockAttemptCount = resultSet.getLong("redis_lock_attempt_count");
        long rabbitMqLaneCount = resultSet.getLong("rabbitmq_lane_count");
        long queueRetryCount = resultSet.getLong("queue_retry_count");
        long dlqCount = resultSet.getLong("dlq_count");
        long queueLagMsP95 = resultSet.getLong("queue_lag_ms_p95");
        long rabbitMqAcceptedLatencyMs = resultSet.getLong("rabbitmq_accepted_latency_ms");
        long rabbitMqCompletionLatencyMs = resultSet.getLong("rabbitmq_completion_latency_ms");
        long afterCommitNotificationCount = resultSet.getLong("after_commit_notification_count");
        long outboxEventCount = resultSet.getLong("outbox_event_count");

        ScenarioRunReport report = new ScenarioRunReport(
                scenario,
                strategy,
                new InvariantResult(
                        scenario,
                        strategy,
                        passed,
                        duplicateRewardCount,
                        overIssueCount,
                        negativeBalanceCount,
                        0,
                        acceptedCount,
                        completedCount,
                        dbWaitMsP95,
                        0,
                        queueLagMsP95,
                        passed ? "invariant passed" : "invariant failed"
                ),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, acceptedCount),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, completedCount),
                        new ScenarioMetric(ScenarioMetricName.DUPLICATE_REWARD_COUNT, duplicateRewardCount),
                        new ScenarioMetric(ScenarioMetricName.REWARD_ISSUED_COUNT, rewardIssuedCount),
                        new ScenarioMetric(ScenarioMetricName.COUPON_ISSUED_COUNT, couponIssuedCount),
                        new ScenarioMetric(ScenarioMetricName.OVER_ISSUE_COUNT, overIssueCount),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, rejectedCount),
                        new ScenarioMetric(ScenarioMetricName.FINAL_POINT_BALANCE, finalPointBalance),
                        new ScenarioMetric(ScenarioMetricName.NEGATIVE_BALANCE_COUNT, negativeBalanceCount),
                        new ScenarioMetric(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT, pointLedgerEntryCount),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT, idempotencyReplayCount),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT, idempotencyHashMismatchCount),
                        new ScenarioMetric(ScenarioMetricName.DB_WAIT_MS_P95, dbWaitMsP95),
                        new ScenarioMetric(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT, redisLockAttemptCount),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_LANE_COUNT, rabbitMqLaneCount),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_RETRY_COUNT, queueRetryCount),
                        new ScenarioMetric(ScenarioMetricName.DLQ_COUNT, dlqCount),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_LAG_MS_P95, queueLagMsP95),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS, rabbitMqAcceptedLatencyMs),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS, rabbitMqCompletionLatencyMs),
                        new ScenarioMetric(ScenarioMetricName.AFTER_COMMIT_NOTIFICATION_COUNT, afterCommitNotificationCount),
                        new ScenarioMetric(ScenarioMetricName.OUTBOX_EVENT_COUNT, outboxEventCount)
                )
        );

        return new ScenarioRunRecord(id, sequence, report);
    }
}
