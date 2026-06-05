package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignScenarioRunnerTest {

    public static void main(String[] args) {
        naiveScenarioReportShowsOverIssueInvariantFailure();
        dbGuardScenarioReportKeepsIssuedCountAtCapacity();
        redisLockScenarioReportIncludesCampaignLockAttempts();
        rabbitMqScenarioReportSeparatesAcceptedAndCompletedCounts();
        rabbitMqScenarioReportIncludesRetryAndDlqTerminalEvidence();
    }

    private static void naiveScenarioReportShowsOverIssueInvariantFailure() {
        ScenarioRunReport report = CouponCampaignScenarioRunner.run(StrategyType.NAIVE, 9001L, 3, 8);

        assertEquals(ScenarioType.COUPON_CAMPAIGN_ISSUE, report.scenario(), "scenario is fixed");
        assertEquals(StrategyType.NAIVE, report.strategy(), "strategy is captured");
        assertEquals(false, report.invariant().passed(), "naive campaign path must fail invariant");
        assertEquals(8L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count is recorded");
        assertEquals(8L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "sync completion count equals accepted count");
        assertEquals(8L, report.metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "naive path over-issues coupons");
        assertEquals(5L, report.metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "over issue count is recorded");
    }

    private static void dbGuardScenarioReportKeepsIssuedCountAtCapacity() {
        ScenarioRunReport report = CouponCampaignScenarioRunner.run(StrategyType.DB_GUARD, 9002L, 3, 8);

        assertEquals(true, report.invariant().passed(), "DB guard scenario must pass invariant");
        assertEquals(3L, report.metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "issued count stops at capacity");
        assertEquals(0L, report.metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "over issue count stays zero");
        assertEquals(5L, report.metricValue(ScenarioMetricName.REJECTED_COUNT), "capacity rejections are recorded");
    }

    private static void redisLockScenarioReportIncludesCampaignLockAttempts() {
        CouponCampaignRunResult result = CouponCampaignRunner.run(StrategyType.REDIS_LOCK_DB_GUARD, 9003L, 3, 8);
        ScenarioRunReport report = CouponCampaignScenarioRunner.toReport(result);

        assertEquals(true, report.invariant().passed(), "Redis lock plus DB guard scenario must pass invariant");
        assertEquals(8L, report.metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT), "lock attempts are recorded");
        assertEquals("lock:coupon-campaign:9003", result.lockKey(), "lock scope is campaign-specific");
        assertEquals(0L, report.metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "DB guard remains capacity defense");
    }

    private static void rabbitMqScenarioReportSeparatesAcceptedAndCompletedCounts() {
        ScenarioRunReport report = CouponCampaignScenarioRunner.run(StrategyType.RABBITMQ_DB_GUARD, 9004L, 3, 8);

        assertEquals(true, report.invariant().passed(), "RabbitMQ single-lane plus DB guard scenario must pass invariant");
        assertEquals(8L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "all commands are accepted first");
        assertEquals(8L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "all accepted commands are later processed");
        assertEquals(3L, report.metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "issued count stops at capacity");
        assertEquals(1L, report.metricValue(ScenarioMetricName.RABBITMQ_LANE_COUNT), "single-lane strategy is explicit");
        assertEquals(true, report.metricValue(ScenarioMetricName.QUEUE_LAG_MS_P95) > 0, "queue lag is recorded separately");
        assertEquals(true, report.metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS) > 0, "accepted latency is recorded");
        assertEquals(true,
                report.metricValue(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS)
                        > report.metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS),
                "final completion latency is separate from accepted latency");
    }

    private static void rabbitMqScenarioReportIncludesRetryAndDlqTerminalEvidence() {
        CouponCampaignRunResult result = CouponCampaignRunner.runRabbitMqWithWorkerFailures(9005L, 3, 8, 2, 1);
        ScenarioRunReport report = CouponCampaignScenarioRunner.toReport(result);

        assertEquals(true, report.invariant().passed(), "RabbitMQ retry and DLQ path must keep capacity invariant");
        assertEquals(8L, report.metricValue(ScenarioMetricName.ACCEPTED_COUNT), "all commands are accepted before worker processing");
        assertEquals(8L, report.metricValue(ScenarioMetricName.COMPLETED_COUNT), "all accepted commands reach a terminal worker outcome");
        assertEquals(3L, report.metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT), "successful issue count still stops at capacity");
        assertEquals(1L, report.metricValue(ScenarioMetricName.RABBITMQ_LANE_COUNT), "single-lane strategy remains explicit");
        assertEquals(2L, report.metricValue(ScenarioMetricName.QUEUE_RETRY_COUNT), "transient retry count is recorded");
        assertEquals(1L, report.metricValue(ScenarioMetricName.DLQ_COUNT), "DLQ count is recorded");
        assertEquals(true,
                report.metricValue(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS)
                        > report.metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS),
                "retry and DLQ evidence still separates accept and completion latency");
        assertEquals(0L, report.metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "DLQ handling does not hide over-issue");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
