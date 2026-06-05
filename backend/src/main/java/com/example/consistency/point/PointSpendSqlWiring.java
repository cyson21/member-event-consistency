package com.example.consistency.point;

import com.example.consistency.persistence.SqlExecutor;

public final class PointSpendSqlWiring {

    private PointSpendSqlWiring() {
    }

    public static PointSpendService service(SqlExecutor executor) {
        return new PointSpendService(new SqlPointSpendRepository(executor));
    }
}

