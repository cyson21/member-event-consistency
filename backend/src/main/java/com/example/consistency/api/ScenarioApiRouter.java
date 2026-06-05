package com.example.consistency.api;

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

import java.util.Map;
import java.util.Objects;

public final class ScenarioApiRouter {

    private final FirstLoginRewardApiHandler firstLoginRewardHandler;
    private final CouponCampaignApiHandler couponCampaignHandler;
    private final PointSpendApiHandler pointSpendHandler;

    public ScenarioApiRouter() {
        this(
                new FirstLoginRewardApiHandler(
                        new FirstLoginRewardScenarioExecutor(new InMemoryScenarioRunReportRepository())
                ),
                new CouponCampaignApiHandler(
                        new CouponCampaignScenarioExecutor(new InMemoryScenarioRunReportRepository())
                ),
                new PointSpendApiHandler(
                        new PointSpendScenarioExecutor(new InMemoryScenarioRunReportRepository())
                )
        );
    }

    public ScenarioApiRouter(
            FirstLoginRewardApiHandler firstLoginRewardHandler,
            CouponCampaignApiHandler couponCampaignHandler,
            PointSpendApiHandler pointSpendHandler
    ) {
        this.firstLoginRewardHandler = Objects.requireNonNull(firstLoginRewardHandler);
        this.couponCampaignHandler = Objects.requireNonNull(couponCampaignHandler);
        this.pointSpendHandler = Objects.requireNonNull(pointSpendHandler);
    }

    public ScenarioApiRouteResponse handle(String method, String path, Map<String, String> body) {
        if (!"POST".equalsIgnoreCase(method)) {
            return routeError(405, "method not allowed");
        }

        return switch (path) {
            case "/api/scenarios/first-login-reward/runs" -> firstLoginReward(body);
            case "/api/scenarios/coupon-campaign-issue/runs" -> couponCampaign(body);
            case "/api/scenarios/point-spend/runs" -> pointSpend(body);
            default -> routeError(404, "unknown route");
        };
    }

    private ScenarioApiRouteResponse firstLoginReward(Map<String, String> body) {
        FirstLoginRewardApiResponse response = firstLoginRewardHandler.handle(new FirstLoginRewardApiRequest(
                longValue(body, "memberId"),
                body.getOrDefault("strategy", ""),
                intValue(body, "requestCount")
        ));

        return new ScenarioApiRouteResponse(response.statusCode(), json(
                entry("statusCode", response.statusCode()),
                entry("scenario", response.scenario()),
                entry("strategy", response.strategy()),
                entry("runSequence", response.runSequence()),
                entry("invariantPassed", response.invariantPassed()),
                entry("acceptedCount", response.acceptedCount()),
                entry("completedCount", response.completedCount()),
                entry("rewardIssuedCount", response.rewardIssuedCount()),
                entry("duplicateRewardCount", response.duplicateRewardCount()),
                entry("redisLockAttemptCount", response.redisLockAttemptCount()),
                entry("afterCommitNotificationCount", response.afterCommitNotificationCount()),
                entry("outboxEventCount", response.outboxEventCount()),
                entry("message", response.message())
        ));
    }

    private ScenarioApiRouteResponse couponCampaign(Map<String, String> body) {
        CouponCampaignApiResponse response = couponCampaignHandler.handle(new CouponCampaignApiRequest(
                longValue(body, "campaignId"),
                body.getOrDefault("strategy", ""),
                intValue(body, "capacity"),
                intValue(body, "requestCount"),
                intValue(body, "transientRetryCount"),
                intValue(body, "dlqCount")
        ));

        return new ScenarioApiRouteResponse(response.statusCode(), json(
                entry("statusCode", response.statusCode()),
                entry("scenario", response.scenario()),
                entry("strategy", response.strategy()),
                entry("runSequence", response.runSequence()),
                entry("invariantPassed", response.invariantPassed()),
                entry("acceptedCount", response.acceptedCount()),
                entry("completedCount", response.completedCount()),
                entry("couponIssuedCount", response.couponIssuedCount()),
                entry("overIssueCount", response.overIssueCount()),
                entry("rejectedCount", response.rejectedCount()),
                entry("redisLockAttemptCount", response.redisLockAttemptCount()),
                entry("rabbitMqLaneCount", response.rabbitMqLaneCount()),
                entry("queueRetryCount", response.queueRetryCount()),
                entry("dlqCount", response.dlqCount()),
                entry("queueLagMsP95", response.queueLagMsP95()),
                entry("rabbitMqAcceptedLatencyMs", response.rabbitMqAcceptedLatencyMs()),
                entry("rabbitMqCompletionLatencyMs", response.rabbitMqCompletionLatencyMs()),
                entry("message", response.message())
        ));
    }

    private ScenarioApiRouteResponse pointSpend(Map<String, String> body) {
        PointSpendApiResponse response = pointSpendHandler.handle(new PointSpendApiRequest(
                longValue(body, "memberId"),
                body.getOrDefault("strategy", ""),
                longValue(body, "initialBalance"),
                longValue(body, "spendAmount"),
                intValue(body, "requestCount"),
                body.getOrDefault("idempotencyKey", "")
        ));

        return new ScenarioApiRouteResponse(response.statusCode(), json(
                entry("statusCode", response.statusCode()),
                entry("scenario", response.scenario()),
                entry("strategy", response.strategy()),
                entry("runSequence", response.runSequence()),
                entry("invariantPassed", response.invariantPassed()),
                entry("acceptedCount", response.acceptedCount()),
                entry("completedCount", response.completedCount()),
                entry("finalPointBalance", response.finalPointBalance()),
                entry("negativeBalanceCount", response.negativeBalanceCount()),
                entry("pointLedgerEntryCount", response.pointLedgerEntryCount()),
                entry("rejectedCount", response.rejectedCount()),
                entry("idempotencyReplayCount", response.idempotencyReplayCount()),
                entry("idempotencyHashMismatchCount", response.idempotencyHashMismatchCount()),
                entry("dbWaitMsP95", response.dbWaitMsP95()),
                entry("message", response.message())
        ));
    }

    private ScenarioApiRouteResponse routeError(int statusCode, String message) {
        return new ScenarioApiRouteResponse(statusCode, json(
                entry("statusCode", statusCode),
                entry("message", message)
        ));
    }

    private int intValue(Map<String, String> body, String key) {
        return (int) longValue(body, key);
    }

    private long longValue(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private JsonEntry entry(String key, Object value) {
        return new JsonEntry(key, value);
    }

    private String json(JsonEntry... entries) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(entries[i].key()).append('"').append(':');
            Object value = entries[i].value();
            if (value instanceof String text) {
                builder.append('"').append(escape(text)).append('"');
            } else {
                builder.append(value);
            }
        }
        return builder.append('}').toString();
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record JsonEntry(String key, Object value) {
    }
}
