package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;

public final class CouponRedemptionSqlWiring {

    private CouponRedemptionSqlWiring() {
    }

    public static CouponRedemptionService service(SqlExecutor executor) {
        return new CouponRedemptionService(new SqlCouponRedemptionRepository(executor));
    }
}
