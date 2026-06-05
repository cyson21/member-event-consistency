package com.example.consistency.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRewardIssueRepository implements RewardIssueRepository {

    private final List<RewardIssue> issuedRewards = new ArrayList<>();
    private final Set<String> uniqueRewardKeys = ConcurrentHashMap.newKeySet();

    @Override
    public synchronized boolean insertNaive(long memberId, RewardType rewardType) {
        issuedRewards.add(new RewardIssue(memberId, rewardType, "ISSUED"));
        return true;
    }

    @Override
    public synchronized boolean insertUnique(long memberId, RewardType rewardType) {
        if (!uniqueRewardKeys.add(uniqueKey(memberId, rewardType))) {
            return false;
        }
        issuedRewards.add(new RewardIssue(memberId, rewardType, "ISSUED"));
        return true;
    }

    @Override
    public synchronized long issuedCount() {
        return issuedRewards.size();
    }

    @Override
    public synchronized long duplicateCount() {
        return Math.max(issuedRewards.size() - uniqueIssueCount(), 0);
    }

    private long uniqueIssueCount() {
        return issuedRewards.stream()
                .map(issue -> uniqueKey(issue.memberId(), issue.rewardType()))
                .distinct()
                .count();
    }

    private String uniqueKey(long memberId, RewardType rewardType) {
        return memberId + ":" + rewardType.name();
    }
}

