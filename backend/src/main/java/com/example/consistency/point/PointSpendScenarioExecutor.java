package com.example.consistency.point;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.StrategyType;

public final class PointSpendScenarioExecutor implements PointSpendScenarioExecution {

    private final ScenarioRunReportRepository repository;

    public PointSpendScenarioExecutor(ScenarioRunReportRepository repository) {
        this.repository = repository;
    }

    @Override
    public ScenarioRunRecord execute(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount
    ) {
        ScenarioRunReport report = PointSpendScenarioRunner.run(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount
        );
        return repository.save(report);
    }

    @Override
    public ScenarioRunRecord executeWithIdempotencyKey(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey
    ) {
        PointSpendRunResult result = PointSpendRunner.runWithIdempotencyKey(
                strategy,
                memberId,
                initialBalance,
                spendAmount,
                requestCount,
                idempotencyKey
        );
        return repository.save(PointSpendScenarioRunner.toReport(result));
    }
}
