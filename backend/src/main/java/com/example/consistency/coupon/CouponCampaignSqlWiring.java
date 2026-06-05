package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;

public final class CouponCampaignSqlWiring {

    private CouponCampaignSqlWiring() {
    }

    public static CouponCampaignService service(SqlExecutor executor, CouponCampaignLockGateway locks) {
        return new CouponCampaignService(new SqlCouponCampaignRepository(executor), locks);
    }
}

