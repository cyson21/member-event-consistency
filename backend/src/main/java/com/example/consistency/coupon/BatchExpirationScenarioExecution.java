package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public interface BatchExpirationScenarioExecution {

    ScenarioRunRecord execute(StrategyType strategy, long couponIssueId, BatchExpirationWinner winner);
}
