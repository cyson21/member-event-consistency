package com.example.consistency.reward;

import com.example.consistency.scenario.InvariantChecker;
import com.example.consistency.scenario.ScenarioMetric;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.List;

public final class FirstLoginRewardScenarioRunner {

    private FirstLoginRewardScenarioRunner() {
    }

    public static ScenarioRunReport run(StrategyType strategy, long memberId, int requestCount) {
        FirstLoginRewardRunResult result = FirstLoginRewardRunner.run(strategy, memberId, requestCount);

        return new ScenarioRunReport(
                ScenarioType.FIRST_LOGIN_REWARD,
                strategy,
                InvariantChecker.evaluate(result.toInvariantResult()),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, result.requestedCount()),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, result.requestedCount()),
                        new ScenarioMetric(ScenarioMetricName.REWARD_ISSUED_COUNT, result.issuedCount()),
                        new ScenarioMetric(ScenarioMetricName.DUPLICATE_REWARD_COUNT, result.duplicateCount()),
                        new ScenarioMetric(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT, result.lockAttemptCount()),
                        new ScenarioMetric(ScenarioMetricName.AFTER_COMMIT_NOTIFICATION_COUNT, result.afterCommitNotificationCount()),
                        new ScenarioMetric(ScenarioMetricName.OUTBOX_EVENT_COUNT, result.outboxEventCount())
                )
        );
    }
}

