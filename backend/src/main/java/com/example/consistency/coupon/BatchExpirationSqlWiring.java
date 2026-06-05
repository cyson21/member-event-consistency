package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;

public final class BatchExpirationSqlWiring {

    private BatchExpirationSqlWiring() {
    }

    public static BatchExpirationService service(SqlExecutor executor) {
        return new BatchExpirationService(new SqlBatchExpirationRepository(executor));
    }
}
