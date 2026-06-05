package com.example.consistency.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqCouponCampaignScenarioExecutorTest {

    @Test
    void queueLagMetricUsesRecordedQueueEventLagInsteadOfRequestCountFormula() {
        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                new CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot(
                        List.of(
                                event("accepted-1", 1L),
                                event("accepted-2", 2L)
                        ),
                        List.of(
                                event("completed-1", 17L),
                                event("completed-2", 53L),
                                event("completed-3", 29L)
                        ),
                        List.of(event("retried-1", 71L)),
                        List.of(event("dlq-1", 97L)),
                        3L,
                        2L
                );

        assertThat(RabbitMqCouponCampaignScenarioExecutor.queueLagMsP95(snapshot)).isEqualTo(97L);
        assertThat(RabbitMqCouponCampaignScenarioExecutor.acceptedLatencyMsP95(snapshot)).isEqualTo(2L);
        assertThat(RabbitMqCouponCampaignScenarioExecutor.completionLatencyMsP95(snapshot)).isEqualTo(97L);
    }

    @Test
    void emptyQueueEventsDoNotReportMeasuredLatency() {
        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                new CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        0L,
                        0L
                );

        assertThat(RabbitMqCouponCampaignScenarioExecutor.queueLagMsP95(snapshot)).isZero();
        assertThat(RabbitMqCouponCampaignScenarioExecutor.acceptedLatencyMsP95(snapshot)).isZero();
        assertThat(RabbitMqCouponCampaignScenarioExecutor.completionLatencyMsP95(snapshot)).isZero();
    }

    @Test
    void overIssueMetricUsesWorkerSnapshotIssuedCountAndCapacity() {
        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                new CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        5L,
                        0L
                );

        assertThat(RabbitMqCouponCampaignScenarioExecutor.overIssueCount(snapshot, 3)).isEqualTo(2L);
        assertThat(RabbitMqCouponCampaignScenarioExecutor.overIssueCount(snapshot, 5)).isZero();
    }

    private static CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent event(String messageId, long lagMs) {
        return new CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent(messageId, 94001L, 0, lagMs);
    }
}
