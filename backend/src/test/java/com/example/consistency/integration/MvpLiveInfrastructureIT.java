package com.example.consistency.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvpLiveInfrastructureIT {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:3.13-management-alpine")
            .withExposedPorts(5672);

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Test
    void rabbitMqCouponCampaignRouteRunsOnlyWhenLiveDependenciesAreHealthy() {
        ResponseEntity<Map<String, Object>> health = rest.exchange(
                url("/actuator/health"),
                HttpMethod.GET,
                null,
                MAP_RESPONSE
        );

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).containsEntry("status", "UP");

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                url("/api/scenarios/coupon-campaign-issue/runs"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "campaignId", 94004,
                        "strategy", "RABBITMQ_DB_GUARD",
                        "capacity", 3,
                        "requestCount", 8,
                        "transientRetryCount", 1,
                        "dlqCount", 0
                )),
                MAP_RESPONSE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("scenario", "COUPON_CAMPAIGN_ISSUE");
        assertThat(response.getBody()).containsEntry("strategy", "RABBITMQ_DB_GUARD");
        assertThat(response.getBody()).containsEntry("invariantPassed", true);
        assertThat(number(response, "acceptedCount")).isEqualTo(8L);
        assertThat(number(response, "completedCount")).isEqualTo(8L);
        assertThat(number(response, "couponIssuedCount")).isEqualTo(3L);
        assertThat(number(response, "overIssueCount")).isZero();
        assertThat(number(response, "rejectedCount")).isEqualTo(5L);
        assertThat(number(response, "rabbitMqLaneCount")).isEqualTo(1L);
        assertThat(number(response, "queueRetryCount")).isEqualTo(1L);
        assertThat(number(response, "dlqCount")).isZero();
        assertThat(number(response, "queueLagMsP95")).isGreaterThanOrEqualTo(0L);
        assertThat(number(response, "rabbitMqAcceptedLatencyMs")).isGreaterThanOrEqualTo(0L);
        assertThat(number(response, "rabbitMqCompletionLatencyMs"))
                .isGreaterThanOrEqualTo(number(response, "rabbitMqAcceptedLatencyMs"));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static long number(ResponseEntity<Map<String, Object>> response, String field) {
        Object value = response.getBody().get(field);
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }
}
