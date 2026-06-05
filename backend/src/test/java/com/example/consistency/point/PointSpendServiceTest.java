package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PointSpendServiceTest {

    public static void main(String[] args) {
        rowLockStrategyReadsBalanceForUpdateBeforeDebit();
        conditionalUpdateStrategyDebitsWithoutRowLockRead();
        idempotencyReplayReturnsWithoutSecondDebit();
        idempotencyHashMismatchRejectsBeforeReplayOrDebit();
    }

    private static void rowLockStrategyReadsBalanceForUpdateBeforeDebit() {
        RecordingPointSpendRepository repository = new RecordingPointSpendRepository();
        repository.balance = 1000L;
        repository.debitResult = true;
        PointSpendService service = new PointSpendService(repository);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                81001L,
                700L,
                StrategyType.DB_ROW_LOCK,
                "spend-81001-001",
                "hash-001"
        ));

        assertEquals(true, decision.accepted(), "row-lock spend is accepted");
        assertEquals(false, decision.replay(), "new spend is not replay");
        assertEquals(300L, decision.finalBalance(), "final balance is calculated");
        assertEquals(List.of("balanceForUpdate", "tryDebit", "insertLedger", "insertIdempotencyRecord"), repository.calls, "row-lock strategy call order");
        assertEquals(81001L, repository.lastMemberId, "member id is passed");
        assertEquals(700L, repository.lastSpendAmount, "spend amount is passed");
        assertEquals(-700L, repository.lastLedgerAmount, "ledger amount is negative spend");
        assertEquals("spend-81001-001", repository.lastIdempotencyKey, "idempotency key is recorded");
        assertEquals("point-ledger:" + decision.eventId(), repository.lastResponseRef, "response ref points to ledger event");
    }

    private static void conditionalUpdateStrategyDebitsWithoutRowLockRead() {
        RecordingPointSpendRepository repository = new RecordingPointSpendRepository();
        repository.debitResult = false;
        PointSpendService service = new PointSpendService(repository);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                81002L,
                700L,
                StrategyType.CONDITIONAL_UPDATE,
                "spend-81002-001",
                "hash-002"
        ));

        assertEquals(false, decision.accepted(), "conditional debit rejects insufficient balance");
        assertEquals(false, decision.rowLockRead(), "conditional update does not claim row-lock evidence");
        assertEquals(List.of("tryDebit"), repository.calls, "conditional update only attempts debit");
    }

    private static void idempotencyReplayReturnsWithoutSecondDebit() {
        RecordingPointSpendRepository repository = new RecordingPointSpendRepository();
        repository.replayCount = 1L;
        PointSpendService service = new PointSpendService(repository);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                81003L,
                700L,
                StrategyType.IDEMPOTENCY_REPLAY,
                "spend-81003-001",
                "hash-003"
        ));

        assertEquals(true, decision.accepted(), "replay returns accepted previous result");
        assertEquals(true, decision.replay(), "replay is marked");
        assertEquals(List.of("requestHashMismatchCount", "replayCount"), repository.calls, "replay checks hash before replay and does not debit or insert ledger");
    }

    private static void idempotencyHashMismatchRejectsBeforeReplayOrDebit() {
        RecordingPointSpendRepository repository = new RecordingPointSpendRepository();
        repository.requestHashMismatchCount = 1L;
        PointSpendService service = new PointSpendService(repository);

        PointSpendDecision decision = service.spend(new PointSpendCommand(
                81004L,
                700L,
                StrategyType.IDEMPOTENCY_REPLAY,
                "spend-81004-001",
                "hash-different"
        ));

        assertEquals(false, decision.accepted(), "hash mismatch is rejected");
        assertEquals(false, decision.replay(), "hash mismatch is not replay");
        assertEquals(true, decision.idempotencyHashMismatch(), "hash mismatch is marked for metrics");
        assertEquals(List.of("requestHashMismatchCount"), repository.calls, "hash mismatch stops before replay lookup and debit");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static final class RecordingPointSpendRepository implements PointSpendRepository {
        private final List<String> calls = new ArrayList<>();
        private long balance;
        private boolean debitResult;
        private long replayCount;
        private long requestHashMismatchCount;
        private long lastMemberId;
        private long lastSpendAmount;
        private long lastLedgerAmount;
        private String lastIdempotencyKey = "";
        private String lastResponseRef = "";

        @Override
        public long balanceForUpdate(long memberId) {
            calls.add("balanceForUpdate");
            lastMemberId = memberId;
            return balance;
        }

        @Override
        public boolean tryDebit(long memberId, long spendAmount) {
            calls.add("tryDebit");
            lastMemberId = memberId;
            lastSpendAmount = spendAmount;
            return debitResult;
        }

        @Override
        public boolean insertLedger(UUID eventId, long memberId, long amount, String idempotencyKey) {
            calls.add("insertLedger");
            lastMemberId = memberId;
            lastLedgerAmount = amount;
            lastIdempotencyKey = idempotencyKey;
            return true;
        }

        @Override
        public boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef) {
            calls.add("insertIdempotencyRecord");
            lastIdempotencyKey = idempotencyKey;
            lastResponseRef = responseRef;
            return true;
        }

        @Override
        public long replayCount(String idempotencyKey) {
            calls.add("replayCount");
            lastIdempotencyKey = idempotencyKey;
            return replayCount;
        }

        @Override
        public long requestHashMismatchCount(String idempotencyKey, String requestHash) {
            calls.add("requestHashMismatchCount");
            lastIdempotencyKey = idempotencyKey;
            return requestHashMismatchCount;
        }
    }
}
