package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

public record FirstLoginRewardConcurrentProbeResult(
        StrategyType strategy,
        long memberId,
        int requestCount,
        long acceptedCount,
        long completedCount,
        long rewardIssuedCount,
        long rejectedCount,
        long duplicateRewardCount,
        long redisLockAttemptCount,
        String lockKey,
        long afterCommitNotificationCount,
        long outboxEventCount
) {
    public boolean invariantPassed() {
        return rewardIssuedCount == 1 && duplicateRewardCount == 0;
    }
}
