package com.example.consistency.scenario;

import java.util.List;

public record ScenarioRunReport(
        ScenarioType scenario,
        StrategyType strategy,
        InvariantResult invariant,
        List<ScenarioMetric> metrics
) {

    public long metricValue(ScenarioMetricName name) {
        return metrics.stream()
                .filter(metric -> metric.name() == name)
                .mapToLong(ScenarioMetric::value)
                .findFirst()
                .orElse(0);
    }
}

