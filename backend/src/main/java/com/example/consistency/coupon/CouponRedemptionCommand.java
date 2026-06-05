package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public record CouponRedemptionCommand(
        long couponIssueId,
        StrategyType strategy,
        String idempotencyKey,
        String requestHash
) {
}
