package com.example.consistency.web;

import com.example.consistency.api.ScenarioApiRouterFactory;
import com.example.consistency.api.ScenarioApiRouter;
import com.example.consistency.coupon.BatchExpirationApiHandler;
import com.example.consistency.coupon.BatchExpirationService;
import com.example.consistency.coupon.BatchExpirationServiceScenarioExecutor;
import com.example.consistency.coupon.CouponRedemptionApiHandler;
import com.example.consistency.coupon.CouponRedemptionService;
import com.example.consistency.coupon.CouponRedemptionServiceScenarioExecutor;
import com.example.consistency.coupon.CouponCampaignServiceScenarioExecutor;
import com.example.consistency.coupon.CouponCampaignService;
import com.example.consistency.coupon.SqlCouponQueueEventRecorder;
import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.point.PointSpendServiceScenarioExecutor;
import com.example.consistency.point.PointSpendService;
import com.example.consistency.reward.FirstLoginRewardServiceScenarioExecutor;
import com.example.consistency.reward.FirstLoginRewardService;
import com.example.consistency.scenario.ScenarioRunReportRepository;
import com.example.consistency.scenario.SqlScenarioRunReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "member-event-consistency.sql.enabled", havingValue = "true", matchIfMissing = true)
public class ScenarioRunConfiguration {

    @Bean
    public ScenarioRunReportRepository scenarioRunReportRepository(SqlExecutor sqlExecutor) {
        return new SqlScenarioRunReportRepository(sqlExecutor);
    }

    @Bean
    public RabbitMqCouponCampaignScenarioExecutor rabbitMqCouponCampaignScenarioExecutor(
            CouponCampaignService couponCampaignService,
            ScenarioRunReportRepository scenarioRunReportRepository,
            CouponCampaignRabbitMqPublisher publisher,
            CouponCampaignRabbitMqRunTracker tracker,
            SqlCouponQueueEventRecorder queueEvents
    ) {
        return new RabbitMqCouponCampaignScenarioExecutor(
                new CouponCampaignServiceScenarioExecutor(couponCampaignService, scenarioRunReportRepository),
                scenarioRunReportRepository,
                publisher,
                tracker,
                queueEvents
        );
    }

    @Bean
    public ScenarioApiRouter scenarioApiRouter(
            FirstLoginRewardService firstLoginRewardService,
            CouponCampaignService couponCampaignService,
            PointSpendService pointSpendService,
            ScenarioRunReportRepository scenarioRunReportRepository,
            RabbitMqCouponCampaignScenarioExecutor rabbitMqCouponCampaignScenarioExecutor
    ) {
        return ScenarioApiRouterFactory.executorBacked(
                new FirstLoginRewardServiceScenarioExecutor(firstLoginRewardService, scenarioRunReportRepository),
                rabbitMqCouponCampaignScenarioExecutor,
                new PointSpendServiceScenarioExecutor(pointSpendService, scenarioRunReportRepository)
        );
    }

    @Bean
    public CouponRedemptionApiHandler couponRedemptionApiHandler(
            CouponRedemptionService couponRedemptionService,
            ScenarioRunReportRepository scenarioRunReportRepository
    ) {
        return new CouponRedemptionApiHandler(
                new CouponRedemptionServiceScenarioExecutor(couponRedemptionService, scenarioRunReportRepository)
        );
    }

    @Bean
    public BatchExpirationApiHandler batchExpirationApiHandler(
            BatchExpirationService batchExpirationService,
            ScenarioRunReportRepository scenarioRunReportRepository
    ) {
        return new BatchExpirationApiHandler(
                new BatchExpirationServiceScenarioExecutor(batchExpirationService, scenarioRunReportRepository)
        );
    }
}
