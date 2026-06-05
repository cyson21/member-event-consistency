package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignScenarioExecution;
import com.example.consistency.coupon.SqlCouponQueueEventRecorder;
import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class RabbitMqCouponCampaignScenarioExecutor implements CouponCampaignScenarioExecution {

    private static final long CAMPAIGN_MEMBER_ID_MULTIPLIER = 100_000L;

    private final CouponCampaignScenarioExecution fallback;
    private final ScenarioRunReportRepository reports;
    private final CouponCampaignRabbitMqPublisher publisher;
    private final CouponCampaignRabbitMqRunTracker tracker;
    private final SqlCouponQueueEventRecorder queueEvents;

    public RabbitMqCouponCampaignScenarioExecutor(
            CouponCampaignScenarioExecution fallback,
            ScenarioRunReportRepository reports,
            CouponCampaignRabbitMqPublisher publisher,
            CouponCampaignRabbitMqRunTracker tracker,
            SqlCouponQueueEventRecorder queueEvents
    ) {
        this.fallback = fallback;
        this.reports = reports;
        this.publisher = publisher;
        this.tracker = tracker;
        this.queueEvents = queueEvents;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long campaignId, int capacity, int requestCount) {
        return executeWithWorkerFailures(strategy, campaignId, capacity, requestCount, 0, 0);
    }

    @Override
    public ScenarioRunRecord executeWithWorkerFailures(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount,
            int transientRetryCount,
            int dlqCount
    ) {
        if (strategy != StrategyType.RABBITMQ_DB_GUARD) {
            return fallback.executeWithWorkerFailures(strategy, campaignId, capacity, requestCount, transientRetryCount, dlqCount);
        }

        UUID operationId = UUID.randomUUID();
        int workerCommandCount = requestCount;
        tracker.start(operationId, workerCommandCount);

        for (int index = 0; index < workerCommandCount; index++) {
            int oneBasedIndex = index + 1;
            CouponCampaignRabbitMqCommand command = new CouponCampaignRabbitMqCommand(
                    operationId,
                    operationId + "-" + oneBasedIndex,
                    campaignId,
                    memberId(campaignId, index),
                    idempotencyKey(campaignId, index),
                    System.currentTimeMillis(),
                    1,
                    retryBudget(oneBasedIndex, transientRetryCount),
                    oneBasedIndex <= dlqCount
            );
            publisher.publish(command);
            tracker.recordAccepted(command);
        }

        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot = tracker.awaitCompletion(
                operationId,
                Duration.ofSeconds(10)
        );

        long completedCount = snapshot.completed().size() + snapshot.dlq().size();
        long queueLagMsP95 = queueLagMsP95(snapshot);
        long overIssueCount = overIssueCount(snapshot, capacity);
        ScenarioRunReport report = new ScenarioRunReport(
                ScenarioType.COUPON_CAMPAIGN_ISSUE,
                StrategyType.RABBITMQ_DB_GUARD,
                InvariantChecker.evaluate(new InvariantResult(
                        ScenarioType.COUPON_CAMPAIGN_ISSUE,
                        StrategyType.RABBITMQ_DB_GUARD,
                        true,
                        0,
                        overIssueCount,
                        0,
                        Math.max(0, requestCount - completedCount),
                        requestCount,
                        completedCount,
                        0,
                        0,
                        queueLagMsP95,
                        "coupon-campaign rabbitmq worker run"
                )),
                metrics(requestCount, completedCount, snapshot, queueLagMsP95, overIssueCount)
        );
        ScenarioRunRecord record = reports.save(report);
        recordQueueEvents(record.id(), snapshot);
        return record;
    }

    private List<ScenarioMetric> metrics(
            int requestCount,
            long completedCount,
            CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot,
            long queueLagMsP95,
            long overIssueCount
    ) {
        long rabbitMqAcceptedLatencyMs = acceptedLatencyMsP95(snapshot);
        long rabbitMqCompletionLatencyMs = completionLatencyMsP95(snapshot);
        List<ScenarioMetric> metrics = new ArrayList<>();
        metrics.add(new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, requestCount));
        metrics.add(new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, completedCount));
        metrics.add(new ScenarioMetric(ScenarioMetricName.COUPON_ISSUED_COUNT, snapshot.issuedCount()));
        metrics.add(new ScenarioMetric(ScenarioMetricName.OVER_ISSUE_COUNT, overIssueCount));
        metrics.add(new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, snapshot.rejectedCount()));
        metrics.add(new ScenarioMetric(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT, 0));
        metrics.add(new ScenarioMetric(ScenarioMetricName.RABBITMQ_LANE_COUNT, 1));
        metrics.add(new ScenarioMetric(ScenarioMetricName.QUEUE_RETRY_COUNT, snapshot.retried().size()));
        metrics.add(new ScenarioMetric(ScenarioMetricName.DLQ_COUNT, snapshot.dlq().size()));
        metrics.add(new ScenarioMetric(ScenarioMetricName.QUEUE_LAG_MS_P95, queueLagMsP95));
        metrics.add(new ScenarioMetric(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS, rabbitMqAcceptedLatencyMs));
        metrics.add(new ScenarioMetric(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS, rabbitMqCompletionLatencyMs));
        return metrics;
    }

    static long queueLagMsP95(CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot) {
        return percentile95(Stream.of(snapshot.completed(), snapshot.retried(), snapshot.dlq())
                .flatMap(List::stream)
                .map(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::lagMs)
                .toList());
    }

    static long acceptedLatencyMsP95(CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot) {
        return percentile95(snapshot.accepted().stream()
                .map(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::lagMs)
                .toList());
    }

    static long completionLatencyMsP95(CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot) {
        return percentile95(Stream.of(snapshot.completed(), snapshot.dlq())
                .flatMap(List::stream)
                .map(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::lagMs)
                .toList());
    }

    static long overIssueCount(CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot, int capacity) {
        return Math.max(0L, snapshot.issuedCount() - capacity);
    }

    private static long percentile95(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        int index = (int) Math.ceil(sorted.size() * 0.95D) - 1;
        return sorted.get(Math.max(0, index));
    }

    private void recordQueueEvents(UUID runId, CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot) {
        for (CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent event : snapshot.accepted()) {
            queueEvents.recordAccepted(runId, event.messageId(), event.campaignId(), event.lagMs());
        }
        for (CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent event : snapshot.completed()) {
            queueEvents.recordCompleted(runId, event.messageId(), event.campaignId(), event.lagMs());
        }
        for (CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent event : snapshot.retried()) {
            queueEvents.recordRetried(runId, event.messageId(), event.campaignId(), event.retryCount(), event.lagMs());
        }
        for (CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent event : snapshot.dlq()) {
            queueEvents.recordDlq(runId, event.messageId(), event.campaignId(), event.retryCount(), event.lagMs());
        }
    }

    private long memberId(long campaignId, int index) {
        return campaignId * CAMPAIGN_MEMBER_ID_MULTIPLIER + index + 1L;
    }

    private String idempotencyKey(long campaignId, int index) {
        return "coupon-" + campaignId + "-" + (index + 1);
    }

    private int retryBudget(int oneBasedIndex, int transientRetryCount) {
        return oneBasedIndex <= transientRetryCount ? 1 : 0;
    }
}
