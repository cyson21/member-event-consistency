package com.example.consistency.reward;

import java.util.ArrayList;
import java.util.List;

public final class FakeRewardFollowUpRecorder implements RewardFollowUpRecorder {

    private final List<RewardFollowUpEvent> afterCommitNotifications = new ArrayList<>();
    private final List<RewardFollowUpEvent> outboxEvents = new ArrayList<>();

    @Override
    public synchronized void recordSuccessfulIssue(long memberId, RewardType rewardType) {
        afterCommitNotifications.add(new RewardFollowUpEvent("FAKE_AFTER_COMMIT_NOTIFICATION", memberId, rewardType));
        outboxEvents.add(new RewardFollowUpEvent("FAKE_OUTBOX", memberId, rewardType));
    }

    @Override
    public synchronized long afterCommitNotificationCount() {
        return afterCommitNotifications.size();
    }

    @Override
    public synchronized long outboxEventCount() {
        return outboxEvents.size();
    }
}

