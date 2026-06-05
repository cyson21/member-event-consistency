package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;

public final class CouponCampaignApiHandlerTest {

    public static void main(String[] args) {
        rabbitMqRequestReturnsAcceptedAndCompletedCountsSeparately();
        rabbitMqRequestReturnsRetryAndDlqEvidence();
        naiveRequestReturnsOverIssueEvidence();
        invalidRequestIsRejectedBeforeExecution();
        pointSpendStrategyIsRejectedForCouponCampaign();
    }

    private static void rabbitMqRequestReturnsAcceptedAndCompletedCountsSeparately() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        CouponCampaignApiHandler handler = new CouponCampaignApiHandler(new CouponCampaignScenarioExecutor(repository));

        CouponCampaignApiResponse response = handler.handle(new CouponCampaignApiRequest(8101L, "RABBITMQ_DB_GUARD", 3, 8));

        assertEquals(202, response.statusCode(), "RabbitMQ request returns accepted-style status");
        assertEquals("COUPON_CAMPAIGN_ISSUE", response.scenario(), "scenario is exposed");
        assertEquals("RABBITMQ_DB_GUARD", response.strategy(), "strategy is exposed");
        assertEquals(true, response.invariantPassed(), "RabbitMQ guarded run passes invariant");
        assertEquals(8L, response.acceptedCount(), "accepted count is exposed");
        assertEquals(8L, response.completedCount(), "completed count is exposed separately");
        assertEquals(3L, response.couponIssuedCount(), "issued count stops at capacity");
        assertEquals(0L, response.overIssueCount(), "over issue count is zero");
        assertEquals(1L, response.rabbitMqLaneCount(), "single lane evidence is exposed");
        assertEquals(true, response.queueLagMsP95() > 0, "queue lag is exposed");
        assertEquals(true, response.rabbitMqAcceptedLatencyMs() > 0, "accepted latency is exposed");
        assertEquals(true,
                response.rabbitMqCompletionLatencyMs() > response.rabbitMqAcceptedLatencyMs(),
                "final completion latency is exposed separately");
        assertEquals(1L, repository.count(), "handler persists the report");
    }

    private static void rabbitMqRequestReturnsRetryAndDlqEvidence() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        CouponCampaignApiHandler handler = new CouponCampaignApiHandler(new CouponCampaignScenarioExecutor(repository));

        CouponCampaignApiResponse response = handler.handle(
                new CouponCampaignApiRequest(8104L, "RABBITMQ_DB_GUARD", 3, 8, 2, 1)
        );

        assertEquals(202, response.statusCode(), "RabbitMQ retry/DLQ request returns accepted-style status");
        assertEquals(2L, response.queueRetryCount(), "transient retry count is exposed");
        assertEquals(1L, response.dlqCount(), "DLQ count is exposed");
        assertEquals(3L, response.couponIssuedCount(), "issued count still stops at capacity");
        assertEquals(0L, response.overIssueCount(), "DLQ does not hide over issue");
        assertEquals(1L, repository.count(), "handler persists retry/DLQ report");
    }

    private static void naiveRequestReturnsOverIssueEvidence() {
        CouponCampaignApiHandler handler = new CouponCampaignApiHandler(
                new CouponCampaignScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );

        CouponCampaignApiResponse response = handler.handle(new CouponCampaignApiRequest(8102L, "NAIVE", 3, 8));

        assertEquals(200, response.statusCode(), "naive synchronous request executes");
        assertEquals(false, response.invariantPassed(), "naive response shows broken invariant");
        assertEquals(5L, response.overIssueCount(), "over issue count is exposed");
    }

    private static void invalidRequestIsRejectedBeforeExecution() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        CouponCampaignApiHandler handler = new CouponCampaignApiHandler(new CouponCampaignScenarioExecutor(repository));

        CouponCampaignApiResponse response = handler.handle(new CouponCampaignApiRequest(0L, "DB_GUARD", -1, 0));

        assertEquals(400, response.statusCode(), "invalid request returns 400");
        assertEquals("campaignId and requestCount must be positive, capacity must be non-negative", response.message(), "validation message is explicit");
        assertEquals(0L, repository.count(), "invalid request is not executed");
    }

    private static void pointSpendStrategyIsRejectedForCouponCampaign() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        CouponCampaignApiHandler handler = new CouponCampaignApiHandler(new CouponCampaignScenarioExecutor(repository));

        CouponCampaignApiResponse response = handler.handle(new CouponCampaignApiRequest(8103L, "DB_ROW_LOCK", 3, 8));

        assertEquals(400, response.statusCode(), "Point Spend strategy is not a Coupon Campaign strategy");
        assertEquals("strategy is not supported for Coupon Campaign Issue", response.message(), "unsupported strategy message is explicit");
        assertEquals(0L, repository.count(), "unsupported strategy is not executed");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
