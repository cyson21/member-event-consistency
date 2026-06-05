package com.example.consistency.reward;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardHarnessTest {

    public static void main(String[] args) {
        naivePathBreaksOneRewardPerMemberInvariant();
        dbGuardKeepsDuplicateCountZero();
        redisLockDbGuardKeepsDuplicateCountZeroAndRecordsLockAttempts();
        successfulIssueCreatesFakeAfterCommitAndOutboxEvents();
    }

    private static void naivePathBreaksOneRewardPerMemberInvariant() {
        FirstLoginRewardRunResult run = FirstLoginRewardRunner.run(StrategyType.NAIVE, 1001L, 8);
        InvariantResult invariant = InvariantChecker.evaluate(run.toInvariantResult());

        assertEquals(false, invariant.passed(), "naive path must fail the invariant");
        assertEquals(7L, run.duplicateCount(), "naive path should issue duplicates for repeated first-login requests");
    }

    private static void dbGuardKeepsDuplicateCountZero() {
        FirstLoginRewardRunResult run = FirstLoginRewardRunner.run(StrategyType.DB_GUARD, 1002L, 8);
        InvariantResult invariant = InvariantChecker.evaluate(run.toInvariantResult());

        assertEquals(true, invariant.passed(), "DB guard must pass the invariant");
        assertEquals(0L, run.duplicateCount(), "DB unique guard should reject duplicate reward issues");
        assertEquals(1L, run.issuedCount(), "DB unique guard should issue exactly one reward");
    }

    private static void redisLockDbGuardKeepsDuplicateCountZeroAndRecordsLockAttempts() {
        FirstLoginRewardRunResult run = FirstLoginRewardRunner.run(StrategyType.REDIS_LOCK_DB_GUARD, 1003L, 8);
        InvariantResult invariant = InvariantChecker.evaluate(run.toInvariantResult());

        assertEquals(true, invariant.passed(), "Redis lock plus DB guard must pass the invariant");
        assertEquals(0L, run.duplicateCount(), "Redis lock path still relies on DB guard for duplicates");
        assertEquals(8L, run.lockAttemptCount(), "Redis lock path should record one lock attempt per request");
        assertEquals("lock:first-login-reward:1003", run.lockKey(), "lock scope must target the reward invariant");
    }

    private static void successfulIssueCreatesFakeAfterCommitAndOutboxEvents() {
        FirstLoginRewardRunResult run = FirstLoginRewardRunner.run(StrategyType.DB_GUARD, 1004L, 3);

        assertEquals(1L, run.afterCommitNotificationCount(), "one fake after-commit notification is recorded per successful issue");
        assertEquals(1L, run.outboxEventCount(), "one fake outbox event is recorded per successful issue");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}

