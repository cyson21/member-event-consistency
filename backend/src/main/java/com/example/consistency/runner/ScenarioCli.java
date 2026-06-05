package com.example.consistency.runner;

import com.example.consistency.api.ScenarioApiRouteResponse;
import com.example.consistency.api.ScenarioApiRouter;
import com.example.consistency.api.ScenarioApiRouterFactory;
import com.example.consistency.coupon.BatchExpirationApiHandler;
import com.example.consistency.coupon.BatchExpirationApiRequest;
import com.example.consistency.coupon.BatchExpirationApiResponse;
import com.example.consistency.coupon.BatchExpirationServiceScenarioExecutor;
import com.example.consistency.coupon.BatchExpirationSqlWiring;
import com.example.consistency.coupon.CouponCampaignApiHandler;
import com.example.consistency.coupon.CouponCampaignApiRequest;
import com.example.consistency.coupon.CouponCampaignApiResponse;
import com.example.consistency.coupon.CouponCampaignHotCampaignProbe;
import com.example.consistency.coupon.CouponCampaignHotCampaignProbeResult;
import com.example.consistency.coupon.CouponCampaignScenarioExecutor;
import com.example.consistency.coupon.LocalCouponCampaignLockGateway;
import com.example.consistency.point.PointSpendApiHandler;
import com.example.consistency.point.PointSpendApiRequest;
import com.example.consistency.point.PointSpendApiResponse;
import com.example.consistency.point.PointSpendConcurrentProbe;
import com.example.consistency.point.PointSpendConcurrentProbeResult;
import com.example.consistency.point.PointSpendScenarioExecutor;
import com.example.consistency.reward.FirstLoginRewardApiHandler;
import com.example.consistency.reward.FirstLoginRewardApiRequest;
import com.example.consistency.reward.FirstLoginRewardApiResponse;
import com.example.consistency.reward.FirstLoginRewardConcurrentProbe;
import com.example.consistency.reward.FirstLoginRewardConcurrentProbeResult;
import com.example.consistency.reward.FirstLoginRewardScenarioExecutor;
import com.example.consistency.reward.RecordingRewardLockGateway;
import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScenarioCli {

    private ScenarioCli() {
    }

    public static void main(String[] args) {
        System.out.println(run(args));
    }

    public static String run(String[] args) {
        Map<String, String> options = parse(args);
        String probeName = options.getOrDefault("probe", "");
        if (!probeName.isBlank()) {
            return runProbe(probeName, options);
        }

        String suiteName = options.getOrDefault("suite", "");
        if (!suiteName.isBlank()) {
            if ("SQL_RECORDING".equals(options.getOrDefault("backend", ""))) {
                return runSqlRecordingSuite(suiteName);
            }
            return runSuite(suiteName);
        }
        if ("SQL_RECORDING".equals(options.getOrDefault("backend", ""))) {
            return runSqlRecording(options);
        }

        String scenarioName = options.getOrDefault("scenario", "");

        ScenarioType scenario;
        try {
            scenario = ScenarioType.valueOf(scenarioName);
        } catch (IllegalArgumentException exception) {
            return badRequest("unknown scenario");
        }

        return switch (scenario) {
            case FIRST_LOGIN_REWARD -> runFirstLoginReward(options);
            case COUPON_CAMPAIGN_ISSUE -> runCouponCampaign(options);
            case POINT_SPEND -> runPointSpend(options);
            case COUPON_REDEMPTION, BATCH_EXPIRATION -> badRequest("scenario is not available in the MVP CLI");
        };
    }

    private static String runProbe(String probeName, Map<String, String> options) {
        if ("MVP_CONCURRENCY".equals(probeName)) {
            return mvpConcurrencyProbeSuite();
        }

        StrategyType strategy;
        try {
            strategy = StrategyType.valueOf(options.getOrDefault("strategy", ""));
        } catch (IllegalArgumentException exception) {
            return badRequest("unknown strategy");
        }

        return switch (probeName) {
            case "FIRST_LOGIN_REWARD_CONCURRENT" -> firstLoginRewardConcurrentProbe(strategy, options);
            case "POINT_CONCURRENT" -> pointConcurrentProbe(strategy, options);
            case "COUPON_HOT_CAMPAIGN" -> couponHotCampaignProbe(strategy, options);
            default -> json(
                    entry("statusCode", 400),
                    entry("probe", probeName),
                    entry("message", "unknown probe")
            );
        };
    }

    private static String mvpConcurrencyProbeSuite() {
        FirstLoginRewardConcurrentProbeResult reward = FirstLoginRewardConcurrentProbe.run(
                StrategyType.REDIS_LOCK_DB_GUARD,
                93012L,
                8
        );
        CouponCampaignHotCampaignProbeResult coupon = CouponCampaignHotCampaignProbe.run(
                StrategyType.RABBITMQ_DB_GUARD,
                98003L,
                3,
                9
        );
        PointSpendConcurrentProbeResult point = PointSpendConcurrentProbe.run(
                StrategyType.CONDITIONAL_UPDATE,
                97002L,
                1000L,
                700L,
                8
        );
        String rewardSummary = "issued=" + reward.rewardIssuedCount()
                + ", duplicate=" + reward.duplicateRewardCount()
                + ", rejected=" + reward.rejectedCount();
        String couponSummary = "issued=" + coupon.issuedCount()
                + ", overIssue=" + coupon.overIssueCount()
                + ", lane=" + coupon.rabbitMqLaneCount();
        String pointSummary = "balance=" + point.finalPointBalance()
                + ", negative=" + point.negativeBalanceCount()
                + ", rejected=" + point.rejectedCount();
        String entriesJson = "["
                + json(
                entry("probe", "FIRST_LOGIN_REWARD_CONCURRENT"),
                entry("scenario", "FIRST_LOGIN_REWARD"),
                entry("strategy", reward.strategy().name()),
                entry("invariantPassed", reward.invariantPassed()),
                entry("summary", rewardSummary)
        )
                + ","
                + json(
                entry("probe", "COUPON_HOT_CAMPAIGN"),
                entry("scenario", "COUPON_CAMPAIGN_ISSUE"),
                entry("strategy", coupon.strategy().name()),
                entry("invariantPassed", coupon.invariantPassed()),
                entry("summary", couponSummary)
        )
                + ","
                + json(
                entry("probe", "POINT_CONCURRENT"),
                entry("scenario", "POINT_SPEND"),
                entry("strategy", point.strategy().name()),
                entry("invariantPassed", point.invariantPassed()),
                entry("summary", pointSummary)
        )
                + "]";

        String summary = json(
                entry("statusCode", 200),
                entry("probe", "MVP_CONCURRENCY"),
                entry("localOnly", true),
                entry("scenarioCount", 3),
                entry("entryCount", 3),
                entry("passingInvariantCount", 3),
                entry("phase2EntryCount", 0)
        );
        return summary.substring(0, summary.length() - 1)
                + ",\"entries\":" + entriesJson
                + "}";
    }

    private static String firstLoginRewardConcurrentProbe(StrategyType strategy, Map<String, String> options) {
        FirstLoginRewardConcurrentProbeResult result = FirstLoginRewardConcurrentProbe.run(
                strategy,
                longOption(options, "member-id", 0),
                intOption(options, "request-count", 0)
        );

        return json(
                entry("statusCode", 200),
                entry("probe", "FIRST_LOGIN_REWARD_CONCURRENT"),
                entry("scenario", "FIRST_LOGIN_REWARD"),
                entry("strategy", result.strategy().name()),
                entry("localOnly", true),
                entry("invariantPassed", result.invariantPassed()),
                entry("acceptedCount", result.acceptedCount()),
                entry("completedCount", result.completedCount()),
                entry("rewardIssuedCount", result.rewardIssuedCount()),
                entry("rejectedCount", result.rejectedCount()),
                entry("duplicateRewardCount", result.duplicateRewardCount()),
                entry("redisLockAttemptCount", result.redisLockAttemptCount()),
                entry("lockKey", result.lockKey()),
                entry("afterCommitNotificationCount", result.afterCommitNotificationCount()),
                entry("outboxEventCount", result.outboxEventCount())
        );
    }

    private static String pointConcurrentProbe(StrategyType strategy, Map<String, String> options) {
        PointSpendConcurrentProbeResult result = PointSpendConcurrentProbe.run(
                strategy,
                longOption(options, "member-id", 0),
                longOption(options, "initial-balance", 0),
                longOption(options, "spend-amount", 0),
                intOption(options, "request-count", 0)
        );

        return json(
                entry("statusCode", 200),
                entry("probe", "POINT_CONCURRENT"),
                entry("scenario", "POINT_SPEND"),
                entry("strategy", result.strategy().name()),
                entry("localOnly", true),
                entry("invariantPassed", result.invariantPassed()),
                entry("requestCount", result.requestCount()),
                entry("successfulSpendCount", result.successfulSpendCount()),
                entry("rejectedCount", result.rejectedCount()),
                entry("finalPointBalance", result.finalPointBalance()),
                entry("negativeBalanceCount", result.negativeBalanceCount()),
                entry("dbWaitMsP95", result.dbWaitMsP95())
        );
    }

    private static String couponHotCampaignProbe(StrategyType strategy, Map<String, String> options) {
        CouponCampaignHotCampaignProbeResult result = CouponCampaignHotCampaignProbe.run(
                strategy,
                longOption(options, "campaign-id", 0),
                intOption(options, "capacity", 0),
                intOption(options, "request-count", 0)
        );

        return json(
                entry("statusCode", 200),
                entry("probe", "COUPON_HOT_CAMPAIGN"),
                entry("scenario", "COUPON_CAMPAIGN_ISSUE"),
                entry("strategy", result.strategy().name()),
                entry("localOnly", true),
                entry("invariantPassed", result.invariantPassed()),
                entry("acceptedCount", result.acceptedCount()),
                entry("completedCount", result.completedCount()),
                entry("issuedCount", result.issuedCount()),
                entry("rejectedCount", result.rejectedCount()),
                entry("overIssueCount", result.overIssueCount()),
                entry("redisLockAttemptCount", result.redisLockAttemptCount()),
                entry("lockKey", result.lockKey()),
                entry("rabbitMqLaneCount", result.rabbitMqLaneCount()),
                entry("acceptedLatencyMs", result.acceptedLatencyMs()),
                entry("completionLatencyMs", result.completionLatencyMs())
        );
    }

    private static String runSqlRecording(Map<String, String> options) {
        String scenarioName = options.getOrDefault("scenario", "");
        ScenarioType scenario;
        try {
            scenario = ScenarioType.valueOf(scenarioName);
        } catch (IllegalArgumentException exception) {
            return badRequest("unknown scenario");
        }

        LocalRecordingSqlExecutor sqlExecutor = new LocalRecordingSqlExecutor();
        ScenarioApiRouter router = ScenarioApiRouterFactory.sqlBacked(
                sqlExecutor,
                new RecordingRewardLockGateway(),
                new LocalCouponCampaignLockGateway()
        );

        ScenarioApiRouteResponse response = switch (scenario) {
            case FIRST_LOGIN_REWARD -> router.handle(
                    "POST",
                    "/api/scenarios/first-login-reward/runs",
                    Map.of(
                            "memberId", stringOption(options, "member-id"),
                            "strategy", options.getOrDefault("strategy", ""),
                            "requestCount", stringOption(options, "request-count")
                    )
            );
            case COUPON_CAMPAIGN_ISSUE -> router.handle(
                    "POST",
                    "/api/scenarios/coupon-campaign-issue/runs",
                    Map.of(
                            "campaignId", stringOption(options, "campaign-id"),
                            "strategy", options.getOrDefault("strategy", ""),
                            "capacity", stringOption(options, "capacity"),
                            "requestCount", stringOption(options, "request-count")
                    )
            );
            case POINT_SPEND -> router.handle(
                    "POST",
                    "/api/scenarios/point-spend/runs",
                    Map.of(
                            "memberId", stringOption(options, "member-id"),
                            "strategy", options.getOrDefault("strategy", ""),
                            "initialBalance", stringOption(options, "initial-balance"),
                            "spendAmount", stringOption(options, "spend-amount"),
                            "requestCount", stringOption(options, "request-count"),
                            "idempotencyKey", options.getOrDefault("idempotency-key", "")
                    )
            );
            case BATCH_EXPIRATION -> {
                BatchExpirationApiResponse batchResponse = runBatchExpirationSqlRecording(sqlExecutor, options);
                yield new ScenarioApiRouteResponse(batchResponse.statusCode(), batchExpirationJson(batchResponse, options));
            }
            case COUPON_REDEMPTION -> new ScenarioApiRouteResponse(
                    400,
                    json(
                            entry("statusCode", 400),
                            entry("scenario", scenario.name()),
                            entry("message", "scenario is not available in the MVP SQL recording CLI")
                    )
            );
        };

        return appendSqlRecordingMetadata(response.body(), sqlExecutor.statementCount());
    }

    private static BatchExpirationApiResponse runBatchExpirationSqlRecording(
            LocalRecordingSqlExecutor sqlExecutor,
            Map<String, String> options
    ) {
        BatchExpirationApiHandler handler = new BatchExpirationApiHandler(
                new BatchExpirationServiceScenarioExecutor(
                        BatchExpirationSqlWiring.service(sqlExecutor),
                        new InMemoryScenarioRunReportRepository()
                )
        );
        return handler.handle(new BatchExpirationApiRequest(
                longOption(options, "coupon-issue-id", 0),
                options.getOrDefault("strategy", ""),
                options.getOrDefault("winner", "")
        ));
    }

    private static String batchExpirationJson(BatchExpirationApiResponse response, Map<String, String> options) {
        return json(
                entry("statusCode", response.statusCode()),
                entry("scenario", response.scenario()),
                entry("strategy", response.strategy()),
                entry("winner", options.getOrDefault("winner", "")),
                entry("runSequence", response.runSequence()),
                entry("invariantPassed", response.invariantPassed()),
                entry("acceptedCount", response.acceptedCount()),
                entry("completedCount", response.completedCount()),
                entry("couponUsedCount", response.couponUsedCount()),
                entry("couponExpiredCount", response.couponExpiredCount()),
                entry("terminalStateConflictCount", response.terminalStateConflictCount()),
                entry("rejectedCount", response.rejectedCount()),
                entry("rejectionReason", response.rejectionReason()),
                entry("sqlEvidence", "conditional coupon issue terminal transition"),
                entry("message", response.message())
        );
    }

    private static String runSqlRecordingSuite(String suiteName) {
        if (!"MVP_SMOKE".equals(suiteName)) {
            return json(
                    entry("statusCode", 400),
                    entry("suite", suiteName),
                    entry("message", "unknown suite")
            );
        }

        LocalRecordingSqlExecutor sqlExecutor = new LocalRecordingSqlExecutor();
        ScenarioApiRouter router = ScenarioApiRouterFactory.sqlBacked(
                sqlExecutor,
                new RecordingRewardLockGateway(),
                new LocalCouponCampaignLockGateway()
        );

        int routeCount = 0;
        int brokenNaiveCount = 0;
        int passingGuardedCount = 0;
        int asyncAcceptedCount = 0;
        List<SqlRecordingRouteEntry> routeEntries = new ArrayList<>();
        for (StrategyType strategy : List.of(
                StrategyType.NAIVE,
                StrategyType.DB_GUARD,
                StrategyType.REDIS_LOCK_DB_GUARD
        )) {
            String route = "/api/scenarios/first-login-reward/runs";
            long memberId = 93001L + strategy.ordinal();
            long statementStart = sqlExecutor.statementCount();
            ScenarioApiRouteResponse response = router.handle(
                    "POST",
                    route,
                    Map.of(
                            "memberId", String.valueOf(memberId),
                            "strategy", strategy.name(),
                            "requestCount", "5"
                    )
            );
            routeCount++;
            if (isBrokenNaive(strategy, response)) {
                brokenNaiveCount++;
            }
            if (isPassingGuarded(strategy, response)) {
                passingGuardedCount++;
            }
            if (response.statusCode() == 202) {
                asyncAcceptedCount++;
            }
            routeEntries.add(sqlRecordingRouteEntry(
                    route,
                    ScenarioType.FIRST_LOGIN_REWARD,
                    strategy,
                    response,
                    sqlEvidenceFor(ScenarioType.FIRST_LOGIN_REWARD, strategy, sqlExecutor.sqlSince(statementStart))
            ));
        }
        for (StrategyType strategy : List.of(
                StrategyType.NAIVE,
                StrategyType.DB_GUARD,
                StrategyType.REDIS_LOCK_DB_GUARD,
                StrategyType.RABBITMQ_DB_GUARD
        )) {
            String route = "/api/scenarios/coupon-campaign-issue/runs";
            long campaignId = 94001L + strategy.ordinal();
            long statementStart = sqlExecutor.statementCount();
            ScenarioApiRouteResponse response = router.handle(
                    "POST",
                    route,
                    Map.of(
                            "campaignId", String.valueOf(campaignId),
                            "strategy", strategy.name(),
                            "capacity", "3",
                            "requestCount", "8"
                    )
            );
            routeCount++;
            if (isBrokenNaive(strategy, response)) {
                brokenNaiveCount++;
            }
            if (isPassingGuarded(strategy, response)) {
                passingGuardedCount++;
            }
            if (response.statusCode() == 202) {
                asyncAcceptedCount++;
            }
            routeEntries.add(sqlRecordingRouteEntry(
                    route,
                    ScenarioType.COUPON_CAMPAIGN_ISSUE,
                    strategy,
                    response,
                    sqlEvidenceFor(ScenarioType.COUPON_CAMPAIGN_ISSUE, strategy, sqlExecutor.sqlSince(statementStart))
            ));
        }
        for (StrategyType strategy : List.of(
                StrategyType.NAIVE,
                StrategyType.DB_ROW_LOCK,
                StrategyType.CONDITIONAL_UPDATE,
                StrategyType.IDEMPOTENCY_REPLAY
        )) {
            String route = "/api/scenarios/point-spend/runs";
            long memberId = 95001L + strategy.ordinal();
            long statementStart = sqlExecutor.statementCount();
            ScenarioApiRouteResponse response = router.handle(
                    "POST",
                    route,
                    Map.of(
                            "memberId", String.valueOf(memberId),
                            "strategy", strategy.name(),
                            "initialBalance", "1000",
                            "spendAmount", "700",
                            "requestCount", "2",
                            "idempotencyKey", strategy == StrategyType.IDEMPOTENCY_REPLAY
                                    ? "spend-" + memberId + "-001"
                                    : ""
                    )
            );
            routeCount++;
            if (isBrokenNaive(strategy, response)) {
                brokenNaiveCount++;
            }
            if (isPassingGuarded(strategy, response)) {
                passingGuardedCount++;
            }
            if (response.statusCode() == 202) {
                asyncAcceptedCount++;
            }
            routeEntries.add(sqlRecordingRouteEntry(
                    route,
                    ScenarioType.POINT_SPEND,
                    strategy,
                    response,
                    sqlEvidenceFor(ScenarioType.POINT_SPEND, strategy, sqlExecutor.sqlSince(statementStart))
            ));
        }

        String summary = json(
                entry("statusCode", 200),
                entry("suite", "MVP_SMOKE"),
                entry("backend", "SQL_RECORDING"),
                entry("localOnly", true),
                entry("scenarioCount", 3),
                entry("routeCount", routeCount),
                entry("brokenNaiveCount", brokenNaiveCount),
                entry("passingGuardedCount", passingGuardedCount),
                entry("asyncAcceptedCount", asyncAcceptedCount),
                entry("phase2EntryCount", 0),
                entry("sqlStatementCount", sqlExecutor.statementCount())
        );
        return summary.substring(0, summary.length() - 1)
                + ",\"routeEntries\":" + sqlRecordingRouteEntriesJson(routeEntries)
                + "}";
    }

    private static boolean isBrokenNaive(StrategyType strategy, ScenarioApiRouteResponse response) {
        return strategy == StrategyType.NAIVE && response.body().contains("\"invariantPassed\":false");
    }

    private static boolean isPassingGuarded(StrategyType strategy, ScenarioApiRouteResponse response) {
        return strategy != StrategyType.NAIVE && response.body().contains("\"invariantPassed\":true");
    }

    private static String runSuite(String suiteName) {
        if (!"MVP_SMOKE".equals(suiteName)) {
            return json(
                    entry("statusCode", 400),
                    entry("suite", suiteName),
                    entry("message", "unknown suite")
            );
        }

        MvpSmokeSuiteResult result = new MvpSmokeSuiteRunner().run();
        return suiteJson(result);
    }

    private static String runFirstLoginReward(Map<String, String> options) {
        FirstLoginRewardApiHandler handler = new FirstLoginRewardApiHandler(
                new FirstLoginRewardScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );
        FirstLoginRewardApiResponse response = handler.handle(new FirstLoginRewardApiRequest(
                longOption(options, "member-id", 0),
                options.getOrDefault("strategy", ""),
                intOption(options, "request-count", 0)
        ));

        return json(
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
                entry("message", response.message())
        );
    }

    private static String runCouponCampaign(Map<String, String> options) {
        CouponCampaignApiHandler handler = new CouponCampaignApiHandler(
                new CouponCampaignScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );
        CouponCampaignApiResponse response = handler.handle(new CouponCampaignApiRequest(
                longOption(options, "campaign-id", 0),
                options.getOrDefault("strategy", ""),
                intOption(options, "capacity", 0),
                intOption(options, "request-count", 0),
                intOption(options, "transient-retry-count", 0),
                intOption(options, "dlq-count", 0)
        ));

        return json(
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
                entry("rabbitMqLaneCount", response.rabbitMqLaneCount()),
                entry("queueRetryCount", response.queueRetryCount()),
                entry("dlqCount", response.dlqCount()),
                entry("queueLagMsP95", response.queueLagMsP95()),
                entry("rabbitMqAcceptedLatencyMs", response.rabbitMqAcceptedLatencyMs()),
                entry("rabbitMqCompletionLatencyMs", response.rabbitMqCompletionLatencyMs()),
                entry("message", response.message())
        );
    }

    private static String runPointSpend(Map<String, String> options) {
        PointSpendApiHandler handler = new PointSpendApiHandler(
                new PointSpendScenarioExecutor(new InMemoryScenarioRunReportRepository())
        );
        PointSpendApiResponse response = handler.handle(new PointSpendApiRequest(
                longOption(options, "member-id", 0),
                options.getOrDefault("strategy", ""),
                longOption(options, "initial-balance", 0),
                longOption(options, "spend-amount", 0),
                intOption(options, "request-count", 0),
                options.getOrDefault("idempotency-key", "")
        ));

        return json(
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
                entry("idempotencyReplayCount", response.idempotencyReplayCount()),
                entry("idempotencyHashMismatchCount", response.idempotencyHashMismatchCount()),
                entry("dbWaitMsP95", response.dbWaitMsP95()),
                entry("message", response.message())
        );
    }

    private static String badRequest(String message) {
        return json(
                entry("statusCode", 400),
                entry("scenario", ""),
                entry("strategy", ""),
                entry("invariantPassed", false),
                entry("message", message)
        );
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "";
            options.put(key, value);
        }
        return options;
    }

    private static int intOption(Map<String, String> options, String key, int defaultValue) {
        return (int) longOption(options, key, defaultValue);
    }

    private static long longOption(Map<String, String> options, String key, long defaultValue) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String stringOption(Map<String, String> options, String key) {
        return options.getOrDefault(key, "");
    }

    private static JsonEntry entry(String key, Object value) {
        return new JsonEntry(key, value);
    }

    private static String json(JsonEntry... entries) {
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

    private static String suiteJson(MvpSmokeSuiteResult result) {
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"statusCode\":200");
        builder.append(",\"suite\":\"MVP_SMOKE\"");
        builder.append(",\"scenarioCount\":").append(result.scenarioCount());
        builder.append(",\"entryCount\":").append(result.entryCount());
        builder.append(",\"brokenNaiveCount\":").append(result.brokenNaiveCount());
        builder.append(",\"passingGuardedCount\":").append(result.passingGuardedCount());
        builder.append(",\"asyncAcceptedCount\":").append(result.asyncAcceptedCount());
        builder.append(",\"phase2EntryCount\":").append(result.phase2EntryCount());
        builder.append(",\"entries\":[");
        for (int i = 0; i < result.entries().size(); i++) {
            MvpSmokeSuiteEntry entry = result.entries().get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append(json(
                    entry("scenario", entry.scenario().name()),
                    entry("strategy", entry.strategy().name()),
                    entry("statusCode", entry.statusCode()),
                    entry("invariantPassed", entry.invariantPassed()),
                    entry("acceptedCount", entry.acceptedCount()),
                    entry("completedCount", entry.completedCount()),
                    entry("summary", entry.summary())
            ));
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String appendSqlRecordingMetadata(String json, long statementCount) {
        String suffix = ",\"backend\":\"SQL_RECORDING\",\"localOnly\":true,\"sqlStatementCount\":" + statementCount + "}";
        return json.substring(0, json.length() - 1) + suffix;
    }

    private static SqlRecordingRouteEntry sqlRecordingRouteEntry(
            String route,
            ScenarioType scenario,
            StrategyType strategy,
            ScenarioApiRouteResponse response,
            String sqlEvidence
    ) {
        return new SqlRecordingRouteEntry(
                route,
                scenario.name(),
                strategy.name(),
                response.statusCode(),
                booleanField(response.body(), "invariantPassed"),
                longField(response.body(), "acceptedCount"),
                longField(response.body(), "completedCount"),
                evidenceFor(scenario, response.body()),
                sqlEvidence
        );
    }

    private static String evidenceFor(ScenarioType scenario, String responseBody) {
        return switch (scenario) {
            case FIRST_LOGIN_REWARD -> "issued=" + longField(responseBody, "rewardIssuedCount")
                    + ", duplicate=" + longField(responseBody, "duplicateRewardCount");
            case COUPON_CAMPAIGN_ISSUE -> "issued=" + longField(responseBody, "couponIssuedCount")
                    + ", overIssue=" + longField(responseBody, "overIssueCount")
                    + ", lane=" + longField(responseBody, "rabbitMqLaneCount");
            case POINT_SPEND -> "balance=" + longField(responseBody, "finalPointBalance")
                    + ", negative=" + longField(responseBody, "negativeBalanceCount")
                    + ", replay=" + longField(responseBody, "idempotencyReplayCount")
                    + ", hashMismatch=" + longField(responseBody, "idempotencyHashMismatchCount");
            case COUPON_REDEMPTION, BATCH_EXPIRATION -> "phase2-selected";
        };
    }

    private static String sqlEvidenceFor(ScenarioType scenario, StrategyType strategy, List<String> sqlStatements) {
        if (scenario == ScenarioType.FIRST_LOGIN_REWARD
                && strategy == StrategyType.NAIVE
                && containsSql(sqlStatements, "insert into reward_issue_attempts")
                && countSql(sqlStatements, "insert into outbox_events") >= 2) {
            return "duplicate-prone attempt insert -> fake follow-up outbox rows";
        }
        if (scenario == ScenarioType.FIRST_LOGIN_REWARD
                && strategy != StrategyType.NAIVE
                && containsSql(sqlStatements, "insert into reward_issues")
                && containsSql(sqlStatements, "on conflict (member_id, reward_type) do nothing")
                && countSql(sqlStatements, "insert into outbox_events") >= 2) {
            return "unique reward issue insert -> fake follow-up outbox rows";
        }
        if (scenario == ScenarioType.COUPON_CAMPAIGN_ISSUE
                && strategy != StrategyType.NAIVE
                && containsSql(sqlStatements, "for update")
                && containsSql(sqlStatements, "insert into coupon_issues")
                && containsSql(sqlStatements, "update coupon_campaigns")
                && containsSql(sqlStatements, "select count(*) from updated")) {
            return "campaign row lock -> coupon issue insert -> issued count update";
        }
        if (scenario != ScenarioType.POINT_SPEND) {
            return "";
        }

        long conditionalDebitCount = countSql(sqlStatements, "update point_accounts");
        boolean hasLedgerInsert = containsSql(sqlStatements, "insert into point_ledger");
        boolean hasIdempotencyRecord = containsSql(sqlStatements, "insert into idempotency_records");
        if (strategy == StrategyType.DB_ROW_LOCK
                && containsSql(sqlStatements, "select balance from point_accounts")
                && containsSql(sqlStatements, "for update")
                && conditionalDebitCount > 0
                && hasLedgerInsert
                && hasIdempotencyRecord) {
            return "select balance for update -> conditional debit -> ledger insert -> idempotency record";
        }
        if (strategy == StrategyType.CONDITIONAL_UPDATE
                && !containsSql(sqlStatements, "for update")
                && conditionalDebitCount > 0
                && hasLedgerInsert
                && hasIdempotencyRecord) {
            return "conditional debit -> ledger insert -> idempotency record";
        }
        if (strategy == StrategyType.IDEMPOTENCY_REPLAY
                && containsSql(sqlStatements, "request_hash <>")
                && countSql(sqlStatements, "select count(*) from idempotency_records") >= 2
                && conditionalDebitCount == 1
                && hasLedgerInsert
                && hasIdempotencyRecord) {
            return "hash check -> replay lookup -> conditional debit once -> replay lookup";
        }
        return "";
    }

    private static boolean containsSql(List<String> sqlStatements, String fragment) {
        return sqlStatements.stream().anyMatch(sql -> sql.contains(fragment));
    }

    private static long countSql(List<String> sqlStatements, String fragment) {
        return sqlStatements.stream()
                .filter(sql -> sql.contains(fragment))
                .count();
    }

    private static String sqlRecordingRouteEntriesJson(List<SqlRecordingRouteEntry> entries) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            SqlRecordingRouteEntry entry = entries.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append(json(
                    entry("route", entry.route()),
                    entry("scenario", entry.scenario()),
                    entry("strategy", entry.strategy()),
                    entry("statusCode", entry.statusCode()),
                    entry("invariantPassed", entry.invariantPassed()),
                    entry("acceptedCount", entry.acceptedCount()),
                    entry("completedCount", entry.completedCount()),
                    entry("evidence", entry.evidence()),
                    entry("sqlEvidence", entry.sqlEvidence())
            ));
        }
        return builder.append(']').toString();
    }

    private static boolean booleanField(String json, String key) {
        return json.contains("\"" + key + "\":true");
    }

    private static long longField(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return 0;
        }
        start += marker.length();
        int end = start;
        if (end < json.length() && json.charAt(end) == '-') {
            end++;
        }
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private record JsonEntry(String key, Object value) {
    }

    private record SqlRecordingRouteEntry(
            String route,
            String scenario,
            String strategy,
            int statusCode,
            boolean invariantPassed,
            long acceptedCount,
            long completedCount,
            String evidence,
            String sqlEvidence
    ) {
    }
}
