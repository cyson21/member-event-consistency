package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public record CouponCampaignHotCampaignProbeResult(
        StrategyType strategy,
        long campaignId,
        int capacity,
        int requestCount,
        long acceptedCount,
        long completedCount,
        long issuedCount,
        long rejectedCount,
        long overIssueCount,
        long redisLockAttemptCount,
        String lockKey,
        long rabbitMqLaneCount,
        long acceptedLatencyMs,
        long completionLatencyMs
) {
    public boolean invariantPassed() {
        return overIssueCount == 0 && issuedCount <= capacity;
    }
}
