package com.example.consistency.web;

import com.example.consistency.reward.SqlRewardFollowUpRecorder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public class RewardFollowUpOutboxListener {

    private final SqlRewardFollowUpRecorder recorder;

    public RewardFollowUpOutboxListener(SqlRewardFollowUpRecorder recorder) {
        this.recorder = recorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFakeNotification(RewardFollowUpRequestedEvent event) {
        recorder.recordFakeAfterCommitNotification(event.memberId(), event.rewardType());
    }
}
