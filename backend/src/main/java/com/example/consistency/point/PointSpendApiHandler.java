package com.example.consistency.point;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.StrategyType;

public final class PointSpendApiHandler {

    private final PointSpendScenarioExecution executor;

    public PointSpendApiHandler(PointSpendScenarioExecution executor) {
        this.executor = executor;
    }

    public PointSpendApiResponse handle(PointSpendApiRequest request) {
        if (request.memberId() <= 0
                || request.initialBalance() < 0
                || request.spendAmount() <= 0
                || request.requestCount() <= 0) {
            return PointSpendApiResponse.badRequest(
                    "memberId, spendAmount, and requestCount must be positive; initialBalance must be non-negative"
            );
        }

        StrategyType strategy;
        try {
            strategy = StrategyType.valueOf(request.strategy());
        } catch (IllegalArgumentException exception) {
            return PointSpendApiResponse.badRequest("unknown strategy");
        }

        if (strategy == StrategyType.DB_GUARD
                || strategy == StrategyType.REDIS_LOCK_DB_GUARD
                || strategy == StrategyType.RABBITMQ_DB_GUARD) {
            return PointSpendApiResponse.badRequest("strategy is not supported for Point Spend");
        }

        ScenarioRunRecord record;
        if (strategy == StrategyType.IDEMPOTENCY_REPLAY) {
            record = executor.executeWithIdempotencyKey(
                    strategy,
                    request.memberId(),
                    request.initialBalance(),
                    request.spendAmount(),
                    request.requestCount(),
                    request.idempotencyKey()
            );
        } else {
            record = executor.execute(
                    strategy,
                    request.memberId(),
                    request.initialBalance(),
                    request.spendAmount(),
                    request.requestCount()
            );
        }

        return success(record);
    }

    private PointSpendApiResponse success(ScenarioRunRecord record) {
        ScenarioRunReport report = record.report();
        return new PointSpendApiResponse(
                200,
                report.scenario().name(),
                report.strategy().name(),
                record.sequence(),
                report.invariant().passed(),
                report.metricValue(ScenarioMetricName.ACCEPTED_COUNT),
                report.metricValue(ScenarioMetricName.COMPLETED_COUNT),
                report.metricValue(ScenarioMetricName.FINAL_POINT_BALANCE),
                report.metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT),
                report.metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT),
                report.metricValue(ScenarioMetricName.REJECTED_COUNT),
                report.metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT),
                report.metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT),
                report.metricValue(ScenarioMetricName.DB_WAIT_MS_P95),
                report.invariant().message()
        );
    }
}
