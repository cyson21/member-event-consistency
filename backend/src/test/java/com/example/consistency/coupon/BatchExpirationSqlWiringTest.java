package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public final class BatchExpirationSqlWiringTest {

    public static void main(String[] args) {
        userUseWinsSqlRecordingUsesConditionalTerminalTransitions();
        batchExpirationWinsSqlRecordingUsesConditionalTerminalTransitions();
    }

    private static void userUseWinsSqlRecordingUsesConditionalTerminalTransitions() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.nextInsertResult(true);
        sql.nextInsertResult(false);
        BatchExpirationServiceScenarioExecutor executor = executor(sql);

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 13021L, BatchExpirationWinner.USER_USE);

        assertEquals(true, record.report().invariant().passed(), "DB guard SQL recording passes invariant");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "user use wins once");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration does not also win");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "losing expiration is rejected");
        assertEquals(2L, countSql(sql, "update coupon_issues"), "DB guard attempts two conditional updates");
        assertStatementContains(sql, "set status = ?", "terminal transition updates status");
        assertStatementContains(sql, "and status = ?", "terminal transition is status-conditional");
    }

    private static void batchExpirationWinsSqlRecordingUsesConditionalTerminalTransitions() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.nextInsertResult(true);
        sql.nextInsertResult(false);
        BatchExpirationServiceScenarioExecutor executor = executor(sql);

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 13022L, BatchExpirationWinner.BATCH_EXPIRATION);

        assertEquals(true, record.report().invariant().passed(), "DB guard SQL recording passes invariant");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.COUPON_USED_COUNT), "use does not also win");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT), "expiration wins once");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "losing use is rejected");
        assertEquals(2L, countSql(sql, "update coupon_issues"), "DB guard attempts two conditional updates");
        assertStatementContains(sql, "EXPIRED", "expiration terminal value is recorded");
        assertStatementContains(sql, "USED", "use terminal value is recorded");
    }

    private static BatchExpirationServiceScenarioExecutor executor(RecordingSqlExecutor sql) {
        return new BatchExpirationServiceScenarioExecutor(
                BatchExpirationSqlWiring.service(sql),
                new InMemoryScenarioRunReportRepository()
        );
    }

    private static void assertStatementContains(RecordingSqlExecutor sql, String expected, String message) {
        for (int index = 0; index < sql.statementCount(); index++) {
            if (sql.statementAt(index).sql().contains(expected)
                    || sql.statementAt(index).params().stream().anyMatch(param -> String.valueOf(param).contains(expected))) {
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
