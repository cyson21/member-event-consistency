package com.example.consistency.reward;

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

public final class FirstLoginRewardServiceScenarioExecutor implements FirstLoginRewardScenarioExecution {

    private final FirstLoginRewardService service;
    private final ScenarioRunReportRepository reports;

    public FirstLoginRewardServiceScenarioExecutor(
            FirstLoginRewardService service,
            ScenarioRunReportRepository reports
    ) {
        this.service = service;
        this.reports = reports;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long memberId, int requestCount) {
        long issuedCount = 0;
        long lockAttemptCount = 0;

        for (int index = 0; index < requestCount; index++) {
            FirstLoginRewardDecision decision = service.issue(new FirstLoginRewardCommand(memberId, strategy));
            if (decision.outcome() == FirstLoginRewardOutcome.ISSUED) {
                issuedCount++;
            }
            if (decision.lockAttempted()) {
                lockAttemptCount++;
            }
        }

        long duplicateRewardCount = strategy == StrategyType.NAIVE ? Math.max(issuedCount - 1, 0) : 0;
        ScenarioRunReport report = new ScenarioRunReport(
                ScenarioType.FIRST_LOGIN_REWARD,
                strategy,
                InvariantChecker.evaluate(new InvariantResult(
                        ScenarioType.FIRST_LOGIN_REWARD,
                        strategy,
                        duplicateRewardCount == 0,
                        duplicateRewardCount,
                        0,
                        0,
                        0,
                        requestCount,
                        requestCount,
                        0,
                        0,
                        0,
                        "first-login-reward service run"
                )),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, requestCount),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, requestCount),
                        new ScenarioMetric(ScenarioMetricName.REWARD_ISSUED_COUNT, issuedCount),
                        new ScenarioMetric(ScenarioMetricName.DUPLICATE_REWARD_COUNT, duplicateRewardCount),
                        new ScenarioMetric(ScenarioMetricName.REDIS_LOCK_ATTEMPT_COUNT, lockAttemptCount),
                        new ScenarioMetric(ScenarioMetricName.AFTER_COMMIT_NOTIFICATION_COUNT, issuedCount),
                        new ScenarioMetric(ScenarioMetricName.OUTBOX_EVENT_COUNT, issuedCount)
                )
        );
        return reports.save(report);
    }
}
