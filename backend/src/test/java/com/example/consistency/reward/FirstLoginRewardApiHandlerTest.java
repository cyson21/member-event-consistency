package com.example.consistency.reward;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardApiHandlerTest {

    public static void main(String[] args) {
        validDbGuardRequestReturnsSynchronousRunResult();
        naiveRequestReturnsBrokenInvariantEvidence();
        redisGuardRequestReturnsLockAttemptMetric();
        invalidRequestIsRejectedBeforeExecution();
        unsupportedRabbitStrategyIsRejectedForFirstLoginReward();
    }

    private static void validDbGuardRequestReturnsSynchronousRunResult() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(new FirstLoginRewardScenarioExecutor(repository));

        FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(6001L, "DB_GUARD", 5));

        assertEquals(200, response.statusCode(), "DB guard request is accepted synchronously");
        assertEquals(1L, response.runSequence(), "run sequence is exposed");
        assertEquals(true, response.invariantPassed(), "DB guard passes invariant");
        assertEquals(5L, response.acceptedCount(), "accepted count is exposed");
        assertEquals(5L, response.completedCount(), "completed count is exposed separately");
        assertEquals(0L, response.duplicateRewardCount(), "duplicate count is zero");
        assertEquals(1L, response.rewardIssuedCount(), "one reward is issued");
        assertEquals(1L, repository.count(), "handler persists the report");
    }

    private static void naiveRequestReturnsBrokenInvariantEvidence() {
        FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(
                new FirstLoginRewardScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );

        FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(6002L, "NAIVE", 5));

        assertEquals(200, response.statusCode(), "naive request still executes");
        assertEquals(false, response.invariantPassed(), "naive response shows broken invariant");
        assertEquals(4L, response.duplicateRewardCount(), "duplicate count is exposed");
    }

    private static void redisGuardRequestReturnsLockAttemptMetric() {
        FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(
                new FirstLoginRewardScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );

        FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(6003L, "REDIS_LOCK_DB_GUARD", 5));

        assertEquals(200, response.statusCode(), "Redis lock plus DB guard request executes");
        assertEquals(true, response.invariantPassed(), "Redis lock plus DB guard passes invariant");
        assertEquals(5L, response.redisLockAttemptCount(), "lock attempt count is exposed");
        assertEquals(0L, response.duplicateRewardCount(), "DB guard remains duplicate defense");
    }

    private static void invalidRequestIsRejectedBeforeExecution() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(new FirstLoginRewardScenarioExecutor(repository));

        FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(0L, "DB_GUARD", 0));

        assertEquals(400, response.statusCode(), "invalid request returns 400");
        assertEquals("memberId and requestCount must be positive", response.message(), "validation message is explicit");
        assertEquals(0L, repository.count(), "invalid request is not executed");
    }

    private static void unsupportedRabbitStrategyIsRejectedForFirstLoginReward() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(new FirstLoginRewardScenarioExecutor(repository));

        FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(6004L, "RABBITMQ_DB_GUARD", 5));

        assertEquals(400, response.statusCode(), "RabbitMQ is not a First Login Reward MVP strategy");
        assertEquals("strategy is not supported for First Login Reward", response.message(), "unsupported strategy message is explicit");
        assertEquals(0L, repository.count(), "unsupported strategy is not executed");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}

