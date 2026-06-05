package com.example.consistency.reward;

import com.example.consistency.scenario.StrategyType;

public record FirstLoginRewardCommand(
        long memberId,
        StrategyType strategy
) {
}

