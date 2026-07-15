package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignDecision;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CouponCampaignRabbitMqRunTracker {

    private final Map<UUID, RabbitMqRunState> runs = new ConcurrentHashMap<UUID, RabbitMqRunState>();

    void start(UUID operationId, int expectedCount) {
        runs.put(operationId, new RabbitMqRunState(expectedCount));
    }

    boolean isActive(UUID operationId) {
        return runs.containsKey(operationId);
    }

    void recordAccepted(CouponCampaignRabbitMqCommand command) {
        RabbitMqRunState state = runs.get(command.operationId());
        if (state != null) {
            state.recordAccepted(command);
        }
    }

    void recordCompleted(CouponCampaignRabbitMqCommand command, CouponCampaignDecision decision) {
        RabbitMqRunState state = runs.get(command.operationId());
        if (state != null) {
            state.recordCompleted(command, decision);
        }
    }

    void recordRetried(UUID operationId, String messageId, long campaignId, int retryCount, long lagMs) {
        RabbitMqRunState state = runs.get(operationId);
        if (state != null) {
            state.recordRetried(messageId, campaignId, retryCount, lagMs);
        }
    }

    void recordDlq(UUID operationId, String messageId, long campaignId, int retryCount, long lagMs) {
        RabbitMqRunState state = runs.get(operationId);
        if (state != null) {
            state.recordDlq(messageId, campaignId, retryCount, lagMs);
        }
    }

    RabbitMqRunSnapshot awaitCompletion(UUID operationId, Duration timeout) {
        RabbitMqRunState state = runs.get(operationId);
        if (state == null) {
            throw new IllegalArgumentException("RabbitMQ run not started: " + operationId);
        }
        try {
            return state.awaitCompletion(timeout);
        } finally {
            runs.remove(operationId);
        }
    }

    public record RabbitMqRunSnapshot(
            List<RabbitMqQueueEvent> accepted,
            List<RabbitMqQueueEvent> completed,
            List<RabbitMqQueueEvent> retried,
            List<RabbitMqQueueEvent> dlq,
            long issuedCount,
            long rejectedCount
    ) {
    }

    public record RabbitMqQueueEvent(
            String messageId,
            long campaignId,
            int retryCount,
            long lagMs
    ) {
    }

    private static final class RabbitMqRunState {

        private final int expectedCount;
        private final List<RabbitMqQueueEvent> accepted = new ArrayList<>();
        private final List<RabbitMqQueueEvent> completed = new ArrayList<>();
        private final List<RabbitMqQueueEvent> retried = new ArrayList<>();
        private final List<RabbitMqQueueEvent> dlq = new ArrayList<>();
        private long issuedCount;
        private long rejectedCount;

        private RabbitMqRunState(int expectedCount) {
            this.expectedCount = expectedCount;
        }

        private synchronized void recordAccepted(CouponCampaignRabbitMqCommand command) {
            accepted.add(new RabbitMqQueueEvent(command.messageId(), command.campaignId(), 0, command.lagMs()));
            notifyAll();
        }

        private synchronized void recordCompleted(CouponCampaignRabbitMqCommand command, CouponCampaignDecision decision) {
            completed.add(new RabbitMqQueueEvent(command.messageId(), command.campaignId(), 0, command.lagMs()));
            if (decision.issued()) {
                issuedCount++;
            } else {
                rejectedCount++;
            }
            notifyAll();
        }

        private synchronized void recordRetried(String messageId, long campaignId, int retryCount, long lagMs) {
            retried.add(new RabbitMqQueueEvent(messageId, campaignId, retryCount, lagMs));
            notifyAll();
        }

        private synchronized void recordDlq(String messageId, long campaignId, int retryCount, long lagMs) {
            dlq.add(new RabbitMqQueueEvent(messageId, campaignId, retryCount, lagMs));
            notifyAll();
        }

        private synchronized RabbitMqRunSnapshot awaitCompletion(Duration timeout) {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (completed.size() + dlq.size() < expectedCount && System.currentTimeMillis() < deadline) {
                long waitMs = Math.max(1L, deadline - System.currentTimeMillis());
                try {
                    wait(waitMs);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return new RabbitMqRunSnapshot(
                    List.copyOf(accepted),
                    List.copyOf(completed),
                    List.copyOf(retried),
                    List.copyOf(dlq),
                    issuedCount,
                    rejectedCount
            );
        }

    }
}
