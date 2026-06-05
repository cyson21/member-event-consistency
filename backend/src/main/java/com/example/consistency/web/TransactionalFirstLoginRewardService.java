package com.example.consistency.web;

import com.example.consistency.reward.FirstLoginRewardCommand;
import com.example.consistency.reward.FirstLoginRewardDecision;
import com.example.consistency.reward.FirstLoginRewardService;
import com.example.consistency.reward.RewardFollowUpRecorder;
import com.example.consistency.reward.RewardIssueRepository;
import com.example.consistency.reward.RewardLockGateway;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalFirstLoginRewardService extends FirstLoginRewardService {

    public TransactionalFirstLoginRewardService(
            RewardIssueRepository rewardIssues,
            RewardFollowUpRecorder followUps,
            RewardLockGateway locks
    ) {
        super(rewardIssues, followUps, locks);
    }

    @Override
    @Transactional
    public FirstLoginRewardDecision issue(FirstLoginRewardCommand command) {
        return super.issue(command);
    }
}
