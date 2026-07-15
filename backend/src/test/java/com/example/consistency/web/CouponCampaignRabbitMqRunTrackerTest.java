package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CouponCampaignRabbitMqRunTrackerTest {

    @Test
    void aggregatesAcceptedCompletedRetriedAndDlqEventsAndIssueCounts() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("5f2d7b2e-53d2-4f9a-a4ef-4fd4f67dd2a9");

        tracker.start(operationId, 2);
        CouponCampaignRabbitMqCommand acceptedOne = command(operationId, "accepted-1", 94001L, 701L);
        CouponCampaignRabbitMqCommand acceptedTwo = command(operationId, "accepted-2", 94002L, 702L);

        tracker.recordAccepted(acceptedOne);
        tracker.recordAccepted(acceptedTwo);
        tracker.recordCompleted(acceptedOne, decisionIssued());
        tracker.recordCompleted(acceptedTwo, decisionRejected());
        tracker.recordRetried(operationId, "retry-1", 94001L, 3, 14L);
        tracker.recordDlq(operationId, "dlq-1", 94002L, 4, 19L);

        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                tracker.awaitCompletion(operationId, Duration.ofMillis(500));

        assertThat(snapshot.accepted()).extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::messageId)
                .containsExactly("accepted-1", "accepted-2");
        assertThat(snapshot.accepted())
                .extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::campaignId, event -> event.retryCount())
                .containsExactly(
                        tuple(94001L, 0),
                        tuple(94002L, 0)
                );
        assertThat(snapshot.completed())
                .extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::messageId,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::campaignId,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::retryCount)
                .containsExactly(
                        tuple("accepted-1", 94001L, 0),
                        tuple("accepted-2", 94002L, 0)
                );
        assertThat(snapshot.retried())
                .extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::messageId,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::campaignId,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::retryCount,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::lagMs)
                .containsExactly(tuple("retry-1", 94001L, 3, 14L));
        assertThat(snapshot.dlq())
                .extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::messageId,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::campaignId,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::retryCount,
                        CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::lagMs)
                .containsExactly(tuple("dlq-1", 94002L, 4, 19L));
        assertThat(snapshot.issuedCount()).isEqualTo(1L);
        assertThat(snapshot.rejectedCount()).isEqualTo(1L);
    }

    @Test
    void awaitCompletionRejectsUnstartedRunId() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("9f9f5f0a-9e31-4c3d-9e6b-1f3e5e8f2f77");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> tracker.awaitCompletion(operationId, Duration.ofMillis(250))
        );

        assertEquals("RabbitMQ run not started: " + operationId, thrown.getMessage());
    }

    @Test
    void completedRunSnapshotCanBeQueriedOnceThenBecomesUnknown() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("d7c45d73-7e3d-4ed5-b0d2-9d3b8f2c4c0e");

        tracker.start(operationId, 1);
        CouponCampaignRabbitMqCommand command = command(operationId, "completed-1", 94003L, 703L);
        tracker.recordCompleted(command, decisionIssued());

        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                tracker.awaitCompletion(operationId, Duration.ofMillis(500));

        assertThat(snapshot.completed()).hasSize(1);
        assertThat(snapshot.accepted()).isEmpty();
        assertThat(snapshot.retried()).isEmpty();
        assertThat(snapshot.dlq()).isEmpty();
        assertThat(snapshot.issuedCount()).isEqualTo(1L);
        assertThat(snapshot.rejectedCount()).isEqualTo(0L);
        assertThat(tracker.isActive(operationId)).isFalse();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> tracker.awaitCompletion(operationId, Duration.ofMillis(250))
        );
        assertEquals("RabbitMQ run not started: " + operationId, thrown.getMessage());
    }

    @Test
    void awaitCompletionReturnsWhenCompletedAndDlqEventsReachExpectedCount() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("ba356bad-5a4f-448e-a889-97ad3b213b88");
        CouponCampaignRabbitMqCommand completed = command(operationId, "completed-1", 94004L, 704L);
        CouponCampaignRabbitMqCommand sentToDlq = command(operationId, "dlq-1", 94004L, 705L);

        tracker.start(operationId, 2);
        tracker.recordAccepted(completed);
        tracker.recordAccepted(sentToDlq);
        tracker.recordCompleted(completed, decisionIssued());
        tracker.recordDlq(operationId, sentToDlq.messageId(), sentToDlq.campaignId(), 1, sentToDlq.lagMs());

        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> tracker.awaitCompletion(operationId, Duration.ofSeconds(5))
        );

        assertThat(snapshot.accepted()).hasSize(2);
        assertThat(snapshot.completed()).hasSize(1);
        assertThat(snapshot.dlq()).hasSize(1);
        assertThat(tracker.isActive(operationId)).isFalse();
    }

    private static CouponCampaignRabbitMqCommand command(
            UUID operationId,
            String messageId,
            long campaignId,
            long memberId
    ) {
        return new CouponCampaignRabbitMqCommand(
                operationId,
                messageId,
                campaignId,
                memberId,
                "key-" + messageId,
                System.currentTimeMillis(),
                1,
                0,
                false
        );
    }

    private static CouponCampaignDecision decisionIssued() {
        return new CouponCampaignDecision(true, false, null, 0L, null);
    }

    private static CouponCampaignDecision decisionRejected() {
        return new CouponCampaignDecision(false, false, null, 0L, "rejected");
    }
}
