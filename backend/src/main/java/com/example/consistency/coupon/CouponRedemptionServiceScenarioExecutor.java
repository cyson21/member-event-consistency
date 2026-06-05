package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.StrategyType;

public final class CouponRedemptionServiceScenarioExecutor implements CouponRedemptionScenarioExecution {

    private final CouponRedemptionService service;
    private final ScenarioRunReportRepository reports;

    public CouponRedemptionServiceScenarioExecutor(
            CouponRedemptionService service,
            ScenarioRunReportRepository reports
    ) {
        this.service = service;
        this.reports = reports;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long couponIssueId, int requestCount) {
        if (strategy == StrategyType.NAIVE) {
            return reports.save(CouponRedemptionScenarioRunner.run(strategy, couponIssueId, requestCount));
        }

        long usedCount = 0;
        long rejectedCount = 0;
        for (int index = 0; index < requestCount; index++) {
            CouponRedemptionDecision decision = service.redeem(new CouponRedemptionCommand(
                    couponIssueId,
                    strategy,
                    "",
                    ""
            ));
            if (decision.used()) {
                usedCount++;
            }
            if (decision.rejected()) {
                rejectedCount++;
            }
        }
        return reports.save(CouponRedemptionScenarioRunner.toReport(new CouponRedemptionRunResult(
                strategy,
                couponIssueId,
                requestCount,
                requestCount,
                requestCount,
                usedCount,
                0,
                0,
                rejectedCount,
                0,
                0
        )));
    }

    @Override
    public ScenarioRunRecord executeWithIdempotency(
            StrategyType strategy,
            long couponIssueId,
            String idempotencyKey,
            String firstRequestHash,
            String retryRequestHash
    ) {
        CouponRedemptionDecision first = service.redeem(new CouponRedemptionCommand(
                couponIssueId,
                strategy,
                idempotencyKey,
                firstRequestHash
        ));
        CouponRedemptionDecision retry = service.redeem(new CouponRedemptionCommand(
                couponIssueId,
                strategy,
                idempotencyKey,
                retryRequestHash
        ));

        long usedCount = count(first.used(), retry.used());
        long rejectedCount = count(first.rejected(), retry.rejected());
        long replayCount = count(first.idempotencyReplay(), retry.idempotencyReplay());
        long mismatchCount = count(first.idempotencyHashMismatch(), retry.idempotencyHashMismatch());
        return reports.save(CouponRedemptionScenarioRunner.toReport(new CouponRedemptionRunResult(
                strategy,
                couponIssueId,
                2,
                2,
                2,
                usedCount,
                0,
                0,
                rejectedCount,
                replayCount,
                mismatchCount
        )));
    }

    private long count(boolean first, boolean second) {
        return (first ? 1 : 0) + (second ? 1 : 0);
    }
}
