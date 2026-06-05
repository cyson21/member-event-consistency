package com.example.consistency.coupon;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

public final class CouponCampaignScenarioExecutorTest {

    public static void main(String[] args) {
        executesAndPersistsNaiveOverIssueEvidence();
        executesAndPersistsRabbitMqSingleLaneEvidence();
    }

    private static void executesAndPersistsNaiveOverIssueEvidence() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        CouponCampaignScenarioExecutor executor = new CouponCampaignScenarioExecutor(repository);

        ScenarioRunRecord record = executor.execute(StrategyType.NAIVE, 9101L, 3, 8);

        assertEquals(1L, record.sequence(), "first saved run gets sequence 1");
        assertEquals(false, record.report().invariant().passed(), "naive invariant failure is persisted");
        assertEquals(5L, record.report().metricValue(ScenarioMetricName.OVER_ISSUE_COUNT), "over issue count is persisted");
        assertEquals(record.report(), repository.findBySequence(record.sequence()), "report is saved in repository");
    }

    private static void executesAndPersistsRabbitMqSingleLaneEvidence() {
        InMemoryScenarioRunReportRepository repository = new InMemoryScenarioRunReportRepository();
        CouponCampaignScenarioExecutor executor = new CouponCampaignScenarioExecutor(repository);

        ScenarioRunRecord record = executor.execute(StrategyType.RABBITMQ_DB_GUARD, 9102L, 3, 8);

        assertEquals(ScenarioType.COUPON_CAMPAIGN_ISSUE, record.report().scenario(), "coupon scenario is persisted");
        assertEquals(true, record.report().invariant().passed(), "RabbitMQ guarded invariant pass is persisted");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.RABBITMQ_LANE_COUNT), "single-lane evidence is persisted");
        assertEquals(8L, record.report().metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count is persisted");
        assertEquals(8L, record.report().metricValue(ScenarioMetricName.COMPLETED_COUNT), "completed count is persisted separately");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
