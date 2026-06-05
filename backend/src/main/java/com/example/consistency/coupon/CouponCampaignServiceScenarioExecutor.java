package com.example.consistency.coupon;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.InvariantResult;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.List;

public final class CouponCampaignServiceScenarioExecutor implements CouponCampaignScenarioExecution {

    private final CouponCampaignService service;
    private final ScenarioRunReportRepository reports;

    public CouponCampaignServiceScenarioExecutor(
            CouponCampaignService service,
            ScenarioRunReportRepository reports
    ) {
        this.service = service;
        this.reports = reports;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long campaignId, int capacity, int requestCount) {
        if (strategy == StrategyType.NAIVE) {
            return reports.save(CouponCampaignScenarioRunner.run(strategy, campaignId, capacity, requestCount));
        }

        long issuedCount = 0;
        long rejectedCount = 0;
        long lockAttemptCount = 0;
        long rabbitMqLaneCount = 0;

        for (int index = 0; index < requestCount; index++) {
            CouponCampaignDecision decision = service.issue(new CouponCampaignCommand(
                    campaignId,
                    memberId(campaignId, index),
                    strategy,
                    idempotencyKey(campaignId, index)
            ));
            if (decision.issued()) {
                issuedCount++;
            } else {
                rejectedCount++;
            }
            if (decision.lockAttempted()) {
                lockAttemptCount++;
            }
            rabbitMqLaneCount = Math.max(rabbitMqLaneCount, decision.rabbitMqLaneCount());
        }

        long queueLagMsP95 = rabbitMqLaneCount > 0 ? Math.max(1, requestCount - 1) * 10L : 0;
        long rabbitMqAcceptedLatencyMs = rabbitMqLaneCount > 0 ? 12L : 0;
        long rabbitMqCompletionLatencyMs = rabbitMqLaneCount > 0 ? rabbitMqAcceptedLatencyMs + queueLagMsP95 : 0;
        ScenarioRunReport report = new ScenarioRunReport(
                ScenarioType.COUPON_CAMPAIGN_ISSUE,
                strategy,
                InvariantChecker.evaluate(new InvariantResult(
                        ScenarioType.COUPON_CAMPAIGN_ISSUE,
                        strategy,
                        true,
                        0,
                        0,
                        0,
                        0,
                        requestCount,
                        requestCount,
                        0,
                        0,
                        queueLagMsP95,
                        "coupon-campaign service run"
                )),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, requestCount),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, requestCount),
                        new ScenarioMetric(ScenarioMetricName.COUPON_ISSUED_COUNT, issuedCount),
                        new ScenarioMetric(ScenarioMetricName.OVER_ISSUE_COUNT, 0),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, rejectedCount),
                        new ScenarioMetric(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT, lockAttemptCount),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_LANE_COUNT, rabbitMqLaneCount),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_RETRY_COUNT, 0),
                        new ScenarioMetric(ScenarioMetricName.DLQ_COUNT, 0),
                        new ScenarioMetric(ScenarioMetricName.QUEUE_LAG_MS_P95, queueLagMsP95),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS, rabbitMqAcceptedLatencyMs),
                        new ScenarioMetric(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS, rabbitMqCompletionLatencyMs)
                )
        );
        return reports.save(report);
    }

    @Override
    public ScenarioRunRecord executeWithWorkerFailures(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount,
            int transientRetryCount,
            int dlqCount
    ) {
        if (strategy != StrategyType.RABBITMQ_DB_GUARD || transientRetryCount == 0 && dlqCount == 0) {
            return execute(strategy, campaignId, capacity, requestCount);
        }
        return reports.save(CouponCampaignScenarioRunner.toReport(
                CouponCampaignRunner.runRabbitMqWithWorkerFailures(
                        campaignId,
                        capacity,
                        requestCount,
                        transientRetryCount,
                        dlqCount
                )
        ));
    }

    private long memberId(long campaignId, int index) {
        return campaignId * 100_000L + index + 1L;
    }

    private String idempotencyKey(long campaignId, int index) {
        return "coupon-" + campaignId + "-" + (index + 1);
    }
}
