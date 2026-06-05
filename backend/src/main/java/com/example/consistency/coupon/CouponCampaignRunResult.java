package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public record CouponCampaignRunResult(
        StrategyType strategy,
        long campaignId,
        long capacity,
        long requestedCount,
        long acceptedCount,
        long completedCount,
        long issuedCount,
        long overIssueCount,
        long rejectedCount,
        long redisLockAttemptCount,
        String lockKey,
        long rabbitMqLaneCount,
        long queueRetryCount,
        long dlqCount,
        long queueLagMsP95,
        long rabbitMqAcceptedLatencyMs,
        long rabbitMqCompletionLatencyMs
) {

    public InvariantResult toInvariantResult() {
        return new InvariantResult(
                ScenarioType.COUPON_CAMPAIGN_ISSUE,
                strategy,
                overIssueCount == 0,
                0,
                overIssueCount,
                0,
                0,
                acceptedCount,
                completedCount,
                0,
                0,
                queueLagMsP95,
                "coupon-campaign-issue run"
        );
    }
}
