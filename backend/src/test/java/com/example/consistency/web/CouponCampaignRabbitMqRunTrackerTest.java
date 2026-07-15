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

        tracker.start(operationId, 3);
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

    @Test
    void duplicateCompletedDeliveryDoesNotInflateTerminalOrDecisionCounts() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("69c7090c-5e6d-4310-83a3-0fb429e0269f");
        CouponCampaignRabbitMqCommand first = command(operationId, "same-message", 94005L, 706L);
        CouponCampaignRabbitMqCommand second = command(operationId, "second-message", 94005L, 707L);

        tracker.start(operationId, 2);
        tracker.recordCompleted(first, decisionIssued());
        tracker.recordCompleted(first, decisionIssued());
        tracker.recordCompleted(second, decisionRejected());

        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                tracker.awaitCompletion(operationId, Duration.ofMillis(500));

        assertThat(snapshot.completed()).extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::messageId)
                .containsExactly("same-message", "second-message");
        assertThat(snapshot.issuedCount()).isEqualTo(1L);
        assertThat(snapshot.rejectedCount()).isEqualTo(1L);
    }

    @Test
    void completedThenDlqForSameMessageCountsAsOneTerminalEvent() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("26000960-aabb-4b95-93c3-6af300daf423");
        CouponCampaignRabbitMqCommand completed = command(operationId, "same-message", 94006L, 708L);

        tracker.start(operationId, 2);
        tracker.recordCompleted(completed, decisionIssued());
        tracker.recordDlq(operationId, completed.messageId(), completed.campaignId(), 2, 30L);
        tracker.recordDlq(operationId, "second-message", completed.campaignId(), 2, 31L);

        CouponCampaignRabbitMqRunTracker.RabbitMqRunSnapshot snapshot =
                tracker.awaitCompletion(operationId, Duration.ofMillis(500));

        assertThat(snapshot.completed()).hasSize(1);
        assertThat(snapshot.dlq()).extracting(CouponCampaignRabbitMqRunTracker.RabbitMqQueueEvent::messageId)
                .containsExactly("second-message");
        assertThat(snapshot.issuedCount()).isEqualTo(1L);
    }

    @Test
    void startRejectsDuplicateActiveOperationIdWithoutReplacingState() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("d7050c0a-9a7a-42d0-bd05-518dce27a911");

        tracker.start(operationId, 1);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> tracker.start(operationId, 2)
        );

        assertEquals("RabbitMQ run already started: " + operationId, thrown.getMessage());
        assertThat(tracker.isActive(operationId)).isTrue();
    }

    @Test
    void startRejectsNonPositiveExpectedCount() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("69a41d2d-bcba-4777-9b03-d08735f03ced");

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tracker.start(operationId, 0)
        );

        assertEquals("expectedCount must be positive", thrown.getMessage());
        assertThat(tracker.isActive(operationId)).isFalse();
    }

    @Test
    void timeoutReportsTerminalDiagnosticsAndCleansUpRun() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("885ff28c-1bb4-4708-a3ce-0c1a8454b7d4");
        CouponCampaignRabbitMqCommand completed = command(operationId, "completed-1", 94007L, 709L);

        tracker.start(operationId, 2);
        tracker.recordCompleted(completed, decisionIssued());

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> tracker.awaitCompletion(operationId, Duration.ofMillis(5))
        );

        assertEquals(
                "RabbitMQ run timed out: expected=2, terminal=1, completed=1, dlq=0",
                thrown.getMessage()
        );
        assertThat(tracker.isActive(operationId)).isFalse();
    }

    @Test
    void interruptionIsPropagatedWithInterruptFlagAndRunCleanup() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("ec01277e-c29b-46d7-976b-8c51fd7cbbd6");
        tracker.start(operationId, 1);

        Thread.currentThread().interrupt();
        try {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> tracker.awaitCompletion(operationId, Duration.ofSeconds(1))
            );

            assertEquals("Interrupted while awaiting RabbitMQ run completion", thrown.getMessage());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(tracker.isActive(operationId)).isFalse();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void awaitCompletionRejectsNonPositiveTimeoutWithoutRemovingRun() {
        CouponCampaignRabbitMqRunTracker tracker = new CouponCampaignRabbitMqRunTracker();
        UUID operationId = UUID.fromString("5a855e86-a109-49db-bcda-0e2610db42f9");
        tracker.start(operationId, 1);

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tracker.awaitCompletion(operationId, Duration.ZERO)
        );

        assertEquals("timeout must be positive", thrown.getMessage());
        assertThat(tracker.isActive(operationId)).isTrue();
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
