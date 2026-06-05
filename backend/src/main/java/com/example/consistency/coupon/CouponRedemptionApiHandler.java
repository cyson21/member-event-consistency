package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionApiHandler {

    private final CouponRedemptionScenarioExecution executor;

    public CouponRedemptionApiHandler(CouponRedemptionScenarioExecution executor) {
        this.executor = executor;
    }

    public CouponRedemptionApiResponse handle(CouponRedemptionApiRequest request) {
        if (request.couponIssueId() <= 0 || request.requestCount() <= 0) {
            return CouponRedemptionApiResponse.badRequest("couponIssueId and requestCount must be positive");
        }

        StrategyType strategy;
        try {
            strategy = StrategyType.valueOf(request.strategy());
        } catch (IllegalArgumentException exception) {
            return CouponRedemptionApiResponse.badRequest("unknown strategy");
        }

        if (strategy == StrategyType.REDIS_LOCK_DB_GUARD
                || strategy == StrategyType.RABBITMQ_DB_GUARD
                || strategy == StrategyType.DB_ROW_LOCK
                || strategy == StrategyType.CONDITIONAL_UPDATE) {
            return CouponRedemptionApiResponse.badRequest("strategy is not supported for Coupon Redemption / Usage");
        }
        if (strategy == StrategyType.IDEMPOTENCY_REPLAY && hasMissingIdempotencyInput(request)) {
            return CouponRedemptionApiResponse.badRequest("idempotency inputs must be present");
        }

        ScenarioRunRecord record;
        if (strategy == StrategyType.IDEMPOTENCY_REPLAY) {
            record = executor.executeWithIdempotency(
                    strategy,
                    request.couponIssueId(),
                    request.idempotencyKey(),
                    request.firstRequestHash(),
                    request.retryRequestHash()
            );
        } else {
            record = executor.execute(strategy, request.couponIssueId(), request.requestCount());
        }

        return success(record);
    }

    private boolean hasMissingIdempotencyInput(CouponRedemptionApiRequest request) {
        return request.idempotencyKey().isBlank()
                || request.firstRequestHash().isBlank()
                || request.retryRequestHash().isBlank();
    }

    private CouponRedemptionApiResponse success(ScenarioRunRecord record) {
        ScenarioRunReport report = record.report();
        return new CouponRedemptionApiResponse(
                200,
                report.scenario().name(),
                report.strategy().name(),
                record.sequence(),
                report.invariant().passed(),
                report.metricValue(ScenarioMetricName.ACCEPTED_COUNT),
                report.metricValue(ScenarioMetricName.COMPLETED_COUNT),
                report.metricValue(ScenarioMetricName.COUPON_USED_COUNT),
                report.metricValue(ScenarioMetricName.DOUBLE_USE_COUNT),
                report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT),
                report.metricValue(ScenarioMetricName.REJECTED_COUNT),
                report.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT),
                report.metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT),
                report.invariant().message()
        );
    }
}
