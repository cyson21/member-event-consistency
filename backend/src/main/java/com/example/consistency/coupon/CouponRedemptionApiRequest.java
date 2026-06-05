package com.example.consistency.coupon;

public record CouponRedemptionApiRequest(
        long couponIssueId,
        String strategy,
        int requestCount,
        String idempotencyKey,
        String firstRequestHash,
        String retryRequestHash
) {
    public CouponRedemptionApiRequest(long couponIssueId, String strategy, int requestCount) {
        this(couponIssueId, strategy, requestCount, "", "", "");
    }
}
