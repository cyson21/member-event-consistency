package com.example.consistency.scenario;

public record ScenarioRunSummary(
        long sequence,
        ScenarioType scenario,
        StrategyType strategy,
        boolean invariantPassed,
        ScenarioRunReport report
) {

    public long metricValue(ScenarioMetricName name) {
        return report.metricValue(name);
    }
}

