package com.example.consistency.reward;

import java.util.function.Supplier;

public interface RewardLockGateway {

    <T> T withRewardLock(long memberId, Supplier<T> operation);

    long attemptCount();

    String lastLockKey();
}

