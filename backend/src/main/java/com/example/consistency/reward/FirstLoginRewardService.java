package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

public class FirstLoginRewardService {

    private final RewardIssueRepository rewardIssues;
    private final RewardFollowUpRecorder followUps;
    private final RewardLockGateway locks;

    public FirstLoginRewardService(
            RewardIssueRepository rewardIssues,
            RewardFollowUpRecorder followUps,
            RewardLockGateway locks
    ) {
        this.rewardIssues = rewardIssues;
        this.followUps = followUps;
        this.locks = locks;
    }

    public FirstLoginRewardDecision issue(FirstLoginRewardCommand command) {
        if (command.strategy() == StrategyType.NAIVE) {
            return issueNaively(command.memberId());
        }
        if (command.strategy() == StrategyType.DB_GUARD) {
            return issueWithDbGuard(command.memberId(), false, "");
        }
        if (command.strategy() == StrategyType.REDIS_LOCK_DB_GUARD) {
            return locks.withRewardLock(
                    command.memberId(),
                    () -> issueWithDbGuard(command.memberId(), true, locks.lastLockKey())
            );
        }
        throw new IllegalArgumentException("Unsupported First Login Reward strategy: " + command.strategy());
    }

    private FirstLoginRewardDecision issueNaively(long memberId) {
        rewardIssues.insertNaive(memberId, RewardType.FIRST_LOGIN);
        followUps.recordSuccessfulIssue(memberId, RewardType.FIRST_LOGIN);
        return FirstLoginRewardDecision.issued(memberId, false, "");
    }

    private FirstLoginRewardDecision issueWithDbGuard(long memberId, boolean lockAttempted, String lockKey) {
        boolean inserted = rewardIssues.insertUnique(memberId, RewardType.FIRST_LOGIN);
        if (!inserted) {
            return FirstLoginRewardDecision.duplicateRejected(memberId, lockAttempted, lockKey);
        }
        followUps.recordSuccessfulIssue(memberId, RewardType.FIRST_LOGIN);
        return FirstLoginRewardDecision.issued(memberId, lockAttempted, lockKey);
    }
}
