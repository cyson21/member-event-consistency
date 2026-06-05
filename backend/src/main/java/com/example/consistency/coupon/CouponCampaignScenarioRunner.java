package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.List;

public final class CouponCampaignScenarioRunner {

    private CouponCampaignScenarioRunner() {
    }

    public static ScenarioRunReport run(StrategyType strategy, long campaignId, int capacity, int requestCount) {
        return toReport(CouponCampaignRunner.run(strategy, campaignId, capacity, requestCount));
    }

    public static ScenarioRunReport toReport(CouponCampaignRunResult result) {
        return new ScenarioRunReport(
                ScenarioType.COUPON_CAMPAIGN_ISSUE,
                result.strategy(),
                InvariantChecker.evaluate(result.toInvariantResult()),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, result.acceptedCount()),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, result.completedCount()),
                        new ScenarioMetric(ScenarioMetricName.COUPON_ISSUED_COUNT, result.issuedCount()),
                        new ScenarioMetric(ScenarioMetricName.OVER_ISSUE_COUNT, result.overIssueCount()),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, result.rejectedCount()),
                        new ScenarioMetric(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT, result.redisLockAttemptCount()),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_LANE_COUNT, result.rabbitMqLaneCount()),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_RETRY_COUNT, result.queueRetryCount()),
                        new ScenarioMetric(ScenarioMetricName.DLQ_COUNT, result.dlqCount()),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_LAG_MS_P95, result.queueLagMsP95()),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS, result.rabbitMqAcceptedLatencyMs()),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS, result.rabbitMqCompletionLatencyMs())
                )
        );
    }
}
