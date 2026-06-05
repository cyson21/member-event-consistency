package com.example.consistency.scenario;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlScenarioRunReportRepository implements ScenarioRunReportRepository {

    private final SqlExecutor executor;

    public SqlScenarioRunReportRepository(SqlExecutor executor) {
        this.executor = executor;
    }

    @Override
    public ScenarioRunRecord save(ScenarioRunReport report) {
        UUID runId = UUID.randomUUID();
        long sequence = executor.insertReturningLong(SqlStatement.of(
                """
                insert into scenario_runs (id, scenario, strategy, status, accepted_count, completed_count)
                values (?, ?, ?, ?, ?, ?)
                returning run_sequence
                """,
                runId,
                report.scenario().name(),
                report.strategy().name(),
                report.invariant().passed() ? "PASSED" : "FAILED",
                report.metricValue(ScenarioMetricName.ACCEPTED_COUNT),
                report.metricValue(ScenarioMetricName.COMPLETED_COUNT)
        ));

        for (ScenarioMetric metric : report.metrics()) {
            executor.insert(SqlStatement.of(
                    """
                    insert into scenario_metrics (run_id, metric_name, metric_value)
                    values (?, ?, ?)
                    """,
                    runId,
                    metric.name().name(),
                    metric.value()
            ));
        }

        return new ScenarioRunRecord(runId, sequence, report);
    }

    @Override
    public ScenarioRunReport findBySequence(long sequence) {
        return executor.queryLatestScenarioRun(reportLookupSql(
                """
                where sr.run_sequence = ?
                """,
                "",
                sequence
        )).report();
    }

    @Override
    public ScenarioRunSummary latestSummary(ScenarioType scenario, StrategyType strategy) {
        ScenarioRunRecord record = executor.queryLatestScenarioRun(reportLookupSql(
                """
                where sr.scenario = ? and sr.strategy = ?
                """,
                """
                order by sr.started_at desc
                limit 1
                """,
                scenario.name(),
                strategy.name()
        ));
        return new ScenarioRunSummary(
                record.sequence(),
                scenario,
                strategy,
                record.report().invariant().passed(),
                record.report()
        );
    }

    @Override
    public long count() {
        return executor.queryLong(SqlStatement.of("""
                select count(*) from scenario_runs
                """));
    }

    private SqlStatement reportLookupSql(String whereClause, String tailClause, Object... params) {
        return SqlStatement.of(
                """
                select
                  sr.id,
                  sr.run_sequence,
                  sr.scenario,
                  sr.strategy,
                  sr.status,
                  sr.accepted_count,
                  sr.completed_count,
                  coalesce(max(case when sm.metric_name = 'DUPLICATE_REWARD_COUNT' then sm.metric_value end), 0) as duplicate_reward_count,
                  coalesce(max(case when sm.metric_name = 'REWARD_ISSUED_COUNT' then sm.metric_value end), 0) as reward_issued_count,
                  coalesce(max(case when sm.metric_name = 'COUPON_ISSUED_COUNT' then sm.metric_value end), 0) as coupon_issued_count,
                  coalesce(max(case when sm.metric_name = 'OVER_ISSUE_COUNT' then sm.metric_value end), 0) as over_issue_count,
                  coalesce(max(case when sm.metric_name = 'REJECTED_COUNT' then sm.metric_value end), 0) as rejected_count,
                  coalesce(max(case when sm.metric_name = 'FINAL_POINT_BALANCE' then sm.metric_value end), 0) as final_point_balance,
                  coalesce(max(case when sm.metric_name = 'NEGATIVE_BALANCE_COUNT' then sm.metric_value end), 0) as negative_balance_count,
                  coalesce(max(case when sm.metric_name = 'POINT_LEDGER_ENTRY_COUNT' then sm.metric_value end), 0) as point_ledger_entry_count,
                  coalesce(max(case when sm.metric_name = 'IDEMPOTENCY_REPLAY_COUNT' then sm.metric_value end), 0) as idempotency_replay_count,
                  coalesce(max(case when sm.metric_name = 'IDEMPOTENCY_HASH_MISMATCH_COUNT' then sm.metric_value end), 0) as idempotency_hash_mismatch_count,
                  coalesce(max(case when sm.metric_name = 'DB_WAIT_MS_P95' then sm.metric_value end), 0) as db_wait_ms_p95,
                  coalesce(max(case when sm.metric_name = 'REDIS_LOCK_ATTEMPT_COUNT' then sm.metric_value end), 0) as redis_lock_attempt_count,
                  coalesce(max(case when sm.metric_name = 'RABBITMQ_LANE_COUNT' then sm.metric_value end), 0) as rabbitmq_lane_count,
                  coalesce(max(case when sm.metric_name = 'QUEUE_RETRY_COUNT' then sm.metric_value end), 0) as queue_retry_count,
                  coalesce(max(case when sm.metric_name = 'DLQ_COUNT' then sm.metric_value end), 0) as dlq_count,
                  coalesce(max(case when sm.metric_name = 'QUEUE_LAG_MS_P95' then sm.metric_value end), 0) as queue_lag_ms_p95,
                  coalesce(max(case when sm.metric_name = 'RABBITMQ_ACCEPTED_LATENCY_MS' then sm.metric_value end), 0) as rabbitmq_accepted_latency_ms,
                  coalesce(max(case when sm.metric_name = 'RABBITMQ_COMPLETION_LATENCY_MS' then sm.metric_value end), 0) as rabbitmq_completion_latency_ms,
                  coalesce(max(case when sm.metric_name = 'AFTER_COMMIT_NOTIFICATION_COUNT' then sm.metric_value end), 0) as after_commit_notification_count,
                  coalesce(max(case when sm.metric_name = 'OUTBOX_EVENT_COUNT' then sm.metric_value end), 0) as outbox_event_count
                from scenario_runs sr
                left join scenario_metrics sm on sm.run_id = sr.id
                """ + whereClause + """
                group by sr.run_sequence, sr.id, sr.scenario, sr.strategy, sr.status, sr.accepted_count, sr.completed_count, sr.started_at
                """ + tailClause + """
                """,
                params
        );
    }
}
