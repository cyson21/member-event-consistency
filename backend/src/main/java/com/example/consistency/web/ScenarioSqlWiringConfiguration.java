package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignLockGateway;
import com.example.consistency.coupon.BatchExpirationService;
import com.example.consistency.coupon.BatchExpirationSqlWiring;
import com.example.consistency.coupon.CouponRedemptionService;
import com.example.consistency.coupon.CouponRedemptionSqlWiring;
import com.example.consistency.coupon.CouponCampaignService;
import com.example.consistency.coupon.CouponCampaignSqlWiring;
import com.example.consistency.coupon.SqlCouponQueueEventRecorder;
import com.example.consistency.persistence.JdbcSqlExecutor;
import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.point.PointSpendService;
import com.example.consistency.point.PointSpendSqlWiring;
import com.example.consistency.reward.FirstLoginRewardService;
import com.example.consistency.reward.RewardFollowUpRecorder;
import com.example.consistency.reward.SqlRewardFollowUpRecorder;
import com.example.consistency.reward.SqlRewardIssueRepository;
import com.example.consistency.reward.RewardLockGateway;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "member-event-consistency.sql.enabled", havingValue = "true", matchIfMissing = true)
public class ScenarioSqlWiringConfiguration {

    @Bean
    public SqlExecutor sqlExecutor(DataSource dataSource) {
        return new JdbcSqlExecutor(dataSource);
    }

    @Bean
    public RewardLockGateway rewardLockGateway(RedissonClient redissonClient) {
        return new RedisRewardLockGateway(redissonClient);
    }

    @Bean
    public CouponCampaignLockGateway couponCampaignLockGateway(RedissonClient redissonClient) {
        return new RedisCouponCampaignLockGateway(redissonClient);
    }

    @Bean
    public SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder(SqlExecutor sqlExecutor) {
        return new SqlRewardFollowUpRecorder(sqlExecutor);
    }

    @Bean
    public RewardFollowUpRecorder rewardFollowUpRecorder(
            SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder,
            ApplicationEventPublisher eventPublisher
    ) {
        return new SpringRewardFollowUpRecorder(sqlRewardFollowUpRecorder, eventPublisher);
    }

    @Bean
    public RewardFollowUpOutboxListener rewardFollowUpOutboxListener(SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder) {
        return new RewardFollowUpOutboxListener(sqlRewardFollowUpRecorder);
    }

    @Bean
    public FirstLoginRewardService firstLoginRewardService(
            SqlExecutor sqlExecutor,
            RewardLockGateway rewardLockGateway,
            RewardFollowUpRecorder rewardFollowUpRecorder
    ) {
        return new TransactionalFirstLoginRewardService(
                new SqlRewardIssueRepository(sqlExecutor),
                rewardFollowUpRecorder,
                rewardLockGateway
        );
    }

    @Bean
    public CouponCampaignService couponCampaignService(SqlExecutor sqlExecutor, CouponCampaignLockGateway couponCampaignLockGateway) {
        return CouponCampaignSqlWiring.service(sqlExecutor, couponCampaignLockGateway);
    }

    @Bean
    public SqlCouponQueueEventRecorder sqlCouponQueueEventRecorder(SqlExecutor sqlExecutor) {
        return new SqlCouponQueueEventRecorder(sqlExecutor);
    }

    @Bean
    public PointSpendService pointSpendService(SqlExecutor sqlExecutor) {
        return PointSpendSqlWiring.service(sqlExecutor);
    }

    @Bean
    public CouponRedemptionService couponRedemptionService(SqlExecutor sqlExecutor) {
        return CouponRedemptionSqlWiring.service(sqlExecutor);
    }

    @Bean
    public BatchExpirationService batchExpirationService(SqlExecutor sqlExecutor) {
        return BatchExpirationSqlWiring.service(sqlExecutor);
    }
}
