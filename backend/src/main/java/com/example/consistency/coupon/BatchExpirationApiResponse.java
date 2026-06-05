package com.example.consistency.coupon;

public record BatchExpirationApiResponse(
        int statusCode,
        String scenario,
        String strategy,
        long runSequence,
        boolean invariantPassed,
        long acceptedCount,
        long completedCount,
        long couponUsedCount,
        long couponExpiredCount,
        long terminalStateConflictCount,
        long rejectedCount,
        String rejectionReason,
        String message
) {

    static BatchExpirationApiResponse badRequest(String message) {
        return new BatchExpirationApiResponse(
                400,
                "BATCH_EXPIRATION",
                "",
                0,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                "",
                message
        );
    }
}
