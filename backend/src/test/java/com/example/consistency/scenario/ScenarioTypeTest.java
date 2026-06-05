package com.example.consistency.scenario;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioTypeTest {

    @Test
    void definesMvpScenariosAndSelectedPhase2ScenarioOnly() {
        assertThat(ScenarioType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "FIRST_LOGIN_REWARD",
                        "COUPON_CAMPAIGN_ISSUE",
                        "POINT_SPEND",
                        "COUPON_REDEMPTION",
                        "BATCH_EXPIRATION"
                );
    }

    @Test
    void rejectsRabbitmqAsPointSpendMvpCoreStrategy() {
        assertThat(ScenarioRunService.isSupported(ScenarioType.POINT_SPEND, StrategyType.RABBITMQ_DB_GUARD))
                .isFalse();
    }

    @Test
    void separatesAsyncAcceptedSemanticsFromFinalCompletion() {
        assertThat(StrategyType.DB_GUARD.isAsyncAccepted()).isFalse();
        assertThat(StrategyType.REDIS_LOCK_DB_GUARD.isAsyncAccepted()).isFalse();
        assertThat(StrategyType.RABBITMQ_DB_GUARD.isAsyncAccepted()).isTrue();
    }
}
