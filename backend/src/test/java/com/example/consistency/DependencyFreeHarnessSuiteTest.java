package com.example.consistency;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

class DependencyFreeHarnessSuiteTest {

    private static final List<String> HARNESS_CLASSES = List.of(
            "com.example.consistency.api.ScenarioApiRouterFactoryTest",
            "com.example.consistency.api.ScenarioApiRouterTest",
            "com.example.consistency.coupon.BatchExpirationApiHandlerTest",
            "com.example.consistency.coupon.BatchExpirationScenarioRunnerTest",
            "com.example.consistency.coupon.BatchExpirationServiceScenarioExecutorTest",
            "com.example.consistency.coupon.BatchExpirationSqlWiringTest",
            "com.example.consistency.coupon.CouponCampaignApiHandlerTest",
            "com.example.consistency.coupon.CouponCampaignHotCampaignProbeTest",
            "com.example.consistency.coupon.CouponCampaignScenarioExecutorTest",
            "com.example.consistency.coupon.CouponCampaignScenarioRunnerTest",
            "com.example.consistency.coupon.CouponCampaignServiceScenarioExecutorTest",
            "com.example.consistency.coupon.CouponCampaignServiceTest",
            "com.example.consistency.coupon.CouponCampaignSqlWiringTest",
            "com.example.consistency.coupon.CouponRedemptionApiHandlerTest",
            "com.example.consistency.coupon.CouponRedemptionScenarioRunnerTest",
            "com.example.consistency.coupon.CouponRedemptionServiceScenarioExecutorTest",
            "com.example.consistency.coupon.CouponRedemptionSqlWiringTest",
            "com.example.consistency.coupon.SqlBatchExpirationRepositoryTest",
            "com.example.consistency.coupon.SqlCouponCampaignRepositoryTest",
            "com.example.consistency.coupon.SqlCouponQueueEventRecorderTest",
            "com.example.consistency.coupon.SqlCouponRedemptionRepositoryTest",
            "com.example.consistency.lock.SqlLockAttemptRecorderTest",
            "com.example.consistency.persistence.JdbcSqlExecutorTest",
            "com.example.consistency.point.PointSpendApiHandlerTest",
            "com.example.consistency.point.PointSpendConcurrentProbeTest",
            "com.example.consistency.point.PointSpendScenarioExecutorTest",
            "com.example.consistency.point.PointSpendScenarioRunnerTest",
            "com.example.consistency.point.PointSpendServiceScenarioExecutorTest",
            "com.example.consistency.point.PointSpendServiceTest",
            "com.example.consistency.point.PointSpendSqlWiringTest",
            "com.example.consistency.point.SqlPointSpendRepositoryTest",
            "com.example.consistency.reward.FirstLoginRewardApiHandlerTest",
            "com.example.consistency.reward.FirstLoginRewardConcurrentProbeTest",
            "com.example.consistency.reward.FirstLoginRewardHarnessTest",
            "com.example.consistency.reward.FirstLoginRewardScenarioExecutorTest",
            "com.example.consistency.reward.FirstLoginRewardScenarioRunnerTest",
            "com.example.consistency.reward.FirstLoginRewardServiceScenarioExecutorTest",
            "com.example.consistency.reward.FirstLoginRewardServiceTest",
            "com.example.consistency.reward.FirstLoginRewardSqlWiringTest",
            "com.example.consistency.reward.SqlRewardFollowUpRecorderTest",
            "com.example.consistency.reward.SqlRewardIssueRepositoryTest",
            "com.example.consistency.reward.SqlRewardOutboxPublisherTest",
            "com.example.consistency.runner.MvpSmokeSuiteRunnerTest",
            "com.example.consistency.runner.ScenarioCliTest",
            "com.example.consistency.scenario.ScenarioRunReportRepositoryTest",
            "com.example.consistency.scenario.SqlScenarioRunReportRepositoryTest"
    );

    @TestFactory
    List<DynamicTest> dependencyFreeHarnesses() {
        return HARNESS_CLASSES.stream()
                .map(className -> DynamicTest.dynamicTest(className, () -> invokeMain(className)))
                .toList();
    }

    private static void invokeMain(String className) throws Exception {
        try {
            Class<?> harnessClass = Class.forName(className);
            harnessClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }
}
