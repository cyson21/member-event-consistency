package com.example.consistency.coupon;

public record CouponRedemptionDecision(
        boolean used,
        boolean rejected,
        boolean idempotencyReplay,
        boolean idempotencyHashMismatch
) {
}
