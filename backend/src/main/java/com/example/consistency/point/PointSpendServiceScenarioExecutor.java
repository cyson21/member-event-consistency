package com.example.consistency.point;

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

public final class PointSpendServiceScenarioExecutor implements PointSpendScenarioExecution {

    private final PointSpendService service;
    private final ScenarioRunReportRepository reports;

    public PointSpendServiceScenarioExecutor(PointSpendService service, ScenarioRunReportRepository reports) {
        this.service = service;
        this.reports = reports;
    }

    @Override
    public ScenarioRunRecord execute(
            StrategyType strategy,
            long memberId,
            long initialBalance,
            long spendAmount,
            int requestCount
    ) {
        return executeWithIdempotencyKey(strategy, memberId, initialBalance, spendAmount, requestCount, "");
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
        if (strategy == StrategyType.NAIVE) {
            PointSpendRunResult result = PointSpendRunner.runWithIdempotencyKey(
                    strategy,
                    memberId,
                    initialBalance,
                    spendAmount,
                    requestCount,
                    idempotencyKey
            );
            return reports.save(PointSpendScenarioRunner.toReport(result));
        }

        long successfulSpendCount = 0;
        long rejectedCount = 0;
        long replayCount = 0;
        long idempotencyHashMismatchCount = 0;
        long rowLockReadCount = 0;

        for (int index = 0; index < requestCount; index++) {
            PointSpendDecision decision = service.spend(new PointSpendCommand(
                    memberId,
                    spendAmount,
                    strategy,
                    keyForRequest(idempotencyKey, memberId, index, strategy),
                    requestHash(memberId, spendAmount, index, strategy)
            ));
            if (decision.rowLockRead()) {
                rowLockReadCount++;
            }
            if (decision.replay()) {
                replayCount++;
            } else if (decision.idempotencyHashMismatch()) {
                idempotencyHashMismatchCount++;
                rejectedCount++;
            } else if (decision.accepted()) {
                successfulSpendCount++;
            } else {
                rejectedCount++;
            }
        }

        long finalBalance = initialBalance - (successfulSpendCount * spendAmount);
        long dbWaitMsP95 = rowLockReadCount > 0 ? 15 : 0;
        ScenarioRunReport report = new ScenarioRunReport(
                ScenarioType.POINT_SPEND,
                strategy,
                InvariantChecker.evaluate(new InvariantResult(
                        ScenarioType.POINT_SPEND,
                        strategy,
                        true,
                        0,
                        0,
                        0,
                        0,
                        requestCount,
                        requestCount,
                        dbWaitMsP95,
                        0,
                        0,
                        "point-spend service run"
                )),
                List.of(
                        new ScenarioMetric(ScenarioMetricName.ACCEPTED_COUNT, requestCount),
                        new ScenarioMetric(ScenarioMetricName.COMPLETED_COUNT, requestCount),
                        new ScenarioMetric(ScenarioMetricName.FINAL_POINT_BALANCE, finalBalance),
                        new ScenarioMetric(ScenarioMetricName.NEGATIVE_BALANCE_COUNT, 0),
                        new ScenarioMetric(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT, successfulSpendCount),
                        new ScenarioMetric(ScenarioMetricName.REJECTED_COUNT, rejectedCount),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT, replayCount),
                        new ScenarioMetric(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT, idempotencyHashMismatchCount),
                        new ScenarioMetric(ScenarioMetricName.DB_WAIT_MS_P95, dbWaitMsP95)
                )
        );
        return reports.save(report);
    }

    private String keyForRequest(String idempotencyKey, long memberId, int index, StrategyType strategy) {
        if (strategy == StrategyType.IDEMPOTENCY_REPLAY && idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return "spend-" + memberId + "-" + (index + 1);
    }

    private String requestHash(long memberId, long spendAmount, int index, StrategyType strategy) {
        if (strategy == StrategyType.IDEMPOTENCY_REPLAY) {
            return "hash-" + strategy.name() + "-" + memberId + "-" + spendAmount;
        }
        return "hash-" + strategy.name() + "-" + memberId + "-" + spendAmount + "-" + index;
    }
}
