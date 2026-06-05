package com.example.consistency.api;

import com.example.consistency.coupon.CouponCampaignApiHandler;
import com.example.consistency.coupon.CouponCampaignLockGateway;
import com.example.consistency.coupon.CouponCampaignScenarioExecution;
import com.example.consistency.coupon.CouponCampaignService;
import com.example.consistency.coupon.CouponCampaignServiceScenarioExecutor;
import com.example.consistency.coupon.CouponCampaignSqlWiring;
import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.point.PointSpendApiHandler;
import com.example.consistency.point.PointSpendScenarioExecution;
import com.example.consistency.point.PointSpendService;
import com.example.consistency.point.PointSpendServiceScenarioExecutor;
import com.example.consistency.point.PointSpendSqlWiring;
import com.example.consistency.reward.FirstLoginRewardApiHandler;
import com.example.consistency.reward.FirstLoginRewardScenarioExecution;
import com.example.consistency.reward.FirstLoginRewardSqlWiring;
import com.example.consistency.reward.FirstLoginRewardService;
import com.example.consistency.reward.FirstLoginRewardServiceScenarioExecutor;
import com.example.consistency.reward.RewardLockGateway;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.SqlScenarioRunReportRepository;

public final class ScenarioApiRouterFactory {

    private ScenarioApiRouterFactory() {
    }

    public static ScenarioApiRouter serviceBacked(
            FirstLoginRewardService firstLoginRewardService,
            CouponCampaignService couponCampaignService,
            PointSpendService pointSpendService,
            ScenarioRunReportRepository reportRepository
    ) {
        return executorBacked(
                new FirstLoginRewardServiceScenarioExecutor(firstLoginRewardService, reportRepository),
                new CouponCampaignServiceScenarioExecutor(couponCampaignService, reportRepository),
                new PointSpendServiceScenarioExecutor(pointSpendService, reportRepository)
        );
    }

    public static ScenarioApiRouter executorBacked(
            FirstLoginRewardScenarioExecution firstLoginRewardExecution,
            CouponCampaignScenarioExecution couponCampaignExecution,
            PointSpendScenarioExecution pointSpendExecution
    ) {
        return new ScenarioApiRouter(
                new FirstLoginRewardApiHandler(
                        firstLoginRewardExecution
                ),
                new CouponCampaignApiHandler(
                        couponCampaignExecution
                ),
                new PointSpendApiHandler(
                        pointSpendExecution
                )
        );
    }

    public static ScenarioApiRouter sqlBacked(
            SqlExecutor sqlExecutor,
            RewardLockGateway rewardLockGateway,
            CouponCampaignLockGateway couponCampaignLockGateway
    ) {
        return serviceBacked(
                FirstLoginRewardSqlWiring.service(sqlExecutor, rewardLockGateway),
                CouponCampaignSqlWiring.service(sqlExecutor, couponCampaignLockGateway),
                PointSpendSqlWiring.service(sqlExecutor),
                new SqlScenarioRunReportRepository(sqlExecutor)
        );
    }
}
