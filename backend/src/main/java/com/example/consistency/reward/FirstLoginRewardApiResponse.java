package com.example.consistency.reward;

public record FirstLoginRewardApiResponse(
        int statusCode,
        String scenario,
        String strategy,
        long runSequence,
        boolean invariantPassed,
        long acceptedCount,
        long completedCount,
        long duplicateRewardCount,
        long rewardIssuedCount,
        long redisLockAttemptCount,
        long afterCommitNotificationCount,
        long outboxEventCount,
        String message
) {

    static FirstLoginRewardApiResponse badRequest(String message) {
        return new FirstLoginRewardApiResponse(
                400,
                "FIRST_LOGIN_REWARD",
                "",
                0,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                message
        );
    }
}

