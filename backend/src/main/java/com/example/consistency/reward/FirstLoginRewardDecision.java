package com.example.consistency.reward;

public record FirstLoginRewardDecision(
        FirstLoginRewardOutcome outcome,
        long memberId,
        RewardType rewardType,
        boolean lockAttempted,
        String lockKey
) {

    static FirstLoginRewardDecision issued(long memberId, boolean lockAttempted, String lockKey) {
        return new FirstLoginRewardDecision(
                FirstLoginRewardOutcome.ISSUED,
                memberId,
                RewardType.FIRST_LOGIN,
                lockAttempted,
                lockKey
        );
    }

    static FirstLoginRewardDecision duplicateRejected(long memberId, boolean lockAttempted, String lockKey) {
        return new FirstLoginRewardDecision(
                FirstLoginRewardOutcome.DUPLICATE_REJECTED,
                memberId,
                RewardType.FIRST_LOGIN,
                lockAttempted,
                lockKey
        );
    }
}

