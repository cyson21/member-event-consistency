package com.example.consistency.persistence;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public final class JdbcSqlExecutorTest {

    public static void main(String[] args) {
        insertBindsParametersAndExecutesUpdate();
        queryLongReadsFirstColumn();
        insertReturningLongReadsFirstColumn();
        queryLatestScenarioRunHydratesReport();
        queryLatestScenarioRunHydratesCouponMetrics();
        queryLatestScenarioRunHydratesPointMetrics();
    }

    private static void insertBindsParametersAndExecutesUpdate() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.updateCount = 1;
        JdbcSqlExecutor executor = new JdbcSqlExecutor(jdbc.dataSource());

        boolean inserted = executor.insert(SqlStatement.of("insert into reward_issues values (?, ?)", 77L, "FIRST_LOGIN"));

        assertEquals(true, inserted, "insert returns true for positive update count");
        assertEquals("insert into reward_issues values (?, ?)", jdbc.sql, "SQL is passed to JDBC");
        assertEquals(List.of(77L, "FIRST_LOGIN"), jdbc.params, "params are bound in order");
    }

    private static void queryLongReadsFirstColumn() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.rows.add(Map.of("1", 42L));
        JdbcSqlExecutor executor = new JdbcSqlExecutor(jdbc.dataSource());

        long value = executor.queryLong(SqlStatement.of("select count(*) from reward_issues"));

        assertEquals(42L, value, "queryLong reads first column");
    }

    private static void insertReturningLongReadsFirstColumn() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.rows.add(Map.of("1", 13L));
        JdbcSqlExecutor executor = new JdbcSqlExecutor(jdbc.dataSource());

        long value = executor.insertReturningLong(SqlStatement.of("insert into scenario_runs returning run_sequence"));

        assertEquals(13L, value, "insertReturningLong reads first returned column");
    }

    private static void queryLatestScenarioRunHydratesReport() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.rows.add(Map.ofEntries(
                entry("run_sequence", 9L),
                entry("scenario", "FIRST_LOGIN_REWARD"),
                entry("strategy", "NAIVE"),
                entry("status", "FAILED"),
                entry("accepted_count", 5L),
                entry("completed_count", 5L),
                entry("duplicate_reward_count", 4L),
                entry("reward_issued_count", 5L),
                entry("redis_lock_attempt_count", 0L),
                entry("after_commit_notification_count", 5L),
                entry("outbox_event_count", 5L)
        ));
        JdbcSqlExecutor executor = new JdbcSqlExecutor(jdbc.dataSource());

        ScenarioRunRecord record = executor.queryLatestScenarioRun(SqlStatement.of("select scenario run"));

        assertEquals(9L, record.sequence(), "sequence is read");
        assertEquals(ScenarioType.FIRST_LOGIN_REWARD, record.report().scenario(), "scenario is read");
        assertEquals(StrategyType.NAIVE, record.report().strategy(), "strategy is read");
        assertEquals(false, record.report().invariant().passed(), "status hydrates invariant");
        assertEquals(4L, record.report().metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT), "duplicate metric is read");
    }

    private static void queryLatestScenarioRunHydratesCouponMetrics() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.rows.add(Map.ofEntries(
                entry("run_sequence", 10L),
                entry("scenario", "COUPON_CAMPAIGN_ISSUE"),
                entry("strategy", "RABBITMQ_DB_GUARD"),
                entry("status", "PASSED"),
                entry("accepted_count", 8L),
                entry("completed_count", 8L),
                entry("duplicate_reward_count", 0L),
                entry("reward_issued_count", 0L),
                entry("coupon_issued_count", 3L),
                entry("over_issue_count", 0L),
                entry("rejected_count", 5L),
                entry("redis_lock_attempt_count", 0L),
                entry("rabbitmq_lane_count", 1L),
                entry("queue_lag_ms_p95", 70L),
                entry("rabbitmq_accepted_latency_ms", 12L),
                entry("rabbitmq_completion_latency_ms", 82L),
                entry("after_commit_notification_count", 0L),
                entry("outbox_event_count", 0L)
        ));
        JdbcSqlExecutor executor = new JdbcSqlExecutor(jdbc.dataSource());

        ScenarioRunRecord record = executor.queryLatestScenarioRun(SqlStatement.of("select coupon scenario run"));

        assertEquals(ScenarioType.COUPON_CAMPAIGN_ISSUE, record.report().scenario(), "coupon scenario is read");
        assertEquals(true, record.report().invariant().passed(), "coupon status hydrates invariant");
        assertEquals(3L, record.report().metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "coupon issued metric is read");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.RABBITMQ_LANE_COUNT), "RabbitMQ lane metric is read");
        assertEquals(70L, record.report().metricValue(ScenarioMetricName.QUEUE_LAG_MS_P95), "queue lag metric is read");
        assertEquals(12L, record.report().metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS), "accepted latency metric is read");
        assertEquals(82L, record.report().metricValue(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS), "completion latency metric is read");
    }

    private static void queryLatestScenarioRunHydratesPointMetrics() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.rows.add(Map.ofEntries(
                entry("run_sequence", 11L),
                entry("scenario", "POINT_SPEND"),
                entry("strategy", "IDEMPOTENCY_REPLAY"),
                entry("status", "PASSED"),
                entry("accepted_count", 2L),
                entry("completed_count", 2L),
                entry("duplicate_reward_count", 0L),
                entry("reward_issued_count", 0L),
                entry("coupon_issued_count", 0L),
                entry("over_issue_count", 0L),
                entry("rejected_count", 0L),
                entry("final_point_balance", 300L),
                entry("negative_balance_count", 0L),
                entry("point_ledger_entry_count", 1L),
                entry("idempotency_replay_count", 1L),
                entry("idempotency_hash_mismatch_count", 0L),
                entry("db_wait_ms_p95", 0L),
                entry("redis_lock_attempt_count", 0L),
                entry("rabbitmq_lane_count", 0L),
                entry("queue_lag_ms_p95", 0L),
                entry("after_commit_notification_count", 0L),
                entry("outbox_event_count", 0L)
        ));
        JdbcSqlExecutor executor = new JdbcSqlExecutor(jdbc.dataSource());

        ScenarioRunRecord record = executor.queryLatestScenarioRun(SqlStatement.of("select point scenario run"));

        assertEquals(ScenarioType.POINT_SPEND, record.report().scenario(), "point scenario is read");
        assertEquals(StrategyType.IDEMPOTENCY_REPLAY, record.report().strategy(), "point strategy is read");
        assertEquals(300L, record.report().metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "final balance metric is read");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "ledger metric is read");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "replay metric is read");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "hash mismatch metric is read");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static final class FakeJdbc {
        private String sql;
        private int updateCount;
        private final List<Object> params = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();

        DataSource dataSource() {
            return proxy(DataSource.class, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return connection();
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    sql = (String) args[0];
                    return preparedStatement();
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        private PreparedStatement preparedStatement() {
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                if ("setObject".equals(method.getName())) {
                    int index = (Integer) args[0];
                    while (params.size() < index) {
                        params.add(null);
                    }
                    params.set(index - 1, args[1]);
                    return null;
                }
                if ("executeUpdate".equals(method.getName())) {
                    return updateCount;
                }
                if ("executeQuery".equals(method.getName())) {
                    return resultSet();
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        private ResultSet resultSet() {
            Map<String, Object> firstRow = rows.isEmpty() ? new HashMap<>() : rows.get(0);
            final boolean[] nextCalled = {false};
            return proxy(ResultSet.class, (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    if (nextCalled[0] || rows.isEmpty()) {
                        return false;
                    }
                    nextCalled[0] = true;
                    return true;
                }
                if ("getLong".equals(method.getName())) {
                    Object key = args[0];
                    Object value = firstRow.get(String.valueOf(key));
                    return value == null ? 0L : ((Number) value).longValue();
                }
                if ("getString".equals(method.getName())) {
                    Object value = firstRow.get(String.valueOf(args[0]));
                    return value == null ? null : String.valueOf(value);
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }
    }
}
