package com.example.consistency.point;

import java.util.UUID;

public record PointSpendDecision(
        boolean accepted,
        boolean replay,
        boolean rowLockRead,
        boolean idempotencyHashMismatch,
        long finalBalance,
        UUID eventId
) {

    public static PointSpendDecision accepted(boolean replay, boolean rowLockRead, long finalBalance, UUID eventId) {
        return new PointSpendDecision(true, replay, rowLockRead, false, finalBalance, eventId);
    }

    public static PointSpendDecision rejected(boolean rowLockRead, long finalBalance) {
        return new PointSpendDecision(false, false, rowLockRead, false, finalBalance, new UUID(0L, 0L));
    }

    public static PointSpendDecision idempotencyHashMismatchRejected() {
        return new PointSpendDecision(false, false, false, true, 0L, new UUID(0L, 0L));
    }
}
