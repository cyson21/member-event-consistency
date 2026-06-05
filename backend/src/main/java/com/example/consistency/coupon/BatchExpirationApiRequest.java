package com.example.consistency.coupon;

public record BatchExpirationApiRequest(
        long couponIssueId,
        String strategy,
        String winner
) {
}
