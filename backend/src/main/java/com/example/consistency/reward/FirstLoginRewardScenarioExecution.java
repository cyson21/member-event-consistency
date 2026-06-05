package com.example.consistency.reward;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public interface FirstLoginRewardScenarioExecution {

    ScenarioRunRecord execute(StrategyType strategy, long memberId, int requestCount);
}
