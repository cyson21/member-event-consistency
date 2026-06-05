package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlBatchExpirationRepositoryTest {

    public static void main(String[] args) {
        markUsedUsesIssuedToUsedConditionalUpdate();
        markExpiredUsesIssuedToExpiredConditionalUpdate();
        terminalStatusCountQueriesUseCouponIssueStatus();
    }

    private static void markUsedUsesIssuedToUsedConditionalUpdate() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(true);
        SqlBatchExpirationRepository repository = new SqlBatchExpirationRepository(executor);

        boolean marked = repository.tryMarkUsed(12002L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(true, marked, "issued coupon can be marked used");
        assertContains(statement.sql(), "update coupon_issues", "use update targets coupon issue");
        assertContains(statement.sql(), "set status = ?", "use update binds terminal status");
        assertContains(statement.sql(), "where id = ?", "use update targets one coupon issue");
        assertContains(statement.sql(), "and status = ?", "use update is conditional on current status");
        assertEquals("USED", statement.params().get(0), "used status is bound");
        assertEquals(12002L, statement.params().get(1), "coupon issue id is bound");
        assertEquals("ISSUED", statement.params().get(2), "source status is bound");
    }

    private static void markExpiredUsesIssuedToExpiredConditionalUpdate() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        SqlBatchExpirationRepository repository = new SqlBatchExpirationRepository(executor);

        boolean marked = repository.tryMarkExpired(12003L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(false, marked, "already terminal coupon cannot be expired again");
        assertContains(statement.sql(), "update coupon_issues", "expiration update targets coupon issue");
        assertContains(statement.sql(), "set status = ?", "expiration update binds terminal status");
        assertContains(statement.sql(), "where id = ?", "expiration update targets one coupon issue");
        assertContains(statement.sql(), "and status = ?", "expiration update is conditional on current status");
        assertEquals("EXPIRED", statement.params().get(0), "expired status is bound");
        assertEquals(12003L, statement.params().get(1), "coupon issue id is bound");
        assertEquals("ISSUED", statement.params().get(2), "source status is bound");
    }

    private static void terminalStatusCountQueriesUseCouponIssueStatus() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        executor.nextLongResult(0L);
        SqlBatchExpirationRepository repository = new SqlBatchExpirationRepository(executor);

        long usedCount = repository.usedCount(12004L);
        long expiredCount = repository.expiredCount(12004L);

        assertEquals(1L, usedCount, "used count returns recorded count");
        assertEquals(0L, expiredCount, "expired count returns recorded count");
        assertContains(executor.statementAt(0).sql(), "select count(*) from coupon_issues", "used count targets coupon issue");
        assertContains(executor.statementAt(0).sql(), "status = ?", "used count filters status");
        assertEquals("USED", executor.statementAt(0).params().get(1), "used status is bound");
        assertContains(executor.statementAt(1).sql(), "select count(*) from coupon_issues", "expired count targets coupon issue");
        assertContains(executor.statementAt(1).sql(), "status = ?", "expired count filters status");
        assertEquals("EXPIRED", executor.statementAt(1).params().get(1), "expired status is bound");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
