package com.example.consistency.point;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;

public final class PointSpendApiHandlerTest {

    public static void main(String[] args) {
        idempotencyReplayRequestReturnsReplayEvidence();
        naiveRequestReturnsNegativeBalanceEvidence();
        invalidRequestIsRejectedBeforeExecution();
        rabbitMqStrategyIsRejectedForPointSpend();
    }

    private static void idempotencyReplayRequestReturnsReplayEvidence() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        PointSpendApiHandler handler = new PointSpendApiHandler(new PointSpendScenarioExecutor(repository));

        PointSpendApiResponse response = handler.handle(
                new PointSpendApiRequest(8201L, "IDEMPOTENCY_REPLAY", 1000, 700, 2, "spend-8201-001")
        );

        assertEquals(200, response.statusCode(), "idempotency replay executes synchronously");
        assertEquals("POINT_SPEND", response.scenario(), "scenario is exposed");
        assertEquals("IDEMPOTENCY_REPLAY", response.strategy(), "strategy is exposed");
        assertEquals(true, response.invariantPassed(), "idempotency replay passes invariant");
        assertEquals(2L, response.acceptedCount(), "accepted count is exposed");
        assertEquals(2L, response.completedCount(), "completed count is exposed");
        assertEquals(300L, response.finalPointBalance(), "final balance is exposed");
        assertEquals(1L, response.pointLedgerEntryCount(), "single ledger entry is exposed");
        assertEquals(1L, response.idempotencyReplayCount(), "replay count is exposed");
        assertEquals(0L, response.idempotencyHashMismatchCount(), "hash mismatch metric is exposed");
        assertEquals(1L, repository.count(), "handler persists the report");
    }

    private static void naiveRequestReturnsNegativeBalanceEvidence() {
        PointSpendApiHandler handler = new PointSpendApiHandler(
                new PointSpendScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );

        PointSpendApiResponse response = handler.handle(new PointSpendApiRequest(8202L, "NAIVE", 1000, 700, 2, ""));

        assertEquals(200, response.statusCode(), "naive synchronous request executes");
        assertEquals(false, response.invariantPassed(), "naive response shows broken invariant");
        assertEquals(-400L, response.finalPointBalance(), "negative final balance is exposed");
        assertEquals(1L, response.negativeBalanceCount(), "negative balance count is exposed");
    }

    private static void invalidRequestIsRejectedBeforeExecution() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        PointSpendApiHandler handler = new PointSpendApiHandler(new PointSpendScenarioExecutor(repository));

        PointSpendApiResponse response = handler.handle(new PointSpendApiRequest(0L, "DB_ROW_LOCK", -1, 0, 0, ""));

        assertEquals(400, response.statusCode(), "invalid request returns 400");
        assertEquals("memberId, spendAmount, and requestCount must be positive; initialBalance must be non-negative", response.message(), "validation message is explicit");
        assertEquals(0L, repository.count(), "invalid request is not executed");
    }

    private static void rabbitMqStrategyIsRejectedForPointSpend() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        PointSpendApiHandler handler = new PointSpendApiHandler(new PointSpendScenarioExecutor(repository));

        PointSpendApiResponse response = handler.handle(new PointSpendApiRequest(8203L, "RABBITMQ_DB_GUARD", 1000, 700, 2, ""));

        assertEquals(400, response.statusCode(), "RabbitMQ is not a Point Spend MVP strategy");
        assertEquals("strategy is not supported for Point Spend", response.message(), "unsupported strategy message is explicit");
        assertEquals(0L, repository.count(), "unsupported strategy is not executed");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
