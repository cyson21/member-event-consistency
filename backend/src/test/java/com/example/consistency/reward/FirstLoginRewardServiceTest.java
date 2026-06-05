package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardServiceTest {

    public static void main(String[] args) {
        naiveStrategyIssuesEveryRequest();
        dbGuardRejectsDuplicateBeforeFollowUps();
        redisLockDbGuardUsesRewardScopedLockAndDbGuard();
    }

    private static void naiveStrategyIssuesEveryRequest() {
        InMemoryRewardIssueRepository repository = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        RecordingRewardLockGateway locks = new RecordingRewardLockGateway();
        FirstLoginRewardService service = new FirstLoginRewardService(repository, followUps, locks);

        FirstLoginRewardDecision first = service.issue(new FirstLoginRewardCommand(2001L, StrategyType.NAIVE));
        FirstLoginRewardDecision second = service.issue(new FirstLoginRewardCommand(2001L, StrategyType.NAIVE));

        assertEquals(FirstLoginRewardOutcome.ISSUED, first.outcome(), "first naive request issues reward");
        assertEquals(FirstLoginRewardOutcome.ISSUED, second.outcome(), "second naive request also issues reward");
        assertEquals(2L, repository.issuedCount(), "naive strategy stores every issue attempt");
        assertEquals(1L, repository.duplicateCount(), "naive strategy creates one duplicate for two same-member requests");
        assertEquals(2L, followUps.afterCommitNotificationCount(), "naive successful issues create fake after-commit records");
        assertEquals(2L, followUps.outboxEventCount(), "naive successful issues create fake outbox records");
    }

    private static void dbGuardRejectsDuplicateBeforeFollowUps() {
        InMemoryRewardIssueRepository repository = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        RecordingRewardLockGateway locks = new RecordingRewardLockGateway();
        FirstLoginRewardService service = new FirstLoginRewardService(repository, followUps, locks);

        FirstLoginRewardDecision first = service.issue(new FirstLoginRewardCommand(2002L, StrategyType.DB_GUARD));
        FirstLoginRewardDecision second = service.issue(new FirstLoginRewardCommand(2002L, StrategyType.DB_GUARD));

        assertEquals(FirstLoginRewardOutcome.ISSUED, first.outcome(), "first guarded request issues reward");
        assertEquals(FirstLoginRewardOutcome.DUPLICATE_REJECTED, second.outcome(), "second guarded request is rejected by unique guard");
        assertEquals(1L, repository.issuedCount(), "DB guard stores one issue");
        assertEquals(0L, repository.duplicateCount(), "DB guard keeps duplicate count zero");
        assertEquals(1L, followUps.afterCommitNotificationCount(), "duplicate rejection does not create after-commit record");
        assertEquals(1L, followUps.outboxEventCount(), "duplicate rejection does not create outbox record");
    }

    private static void redisLockDbGuardUsesRewardScopedLockAndDbGuard() {
        InMemoryRewardIssueRepository repository = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        RecordingRewardLockGateway locks = new RecordingRewardLockGateway();
        FirstLoginRewardService service = new FirstLoginRewardService(repository, followUps, locks);

        service.issue(new FirstLoginRewardCommand(2003L, StrategyType.REDIS_LOCK_DB_GUARD));
        FirstLoginRewardDecision second = service.issue(new FirstLoginRewardCommand(2003L, StrategyType.REDIS_LOCK_DB_GUARD));

        assertEquals(FirstLoginRewardOutcome.DUPLICATE_REJECTED, second.outcome(), "Redis lock path still relies on DB guard");
        assertEquals(2L, locks.attemptCount(), "lock attempt is recorded per request");
        assertEquals("lock:first-login-reward:2003", locks.lastLockKey(), "lock key is reward scoped");
        assertEquals(1L, repository.issuedCount(), "Redis lock plus DB guard stores one issue");
        assertEquals(0L, repository.duplicateCount(), "Redis lock plus DB guard keeps duplicate count zero");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}

