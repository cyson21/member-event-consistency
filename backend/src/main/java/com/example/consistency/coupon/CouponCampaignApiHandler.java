package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignApiHandler {

    private final CouponCampaignScenarioExecution executor;

    public CouponCampaignApiHandler(CouponCampaignScenarioExecution executor) {
        this.executor = executor;
    }

    public CouponCampaignApiResponse handle(CouponCampaignApiRequest request) {
        if (request.campaignId() <= 0
                || request.capacity() < 0
                || request.requestCount() <= 0
                || request.transientRetryCount() < 0
                || request.dlqCount() < 0
                || request.dlqCount() > request.requestCount()) {
            return CouponCampaignApiResponse.badRequest(
                    "campaignId and requestCount must be positive, capacity must be non-negative"
            );
        }

        StrategyType strategy;
        try {
            strategy = StrategyType.valueOf(request.strategy());
        } catch (IllegalArgumentException exception) {
            return CouponCampaignApiResponse.badRequest("unknown strategy");
        }

        if (strategy == StrategyType.DB_ROW_LOCK
                || strategy == StrategyType.CONDITIONAL_UPDATE
                || strategy == StrategyType.IDEMPOTENCY_REPLAY) {
            return CouponCampaignApiResponse.badRequest("strategy is not supported for Coupon Campaign Issue");
        }

        ScenarioRunRecord record = executor.executeWithWorkerFailures(
                strategy,
                request.campaignId(),
                request.capacity(),
                request.requestCount(),
                request.transientRetryCount(),
                request.dlqCount()
        );
        return success(record);
    }

    private CouponCampaignApiResponse success(ScenarioRunRecord record) {
        ScenarioRunReport report = record.report();
        int statusCode = report.strategy().isAsyncAccepted() ? 202 : 200;
        return new CouponCampaignApiResponse(
                statusCode,
                report.scenario().name(),
                report.strategy().name(),
                record.sequence(),
                report.invariant().passed(),
                report.metricValue(ScenarioMetricName.ACCEPTED_COUNT),
                report.metricValue(ScenarioMetricName.COMPLETED_COUNT),
                report.metricValue(ScenarioMetricName.COUPON_ISSUED_COUNT),
                report.metricValue(ScenarioMetricName.OVER_ISSUE_COUNT),
                report.metricValue(ScenarioMetricName.REJECTED_COUNT),
                report.metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT),
                report.metricValue(ScenarioMetricName.RABBITMQ_LANE_COUNT),
                report.metricValue(ScenarioMetricName.QUEUE_RETRY_COUNT),
                report.metricValue(ScenarioMetricName.DLQ_COUNT),
                report.metricValue(ScenarioMetricName.QUEUE_LAG_MS_P95),
                report.metricValue(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS),
                report.metricValue(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS),
                report.invariant().message()
        );
    }
}
