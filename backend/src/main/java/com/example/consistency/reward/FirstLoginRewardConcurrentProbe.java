package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class FirstLoginRewardConcurrentProbe {

    private FirstLoginRewardConcurrentProbe() {
    }

    public static FirstLoginRewardConcurrentProbeResult run(StrategyType strategy, long memberId, int requestCount) {
        if (memberId <= 0 || requestCount < 0) {
            throw new IllegalArgumentException("memberId must be positive and requestCount must be non-negative");
        }
        if (strategy != StrategyType.DB_GUARD && strategy != StrategyType.REDIS_LOCK_DB_GUARD) {
            throw new IllegalArgumentException(
                    "strategy is not supported for concurrent First Login Reward probe: " + strategy
            );
        }

        InMemoryRewardIssueRepository rewardIssues = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        RecordingRewardLockGateway locks = new RecordingRewardLockGateway();
        FirstLoginRewardService service = new FirstLoginRewardService(rewardIssues, followUps, locks);
        List<FirstLoginRewardDecision> decisions = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int index = 0; index < requestCount; index++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                await(start);
                decisions.add(service.issue(new FirstLoginRewardCommand(memberId, strategy)));
            });
            thread.start();
            threads.add(thread);
        }

        await(ready);
        start.countDown();
        for (Thread thread : threads) {
            join(thread);
        }

        long rewardIssuedCount = rewardIssues.issuedCount();
        return new FirstLoginRewardConcurrentProbeResult(
                strategy,
                memberId,
                requestCount,
                requestCount,
                requestCount,
                rewardIssuedCount,
                requestCount - rewardIssuedCount,
                rewardIssues.duplicateCount(),
                locks.attemptCount(),
                locks.lastLockKey(),
                followUps.afterCommitNotificationCount(),
                followUps.outboxEventCount()
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent reward probe interrupted", exception);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent reward probe interrupted", exception);
        }
    }
}
