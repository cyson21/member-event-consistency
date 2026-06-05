package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;

public final class BatchExpirationApiHandlerTest {

    public static void main(String[] args) {
        dbGuardRequestReturnsSingleTerminalStateAndRejectionReason();
        invalidRequestIsRejectedBeforeExecution();
        unsupportedStrategyIsRejected();
        invalidWinnerIsRejected();
    }

    private static void dbGuardRequestReturnsSingleTerminalStateAndRejectionReason() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        BatchExpirationApiHandler handler = handler(reports);

        BatchExpirationApiResponse response = handler.handle(
                new BatchExpirationApiRequest(13004L, "DB_GUARD", "USER_USE")
        );

        assertEquals(200, response.statusCode(), "DB guard executes synchronously");
        assertEquals("BATCH_EXPIRATION", response.scenario(), "scenario is exposed");
        assertEquals("DB_GUARD", response.strategy(), "strategy is exposed");
        assertEquals(true, response.invariantPassed(), "DB guard response passes invariant");
        assertEquals(1L, response.couponUsedCount(), "use wins once");
        assertEquals(0L, response.couponExpiredCount(), "expiration does not also win");
        assertEquals(1L, response.rejectedCount(), "losing expiration is rejected");
        assertEquals("expiration rejected because coupon already used", response.rejectionReason(), "rejection reason is exposed");
        assertEquals(1L, reports.count(), "handler persists the report");
    }

    private static void invalidRequestIsRejectedBeforeExecution() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        BatchExpirationApiHandler handler = handler(reports);

        BatchExpirationApiResponse response = handler.handle(new BatchExpirationApiRequest(0L, "DB_GUARD", "USER_USE"));

        assertEquals(400, response.statusCode(), "invalid request returns 400");
        assertEquals("couponIssueId must be positive", response.message(), "validation message is explicit");
        assertEquals(0L, reports.count(), "invalid request is not executed");
    }

    private static void unsupportedStrategyIsRejected() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        BatchExpirationApiHandler handler = handler(reports);

        BatchExpirationApiResponse response = handler.handle(
                new BatchExpirationApiRequest(13005L, "RABBITMQ_DB_GUARD", "USER_USE")
        );

        assertEquals(400, response.statusCode(), "RabbitMQ is not a Batch Expiration strategy in this slice");
        assertEquals("strategy is not supported for Batch Expiration vs User Use", response.message(), "unsupported strategy message is explicit");
        assertEquals(0L, reports.count(), "unsupported strategy is not executed");
    }

    private static void invalidWinnerIsRejected() {
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        BatchExpirationApiHandler handler = handler(reports);

        BatchExpirationApiResponse response = handler.handle(
                new BatchExpirationApiRequest(13006L, "DB_GUARD", "OTHER")
        );

        assertEquals(400, response.statusCode(), "invalid winner returns 400");
        assertEquals("unknown winner", response.message(), "winner validation message is explicit");
        assertEquals(0L, reports.count(), "invalid winner is not executed");
    }

    private static BatchExpirationApiHandler handler(InMemoryScenarioRunReportRepository reports) {
        return new BatchExpirationApiHandler(new BatchExpirationServiceScenarioExecutor(
                new BatchExpirationService(new InMemoryBatchExpirationRepository()),
                reports
        ));
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
