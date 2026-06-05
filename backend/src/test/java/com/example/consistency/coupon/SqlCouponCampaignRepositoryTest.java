package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlCouponCampaignRepositoryTest {

    public static void main(String[] args) {
        tryReserveCapacityUsesConditionalUpdate();
        insertIssueUsesPerMemberUniqueGuard();
        issueWithCapacityGuardUsesSingleStatementWithCampaignRowLock();
        countQueriesUseCouponTables();
    }

    private static void tryReserveCapacityUsesConditionalUpdate() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlCouponCampaignRepository repository = new SqlCouponCampaignRepository(executor);

        boolean reserved = repository.tryReserveCapacity(94001L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(true, reserved, "capacity reservation reports update success");
        assertContains(statement.sql(), "update coupon_campaigns", "capacity reservation targets campaign table");
        assertContains(statement.sql(), "issued_count = issued_count + 1", "capacity reservation increments issued count");
        assertContains(statement.sql(), "issued_count < capacity", "capacity reservation is conditional");
        assertEquals(94001L, statement.params().get(0), "campaign id is bound");
    }

    private static void insertIssueUsesPerMemberUniqueGuard() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        SqlCouponCampaignRepository repository = new SqlCouponCampaignRepository(executor);

        boolean inserted = repository.insertIssue(94001L, 93001L, "coupon-94001-93001");

        SqlStatement statement = executor.lastStatement();
        assertEquals(false, inserted, "duplicate member issue returns false");
        assertContains(statement.sql(), "insert into coupon_issues", "issue insert targets coupon_issues");
        assertContains(statement.sql(), "on conflict (campaign_id, member_id) do nothing", "issue insert uses member uniqueness");
        assertEquals(94001L, statement.params().get(0), "campaign id is bound");
        assertEquals(93001L, statement.params().get(1), "member id is bound");
        assertEquals("ISSUED", statement.params().get(2), "status is bound");
        assertEquals("coupon-94001-93001", statement.params().get(3), "idempotency key is bound");
    }

    private static void issueWithCapacityGuardUsesSingleStatementWithCampaignRowLock() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        SqlCouponCampaignRepository repository = new SqlCouponCampaignRepository(executor);

        CouponCampaignIssueResult result = repository.issueWithCapacityGuard(94001L, 93001L, "coupon-94001-93001");

        SqlStatement statement = executor.lastStatement();
        assertEquals(true, result.issued(), "single-statement guarded issue succeeds when row is returned");
        assertContains(statement.sql(), "for update", "guarded issue locks campaign row before issue insert");
        assertContains(statement.sql(), "insert into coupon_issues", "guarded issue inserts coupon issue");
        assertContains(statement.sql(), "update coupon_campaigns", "guarded issue increments issued count");
        assertContains(statement.sql(), "select count(*) from updated", "guarded issue returns updated row count");
        assertEquals(94001L, statement.params().get(0), "campaign id is bound");
        assertEquals(93001L, statement.params().get(1), "member id is bound");
        assertEquals("coupon-94001-93001", statement.params().get(2), "idempotency key is bound");
    }

    private static void countQueriesUseCouponTables() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(3L);
        executor.nextLongResult(0L);
        SqlCouponCampaignRepository repository = new SqlCouponCampaignRepository(executor);

        assertEquals(3L, repository.issuedCount(94001L), "issued count comes from campaign row");
        assertEquals(0L, repository.overIssueCount(94001L), "over issue count is calculated from campaign row");
        assertContains(executor.statementAt(0).sql(), "select issued_count from coupon_campaigns", "issued count query uses campaign row");
        assertContains(executor.statementAt(1).sql(), "greatest(issued_count - capacity, 0)", "over issue query calculates surplus");
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
