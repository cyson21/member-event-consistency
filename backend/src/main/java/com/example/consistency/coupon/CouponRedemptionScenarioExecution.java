package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

public interface CouponRedemptionScenarioExecution {

    ScenarioRunRecord execute(StrategyType strategy, long couponIssueId, int requestCount);

    ScenarioRunRecord executeWithIdempotency(
            StrategyType strategy,
            long couponIssueId,
            String idempotencyKey,
            String firstRequestHash,
            String retryRequestHash
    );
}
