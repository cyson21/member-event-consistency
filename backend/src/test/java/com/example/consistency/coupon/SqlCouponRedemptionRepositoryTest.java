package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlCouponRedemptionRepositoryTest {

    public static void main(String[] args) {
        markUsedUsesIssuedToUsedConditionalUpdate();
        idempotencyRecordAndReplayLookupUseRecordsTable();
        idempotencyHashMismatchLookupUsesRecordsTable();
        usedCountQueryUsesCouponIssueStatus();
    }

    private static void markUsedUsesIssuedToUsedConditionalUpdate() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        SqlCouponRedemptionRepository repository = new SqlCouponRedemptionRepository(executor);

        boolean marked = repository.tryMarkUsed(10003L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(false, marked, "already terminal coupon returns false");
        assertContains(statement.sql(), "update coupon_issues", "redemption update targets coupon issue");
        assertContains(statement.sql(), "set status = ?", "redemption update binds terminal status");
        assertContains(statement.sql(), "where id = ?", "redemption update targets one coupon issue");
        assertContains(statement.sql(), "and status = ?", "redemption update is conditional on current status");
        assertEquals("USED", statement.params().get(0), "terminal status is bound");
        assertEquals(10003L, statement.params().get(1), "coupon issue id is bound");
        assertEquals("ISSUED", statement.params().get(2), "source status is bound");
    }

    private static void idempotencyRecordAndReplayLookupUseRecordsTable() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(true);
        executor.nextLongResult(1L);
        SqlCouponRedemptionRepository repository = new SqlCouponRedemptionRepository(executor);

        boolean recorded = repository.insertIdempotencyRecord(
                "redeem-10003",
                "member=501|coupon=10003",
                "coupon-issue:10003"
        );
        long replayCount = repository.replayCount("redeem-10003");

        assertEquals(true, recorded, "idempotency insert returns true");
        assertEquals(1L, replayCount, "replay lookup returns recorded count");
        assertContains(executor.statementAt(0).sql(), "insert into idempotency_records", "idempotency insert targets records table");
        assertContains(executor.statementAt(0).sql(), "on conflict (idempotency_key) do nothing", "idempotency insert uses unique key");
        assertContains(executor.statementAt(1).sql(), "select count(*) from idempotency_records", "replay lookup uses records table");
        assertEquals("redeem-10003", executor.statementAt(0).params().get(0), "idempotency key is bound");
        assertEquals("member=501|coupon=10003", executor.statementAt(0).params().get(1), "request hash is bound");
        assertEquals("coupon-issue:10003", executor.statementAt(0).params().get(2), "response reference is bound");
        assertEquals("COMPLETED", executor.statementAt(0).params().get(3), "status is bound");
    }

    private static void idempotencyHashMismatchLookupUsesRecordsTable() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        SqlCouponRedemptionRepository repository = new SqlCouponRedemptionRepository(executor);

        long mismatchCount = repository.requestHashMismatchCount("redeem-10003", "member=502|coupon=10003");

        SqlStatement statement = executor.lastStatement();
        assertEquals(1L, mismatchCount, "mismatch count returns recorded count");
        assertContains(statement.sql(), "select count(*) from idempotency_records", "mismatch lookup uses records table");
        assertContains(statement.sql(), "idempotency_key = ?", "mismatch lookup filters key");
        assertContains(statement.sql(), "request_hash <> ?", "mismatch lookup compares request hash");
        assertEquals("redeem-10003", statement.params().get(0), "idempotency key is bound");
        assertEquals("member=502|coupon=10003", statement.params().get(1), "request hash is bound");
    }

    private static void usedCountQueryUsesCouponIssueStatus() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        SqlCouponRedemptionRepository repository = new SqlCouponRedemptionRepository(executor);

        long usedCount = repository.usedCount(10003L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(1L, usedCount, "used count returns recorded count");
        assertContains(statement.sql(), "select count(*) from coupon_issues", "used count query targets coupon issue");
        assertContains(statement.sql(), "status = ?", "used count filters terminal used status");
        assertEquals(10003L, statement.params().get(0), "coupon issue id is bound");
        assertEquals("USED", statement.params().get(1), "used status is bound");
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
