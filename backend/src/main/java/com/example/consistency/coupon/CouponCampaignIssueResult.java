package com.example.consistency.coupon;

public record CouponCampaignIssueResult(
        boolean issued,
        String rejectionReason
) {

    public static CouponCampaignIssueResult success() {
        return new CouponCampaignIssueResult(true, "");
    }

    public static CouponCampaignIssueResult capacityRejected() {
        return new CouponCampaignIssueResult(false, "capacity");
    }

    public static CouponCampaignIssueResult duplicateRejected() {
        return new CouponCampaignIssueResult(false, "duplicate");
    }
}
