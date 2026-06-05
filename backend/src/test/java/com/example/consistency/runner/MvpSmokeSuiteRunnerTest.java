package com.example.consistency.runner;

import com.example.consistency.scenario.ScenarioType;

public final class MvpSmokeSuiteRunnerTest {

    public static void main(String[] args) {
        suiteCoversOnlyFixedMvpScenarios();
        suiteSeparatesNaiveFailuresFromGuardedPasses();
        cliEmitsSuiteComparisonJson();
    }

    private static void suiteCoversOnlyFixedMvpScenarios() {
        MvpSmokeSuiteResult result = new MvpSmokeSuiteRunner().run();

        assertEquals(3, result.scenarioCount(), "suite covers three MVP scenarios");
        assertEquals(11, result.entryCount(), "suite covers all MVP strategy entries");
        assertEquals(3, result.entriesFor(ScenarioType.FIRST_LOGIN_REWARD).size(), "reward strategy count");
        assertEquals(4, result.entriesFor(ScenarioType.COUPON_CAMPAIGN_ISSUE).size(), "coupon strategy count");
        assertEquals(4, result.entriesFor(ScenarioType.POINT_SPEND).size(), "point strategy count");
    }

    private static void suiteSeparatesNaiveFailuresFromGuardedPasses() {
        MvpSmokeSuiteResult result = new MvpSmokeSuiteRunner().run();

        assertEquals(3, result.brokenNaiveCount(), "each naive path breaks its invariant");
        assertEquals(8, result.passingGuardedCount(), "every guarded MVP strategy passes");
        assertEquals(1, result.asyncAcceptedCount(), "only coupon RabbitMQ path returns accepted status");
        assertEquals(0, result.phase2EntryCount(), "Phase 2 candidates stay out of the MVP suite");
    }

    private static void cliEmitsSuiteComparisonJson() {
        String json = ScenarioCli.run(new String[]{"--suite", "MVP_SMOKE"});

        assertContains(json, "\"suite\":\"MVP_SMOKE\"", "suite name is emitted");
        assertContains(json, "\"scenarioCount\":3", "scenario count is emitted");
        assertContains(json, "\"entryCount\":11", "entry count is emitted");
        assertContains(json, "\"brokenNaiveCount\":3", "naive failure count is emitted");
        assertContains(json, "\"passingGuardedCount\":8", "guarded pass count is emitted");
        assertContains(json, "\"asyncAcceptedCount\":1", "accepted split count is emitted");
        assertContains(json, "\"scenario\":\"COUPON_CAMPAIGN_ISSUE\"", "coupon entry is emitted");
        assertContains(json, "\"strategy\":\"RABBITMQ_DB_GUARD\"", "RabbitMQ single-lane entry is emitted");
        assertContains(json, "\"statusCode\":202", "accepted status is emitted");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
