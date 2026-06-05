package com.example.consistency.coupon;

public record CouponRedemptionApiResponse(
        int statusCode,
        String scenario,
        String strategy,
        long runSequence,
        boolean invariantPassed,
        long acceptedCount,
        long completedCount,
        long couponUsedCount,
        long doubleUseCount,
        long terminalStateConflictCount,
        long rejectedCount,
        long idempotencyReplayCount,
        long idempotencyHashMismatchCount,
        String message
) {

    static CouponRedemptionApiResponse badRequest(String message) {
        return new CouponRedemptionApiResponse(
                400,
                "COUPON_REDEMPTION",
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
                message
        );
    }
}
