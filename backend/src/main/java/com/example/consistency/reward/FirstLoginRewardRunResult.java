package com.example.consistency.reward;

import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public record FirstLoginRewardRunResult(
        StrategyType strategy,
        long requestedCount,
        long issuedCount,
        long duplicateCount,
        long lockAttemptCount,
        String lockKey,
        long afterCommitNotificationCount,
        long outboxEventCount
) {

    public InvariantResult toInvariantResult() {
        return new InvariantResult(
                ScenarioType.FIRST_LOGIN_REWARD,
                strategy,
                duplicateCount == 0,
                duplicateCount,
                0,
                0,
                0,
                requestedCount,
                requestedCount,
                0,
                0,
                0,
                "first-login-reward run"
        );
    }
}

