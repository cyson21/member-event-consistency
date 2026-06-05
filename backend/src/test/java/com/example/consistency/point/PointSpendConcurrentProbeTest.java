package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

public final class PointSpendConcurrentProbeTest {

    public static void main(String[] args) {
        rowLockProbeRejectsConcurrentOverspend();
        conditionalUpdateProbeRejectsConcurrentOverspendWithoutRowLockWait();
        unsupportedStrategyIsRejected();
    }

    private static void rowLockProbeRejectsConcurrentOverspend() {
        PointSpendConcurrentProbeResult result = PointSpendConcurrentProbe.run(
                StrategyType.DB_ROW_LOCK,
                97001L,
                1000L,
                700L,
                8
        );

        assertEquals(true, result.invariantPassed(), "row-lock invariant");
        assertEquals(8, result.requestCount(), "row-lock request count");
        assertEquals(1L, result.successfulSpendCount(), "row-lock successful spend count");
        assertEquals(7L, result.rejectedCount(), "row-lock rejected count");
        assertEquals(300L, result.finalPointBalance(), "row-lock final balance");
        assertEquals(0L, result.negativeBalanceCount(), "row-lock negative balance count");
        assertEquals(true, result.dbWaitMsP95() > 0, "row-lock wait evidence");
    }

    private static void conditionalUpdateProbeRejectsConcurrentOverspendWithoutRowLockWait() {
        PointSpendConcurrentProbeResult result = PointSpendConcurrentProbe.run(
                StrategyType.CONDITIONAL_UPDATE,
                97002L,
                1000L,
                700L,
                8
        );

        assertEquals(true, result.invariantPassed(), "conditional update invariant");
        assertEquals(1L, result.successfulSpendCount(), "conditional successful spend count");
        assertEquals(7L, result.rejectedCount(), "conditional rejected count");
        assertEquals(300L, result.finalPointBalance(), "conditional final balance");
        assertEquals(0L, result.negativeBalanceCount(), "conditional negative balance count");
        assertEquals(0L, result.dbWaitMsP95(), "conditional update does not claim row-lock wait");
    }

    private static void unsupportedStrategyIsRejected() {
        try {
            PointSpendConcurrentProbe.run(StrategyType.IDEMPOTENCY_REPLAY, 97003L, 1000L, 700L, 2);
            throw new AssertionError("unsupported strategy should fail");
        } catch (IllegalArgumentException exception) {
            assertEquals(
                    "strategy is not supported for concurrent Point Spend probe: IDEMPOTENCY_REPLAY",
                    exception.getMessage(),
                    "unsupported strategy message"
            );
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
