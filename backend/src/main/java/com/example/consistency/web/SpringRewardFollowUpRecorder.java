package com.example.consistency.web;

import com.example.consistency.reward.RewardFollowUpRecorder;
import com.example.consistency.reward.RewardType;
import com.example.consistency.reward.SqlRewardFollowUpRecorder;
import org.springframework.context.ApplicationEventPublisher;

public final class SpringRewardFollowUpRecorder implements RewardFollowUpRecorder {

    private static final String LOCAL_FAKE_PROVIDER = "LOCAL_FAKE";

    private final SqlRewardFollowUpRecorder recorder;
    private final ApplicationEventPublisher eventPublisher;

    public SpringRewardFollowUpRecorder(
            SqlRewardFollowUpRecorder recorder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.recorder = recorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void recordSuccessfulIssue(long memberId, RewardType rewardType) {
        String provider = LOCAL_FAKE_PROVIDER;
        if (!LOCAL_FAKE_PROVIDER.equals(provider)) {
            throw new IllegalStateException("First Login Reward follow-up must stay local-only");
        }
        recorder.recordRewardIssued(memberId, rewardType);
        eventPublisher.publishEvent(new RewardFollowUpRequestedEvent(memberId, rewardType));
    }

    @Override
    public long afterCommitNotificationCount() {
        return recorder.afterCommitNotificationCount();
    }

    @Override
    public long outboxEventCount() {
        return recorder.outboxEventCount();
    }
}
