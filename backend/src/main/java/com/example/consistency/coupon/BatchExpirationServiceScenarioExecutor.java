package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.StrategyType;

public final class BatchExpirationServiceScenarioExecutor implements BatchExpirationScenarioExecution {

    private final BatchExpirationService service;
    private final ScenarioRunReportRepository reports;

    public BatchExpirationServiceScenarioExecutor(
            BatchExpirationService service,
            ScenarioRunReportRepository reports
    ) {
        this.service = service;
        this.reports = reports;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long couponIssueId, BatchExpirationWinner winner) {
        if (strategy == StrategyType.NAIVE) {
            return reports.save(BatchExpirationScenarioRunner.run(strategy, couponIssueId, winner));
        }
        if (strategy != StrategyType.DB_GUARD) {
            throw new IllegalArgumentException("strategy is not supported for Batch Expiration vs User Use");
        }

        BatchExpirationDecision first = firstTransition(couponIssueId, winner);
        BatchExpirationDecision second = secondTransition(couponIssueId, winner);
        long usedCount = count(first.used(), second.used());
        long expiredCount = count(first.expired(), second.expired());
        long rejectedCount = count(first.rejected(), second.rejected());
        String rejectionReason = first.rejected() ? first.rejectionReason() : second.rejectionReason();

        return reports.save(BatchExpirationScenarioRunner.toReport(new BatchExpirationRunResult(
                strategy,
                couponIssueId,
                winner,
                2,
                2,
                usedCount,
                expiredCount,
                0,
                rejectedCount,
                rejectionReason
        )));
    }

    private BatchExpirationDecision firstTransition(long couponIssueId, BatchExpirationWinner winner) {
        return winner == BatchExpirationWinner.USER_USE
                ? service.use(couponIssueId)
                : service.expire(couponIssueId);
    }

    private BatchExpirationDecision secondTransition(long couponIssueId, BatchExpirationWinner winner) {
        return winner == BatchExpirationWinner.USER_USE
                ? service.expire(couponIssueId)
                : service.use(couponIssueId);
    }

    private long count(boolean first, boolean second) {
        return (first ? 1 : 0) + (second ? 1 : 0);
    }
}
