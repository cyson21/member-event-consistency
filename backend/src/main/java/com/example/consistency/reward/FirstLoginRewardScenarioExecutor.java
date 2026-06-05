package com.example.consistency.reward;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.StrategyType;

public final class FirstLoginRewardScenarioExecutor implements FirstLoginRewardScenarioExecution {

    private final ScenarioRunReportRepository reports;

    public FirstLoginRewardScenarioExecutor(ScenarioRunReportRepository reports) {
        this.reports = reports;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long memberId, int requestCount) {
        ScenarioRunReport report = FirstLoginRewardScenarioRunner.run(strategy, memberId, requestCount);
        return reports.save(report);
    }
}
