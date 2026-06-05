package com.example.consistency.reward;

import com.example.consistency.persistence.SqlExecutor;

public final class FirstLoginRewardSqlWiring {

    private FirstLoginRewardSqlWiring() {
    }

    public static FirstLoginRewardService service(SqlExecutor executor, RewardLockGateway locks) {
        return new FirstLoginRewardService(
                new SqlRewardIssueRepository(executor),
                new SqlRewardFollowUpRecorder(executor),
                locks
        );
    }
}

