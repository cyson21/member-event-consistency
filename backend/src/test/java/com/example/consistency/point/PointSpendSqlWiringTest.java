package com.example.consistency.point;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.scenario.StrategyType;

public final class PointSpendSqlWiringTest {

    public static void main(String[] args) {
        rowLockStrategyReadsForUpdateThenWritesLedgerAndIdempotencyRecord();
        conditionalUpdateRejectionStopsBeforeLedgerAndIdempotencyWrites();
        idempotencyReplayStopsBeforeDebitAndLedgerWrites();
        idempotencyHashMismatchStopsBeforeReplayDebitAndLedgerWrites();
    }

    private static void rowLockStrategyReadsForUpdateThenWritesLedgerAndIdempotencyRecord() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1000L);
        PointSpendService service = PointSpendSqlWiring.service(executor);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                83001L,
                700L,
                StrategyType.DB_ROW_LOCK,
                "spend-83001-001",
                "hash-001"
        ));

        assertEquals(true, decision.accepted(), "row-lock SQL spend is accepted");
        assertEquals(true, decision.rowLockRead(), "row-lock read is reflected");
        assertEquals(300L, decision.finalBalance(), "final balance is calculated from locked read");
        assertEquals(4L, executor.statementCount(), "row-lock path reads, debits, writes ledger, and records idempotency");
        assertContains(executor.statementAt(0).sql(), "for update", "first statement locks point account row");
        assertContains(executor.statementAt(1).sql(), "update point_accounts", "second statement conditionally debits");
        assertContains(executor.statementAt(2).sql(), "insert into point_ledger", "third statement writes ledger");
        assertContains(executor.statementAt(3).sql(), "insert into idempotency_records", "fourth statement records idempotency");
    }

    private static void conditionalUpdateRejectionStopsBeforeLedgerAndIdempotencyWrites() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        PointSpendService service = PointSpendSqlWiring.service(executor);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                83002L,
                700L,
                StrategyType.CONDITIONAL_UPDATE,
                "spend-83002-001",
                "hash-002"
        ));

        assertEquals(false, decision.accepted(), "insufficient balance is rejected");
        assertEquals(false, decision.rowLockRead(), "conditional update does not claim row-lock evidence");
        assertEquals(1L, executor.statementCount(), "rejection stops before ledger and idempotency writes");
        assertContains(executor.statementAt(0).sql(), "balance >= ?", "conditional debit keeps non-negative guard");
    }

    private static void idempotencyReplayStopsBeforeDebitAndLedgerWrites() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(0L);
        executor.nextLongResult(1L);
        PointSpendService service = PointSpendSqlWiring.service(executor);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                83003L,
                700L,
                StrategyType.IDEMPOTENCY_REPLAY,
                "spend-83003-001",
                "hash-003"
        ));

        assertEquals(true, decision.accepted(), "replay returns accepted previous result");
        assertEquals(true, decision.replay(), "decision marks replay");
        assertEquals(2L, executor.statementCount(), "hash check and replay lookup stop before debit and ledger writes");
        assertContains(executor.statementAt(0).sql(), "request_hash <> ?", "first statement checks hash mismatch");
        assertContains(executor.statementAt(1).sql(), "select count(*) from idempotency_records", "replay lookup checks idempotency table");
    }

    private static void idempotencyHashMismatchStopsBeforeReplayDebitAndLedgerWrites() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        PointSpendService service = PointSpendSqlWiring.service(executor);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                83004L,
                700L,
                StrategyType.IDEMPOTENCY_REPLAY,
                "spend-83004-001",
                "hash-different"
        ));

        assertEquals(false, decision.accepted(), "hash mismatch rejects duplicate key with different request");
        assertEquals(false, decision.replay(), "hash mismatch is not replay");
        assertEquals(1L, executor.statementCount(), "hash mismatch stops before replay lookup, debit, and ledger writes");
        assertContains(executor.statementAt(0).sql(), "request_hash <> ?", "hash mismatch lookup compares stored request hash");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + " expected fragment=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
