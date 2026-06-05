package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;

public final class CouponRedemptionApiHandlerTest {

    public static void main(String[] args) {
        dbGuardRequestReturnsSingleUseEvidence();
        idempotencyReplayRequestReturnsReplayEvidence();
        idempotencyReplayRequestReturnsHashMismatchEvidence();
        invalidRequestIsRejectedBeforeExecution();
        unsupportedStrategyIsRejected();
    }

    private static void dbGuardRequestReturnsSingleUseEvidence() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        CouponRedemptionApiHandler handler = handler(reports);

        CouponRedemptionApiResponse response = handler.handle(
                new CouponRedemptionApiRequest(10008L, "DB_GUARD", 2)
        );

        assertEquals(200, response.statusCode(), "DB guard executes synchronously");
        assertEquals("COUPON_REDEMPTION", response.scenario(), "scenario is exposed");
        assertEquals("DB_GUARD", response.strategy(), "strategy is exposed");
        assertEquals(true, response.invariantPassed(), "DB guard response passes invariant");
        assertEquals(1L, response.couponUsedCount(), "coupon is used once");
        assertEquals(1L, response.rejectedCount(), "second terminal transition is rejected");
        assertEquals(1L, reports.count(), "handler persists the report");
    }

    private static void idempotencyReplayRequestReturnsReplayEvidence() {
        CouponRedemptionApiHandler handler = handler(new InMemoryScenarioRunReportRepository());

        CouponRedemptionApiResponse response = handler.handle(
                new CouponRedemptionApiRequest(
                        10009L,
                        "IDEMPOTENCY_REPLAY",
                        2,
                        "redeem-10009",
                        "member=501|coupon=10009",
                        "member=501|coupon=10009"
                )
        );

        assertEquals(200, response.statusCode(), "idempotency replay executes synchronously");
        assertEquals(1L, response.couponUsedCount(), "coupon is used once");
        assertEquals(1L, response.idempotencyReplayCount(), "same request retry is reported as replay");
        assertEquals(0L, response.idempotencyHashMismatchCount(), "same request retry has no mismatch");
        assertEquals(0L, response.rejectedCount(), "same request retry is not rejected");
    }

    private static void idempotencyReplayRequestReturnsHashMismatchEvidence() {
        CouponRedemptionApiHandler handler = handler(new InMemoryScenarioRunReportRepository());

        CouponRedemptionApiResponse response = handler.handle(
                new CouponRedemptionApiRequest(
                        10010L,
                        "IDEMPOTENCY_REPLAY",
                        2,
                        "redeem-10010",
                        "member=501|coupon=10010",
                        "member=502|coupon=10010"
                )
        );

        assertEquals(200, response.statusCode(), "hash mismatch is a handled business rejection");
        assertEquals(1L, response.couponUsedCount(), "coupon is used once");
        assertEquals(0L, response.idempotencyReplayCount(), "mismatch retry is not replayed");
        assertEquals(1L, response.idempotencyHashMismatchCount(), "mismatch retry is reported");
        assertEquals(1L, response.rejectedCount(), "mismatch retry is rejected");
    }

    private static void invalidRequestIsRejectedBeforeExecution() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        CouponRedemptionApiHandler handler = handler(reports);

        CouponRedemptionApiResponse response = handler.handle(new CouponRedemptionApiRequest(0L, "DB_GUARD", 0));

        assertEquals(400, response.statusCode(), "invalid request returns 400");
        assertEquals("couponIssueId and requestCount must be positive", response.message(), "validation message is explicit");
        assertEquals(0L, reports.count(), "invalid request is not executed");
    }

    private static void unsupportedStrategyIsRejected() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        CouponRedemptionApiHandler handler = handler(reports);

        CouponRedemptionApiResponse response = handler.handle(
                new CouponRedemptionApiRequest(10011L, "RABBITMQ_DB_GUARD", 2)
        );

        assertEquals(400, response.statusCode(), "RabbitMQ is not a Coupon Redemption strategy in this slice");
        assertEquals("strategy is not supported for Coupon Redemption / Usage", response.message(), "unsupported strategy message is explicit");
        assertEquals(0L, reports.count(), "unsupported strategy is not executed");
    }

    private static CouponRedemptionApiHandler handler(InMemoryScenarioRunReportRepository reports) {
        return new CouponRedemptionApiHandler(new CouponRedemptionServiceScenarioExecutor(
                new CouponRedemptionService(new InMemoryCouponRedemptionRepository()),
                reports
        ));
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
