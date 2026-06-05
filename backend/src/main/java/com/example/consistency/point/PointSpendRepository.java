package com.example.consistency.point;

import java.util.UUID;

public interface PointSpendRepository {

    long balanceForUpdate(long memberId);

    boolean tryDebit(long memberId, long spendAmount);

    boolean insertLedger(UUID eventId, long memberId, long amount, String idempotencyKey);

    boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef);

    long replayCount(String idempotencyKey);

    long requestHashMismatchCount(String idempotencyKey, String requestHash);
}
