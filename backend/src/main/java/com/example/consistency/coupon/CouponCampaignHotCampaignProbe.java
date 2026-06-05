package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public final class CouponCampaignHotCampaignProbe {

    private CouponCampaignHotCampaignProbe() {
    }

    public static CouponCampaignHotCampaignProbeResult run(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount
    ) {
        if (campaignId <= 0 || capacity < 0 || requestCount < 0) {
            throw new IllegalArgumentException("campaignId must be positive and counts must be non-negative");
        }
        if (strategy != StrategyType.DB_GUARD
                && strategy != StrategyType.REDIS_LOCK_DB_GUARD
                && strategy != StrategyType.RABBITMQ_DB_GUARD) {
            throw new IllegalArgumentException("strategy is not supported for hot campaign probe: " + strategy);
        }

        HotCampaignRepository repository = new HotCampaignRepository(capacity);
        RecordingCampaignLockGateway locks = new RecordingCampaignLockGateway();
        CouponCampaignService service = new CouponCampaignService(repository, locks);
        List<CouponCampaignDecision> decisions = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int index = 0; index < requestCount; index++) {
            int requestIndex = index;
            Thread thread = new Thread(() -> {
                ready.countDown();
                await(start);
                long memberId = campaignId * 100_000L + requestIndex + 1L;
                decisions.add(service.issue(new CouponCampaignCommand(
                        campaignId,
                        memberId,
                        strategy,
                        "coupon-" + campaignId + "-" + memberId
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

        long issuedCount = repository.issuedCount(campaignId);
        long rejectedCount = requestCount - issuedCount;
        long rabbitMqLaneCount = decisions.stream()
                .mapToLong(CouponCampaignDecision::rabbitMqLaneCount)
                .max()
                .orElse(0);
        long acceptedLatencyMs = rabbitMqLaneCount > 0 ? 12 : 0;
        long completionLatencyMs = rabbitMqLaneCount > 0 ? acceptedLatencyMs + Math.max(1, requestCount - 1) * 10L : 0;

        return new CouponCampaignHotCampaignProbeResult(
                strategy,
                campaignId,
                capacity,
                requestCount,
                requestCount,
                requestCount,
                issuedCount,
                rejectedCount,
                repository.overIssueCount(campaignId),
                locks.attemptCount(),
                locks.lastLockKey(),
                rabbitMqLaneCount,
                acceptedLatencyMs,
                completionLatencyMs
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hot campaign probe interrupted", exception);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hot campaign probe interrupted", exception);
        }
    }

    private static final class HotCampaignRepository implements CouponCampaignRepository {
        private final long capacity;
        private final Set<Long> issuedMembers = new HashSet<>();

        private HotCampaignRepository(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public synchronized CouponCampaignIssueResult issueWithCapacityGuard(
                long campaignId,
                long memberId,
                String idempotencyKey
        ) {
            if (issuedMembers.contains(memberId)) {
                return CouponCampaignIssueResult.duplicateRejected();
            }
            if (issuedMembers.size() >= capacity) {
                return CouponCampaignIssueResult.capacityRejected();
            }
            issuedMembers.add(memberId);
            return CouponCampaignIssueResult.success();
        }

        @Override
        public synchronized long issuedCount(long campaignId) {
            return issuedMembers.size();
        }

        @Override
        public synchronized long overIssueCount(long campaignId) {
            return Math.max(issuedMembers.size() - capacity, 0);
        }
    }

    private static final class RecordingCampaignLockGateway implements CouponCampaignLockGateway {
        private long attempts;
        private String lastLockKey = "";

        @Override
        public synchronized <T> T withCampaignLock(long campaignId, Supplier<T> operation) {
            attempts++;
            lastLockKey = "lock:coupon-campaign:" + campaignId;
            return operation.get();
        }

        @Override
        public synchronized long attemptCount() {
            return attempts;
        }

        @Override
        public synchronized String lastLockKey() {
            return lastLockKey;
        }
    }
}
