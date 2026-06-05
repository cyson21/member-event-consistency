package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class CouponCampaignServiceScenarioExecutorTest {

    public static void main(String[] args) {
        dbGuardPersistsCapacityGuardMetricsFromServiceDecisions();
        redisLockPathRecordsCampaignScopedLockAttempts();
        rabbitMqPathKeepsAcceptedAndCompletedSeparateWithSingleLane();
        naivePathKeepsHarnessFailureEvidence();
    }

    private static void dbGuardPersistsCapacityGuardMetricsFromServiceDecisions() {
        CapacityCouponCampaignRepository coupons = new CapacityCouponCampaignRepository(3);
        CouponCampaignService service = new CouponCampaignService(coupons, new RecordingCampaignLockGateway());
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        CouponCampaignServiceScenarioExecutor executor = new CouponCampaignServiceScenarioExecutor(service, reports);

        ScenarioRunRecord record = executor.execute(StrategyType.DB_GUARD, 92001L, 3, 5);

        assertEquals(true, record.report().invariant().passed(), "DB guard invariant");
        assertEquals(5L, record.report().metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count");
        assertEquals(5L, record.report().metricValue(ScenarioMetricName.COMPLETED_COUNT), "completed count");
        assertEquals(3L, record.report().metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "issued count");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "rejected count");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "over issue count");
        assertEquals(1L, reports.count(), "report persisted once");
    }

    private static void redisLockPathRecordsCampaignScopedLockAttempts() {
        CapacityCouponCampaignRepository coupons = new CapacityCouponCampaignRepository(2);
        RecordingCampaignLockGateway locks = new RecordingCampaignLockGateway();
        CouponCampaignService service = new CouponCampaignService(coupons, locks);
        CouponCampaignServiceScenarioExecutor executor = new CouponCampaignServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.REDIS_LOCK_DB_GUARD, 92002L, 2, 4);

        assertEquals(true, record.report().invariant().passed(), "Redis plus DB guard invariant");
        assertEquals(4L, record.report().metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT), "campaign lock attempts");
        assertEquals("lock:coupon-campaign:92002", locks.lastLockKey(), "campaign lock scope");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "issued count under lock");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "rejected count under lock");
    }

    private static void rabbitMqPathKeepsAcceptedAndCompletedSeparateWithSingleLane() {
        CapacityCouponCampaignRepository coupons = new CapacityCouponCampaignRepository(2);
        CouponCampaignService service = new CouponCampaignService(coupons, new RecordingCampaignLockGateway());
        CouponCampaignServiceScenarioExecutor executor = new CouponCampaignServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.RABBITMQ_DB_GUARD, 92003L, 2, 5);

        assertEquals(true, record.report().invariant().passed(), "RabbitMQ plus DB guard invariant");
        assertEquals(5L, record.report().metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count");
        assertEquals(5L, record.report().metricValue(ScenarioMetricName.COMPLETED_COUNT), "completed count");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.RABBITMQ_LANE_COUNT), "single lane evidence");
        assertEquals(true, record.report().metricValue(ScenarioMetricName.QUEUE_LAG_MS_P95) > 0, "queue lag evidence");
        assertEquals(true, record.report().metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS) > 0, "accepted latency evidence");
        assertEquals(true,
                record.report().metricValue(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS)
                        > record.report().metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS),
                "completion latency remains separate from accepted latency");
    }

    private static void naivePathKeepsHarnessFailureEvidence() {
        CouponCampaignService service = new CouponCampaignService(
                new CapacityCouponCampaignRepository(3),
                new RecordingCampaignLockGateway()
        );
        CouponCampaignServiceScenarioExecutor executor = new CouponCampaignServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.NAIVE, 92004L, 3, 5);

        assertEquals(false, record.report().invariant().passed(), "naive invariant failure remains visible");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "naive over issue count");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static final class CapacityCouponCampaignRepository implements CouponCampaignRepository {
        private final long capacity;
        private final Set<Long> issuedMembers = new HashSet<>();

        private CapacityCouponCampaignRepository(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public CouponCampaignIssueResult issueWithCapacityGuard(long campaignId, long memberId, String idempotencyKey) {
            if (issuedMembers.contains(memberId)) {
                return CouponCampaignIssueResult.duplicateRejected();
            }
            if (issuedMembers.size() >= capacity) {
                return CouponCampaignIssueResult.capacityRejected();
            }
            issuedMembers.add(memberId);
            return CouponCampaignIssueResult.success();
        }

        @Override
        public long issuedCount(long campaignId) {
            return issuedMembers.size();
        }

        @Override
        public long overIssueCount(long campaignId) {
            return Math.max(issuedMembers.size() - capacity, 0);
        }
    }

    private static final class RecordingCampaignLockGateway implements CouponCampaignLockGateway {
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
