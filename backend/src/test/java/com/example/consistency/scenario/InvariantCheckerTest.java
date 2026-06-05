package com.example.consistency.scenario;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvariantCheckerTest {

    @Test
    void firstLoginRewardFailsWhenDuplicateRewardExists() {
        InvariantResult result = InvariantChecker.evaluate(new InvariantResult(
                ScenarioType.FIRST_LOGIN_REWARD,
                StrategyType.DB_GUARD,
                true,
                1,
                0,
                0,
                0,
                10,
                10,
                0,
                0,
                0,
                "seed"
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("duplicate reward");
    }

    @Test
    void couponCampaignFailsWhenCapacityIsOverIssued() {
        InvariantResult result = InvariantChecker.evaluate(new InvariantResult(
                ScenarioType.COUPON_CAMPAIGN_ISSUE,
                StrategyType.DB_GUARD,
                true,
                0,
                1,
                0,
                0,
                20,
                20,
                0,
                0,
                0,
                "seed"
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("over issue");
    }

    @Test
    void pointSpendFailsWhenNegativeBalanceExists() {
        InvariantResult result = InvariantChecker.evaluate(new InvariantResult(
                ScenarioType.POINT_SPEND,
                StrategyType.DB_GUARD,
                true,
                0,
                0,
                1,
                0,
                30,
                30,
                0,
                0,
                0,
                "seed"
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("negative balance");
    }
}

