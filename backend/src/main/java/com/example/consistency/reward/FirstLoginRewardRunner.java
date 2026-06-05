package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FirstLoginRewardRunner {

    private FirstLoginRewardRunner() {
    }

    public static FirstLoginRewardRunResult run(StrategyType strategy, long memberId, int requestCount) {
        InMemoryRewardIssueRepository repository = new InMemoryRewardIssueRepository();
        FakeRewardFollowUpRecorder followUps = new FakeRewardFollowUpRecorder();
        RecordingRewardLockGateway locks = new RecordingRewardLockGateway();
        FirstLoginRewardService service = new FirstLoginRewardService(repository, followUps, locks);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        List<RuntimeException> failures = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, requestCount));
        try {
            for (int i = 0; i < requestCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        service.issue(new FirstLoginRewardCommand(memberId, strategy));
                    } catch (RuntimeException exception) {
                        synchronized (failures) {
                            failures.add(exception);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            await(ready);
            start.countDown();
            await(done);
        } finally {
            executor.shutdown();
        }

        if (!failures.isEmpty()) {
            throw failures.get(0);
        }

        return new FirstLoginRewardRunResult(
                strategy,
                requestCount,
                repository.issuedCount(),
                repository.duplicateCount(),
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
            throw new IllegalStateException("Interrupted while running First Login Reward harness", exception);
        }
    }
}
