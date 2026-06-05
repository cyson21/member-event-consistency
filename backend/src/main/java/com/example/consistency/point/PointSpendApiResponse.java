package com.example.consistency.point;

public record PointSpendApiResponse(
        int statusCode,
        String scenario,
        String strategy,
        long runSequence,
        boolean invariantPassed,
        long acceptedCount,
        long completedCount,
        long finalPointBalance,
        long negativeBalanceCount,
        long pointLedgerEntryCount,
        long rejectedCount,
        long idempotencyReplayCount,
        long idempotencyHashMismatchCount,
        long dbWaitMsP95,
        String message
) {

    static PointSpendApiResponse badRequest(String message) {
        return new PointSpendApiResponse(
                400,
                "POINT_SPEND",
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
                message
        );
    }
}
