package com.example.consistency.point;

public record PointSpendApiRequest(
        long memberId,
        String strategy,
        long initialBalance,
        long spendAmount,
        int requestCount,
        String idempotencyKey
) {
}
