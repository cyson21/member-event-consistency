package com.example.consistency.coupon;

import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CouponCampaignServiceTest {

    public static void main(String[] args) {
        dbGuardIssuesThroughCapacityGuardPort();
        redisLockStrategyUsesCampaignScopedLockBeforeDbGuard();
        rabbitMqWorkerStrategyKeepsSingleLaneExplicit();
    }

    private static void dbGuardIssuesThroughCapacityGuardPort() {
        RecordingCouponCampaignRepository repository = new RecordingCouponCampaignRepository();
        repository.issueResult = CouponCampaignIssueResult.success();
        RecordingCouponCampaignLockGateway locks = new RecordingCouponCampaignLockGateway();
        CouponCampaignService service = new CouponCampaignService(repository, locks);

        CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                96001L,
                91001L,
                StrategyType.DB_GUARD,
                "coupon-96001-91001"
        ));

        assertEquals(true, decision.issued(), "DB guard issue succeeds");
        assertEquals(false, decision.lockAttempted(), "DB guard does not claim Redis lock");
        assertEquals(0L, decision.rabbitMqLaneCount(), "DB guard does not claim RabbitMQ lane");
        assertEquals(List.of("issueWithCapacityGuard"), repository.calls, "DB guard uses one repository port");
        assertEquals(96001L, repository.lastCampaignId, "campaign id is passed");
        assertEquals(91001L, repository.lastMemberId, "member id is passed");
        assertEquals("coupon-96001-91001", repository.lastIdempotencyKey, "idempotency key is passed");
    }

    private static void redisLockStrategyUsesCampaignScopedLockBeforeDbGuard() {
        RecordingCouponCampaignRepository repository = new RecordingCouponCampaignRepository();
        repository.issueResult = CouponCampaignIssueResult.capacityRejected();
        RecordingCouponCampaignLockGateway locks = new RecordingCouponCampaignLockGateway();
        CouponCampaignService service = new CouponCampaignService(repository, locks);

        CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                96002L,
                91002L,
                StrategyType.REDIS_LOCK_DB_GUARD,
                "coupon-96002-91002"
        ));

        assertEquals(false, decision.issued(), "capacity rejection is visible");
        assertEquals(true, decision.lockAttempted(), "Redis lock attempt is recorded");
        assertEquals("lock:coupon-campaign:96002", decision.lockKey(), "lock scope is campaign-specific");
        assertEquals(1L, locks.attemptCount(), "one campaign lock attempt is recorded");
        assertEquals(List.of("issueWithCapacityGuard"), repository.calls, "DB guard remains final invariant defense");
    }

    private static void rabbitMqWorkerStrategyKeepsSingleLaneExplicit() {
        RecordingCouponCampaignRepository repository = new RecordingCouponCampaignRepository();
        repository.issueResult = CouponCampaignIssueResult.duplicateRejected();
        RecordingCouponCampaignLockGateway locks = new RecordingCouponCampaignLockGateway();
        CouponCampaignService service = new CouponCampaignService(repository, locks);

        CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                96003L,
                91003L,
                StrategyType.RABBITMQ_DB_GUARD,
                "coupon-96003-91003"
        ));

        assertEquals(false, decision.issued(), "duplicate rejection is visible");
        assertEquals(false, decision.lockAttempted(), "RabbitMQ worker does not imply Redis lock");
        assertEquals(1L, decision.rabbitMqLaneCount(), "single lane is explicit");
        assertEquals("duplicate", decision.rejectionReason(), "duplicate reason is preserved");
        assertEquals(List.of("issueWithCapacityGuard"), repository.calls, "worker still uses DB guard port");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static final class RecordingCouponCampaignRepository implements CouponCampaignRepository {
        private final List<String> calls = new ArrayList<>();
        private CouponCampaignIssueResult issueResult = CouponCampaignIssueResult.success();
        private long lastCampaignId;
        private long lastMemberId;
        private String lastIdempotencyKey = "";

        @Override
        public CouponCampaignIssueResult issueWithCapacityGuard(long campaignId, long memberId, String idempotencyKey) {
            calls.add("issueWithCapacityGuard");
            lastCampaignId = campaignId;
            lastMemberId = memberId;
            lastIdempotencyKey = idempotencyKey;
            return issueResult;
        }

        @Override
        public long issuedCount(long campaignId) {
            return 0;
        }

        @Override
        public long overIssueCount(long campaignId) {
            return 0;
        }
    }

    private static final class RecordingCouponCampaignLockGateway implements CouponCampaignLockGateway {
        private long attempts;
        private String lastLockKey = "";

        @Override
        public <T> T withCampaignLock(long campaignId, Supplier<T> operation) {
            attempts++;
            lastLockKey = "lock:coupon-campaign:" + campaignId;
            return operation.get();
        }

        @Override
        public long attemptCount() {
            return attempts;
        }

        @Override
        public String lastLockKey() {
            return lastLockKey;
        }
    }
}
