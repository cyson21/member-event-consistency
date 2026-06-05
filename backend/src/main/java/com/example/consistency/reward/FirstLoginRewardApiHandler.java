package com.example.consistency.reward;

import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardApiHandler {

    private final FirstLoginRewardScenarioExecution executor;

    public FirstLoginRewardApiHandler(FirstLoginRewardScenarioExecution executor) {
        this.executor = executor;
    }

    public FirstLoginRewardApiResponse handle(FirstLoginRewardApiRequest request) {
        if (request.memberId() <= 0 || request.requestCount() <= 0) {
            return FirstLoginRewardApiResponse.badRequest("memberId and requestCount must be positive");
        }

        StrategyType strategy;
        try {
            strategy = StrategyType.valueOf(request.strategy());
        } catch (IllegalArgumentException exception) {
            return FirstLoginRewardApiResponse.badRequest("unknown strategy");
        }

        if (strategy == StrategyType.RABBITMQ_DB_GUARD
                || strategy == StrategyType.DB_ROW_LOCK
                || strategy == StrategyType.CONDITIONAL_UPDATE
                || strategy == StrategyType.IDEMPOTENCY_REPLAY) {
            return FirstLoginRewardApiResponse.badRequest("strategy is not supported for First Login Reward");
        }

        ScenarioRunRecord record = executor.execute(strategy, request.memberId(), request.requestCount());
        return success(record);
    }

    private FirstLoginRewardApiResponse success(ScenarioRunRecord record) {
        ScenarioRunReport report = record.report();
        return new FirstLoginRewardApiResponse(
                200,
                report.scenario().name(),
                report.strategy().name(),
                record.sequence(),
                report.invariant().passed(),
                report.metricValue(ScenarioMetricName.ACCEPTED_COUNT),
                report.metricValue(ScenarioMetricName.COMPLETED_COUNT),
                report.metricValue(ScenarioMetricName.DUPLICATE_REWARD_COUNT),
                report.metricValue(ScenarioMetricName.REWARD_ISSUED_COUNT),
                report.metricValue(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT),
                report.metricValue(ScenarioMetricName.AFTER_COMMIT_NOTIFICATION_COUNT),
                report.metricValue(ScenarioMetricName.OUTBOX_EVENT_COUNT),
                report.invariant().message()
        );
    }
}
