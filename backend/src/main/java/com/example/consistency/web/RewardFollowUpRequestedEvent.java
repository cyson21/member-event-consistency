package com.example.consistency.web;

import com.example.consistency.reward.RewardType;

public record RewardFollowUpRequestedEvent(
        long memberId,
        RewardType rewardType
) {
}
