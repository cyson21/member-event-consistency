package com.example.consistency.reward;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardSqlWiringTest {

    public static void main(String[] args) {
        dbGuardSuccessWritesRewardIssueThenOutboxRows();
        dbGuardDuplicateStopsBeforeOutboxRows();
        redisLockDbGuardUsesRewardScopedLockWithSqlAdapters();
    }

    private static void dbGuardSuccessWritesRewardIssueThenOutboxRows() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        FirstLoginRewardService service = FirstLoginRewardSqlWiring.service(
                executor,
                new RecordingRewardLockGateway()
        );

        FirstLoginRewardDecision decision = service.issue(new FirstLoginRewardCommand(82001L, StrategyType.DB_GUARD));

        assertEquals(FirstLoginRewardOutcome.ISSUED, decision.outcome(), "DB guard success issues reward");
        assertEquals(3L, executor.statementCount(), "reward issue plus two outbox rows are written");
        assertContains(executor.statementAt(0).sql(), "insert into reward_issues", "first statement writes reward issue");
        assertContains(executor.statementAt(0).sql(), "on conflict (member_id, reward_type) do nothing", "reward issue uses unique guard");
        assertContains(executor.statementAt(1).sql(), "insert into outbox_events", "second statement writes reward-issued outbox row");
        assertContains(executor.statementAt(2).sql(), "insert into outbox_events", "third statement writes fake notification outbox row");
    }

    private static void dbGuardDuplicateStopsBeforeOutboxRows() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        FirstLoginRewardService service = FirstLoginRewardSqlWiring.service(
                executor,
                new RecordingRewardLockGateway()
        );

        FirstLoginRewardDecision decision = service.issue(new FirstLoginRewardCommand(82002L, StrategyType.DB_GUARD));

        assertEquals(FirstLoginRewardOutcome.DUPLICATE_REJECTED, decision.outcome(), "duplicate is rejected");
        assertEquals(1L, executor.statementCount(), "duplicate rejection stops before outbox writes");
        assertContains(executor.statementAt(0).sql(), "insert into reward_issues", "only reward issue insert is attempted");
    }

    private static void redisLockDbGuardUsesRewardScopedLockWithSqlAdapters() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        RecordingRewardLockGateway locks = new RecordingRewardLockGateway();
        FirstLoginRewardService service = FirstLoginRewardSqlWiring.service(executor, locks);

        FirstLoginRewardDecision decision = service.issue(new FirstLoginRewardCommand(
                82003L,
                StrategyType.REDIS_LOCK_DB_GUARD
        ));

        assertEquals(FirstLoginRewardOutcome.ISSUED, decision.outcome(), "Redis lock plus DB guard issues reward");
        assertEquals(true, decision.lockAttempted(), "lock attempt is reflected in decision");
        assertEquals("lock:first-login-reward:82003", locks.lastLockKey(), "lock key is reward scoped");
        assertEquals(1L, locks.attemptCount(), "one lock attempt is recorded");
        assertEquals(3L, executor.statementCount(), "SQL adapters still write reward issue and outbox rows");
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

