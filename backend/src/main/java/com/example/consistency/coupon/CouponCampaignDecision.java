package com.example.consistency.coupon;

public record CouponCampaignDecision(
        boolean issued,
        boolean lockAttempted,
        String lockKey,
        long rabbitMqLaneCount,
        String rejectionReason
) {

    public static CouponCampaignDecision fromResult(
            CouponCampaignIssueResult result,
            boolean lockAttempted,
            String lockKey,
            long rabbitMqLaneCount
    ) {
        return new CouponCampaignDecision(
                result.issued(),
                lockAttempted,
                lockKey,
                rabbitMqLaneCount,
                result.rejectionReason()
        );
    }
}

