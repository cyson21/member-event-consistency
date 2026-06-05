package com.example.consistency.coupon;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.scenario.StrategyType;

import java.util.function.Supplier;

public final class CouponCampaignSqlWiringTest {

    public static void main(String[] args) {
        dbGuardPathUsesSingleGuardedSqlStatement();
        redisLockPathKeepsCampaignLockScopeAndSqlGuard();
        rabbitMqWorkerPathKeepsSingleLaneExplicitAndUsesSqlGuard();
    }

    private static void dbGuardPathUsesSingleGuardedSqlStatement() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        CouponCampaignService service = CouponCampaignSqlWiring.service(
                executor,
                new RecordingCouponCampaignLockGateway()
        );

        CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                97001L,
                91001L,
                StrategyType.DB_GUARD,
                "coupon-97001-91001"
        ));

        assertEquals(true, decision.issued(), "DB guard SQL path issues coupon");
        assertEquals(false, decision.lockAttempted(), "DB guard path does not claim Redis lock");
        assertEquals(0L, decision.rabbitMqLaneCount(), "DB guard path does not claim RabbitMQ lane");
        assertEquals(1L, executor.statementCount(), "DB guard path uses one guarded SQL statement");
        assertContains(executor.statementAt(0).sql(), "for update", "guarded SQL locks campaign row");
        assertContains(executor.statementAt(0).sql(), "insert into coupon_issues", "guarded SQL inserts coupon issue");
        assertContains(executor.statementAt(0).sql(), "update coupon_campaigns", "guarded SQL increments issued count");
    }

    private static void redisLockPathKeepsCampaignLockScopeAndSqlGuard() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(0L);
        RecordingCouponCampaignLockGateway locks = new RecordingCouponCampaignLockGateway();
        CouponCampaignService service = CouponCampaignSqlWiring.service(executor, locks);

        CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                97002L,
                91002L,
                StrategyType.REDIS_LOCK_DB_GUARD,
                "coupon-97002-91002"
        ));

        assertEquals(false, decision.issued(), "capacity rejection is visible");
        assertEquals(true, decision.lockAttempted(), "Redis lock attempt is reflected");
        assertEquals("lock:coupon-campaign:97002", decision.lockKey(), "lock scope is campaign-specific");
        assertEquals(1L, locks.attemptCount(), "one campaign lock attempt is recorded");
        assertEquals(1L, executor.statementCount(), "Redis path still relies on SQL guard");
    }

    private static void rabbitMqWorkerPathKeepsSingleLaneExplicitAndUsesSqlGuard() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        CouponCampaignService service = CouponCampaignSqlWiring.service(
                executor,
                new RecordingCouponCampaignLockGateway()
        );

        CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                97003L,
                91003L,
                StrategyType.RABBITMQ_DB_GUARD,
                "coupon-97003-91003"
        ));

        assertEquals(true, decision.issued(), "RabbitMQ worker SQL path issues coupon");
        assertEquals(false, decision.lockAttempted(), "RabbitMQ path does not imply Redis lock");
        assertEquals(1L, decision.rabbitMqLaneCount(), "single lane is explicit");
        assertEquals(1L, executor.statementCount(), "RabbitMQ worker path still uses SQL guard");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
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

