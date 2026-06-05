package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.StrategyType;

public final class BatchExpirationApiHandler {

    private final BatchExpirationScenarioExecution executor;

    public BatchExpirationApiHandler(BatchExpirationScenarioExecution executor) {
        this.executor = executor;
    }

    public BatchExpirationApiResponse handle(BatchExpirationApiRequest request) {
        if (request.couponIssueId() <= 0) {
            return BatchExpirationApiResponse.badRequest("couponIssueId must be positive");
        }

        StrategyType strategy;
        try {
            strategy = StrategyType.valueOf(request.strategy());
        } catch (IllegalArgumentException exception) {
            return BatchExpirationApiResponse.badRequest("unknown strategy");
        }

        if (strategy != StrategyType.NAIVE && strategy != StrategyType.DB_GUARD) {
            return BatchExpirationApiResponse.badRequest("strategy is not supported for Batch Expiration vs User Use");
        }

        BatchExpirationWinner winner;
        try {
            winner = BatchExpirationWinner.valueOf(request.winner());
        } catch (IllegalArgumentException exception) {
            return BatchExpirationApiResponse.badRequest("unknown winner");
        }

        return success(executor.execute(strategy, request.couponIssueId(), winner));
    }

    private BatchExpirationApiResponse success(ScenarioRunRecord record) {
        ScenarioRunReport report = record.report();
        String rejectionReason = report.metricValue(ScenarioMetricName.REJECTED_COUNT) > 0
                ? report.invariant().message()
                : "";
        return new BatchExpirationApiResponse(
                200,
                report.scenario().name(),
                report.strategy().name(),
                record.sequence(),
                report.invariant().passed(),
                report.metricValue(ScenarioMetricName.ACCEPTED_COUNT),
                report.metricValue(ScenarioMetricName.COMPLETED_COUNT),
                report.metricValue(ScenarioMetricName.COUPON_USED_COUNT),
                report.metricValue(ScenarioMetricName.COUPON_EXPIRED_COUNT),
                report.metricValue(ScenarioMetricName.TERMINAL_STATE_CONFLICT_COUNT),
                report.metricValue(ScenarioMetricName.REJECTED_COUNT),
                rejectionReason,
                report.invariant().message()
        );
    }
}
