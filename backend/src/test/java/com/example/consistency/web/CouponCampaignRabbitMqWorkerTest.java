package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponCampaignRabbitMqWorkerTest {

    @Test
    void recordsDlqTerminalOnlyAfterDlqPublishSucceeds() {
        CouponCampaignService service = mock(CouponCampaignService.class);
        CouponCampaignRabbitMqPublisher publisher = mock(CouponCampaignRabbitMqPublisher.class);
        CouponCampaignRabbitMqRunTracker tracker = mock(CouponCampaignRabbitMqRunTracker.class);
        CouponCampaignRabbitMqWorker worker = new CouponCampaignRabbitMqWorker(service, publisher, tracker);
        CouponCampaignRabbitMqCommand command = command("dlq-message", 0, true);
        when(tracker.isActive(command.operationId())).thenReturn(true);

        worker.handle(command);

        InOrder ordered = inOrder(publisher, tracker);
        ordered.verify(publisher).publishDlq(command);
        ordered.verify(tracker).recordDlq(
                org.mockito.ArgumentMatchers.eq(command.operationId()),
                org.mockito.ArgumentMatchers.eq(command.messageId()),
                org.mockito.ArgumentMatchers.eq(command.campaignId()),
                org.mockito.ArgumentMatchers.eq(command.attempt()),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(service, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dlqPublishFailureDoesNotCreateFalseTerminalRecord() {
        CouponCampaignService service = mock(CouponCampaignService.class);
        CouponCampaignRabbitMqPublisher publisher = mock(CouponCampaignRabbitMqPublisher.class);
        CouponCampaignRabbitMqRunTracker tracker = mock(CouponCampaignRabbitMqRunTracker.class);
        CouponCampaignRabbitMqWorker worker = new CouponCampaignRabbitMqWorker(service, publisher, tracker);
        CouponCampaignRabbitMqCommand command = command("dlq-failure", 0, true);
        RuntimeException publishFailure = new RuntimeException("DLQ unavailable");
        when(tracker.isActive(command.operationId())).thenReturn(true);
        doThrow(publishFailure).when(publisher).publishDlq(command);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> worker.handle(command));

        assertSame(publishFailure, thrown);
        verify(tracker, never()).recordDlq(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void recordsRetryOnlyAfterRepublishSucceeds() {
        CouponCampaignService service = mock(CouponCampaignService.class);
        CouponCampaignRabbitMqPublisher publisher = mock(CouponCampaignRabbitMqPublisher.class);
        CouponCampaignRabbitMqRunTracker tracker = mock(CouponCampaignRabbitMqRunTracker.class);
        CouponCampaignRabbitMqWorker worker = new CouponCampaignRabbitMqWorker(service, publisher, tracker);
        CouponCampaignRabbitMqCommand command = command("retry-message", 1, false);
        when(tracker.isActive(command.operationId())).thenReturn(true);

        worker.handle(command);

        InOrder ordered = inOrder(publisher, tracker);
        ordered.verify(publisher).publish(command.retriedCopy());
        ordered.verify(tracker).recordRetried(
                org.mockito.ArgumentMatchers.eq(command.operationId()),
                org.mockito.ArgumentMatchers.eq(command.messageId()),
                org.mockito.ArgumentMatchers.eq(command.campaignId()),
                org.mockito.ArgumentMatchers.eq(command.attempt() + 1),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private static CouponCampaignRabbitMqCommand command(
            String messageId,
            int transientFailureBudget,
            boolean dlq
    ) {
        return new CouponCampaignRabbitMqCommand(
                UUID.fromString("7e1a60af-c842-4220-a6b4-5a13eea3db02"),
                messageId,
                94001L,
                77001L,
                "key-" + messageId,
                System.currentTimeMillis(),
                1,
                transientFailureBudget,
                dlq
        );
    }
}
