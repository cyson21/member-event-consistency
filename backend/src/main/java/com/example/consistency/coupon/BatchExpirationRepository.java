package com.example.consistency.coupon;

public interface BatchExpirationRepository {

    boolean tryMarkUsed(long couponIssueId);

    boolean tryMarkExpired(long couponIssueId);

    long usedCount(long couponIssueId);

    long expiredCount(long couponIssueId);
}
