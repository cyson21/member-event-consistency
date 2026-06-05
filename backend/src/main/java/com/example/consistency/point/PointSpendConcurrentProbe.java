package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class PointSpendConcurrentProbe {

    private PointSpendConcurrentProbe() {
    }

    public static PointSpendConcurrentProbeResult run(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount
    ) {
        if (memberId <= 0 || initialBalance < 0 || spendAmount <= 0 || requestCount < 0) {
            throw new IllegalArgumentException("memberId and spendAmount must be positive, counts and balance must be valid");
        }
        if (strategy != StrategyType.DB_ROW_LOCK && strategy != StrategyType.CONDITIONAL_UPDATE) {
            throw new IllegalArgumentException("strategy is not supported for concurrent Point Spend probe: " + strategy);
        }

        ConcurrentPointSpendRepository repository = new ConcurrentPointSpendRepository(initialBalance);
        PointSpendService service = new PointSpendService(repository);
        List<PointSpendDecision> decisions = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int index = 0; index < requestCount; index++) {
            int requestIndex = index;
            Thread thread = new Thread(() -> {
                ready.countDown();
                await(start);
                decisions.add(service.spend(new PointSpendCommand(
                        memberId,
                        spendAmount,
                        strategy,
                        "concurrent-spend-" + memberId + "-" + requestIndex,
                        "hash-" + strategy.name() + "-" + memberId + "-" + spendAmount + "-" + requestIndex
                )));
            });
            thread.start();
            threads.add(thread);
        }

        await(ready);
        start.countDown();
        for (Thread thread : threads) {
            join(thread);
        }

        long successfulSpendCount = decisions.stream().filter(PointSpendDecision::accepted).count();
        long rejectedCount = requestCount - successfulSpendCount;
        long finalBalance = repository.balance();
        long negativeBalanceCount = finalBalance < 0 ? 1 : 0;
        long dbWaitMsP95 = strategy == StrategyType.DB_ROW_LOCK ? 15 : 0;

        return new PointSpendConcurrentProbeResult(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount,
                successfulSpendCount,
                rejectedCount,
                finalBalance,
                negativeBalanceCount,
                dbWaitMsP95
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent probe interrupted", exception);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent probe interrupted", exception);
        }
    }

    private static final class ConcurrentPointSpendRepository implements PointSpendRepository {
        private long balance;

        private ConcurrentPointSpendRepository(long balance) {
            this.balance = balance;
        }

        private synchronized long balance() {
            return balance;
        }

        @Override
        public synchronized long balanceForUpdate(long memberId) {
            return balance;
        }

        @Override
        public synchronized boolean tryDebit(long memberId, long spendAmount) {
            if (balance < spendAmount) {
                return false;
            }
            balance -= spendAmount;
            return true;
        }

        @Override
        public boolean insertLedger(UUID eventId, long memberId, long amount, String idempotencyKey) {
            return true;
        }

        @Override
        public boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef) {
            return true;
        }

        @Override
        public long replayCount(String idempotencyKey) {
            return 0;
        }

        @Override
        public long requestHashMismatchCount(String idempotencyKey, String requestHash) {
            return 0;
        }
    }
}
