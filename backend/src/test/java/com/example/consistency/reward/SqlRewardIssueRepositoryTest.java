package com.example.consistency.reward;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlRewardIssueRepositoryTest {

    public static void main(String[] args) {
        naiveInsertUsesPlainRewardIssueInsert();
        uniqueGuardUsesOnConflictDoNothing();
        countsUseRewardIssueQueries();
    }

    private static void naiveInsertUsesPlainRewardIssueInsert() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        SqlRewardIssueRepository repository = new SqlRewardIssueRepository(executor);

        boolean inserted = repository.insertNaive(5001L, RewardType.FIRST_LOGIN);

        SqlStatement statement = executor.lastStatement();
        assertEquals(true, inserted, "naive insert reports success");
        assertContains(statement.sql(), "insert into reward_issue_attempts", "naive insert targets duplicate-prone attempts");
        assertContains(statement.sql(), "values (?, ?, ?, ?)", "naive insert uses bind variables");
        assertEquals(5001L, statement.params().get(1), "member id is bound");
        assertEquals("FIRST_LOGIN", statement.params().get(2), "reward type is bound");
        assertEquals("ISSUED", statement.params().get(3), "status is bound");
    }

    private static void uniqueGuardUsesOnConflictDoNothing() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        SqlRewardIssueRepository repository = new SqlRewardIssueRepository(executor);

        boolean inserted = repository.insertUnique(5002L, RewardType.FIRST_LOGIN);

        SqlStatement statement = executor.lastStatement();
        assertEquals(false, inserted, "unique conflict returns false");
        assertContains(statement.sql(), "on conflict (member_id, reward_type) do nothing", "unique guard is DB-backed");
    }

    private static void countsUseRewardIssueQueries() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(3L);
        executor.nextLongResult(2L);
        SqlRewardIssueRepository repository = new SqlRewardIssueRepository(executor);

        assertEquals(3L, repository.issuedCount(), "issued count comes from SQL");
        assertEquals(2L, repository.duplicateCount(), "duplicate count comes from SQL");
        assertContains(executor.statementAt(0).sql(), "count(*) from reward_issue_attempts", "issued query counts naive attempts");
        assertContains(executor.statementAt(1).sql(), "from reward_issue_attempts", "duplicate query uses naive attempts");
        assertContains(executor.statementAt(1).sql(), "greatest(count(*) - count(distinct", "duplicate query calculates duplicate surplus");
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
