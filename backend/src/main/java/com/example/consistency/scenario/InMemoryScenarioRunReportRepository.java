package com.example.consistency.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InMemoryScenarioRunReportRepository implements ScenarioRunReportRepository {

    private final List<ScenarioRunRecord> records = new ArrayList<>();

    @Override
    public synchronized ScenarioRunRecord save(ScenarioRunReport report) {
        ScenarioRunRecord record = new ScenarioRunRecord(UUID.randomUUID(), records.size() + 1L, report);
        records.add(record);
        return record;
    }

    @Override
    public synchronized ScenarioRunReport findBySequence(long sequence) {
        return records.stream()
                .filter(record -> record.sequence() == sequence)
                .map(ScenarioRunRecord::report)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Scenario run not found: " + sequence));
    }

    @Override
    public synchronized ScenarioRunSummary latestSummary(ScenarioType scenario, StrategyType strategy) {
        for (int index = records.size() - 1; index >= 0; index--) {
            ScenarioRunRecord record = records.get(index);
            ScenarioRunReport report = record.report();
            if (report.scenario() == scenario && report.strategy() == strategy) {
                return new ScenarioRunSummary(
                        record.sequence(),
                        scenario,
                        strategy,
                        report.invariant().passed(),
                        report
                );
            }
        }
        throw new IllegalArgumentException("Scenario run not found: " + scenario + " / " + strategy);
    }

    @Override
    public synchronized long count() {
        return records.size();
    }
}
