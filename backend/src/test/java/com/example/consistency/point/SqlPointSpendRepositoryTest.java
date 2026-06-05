package com.example.consistency.point;

import com.example.consistency.persistence.RecordingSqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlPointSpendRepositoryTest {

    public static void main(String[] args) {
        rowLockReadUsesForUpdate();
        conditionalDebitKeepsBalanceNonNegative();
        ledgerInsertUsesEventAndIdempotencyKeys();
        idempotencyRecordAndReplayLookupUseRecordsTable();
        idempotencyHashMismatchLookupUsesRecordsTable();
    }

    private static void rowLockReadUsesForUpdate() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1000L);
        SqlPointSpendRepository repository = new SqlPointSpendRepository(executor);

        long balance = repository.balanceForUpdate(95001L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(1000L, balance, "row-lock read returns recorded balance");
        assertContains(statement.sql(), "select balance from point_accounts", "row-lock read targets point account");
        assertContains(statement.sql(), "for update", "row-lock read locks account row");
        assertEquals(95001L, statement.params().get(0), "member id is bound");
    }

    private static void conditionalDebitKeepsBalanceNonNegative() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(false);
        SqlPointSpendRepository repository = new SqlPointSpendRepository(executor);

        boolean debited = repository.tryDebit(95001L, 700L);

        SqlStatement statement = executor.lastStatement();
        assertEquals(false, debited, "insufficient balance returns false");
        assertContains(statement.sql(), "update point_accounts", "debit targets point account");
        assertContains(statement.sql(), "balance = balance - ?", "debit subtracts spend amount");
        assertContains(statement.sql(), "balance >= ?", "debit has non-negative guard");
        assertEquals(700L, statement.params().get(0), "debit amount is bound for subtraction");
        assertEquals(95001L, statement.params().get(1), "member id is bound");
        assertEquals(700L, statement.params().get(2), "debit amount is bound for guard");
    }

    private static void ledgerInsertUsesEventAndIdempotencyKeys() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000951");
        SqlPointSpendRepository repository = new SqlPointSpendRepository(executor);

        boolean inserted = repository.insertLedger(eventId, 95001L, -700L, "spend-95001-001");

        SqlStatement statement = executor.lastStatement();
        assertEquals(true, inserted, "ledger insert returns true");
        assertContains(statement.sql(), "insert into point_ledger", "ledger insert targets point_ledger");
        assertContains(statement.sql(), "on conflict (idempotency_key) do nothing", "ledger insert is idempotency guarded");
        assertEquals(eventId, statement.params().get(0), "event id is bound");
        assertEquals(95001L, statement.params().get(1), "member id is bound");
        assertEquals(-700L, statement.params().get(2), "amount is bound");
        assertEquals("SPEND", statement.params().get(3), "ledger type is bound");
        assertEquals("spend-95001-001", statement.params().get(4), "idempotency key is bound");
    }

    private static void idempotencyRecordAndReplayLookupUseRecordsTable() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextInsertResult(true);
        executor.nextLongResult(1L);
        SqlPointSpendRepository repository = new SqlPointSpendRepository(executor);

        boolean recorded = repository.insertIdempotencyRecord("spend-95001-001", "hash-001", "point-ledger:1");
        long replayCount = repository.replayCount("spend-95001-001");

        assertEquals(true, recorded, "idempotency record insert returns true");
        assertEquals(1L, replayCount, "replay lookup returns recorded count");
        assertContains(executor.statementAt(0).sql(), "insert into idempotency_records", "idempotency insert targets records table");
        assertContains(executor.statementAt(0).sql(), "on conflict (idempotency_key) do nothing", "idempotency insert uses unique key");
        assertContains(executor.statementAt(1).sql(), "select count(*) from idempotency_records", "replay lookup uses records table");
        assertEquals("spend-95001-001", executor.statementAt(0).params().get(0), "idempotency key is bound");
        assertEquals("hash-001", executor.statementAt(0).params().get(1), "request hash is bound");
        assertEquals("point-ledger:1", executor.statementAt(0).params().get(2), "response reference is bound");
        assertEquals("COMPLETED", executor.statementAt(0).params().get(3), "status is bound");
    }

    private static void idempotencyHashMismatchLookupUsesRecordsTable() {
        RecordingSqlExecutor executor = new RecordingSqlExecutor();
        executor.nextLongResult(1L);
        SqlPointSpendRepository repository = new SqlPointSpendRepository(executor);

        long mismatchCount = repository.requestHashMismatchCount("spend-95001-001", "hash-002");

        SqlStatement statement = executor.lastStatement();
        assertEquals(1L, mismatchCount, "mismatch count returns recorded count");
        assertContains(statement.sql(), "select count(*) from idempotency_records", "mismatch lookup uses records table");
        assertContains(statement.sql(), "idempotency_key = ?", "mismatch lookup filters key");
        assertContains(statement.sql(), "request_hash <> ?", "mismatch lookup compares request hash");
        assertEquals("spend-95001-001", statement.params().get(0), "idempotency key is bound");
        assertEquals("hash-002", statement.params().get(1), "request hash is bound");
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
