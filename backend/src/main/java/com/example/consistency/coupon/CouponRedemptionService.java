package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionService {

    private final CouponRedemptionRepository repository;

    public CouponRedemptionService(CouponRedemptionRepository repository) {
        this.repository = repository;
    }

    public CouponRedemptionDecision redeem(CouponRedemptionCommand command) {
        return switch (command.strategy()) {
            case DB_GUARD -> redeemWithDbGuard(command.couponIssueId());
            case IDEMPOTENCY_REPLAY -> redeemWithIdempotency(command);
            case NAIVE, REDIS_LOCK_DB_GUARD, RABBITMQ_DB_GUARD, DB_ROW_LOCK, CONDITIONAL_UPDATE ->
                    throw new IllegalArgumentException("strategy is not supported for Coupon Redemption / Usage");
        };
    }

    private CouponRedemptionDecision redeemWithDbGuard(long couponIssueId) {
        boolean used = repository.tryMarkUsed(couponIssueId);
        return new CouponRedemptionDecision(used, !used, false, false);
    }

    private CouponRedemptionDecision redeemWithIdempotency(CouponRedemptionCommand command) {
        if (repository.requestHashMismatchCount(command.idempotencyKey(), command.requestHash()) > 0) {
            return new CouponRedemptionDecision(false, true, false, true);
        }
        if (repository.replayCount(command.idempotencyKey()) > 0) {
            return new CouponRedemptionDecision(false, false, true, false);
        }

        boolean used = repository.tryMarkUsed(command.couponIssueId());
        if (used) {
            repository.insertIdempotencyRecord(
                    command.idempotencyKey(),
                    command.requestHash(),
                    "coupon-issue:" + command.couponIssueId()
            );
        }
        return new CouponRedemptionDecision(used, !used, false, false);
    }
}
