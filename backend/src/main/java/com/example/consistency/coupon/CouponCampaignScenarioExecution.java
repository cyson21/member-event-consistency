package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public interface CouponCampaignScenarioExecution {

    ScenarioRunRecord execute(StrategyType strategy, long campaignId, int capacity, int requestCount);

    default ScenarioRunRecord executeWithWorkerFailures(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount,
            int transientRetryCount,
            int dlqCount
    ) {
        return execute(strategy, campaignId, capacity, requestCount);
    }
}
