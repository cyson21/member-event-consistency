package com.example.consistency.scenario;

public interface ScenarioRunReportRepository {

    ScenarioRunRecord save(ScenarioRunReport report);

    ScenarioRunReport findBySequence(long sequence);

    ScenarioRunSummary latestSummary(ScenarioType scenario, StrategyType strategy);

    long count();
}

