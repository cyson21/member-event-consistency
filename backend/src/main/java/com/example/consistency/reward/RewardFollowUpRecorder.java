package com.example.consistency.reward;

public interface RewardFollowUpRecorder {

    void recordSuccessfulIssue(long memberId, RewardType rewardType);

    long afterCommitNotificationCount();

    long outboxEventCount();
}

