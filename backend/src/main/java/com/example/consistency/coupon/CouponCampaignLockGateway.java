package com.example.consistency.coupon;

import java.util.function.Supplier;

public interface CouponCampaignLockGateway {

    <T> T withCampaignLock(long campaignId, Supplier<T> operation);

    long attemptCount();

    String lastLockKey();
}

