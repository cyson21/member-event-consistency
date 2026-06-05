package com.example.consistency.coupon;

public interface CouponCampaignRepository {

    CouponCampaignIssueResult issueWithCapacityGuard(long campaignId, long memberId, String idempotencyKey);

    long issuedCount(long campaignId);

    long overIssueCount(long campaignId);
}

