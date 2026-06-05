package com.example.consistency.web;

import java.io.Serializable;
import java.util.UUID;

public record CouponCampaignRabbitMqCommand(
        UUID operationId,
        String messageId,
        long campaignId,
        long memberId,
        String idempotencyKey,
        long acceptedAtEpochMs,
        int attempt,
        int transientFailureBudget,
        boolean dlq
) implements Serializable {

    public CouponCampaignRabbitMqCommand retriedCopy() {
        return new CouponCampaignRabbitMqCommand(
                operationId,
                messageId,
                campaignId,
                memberId,
                idempotencyKey,
                acceptedAtEpochMs,
                attempt + 1,
                Math.max(0, transientFailureBudget - 1),
                dlq
        );
    }

    public long lagMs() {
        return Math.max(0L, System.currentTimeMillis() - acceptedAtEpochMs);
    }
}
