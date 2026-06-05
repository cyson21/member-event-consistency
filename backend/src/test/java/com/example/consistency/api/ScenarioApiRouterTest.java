package com.example.consistency.api;

import com.example.consistency.coupon.CouponCampaignApiHandler;
import com.example.consistency.coupon.CouponCampaignScenarioExecutor;
import com.example.consistency.point.PointSpendApiHandler;
import com.example.consistency.point.PointSpendScenarioExecutor;
import com.example.consistency.reward.FirstLoginRewardApiHandler;
import com.example.consistency.reward.FirstLoginRewardScenarioExecutor;
import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioRunReportRepository;

import java.util.Map;

public final class ScenarioApiRouterTest {

    public static void main(String[] args) {
        firstLoginRewardPostRouteReturnsSyncEvidence();
        couponRabbitRouteKeepsAcceptedAndCompletedSeparate();
        couponRabbitRouteForwardsRetryAndDlqControls();
        pointIdempotencyRouteReturnsReplayEvidence();
        constructorInjectedHandlersReuseSharedRepository();
        invalidMethodReturnsMethodNotAllowed();
        phase2RouteReturnsNotFound();
    }

    private static void firstLoginRewardPostRouteReturnsSyncEvidence() {
        ScenarioApiRouteResponse response = new ScenarioApiRouter().handle(
                "POST",
                "/api/scenarios/first-login-reward/runs",
                Map.of(
                        "memberId", "93001",
                        "strategy", "DB_GUARD",
                        "requestCount", "5"
                )
        );

        assertEquals(200, response.statusCode(), "reward route status");
        assertContains(response.body(), "\"scenario\":\"FIRST_LOGIN_REWARD\"", "reward scenario");
        assertContains(response.body(), "\"duplicateRewardCount\":0", "reward duplicate metric");
    }

    private static void couponRabbitRouteKeepsAcceptedAndCompletedSeparate() {
        ScenarioApiRouteResponse response = new ScenarioApiRouter().handle(
                "POST",
                "/api/scenarios/coupon-campaign-issue/runs",
                Map.of(
                        "campaignId", "94001",
                        "strategy", "RABBITMQ_DB_GUARD",
                        "capacity", "3",
                        "requestCount", "8"
                )
        );

        assertEquals(202, response.statusCode(), "coupon Rabbit route status");
        assertContains(response.body(), "\"acceptedCount\":8", "accepted count");
        assertContains(response.body(), "\"completedCount\":8", "completed count");
        assertContains(response.body(), "\"rabbitMqLaneCount\":1", "single lane evidence");
    }

    private static void couponRabbitRouteForwardsRetryAndDlqControls() {
        ScenarioApiRouteResponse response = new ScenarioApiRouter().handle(
                "POST",
                "/api/scenarios/coupon-campaign-issue/runs",
                Map.of(
                        "campaignId", "94002",
                        "strategy", "RABBITMQ_DB_GUARD",
                        "capacity", "3",
                        "requestCount", "8",
                        "transientRetryCount", "2",
                        "dlqCount", "1"
                )
        );

        assertEquals(202, response.statusCode(), "coupon Rabbit retry/DLQ route status");
        assertContains(response.body(), "\"queueRetryCount\":2", "retry count is forwarded");
        assertContains(response.body(), "\"dlqCount\":1", "DLQ count is forwarded");
        assertContains(response.body(), "\"couponIssuedCount\":3", "capacity guard still applies");
    }

    private static void pointIdempotencyRouteReturnsReplayEvidence() {
        ScenarioApiRouteResponse response = new ScenarioApiRouter().handle(
                "POST",
                "/api/scenarios/point-spend/runs",
                Map.of(
                        "memberId", "95001",
                        "strategy", "IDEMPOTENCY_REPLAY",
                        "initialBalance", "1000",
                        "spendAmount", "700",
                        "requestCount", "2",
                        "idempotencyKey", "spend-95001-001"
                )
        );

        assertEquals(200, response.statusCode(), "point route status");
        assertContains(response.body(), "\"scenario\":\"POINT_SPEND\"", "point scenario");
        assertContains(response.body(), "\"finalPointBalance\":300", "point balance metric");
        assertContains(response.body(), "\"idempotencyReplayCount\":1", "idempotency replay metric");
        assertContains(response.body(), "\"idempotencyHashMismatchCount\":0", "hash mismatch metric");
    }

    private static void constructorInjectedHandlersReuseSharedRepository() {
        ScenarioRunReportRepository reportRepository = new InMemoryScenarioRunReportRepository();
        ScenarioApiRouter router = new ScenarioApiRouter(
                new FirstLoginRewardApiHandler(new FirstLoginRewardScenarioExecutor(reportRepository)),
                new CouponCampaignApiHandler(new CouponCampaignScenarioExecutor(reportRepository)),
                new PointSpendApiHandler(new PointSpendScenarioExecutor(reportRepository))
        );

        router.handle(
                "POST",
                "/api/scenarios/first-login-reward/runs",
                Map.of(
                        "memberId", "93001",
                        "strategy", "DB_GUARD",
                        "requestCount", "1"
                )
        );
        ScenarioApiRouteResponse secondResponse = router.handle(
                "POST",
                "/api/scenarios/point-spend/runs",
                Map.of(
                        "memberId", "95001",
                        "strategy", "DB_ROW_LOCK",
                        "initialBalance", "1000",
                        "spendAmount", "100",
                        "requestCount", "1"
                )
        );

        assertContains(secondResponse.body(), "\"runSequence\":2", "injected handlers share report repository");
    }

    private static void invalidMethodReturnsMethodNotAllowed() {
        ScenarioApiRouteResponse response = new ScenarioApiRouter().handle(
                "GET",
                "/api/scenarios/first-login-reward/runs",
                Map.of()
        );

        assertEquals(405, response.statusCode(), "invalid method status");
        assertContains(response.body(), "\"message\":\"method not allowed\"", "invalid method message");
    }

    private static void phase2RouteReturnsNotFound() {
        ScenarioApiRouteResponse response = new ScenarioApiRouter().handle(
                "POST",
                "/api/scenarios/coupon-redemption/runs",
                Map.of("strategy", "DB_GUARD")
        );

        assertEquals(404, response.statusCode(), "Phase 2 route status");
        assertContains(response.body(), "\"message\":\"unknown route\"", "unknown route message");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
