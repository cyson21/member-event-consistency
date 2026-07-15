package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignDecision;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CouponCampaignRabbitMqRunTracker {

    private final Map<UUID, RabbitMqRunState> runs = new ConcurrentHashMap<UUID, RabbitMqRunState>();

    void start(UUID operationId, int expectedCount) {
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (expectedCount <= 0) {
            throw new IllegalArgumentException("expectedCount must be positive");
        }
        RabbitMqRunState existing = runs.putIfAbsent(operationId, new RabbitMqRunState(expectedCount));
        if (existing != null) {
            throw new IllegalStateException("RabbitMQ run already started: " + operationId);
        }
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
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
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
        private final Set<String> terminalMessageIds = new HashSet<>();
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
            if (!terminalMessageIds.add(command.messageId())) {
                return;
            }
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
            if (!terminalMessageIds.add(messageId)) {
                return;
            }
            dlq.add(new RabbitMqQueueEvent(messageId, campaignId, retryCount, lagMs));
            notifyAll();
        }

        private synchronized RabbitMqRunSnapshot awaitCompletion(Duration timeout) {
            long remainingNanos;
            try {
                remainingNanos = timeout.toNanos();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("timeout is too large", exception);
            }
            while (terminalMessageIds.size() < expectedCount) {
                if (remainingNanos <= 0) {
                    throw new IllegalStateException(
                            "RabbitMQ run timed out: expected=" + expectedCount
                                    + ", terminal=" + terminalMessageIds.size()
                                    + ", completed=" + completed.size()
                                    + ", dlq=" + dlq.size()
                    );
                }
                long waitStartedAt = System.nanoTime();
                long waitMs = remainingNanos / 1_000_000L;
                int waitNanos = (int) (remainingNanos % 1_000_000L);
                try {
                    wait(waitMs, waitNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while awaiting RabbitMQ run completion", exception);
                }
                remainingNanos -= Math.max(1L, System.nanoTime() - waitStartedAt);
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
