package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public record CouponRedemptionRunResult(
        StrategyType strategy,
        long couponIssueId,
        long requestedCount,
        long acceptedCount,
        long completedCount,
        long usedCount,
        long doubleUseCount,
        long terminalStateConflictCount,
        long rejectedCount,
        long idempotencyReplayCount,
        long idempotencyHashMismatchCount
) {

    public InvariantResult toInvariantResult() {
        return new InvariantResult(
                ScenarioType.COUPON_REDEMPTION,
                strategy,
                doubleUseCount == 0 && terminalStateConflictCount == 0,
                0,
                0,
                0,
                doubleUseCount,
                terminalStateConflictCount,
                0,
                acceptedCount,
                completedCount,
                0,
                0,
                0,
                "coupon-redemption run"
        );
    }
}
