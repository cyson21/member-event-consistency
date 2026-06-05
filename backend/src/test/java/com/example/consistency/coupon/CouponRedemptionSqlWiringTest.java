package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionSqlWiringTest {

    public static void main(String[] args) {
        dbGuardSqlRecordingUsesConditionalTerminalTransition();
        idempotencyReplaySqlRecordingUsesHashCheckReplayAndConditionalTransition();
        idempotencyMismatchSqlRecordingRejectsBeforeSecondTerminalTransition();
    }

    private static void dbGuardSqlRecordingUsesConditionalTerminalTransition() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.nextInsertResult(true);
        sql.nextInsertResult(false);
        CouponRedemptionServiceScenarioExecutor executor = executor(sql);

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 10012L, 2);

        assertEquals(true, record.report().invariant().passed(), "DB guard SQL recording passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "one SQL transition succeeds");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "second SQL transition is rejected");
        assertEquals(2L, countSql(sql, "update coupon_issues"), "DB guard attempts two conditional updates");
        assertStatementContains(sql, "and status = ?", "terminal transition is status-conditional");
    }

    private static void idempotencyReplaySqlRecordingUsesHashCheckReplayAndConditionalTransition() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.nextLongResult(0L);
        sql.nextLongResult(0L);
        sql.nextInsertResult(true);
        sql.nextInsertResult(true);
        sql.nextLongResult(0L);
        sql.nextLongResult(1L);
        CouponRedemptionServiceScenarioExecutor executor = executor(sql);

        ScenarioRunRecord record = executor.executeWithIdempotency(
                StrategyType.IDEMPOTENCY_REPLAY,
                10013L,
                "redeem-10013",
                "member=501|coupon=10013",
                "member=501|coupon=10013"
        );

        assertEquals(true, record.report().invariant().passed(), "idempotency SQL recording passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "coupon is used once");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "same request retry is replayed");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "same request retry has no mismatch");
        assertEquals(1L, countSql(sql, "update coupon_issues"), "replay path performs one terminal update");
        assertStatementContains(sql, "insert into idempotency_records", "first request records idempotency result");
        assertStatementContains(sql, "request_hash <>", "retry checks request hash mismatch first");
        assertStatementContains(sql, "select count(*) from idempotency_records", "retry checks replay record");
    }

    private static void idempotencyMismatchSqlRecordingRejectsBeforeSecondTerminalTransition() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.nextLongResult(0L);
        sql.nextLongResult(0L);
        sql.nextInsertResult(true);
        sql.nextInsertResult(true);
        sql.nextLongResult(1L);
        CouponRedemptionServiceScenarioExecutor executor = executor(sql);

        ScenarioRunRecord record = executor.executeWithIdempotency(
                StrategyType.IDEMPOTENCY_REPLAY,
                10014L,
                "redeem-10014",
                "member=501|coupon=10014",
                "member=502|coupon=10014"
        );

        assertEquals(true, record.report().invariant().passed(), "mismatch SQL recording passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "coupon is used once");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "mismatch is reported");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "mismatch retry is rejected");
        assertEquals(1L, countSql(sql, "update coupon_issues"), "mismatch path performs no second terminal update");
        assertStatementContains(sql, "request_hash <>", "mismatch path checks stored hash");
    }

    private static CouponRedemptionServiceScenarioExecutor executor(RecordingSqlExecutor sql) {
        return new CouponRedemptionServiceScenarioExecutor(
                CouponRedemptionSqlWiring.service(sql),
                new InMemoryScenarioRunReportRepository()
        );
    }

    private static void assertStatementContains(RecordingSqlExecutor sql, String expected, String message) {
        for (int index = 0; index < sql.statementCount(); index++) {
            if (sql.statementAt(index).sql().contains(expected)) {
                return;
            }
        }
        throw new AssertionError(message + " expected SQL fragment=[" + expected + "]");
    }

    private static long countSql(RecordingSqlExecutor sql, String expected) {
        long count = 0;
        for (int index = 0; index < sql.statementCount(); index++) {
            if (sql.statementAt(index).sql().contains(expected)) {
                count++;
            }
        }
        return count;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
