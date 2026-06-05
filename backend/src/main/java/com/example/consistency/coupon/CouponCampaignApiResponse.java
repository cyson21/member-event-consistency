package com.example.consistency.coupon;

public record CouponCampaignApiResponse(
        int statusCode,
        String scenario,
        String strategy,
        long runSequence,
        boolean invariantPassed,
        long acceptedCount,
        long completedCount,
        long couponIssuedCount,
        long overIssueCount,
        long rejectedCount,
        long redisLockAttemptCount,
        long rabbitMqLaneCount,
        long queueRetryCount,
        long dlqCount,
        long queueLagMsP95,
        long rabbitMqAcceptedLatencyMs,
        long rabbitMqCompletionLatencyMs,
        String message
) {

    static CouponCampaignApiResponse badRequest(String message) {
        return new CouponCampaignApiResponse(
                400,
                "COUPON_CAMPAIGN_ISSUE",
                "",
                0,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                message
        );
    }
}
