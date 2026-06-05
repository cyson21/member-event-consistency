package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public record CouponCampaignCommand(
        long campaignId,
        long memberId,
        StrategyType strategy,
        String idempotencyKey
) {
}

