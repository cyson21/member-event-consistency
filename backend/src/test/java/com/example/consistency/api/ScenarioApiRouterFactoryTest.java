package com.example.consistency.api;

import com.example.consistency.coupon.CouponCampaignIssueResult;
import com.example.consistency.coupon.CouponCampaignLockGateway;
import com.example.consistency.coupon.CouponCampaignRepository;
import com.example.consistency.coupon.CouponCampaignService;
import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.point.PointSpendRepository;
import com.example.consistency.point.PointSpendService;
import com.example.consistency.reward.FakeRewardFollowUpRecorder;
import com.example.consistency.reward.FirstLoginRewardService;
import com.example.consistency.reward.InMemoryRewardIssueRepository;
import com.example.consistency.reward.RewardLockGateway;
import com.example.consistency.reward.RecordingRewardLockGateway;
import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class ScenarioApiRouterFactoryTest {

    public static void main(String[] args) {
        serviceBackedRouterRunsAllMvpRoutesThroughSharedReportRepository();
        sqlBackedRouterRunsMvpRoutesThroughSqlAdaptersAndReportPersistence();
    }

    private static void serviceBackedRouterRunsAllMvpRoutesThroughSharedReportRepository() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        ScenarioApiRouter router = ScenarioApiRouterFactory.serviceBacked(
                new FirstLoginRewardService(
                        new InMemoryRewardIssueRepository(),
                        new FakeRewardFollowUpRecorder(),
                        new RecordingRewardLockGateway()
                ),
                new CouponCampaignService(
                        new CapacityCouponCampaignRepository(2),
                        new RecordingCampaignLockGateway()
                ),
                new PointSpendService(new BalancePointSpendRepository(1000L)),
                reports
        );

        ScenarioApiRouteResponse reward = router.handle(
                "POST",
                "/api/scenarios/first-login-reward/runs",
                Map.of("memberId", "94001", "strategy", "DB_GUARD", "requestCount", "2")
        );
        ScenarioApiRouteResponse coupon = router.handle(
                "POST",
                "/api/scenarios/coupon-campaign-issue/runs",
                Map.of("campaignId", "95001", "strategy", "RABBITMQ_DB_GUARD", "capacity", "2", "requestCount", "4")
        );
        ScenarioApiRouteResponse point = router.handle(
                "POST",
                "/api/scenarios/point-spend/runs",
                Map.of(
                        "memberId", "96001",
                        "strategy", "IDEMPOTENCY_REPLAY",
                        "initialBalance", "1000",
                        "spendAmount", "700",
                        "requestCount", "2",
                        "idempotencyKey", "spend-96001-001"
                )
        );

        assertEquals(200, reward.statusCode(), "reward route status");
        assertEquals(202, coupon.statusCode(), "coupon async status");
        assertEquals(200, point.statusCode(), "point route status");
        assertContains(reward.body(), "\"runSequence\":1", "reward sequence");
        assertContains(coupon.body(), "\"runSequence\":2", "coupon sequence");
        assertContains(point.body(), "\"runSequence\":3", "point sequence");
        assertContains(coupon.body(), "\"rabbitMqLaneCount\":1", "coupon single lane");
        assertContains(point.body(), "\"idempotencyReplayCount\":1", "point replay count");
        assertEquals(3L, reports.count(), "shared report repository stores all MVP route runs");
    }

    private static void sqlBackedRouterRunsMvpRoutesThroughSqlAdaptersAndReportPersistence() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.nextLongResult(1L); // reward report sequence
        sql.nextLongResult(1L); // coupon issue success
        sql.nextLongResult(2L); // coupon report sequence
        sql.nextLongResult(0L); // point first request-hash mismatch lookup
        sql.nextLongResult(0L); // point first replay lookup
        sql.nextLongResult(0L); // point second request-hash mismatch lookup
        sql.nextLongResult(1L); // point second replay lookup
        sql.nextLongResult(3L); // point report sequence
        ScenarioApiRouter router = ScenarioApiRouterFactory.sqlBacked(
                sql,
                new RecordingRewardLockGateway(),
                new NoOpCampaignLockGateway()
        );

        ScenarioApiRouteResponse reward = router.handle(
                "POST",
                "/api/scenarios/first-login-reward/runs",
                Map.of("memberId", "97001", "strategy", "DB_GUARD", "requestCount", "1")
        );
        ScenarioApiRouteResponse coupon = router.handle(
                "POST",
                "/api/scenarios/coupon-campaign-issue/runs",
                Map.of("campaignId", "98001", "strategy", "RABBITMQ_DB_GUARD", "capacity", "2", "requestCount", "1")
        );
        ScenarioApiRouteResponse point = router.handle(
                "POST",
                "/api/scenarios/point-spend/runs",
                Map.of(
                        "memberId", "99001",
                        "strategy", "IDEMPOTENCY_REPLAY",
                        "initialBalance", "1000",
                        "spendAmount", "700",
                        "requestCount", "2",
                        "idempotencyKey", "spend-99001-001"
                )
        );

        assertEquals(200, reward.statusCode(), "SQL reward route status");
        assertEquals(202, coupon.statusCode(), "SQL coupon route status");
        assertEquals(200, point.statusCode(), "SQL point route status");
        assertContains(reward.body(), "\"runSequence\":1", "SQL reward sequence");
        assertContains(coupon.body(), "\"runSequence\":2", "SQL coupon sequence");
        assertContains(point.body(), "\"runSequence\":3", "SQL point sequence");
        assertStatementContains(sql, "insert into reward_issues", "reward issue SQL");
        assertStatementContains(sql, "insert into coupon_issues", "coupon issue SQL");
        assertStatementContains(sql, "select count(*) from idempotency_records", "point replay SQL");
        assertStatementContains(sql, "insert into scenario_runs", "scenario run SQL");
        assertStatementContains(sql, "insert into scenario_metrics", "scenario metrics SQL");
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

    private static void assertStatementContains(RecordingSqlExecutor sql, String expected, String message) {
        for (int index = 0; index < sql.statementCount(); index++) {
            if (sql.statementAt(index).sql().contains(expected)) {
                return;
            }
        }
        throw new AssertionError(message + " expected SQL fragment=[" + expected + "]");
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
        @Override
        public <T> T withCampaignLock(long campaignId, Supplier<T> operation) {
            return operation.get();
        }

        @Override
        public long attemptCount() {
            return 0;
        }

        @Override
        public String lastLockKey() {
            return "";
        }
    }

    private static final class NoOpCampaignLockGateway implements CouponCampaignLockGateway {
        @Override
        public <T> T withCampaignLock(long campaignId, Supplier<T> operation) {
            return operation.get();
        }

        @Override
        public long attemptCount() {
            return 0;
        }

        @Override
        public String lastLockKey() {
            return "";
        }
    }

    private static final class BalancePointSpendRepository implements PointSpendRepository {
        private long balance;
        private final Set<String> idempotencyKeys = new HashSet<>();

        private BalancePointSpendRepository(long balance) {
            this.balance = balance;
        }

        @Override
        public long balanceForUpdate(long memberId) {
            return balance;
        }

        @Override
        public boolean tryDebit(long memberId, long spendAmount) {
            if (balance < spendAmount) {
                return false;
            }
            balance -= spendAmount;
            return true;
        }

        @Override
        public boolean insertLedger(UUID eventId, long memberId, long amount, String idempotencyKey) {
            return true;
        }

        @Override
        public boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef) {
            return idempotencyKeys.add(idempotencyKey);
        }

        @Override
        public long replayCount(String idempotencyKey) {
            return idempotencyKeys.contains(idempotencyKey) ? 1L : 0L;
        }

        @Override
        public long requestHashMismatchCount(String idempotencyKey, String requestHash) {
            return 0L;
        }
    }
}
