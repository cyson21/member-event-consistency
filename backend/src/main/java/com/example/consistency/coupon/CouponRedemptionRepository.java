package com.example.consistency.coupon;

public interface CouponRedemptionRepository {

    boolean tryMarkUsed(long couponIssueId);

    boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef);

    long replayCount(String idempotencyKey);

    long requestHashMismatchCount(String idempotencyKey, String requestHash);

    long usedCount(long couponIssueId);
}
