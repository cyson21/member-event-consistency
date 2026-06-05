package com.example.consistency.coupon;

public record CouponCampaignApiRequest(
        long campaignId,
        String strategy,
        int capacity,
        int requestCount,
        int transientRetryCount,
        int dlqCount
) {
    public CouponCampaignApiRequest(long campaignId, String strategy, int capacity, int requestCount) {
        this(campaignId, strategy, capacity, requestCount, 0, 0);
    }
}
