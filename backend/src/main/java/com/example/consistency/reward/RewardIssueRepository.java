package com.example.consistency.reward;

public interface RewardIssueRepository {

    boolean insertNaive(long memberId, RewardType rewardType);

    boolean insertUnique(long memberId, RewardType rewardType);

    long issuedCount();

    long duplicateCount();
}

