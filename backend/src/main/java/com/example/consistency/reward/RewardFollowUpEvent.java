package com.example.consistency.reward;

public record RewardFollowUpEvent(
        String channel,
        long memberId,
        RewardType rewardType
) {
}

