package com.example.consistency.reward;

public record RewardIssue(
        long memberId,
        RewardType rewardType,
        String status
) {
}

