package com.example.consistency.coupon;

import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioRunReport;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignScenarioExecutor implements CouponCampaignScenarioExecution {

    private final ScenarioRunReportRepository repository;

    public CouponCampaignScenarioExecutor(ScenarioRunReportRepository repository) {
        this.repository = repository;
    }

    @Override
    public ScenarioRunRecord execute(StrategyType strategy, long campaignId, int capacity, int requestCount) {
        ScenarioRunReport report = CouponCampaignScenarioRunner.run(strategy, campaignId, capacity, requestCount);
        return repository.save(report);
    }

    @Override
    public ScenarioRunRecord executeWithWorkerFailures(
            StrategyType strategy,
            long campaignId,
            int capacity,
            int requestCount,
            int transientRetryCount,
            int dlqCount
    ) {
        if (strategy != StrategyType.RABBITMQ_DB_GUARD || transientRetryCount == 0 && dlqCount == 0) {
            return execute(strategy, campaignId, capacity, requestCount);
        }
        ScenarioRunReport report = CouponCampaignScenarioRunner.toReport(
                CouponCampaignRunner.runRabbitMqWithWorkerFailures(
                        campaignId,
                        capacity,
                        requestCount,
                        transientRetryCount,
                        dlqCount
                )
        );
        return repository.save(report);
    }
}
