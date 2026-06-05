package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignService {

    private final CouponCampaignRepository repository;
    private final CouponCampaignLockGateway locks;

    public CouponCampaignService(CouponCampaignRepository repository, CouponCampaignLockGateway locks) {
        this.repository = repository;
        this.locks = locks;
    }

    public CouponCampaignDecision issue(CouponCampaignCommand command) {
        if (command.campaignId() <= 0 || command.memberId() <= 0) {
            throw new IllegalArgumentException("campaignId and memberId must be positive");
        }
        if (command.strategy() == StrategyType.DB_GUARD) {
            return issueWithDbGuard(command, false, "", 0);
        }
        if (command.strategy() == StrategyType.REDIS_LOCK_DB_GUARD) {
            return locks.withCampaignLock(
                    command.campaignId(),
                    () -> issueWithDbGuard(command, true, locks.lastLockKey(), 0)
            );
        }
        if (command.strategy() == StrategyType.RABBITMQ_DB_GUARD) {
            return issueWithDbGuard(command, false, "", 1);
        }
        throw new IllegalArgumentException("strategy is not supported for Coupon Campaign Issue service: " + command.strategy());
    }

    private CouponCampaignDecision issueWithDbGuard(
            CouponCampaignCommand command,
            boolean lockAttempted,
            String lockKey,
            long rabbitMqLaneCount
    ) {
        CouponCampaignIssueResult result = repository.issueWithCapacityGuard(
                command.campaignId(),
                command.memberId(),
                command.idempotencyKey()
        );
        return CouponCampaignDecision.fromResult(result, lockAttempted, lockKey, rabbitMqLaneCount);
    }
}

