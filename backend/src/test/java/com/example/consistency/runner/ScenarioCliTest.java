package com.example.consistency.runner;

public final class ScenarioCliTest {

    public static void main(String[] args) {
        firstLoginRewardCommandReturnsRewardEvidence();
        couponRabbitCommandReturnsAcceptedAndCompletionEvidence();
        couponRabbitCommandReturnsRetryAndDlqEvidence();
        pointIdempotencyCommandReturnsReplayEvidence();
        firstLoginRewardConcurrentProbeCommandReturnsDuplicatePreventionEvidence();
        pointConcurrentProbeCommandReturnsGuardedOverspendEvidence();
        couponHotCampaignProbeCommandReturnsCapacityEvidence();
        mvpConcurrencyProbeCommandReturnsFixedMvpProbeSuiteEvidence();
        sqlRecordingBackendRunsThroughSqlBackedRouter();
        sqlRecordingBackendRunsMvpSuiteThroughSqlBackedRouter();
        sqlRecordingBackendRunsSelectedBatchExpirationOutsideMvpSuite();
        invalidScenarioReturnsBadRequestJson();
    }

    private static void firstLoginRewardCommandReturnsRewardEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--scenario", "FIRST_LOGIN_REWARD",
                "--strategy", "DB_GUARD",
                "--member-id", "93001",
                "--request-count", "5"
        });

        assertContains(json, "\"scenario\":\"FIRST_LOGIN_REWARD\"", "scenario is emitted");
        assertContains(json, "\"strategy\":\"DB_GUARD\"", "strategy is emitted");
        assertContains(json, "\"statusCode\":200", "sync status code is emitted");
        assertContains(json, "\"invariantPassed\":true", "invariant status is emitted");
        assertContains(json, "\"rewardIssuedCount\":1", "reward metric is emitted");
        assertContains(json, "\"duplicateRewardCount\":0", "duplicate metric is emitted");
    }

    private static void couponRabbitCommandReturnsAcceptedAndCompletionEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--scenario", "COUPON_CAMPAIGN_ISSUE",
                "--strategy", "RABBITMQ_DB_GUARD",
                "--campaign-id", "94001",
                "--capacity", "3",
                "--request-count", "8"
        });

        assertContains(json, "\"statusCode\":202", "RabbitMQ strategy returns accepted-style status");
        assertContains(json, "\"acceptedCount\":8", "accepted count is emitted");
        assertContains(json, "\"completedCount\":8", "completed count is emitted separately");
        assertContains(json, "\"couponIssuedCount\":3", "coupon issued metric is emitted");
        assertContains(json, "\"rabbitMqLaneCount\":1", "lane metric is emitted");
        assertContains(json, "\"queueLagMsP95\":70", "queue lag metric is emitted");
        assertContains(json, "\"rabbitMqAcceptedLatencyMs\":12", "accepted latency metric is emitted");
        assertContains(json, "\"rabbitMqCompletionLatencyMs\":82", "completion latency metric is emitted");
    }

    private static void couponRabbitCommandReturnsRetryAndDlqEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--scenario", "COUPON_CAMPAIGN_ISSUE",
                "--strategy", "RABBITMQ_DB_GUARD",
                "--campaign-id", "94002",
                "--capacity", "3",
                "--request-count", "8",
                "--transient-retry-count", "2",
                "--dlq-count", "1"
        });

        assertContains(json, "\"statusCode\":202", "RabbitMQ retry/DLQ command returns accepted-style status");
        assertContains(json, "\"queueRetryCount\":2", "retry count is emitted");
        assertContains(json, "\"dlqCount\":1", "DLQ count is emitted");
        assertContains(json, "\"couponIssuedCount\":3", "issued count still stops at capacity");
        assertContains(json, "\"overIssueCount\":0", "over issue remains zero");
    }

    private static void pointIdempotencyCommandReturnsReplayEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--scenario", "POINT_SPEND",
                "--strategy", "IDEMPOTENCY_REPLAY",
                "--member-id", "95001",
                "--initial-balance", "1000",
                "--spend-amount", "700",
                "--request-count", "2",
                "--idempotency-key", "spend-95001-001"
        });

        assertContains(json, "\"scenario\":\"POINT_SPEND\"", "point scenario is emitted");
        assertContains(json, "\"finalPointBalance\":300", "final balance is emitted");
        assertContains(json, "\"pointLedgerEntryCount\":1", "ledger metric is emitted");
        assertContains(json, "\"idempotencyReplayCount\":1", "replay metric is emitted");
        assertContains(json, "\"idempotencyHashMismatchCount\":0", "hash mismatch metric is emitted");
    }

    private static void firstLoginRewardConcurrentProbeCommandReturnsDuplicatePreventionEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--probe", "FIRST_LOGIN_REWARD_CONCURRENT",
                "--strategy", "REDIS_LOCK_DB_GUARD",
                "--member-id", "93012",
                "--request-count", "8"
        });

        assertContains(json, "\"probe\":\"FIRST_LOGIN_REWARD_CONCURRENT\"", "reward probe name is emitted");
        assertContains(json, "\"scenario\":\"FIRST_LOGIN_REWARD\"", "reward probe scenario is emitted");
        assertContains(json, "\"strategy\":\"REDIS_LOCK_DB_GUARD\"", "reward probe strategy is emitted");
        assertContains(json, "\"localOnly\":true", "reward probe local-only boundary is emitted");
        assertContains(json, "\"invariantPassed\":true", "reward probe invariant is emitted");
        assertContains(json, "\"rewardIssuedCount\":1", "reward probe issued count is emitted");
        assertContains(json, "\"rejectedCount\":7", "reward probe rejection count is emitted");
        assertContains(json, "\"duplicateRewardCount\":0", "reward probe duplicate count is emitted");
        assertContains(json, "\"redisLockAttemptCount\":8", "reward probe lock attempts are emitted");
        assertContains(json, "\"lockKey\":\"lock:first-login-reward:93012\"", "reward-scoped lock key is emitted");
        assertContains(json, "\"outboxEventCount\":1", "reward probe local outbox count is emitted");
    }

    private static void pointConcurrentProbeCommandReturnsGuardedOverspendEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--probe", "POINT_CONCURRENT",
                "--strategy", "CONDITIONAL_UPDATE",
                "--member-id", "97002",
                "--initial-balance", "1000",
                "--spend-amount", "700",
                "--request-count", "8"
        });

        assertContains(json, "\"probe\":\"POINT_CONCURRENT\"", "point probe name is emitted");
        assertContains(json, "\"strategy\":\"CONDITIONAL_UPDATE\"", "point probe strategy is emitted");
        assertContains(json, "\"localOnly\":true", "point probe local-only boundary is emitted");
        assertContains(json, "\"invariantPassed\":true", "point probe invariant is emitted");
        assertContains(json, "\"successfulSpendCount\":1", "point probe successful spend count is emitted");
        assertContains(json, "\"rejectedCount\":7", "point probe rejection count is emitted");
        assertContains(json, "\"finalPointBalance\":300", "point probe final balance is emitted");
        assertContains(json, "\"negativeBalanceCount\":0", "point probe negative balance count is emitted");
    }

    private static void couponHotCampaignProbeCommandReturnsCapacityEvidence() {
        String json = ScenarioCli.run(new String[]{
                "--probe", "COUPON_HOT_CAMPAIGN",
                "--strategy", "RABBITMQ_DB_GUARD",
                "--campaign-id", "98003",
                "--capacity", "3",
                "--request-count", "9"
        });

        assertContains(json, "\"probe\":\"COUPON_HOT_CAMPAIGN\"", "coupon probe name is emitted");
        assertContains(json, "\"strategy\":\"RABBITMQ_DB_GUARD\"", "coupon probe strategy is emitted");
        assertContains(json, "\"localOnly\":true", "coupon probe local-only boundary is emitted");
        assertContains(json, "\"invariantPassed\":true", "coupon probe invariant is emitted");
        assertContains(json, "\"issuedCount\":3", "coupon probe issued count is emitted");
        assertContains(json, "\"rejectedCount\":6", "coupon probe rejection count is emitted");
        assertContains(json, "\"overIssueCount\":0", "coupon probe over issue count is emitted");
        assertContains(json, "\"rabbitMqLaneCount\":1", "coupon probe single lane evidence is emitted");
        assertContains(json, "\"acceptedLatencyMs\":12", "coupon probe accepted latency is emitted");
    }

    private static void mvpConcurrencyProbeCommandReturnsFixedMvpProbeSuiteEvidence() {
        String json = ScenarioCli.run(new String[]{"--probe", "MVP_CONCURRENCY"});

        assertContains(json, "\"statusCode\":200", "MVP concurrency suite status is emitted");
        assertContains(json, "\"probe\":\"MVP_CONCURRENCY\"", "MVP concurrency probe name is emitted");
        assertContains(json, "\"localOnly\":true", "MVP concurrency local-only boundary is emitted");
        assertContains(json, "\"scenarioCount\":3", "fixed MVP scenario count is emitted");
        assertContains(json, "\"entryCount\":3", "fixed MVP probe entry count is emitted");
        assertContains(json, "\"passingInvariantCount\":3", "passing invariant count is emitted");
        assertContains(json, "\"phase2EntryCount\":0", "Phase 2 stays excluded");
        assertContains(json, "\"entries\":[", "probe entries are emitted");
        assertContains(json, "\"probe\":\"FIRST_LOGIN_REWARD_CONCURRENT\"", "reward probe entry is emitted");
        assertContains(json, "\"probe\":\"COUPON_HOT_CAMPAIGN\"", "coupon probe entry is emitted");
        assertContains(json, "\"probe\":\"POINT_CONCURRENT\"", "point probe entry is emitted");
        assertContains(json, "\"summary\":\"issued=1, duplicate=0, rejected=7\"", "reward summary is emitted");
        assertContains(json, "\"summary\":\"issued=3, overIssue=0, lane=1\"", "coupon summary is emitted");
        assertContains(json, "\"summary\":\"balance=300, negative=0, rejected=7\"", "point summary is emitted");
    }

    private static void sqlRecordingBackendRunsThroughSqlBackedRouter() {
        String json = ScenarioCli.run(new String[]{
                "--backend", "SQL_RECORDING",
                "--scenario", "COUPON_CAMPAIGN_ISSUE",
                "--strategy", "RABBITMQ_DB_GUARD",
                "--campaign-id", "94001",
                "--capacity", "3",
                "--request-count", "1"
        });

        assertContains(json, "\"statusCode\":202", "SQL recording mode preserves route status");
        assertContains(json, "\"scenario\":\"COUPON_CAMPAIGN_ISSUE\"", "scenario is emitted");
        assertContains(json, "\"rabbitMqLaneCount\":1", "single lane evidence is emitted");
        assertContains(json, "\"backend\":\"SQL_RECORDING\"", "backend mode is emitted");
        assertContains(json, "\"localOnly\":true", "local-only boundary is emitted");
        assertContains(json, "\"sqlStatementCount\":", "SQL statement count is emitted");
    }

    private static void sqlRecordingBackendRunsMvpSuiteThroughSqlBackedRouter() {
        String json = ScenarioCli.run(new String[]{
                "--backend", "SQL_RECORDING",
                "--suite", "MVP_SMOKE"
        });

        assertContains(json, "\"statusCode\":200", "SQL recording suite status");
        assertContains(json, "\"suite\":\"MVP_SMOKE\"", "suite name is emitted");
        assertContains(json, "\"backend\":\"SQL_RECORDING\"", "backend mode is emitted");
        assertContains(json, "\"localOnly\":true", "local-only boundary is emitted");
        assertContains(json, "\"routeCount\":11", "fixed MVP route count is emitted");
        assertContains(json, "\"brokenNaiveCount\":3", "naive failure count is emitted");
        assertContains(json, "\"passingGuardedCount\":8", "guarded pass count is emitted");
        assertContains(json, "\"asyncAcceptedCount\":1", "accepted-style route count is emitted");
        assertContains(json, "\"phase2EntryCount\":0", "Phase 2 entries stay excluded");
        assertContains(json, "\"sqlStatementCount\":", "SQL statement count is emitted");
        assertContains(json, "\"routeEntries\":[", "SQL recording route matrix is emitted");
        assertEquals(11, countOccurrences(json, "\"route\":\"/api/scenarios/"), "fixed MVP route entry count");
        assertContains(json, "\"strategy\":\"RABBITMQ_DB_GUARD\"", "RabbitMQ route entry is emitted");
        assertContains(json, "\"statusCode\":202", "accepted-style route entry status is emitted");
        assertContains(json, "\"acceptedCount\":8", "route accepted count is emitted");
        assertContains(json, "\"completedCount\":8", "route completed count is emitted separately");
        assertContains(json, "\"evidence\":\"issued=3, overIssue=0, lane=1\"", "coupon route evidence is emitted");
        assertContains(json, "\"evidence\":\"balance=-400, negative=1, replay=0, hashMismatch=0\"", "point naive evidence is emitted");
        assertContains(json, "\"evidence\":\"balance=300, negative=0, replay=1, hashMismatch=0\"", "point idempotency evidence is emitted");
    }

    private static void sqlRecordingBackendRunsSelectedBatchExpirationOutsideMvpSuite() {
        String json = ScenarioCli.run(new String[]{
                "--backend", "SQL_RECORDING",
                "--scenario", "BATCH_EXPIRATION",
                "--strategy", "DB_GUARD",
                "--coupon-issue-id", "13021",
                "--winner", "USER_USE"
        });

        assertContains(json, "\"statusCode\":200", "Batch Expiration SQL recording status is emitted");
        assertContains(json, "\"scenario\":\"BATCH_EXPIRATION\"", "scenario is emitted");
        assertContains(json, "\"strategy\":\"DB_GUARD\"", "strategy is emitted");
        assertContains(json, "\"winner\":\"USER_USE\"", "winner is emitted");
        assertContains(json, "\"couponUsedCount\":1", "use winner metric is emitted");
        assertContains(json, "\"couponExpiredCount\":0", "expiration loser metric is emitted");
        assertContains(json, "\"rejectedCount\":1", "losing terminal transition is rejected");
        assertContains(json, "\"sqlEvidence\":\"conditional coupon issue terminal transition\"", "SQL evidence is emitted");
        assertContains(json, "\"backend\":\"SQL_RECORDING\"", "backend mode is emitted");
        assertContains(json, "\"localOnly\":true", "local-only boundary is emitted");

        String suiteJson = ScenarioCli.run(new String[]{
                "--backend", "SQL_RECORDING",
                "--suite", "MVP_SMOKE"
        });
        assertContains(suiteJson, "\"routeCount\":11", "fixed MVP suite route count remains unchanged");
        assertContains(suiteJson, "\"phase2EntryCount\":0", "Batch Expiration stays outside MVP suite");
    }

    private static void invalidScenarioReturnsBadRequestJson() {
        String json = ScenarioCli.run(new String[]{"--scenario", "PHASE_2_CANDIDATE"});

        assertContains(json, "\"statusCode\":400", "invalid scenario returns 400");
        assertContains(json, "\"message\":\"unknown scenario\"", "invalid scenario message is explicit");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
