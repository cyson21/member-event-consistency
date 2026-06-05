package com.example.consistency.runner;

import com.example.consistency.coupon.CouponCampaignApiHandler;
import com.example.consistency.coupon.CouponCampaignApiRequest;
import com.example.consistency.coupon.CouponCampaignApiResponse;
import com.example.consistency.coupon.CouponCampaignScenarioExecutor;
import com.example.consistency.point.PointSpendApiHandler;
import com.example.consistency.point.PointSpendApiRequest;
import com.example.consistency.point.PointSpendApiResponse;
import com.example.consistency.point.PointSpendScenarioExecutor;
import com.example.consistency.reward.FirstLoginRewardApiHandler;
import com.example.consistency.reward.FirstLoginRewardApiRequest;
import com.example.consistency.reward.FirstLoginRewardApiResponse;
import com.example.consistency.reward.FirstLoginRewardScenarioExecutor;
import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.List;

public final class MvpSmokeSuiteRunner {

    public MvpSmokeSuiteResult run() {
        List<MvpSmokeSuiteEntry> entries = new ArrayList<>();
        addFirstLoginReward(entries);
        addCouponCampaign(entries);
        addPointSpend(entries);
        return new MvpSmokeSuiteResult(entries);
    }

    private void addFirstLoginReward(List<MvpSmokeSuiteEntry> entries) {
        for (StrategyType strategy : List.of(
                StrategyType.NAIVE,
                StrategyType.DB_GUARD,
                StrategyType.REDIS_LOCK_DB_GUARD
        )) {
            FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(
                    new FirstLoginRewardScenarioExecutor(new InMemoryScenarioRunReportRepository())
            );
            FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(
                    93001,
                    strategy.name(),
                    5
            ));
            entries.add(new MvpSmokeSuiteEntry(
                    ScenarioType.FIRST_LOGIN_REWARD,
                    strategy,
                    response.statusCode(),
                    response.invariantPassed(),
                    response.acceptedCount(),
                    response.completedCount(),
                    "issued=" + response.rewardIssuedCount() + ", duplicate=" + response.duplicateRewardCount()
            ));
        }
    }

    private void addCouponCampaign(List<MvpSmokeSuiteEntry> entries) {
        for (StrategyType strategy : List.of(
                StrategyType.NAIVE,
                StrategyType.DB_GUARD,
                StrategyType.REDIS_LOCK_DB_GUARD,
                StrategyType.RABBITMQ_DB_GUARD
        )) {
            CouponCampaignApiHandler handler = new CouponCampaignApiHandler(
                    new CouponCampaignScenarioExecutor(new InMemoryScenarioRunReportRepository())
            );
            CouponCampaignApiResponse response = handler.handle(new CouponCampaignApiRequest(
                    94001,
                    strategy.name(),
                    3,
                    8
            ));
            entries.add(new MvpSmokeSuiteEntry(
                    ScenarioType.COUPON_CAMPAIGN_ISSUE,
                    strategy,
                    response.statusCode(),
                    response.invariantPassed(),
                    response.acceptedCount(),
                    response.completedCount(),
                    "issued=" + response.couponIssuedCount()
                            + ", overIssue=" + response.overIssueCount()
                            + ", lane=" + response.rabbitMqLaneCount()
            ));
        }
    }

    private void addPointSpend(List<MvpSmokeSuiteEntry> entries) {
        for (StrategyType strategy : List.of(
                StrategyType.NAIVE,
                StrategyType.DB_ROW_LOCK,
                StrategyType.CONDITIONAL_UPDATE,
                StrategyType.IDEMPOTENCY_REPLAY
        )) {
            PointSpendApiHandler handler = new PointSpendApiHandler(
                    new PointSpendScenarioExecutor(new InMemoryScenarioRunReportRepository())
            );
            PointSpendApiRequest request = new PointSpendApiRequest(
                    95001,
                    strategy.name(),
                    1000,
                    700,
                    2,
                    strategy == StrategyType.IDEMPOTENCY_REPLAY ? "spend-95001-001" : ""
            );
            PointSpendApiResponse response = handler.handle(request);
            entries.add(new MvpSmokeSuiteEntry(
                    ScenarioType.POINT_SPEND,
                    strategy,
                    response.statusCode(),
                    response.invariantPassed(),
                    response.acceptedCount(),
                    response.completedCount(),
                    "balance=" + response.finalPointBalance()
                            + ", negative=" + response.negativeBalanceCount()
                            + ", replay=" + response.idempotencyReplayCount()
            ));
        }
    }
}

