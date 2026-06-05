package com.example.consistency.point;

import com.example.consistency.scenario.StrategyType;

import java.util.UUID;

public final class PointSpendService {

    private final PointSpendRepository repository;

    public PointSpendService(PointSpendRepository repository) {
        this.repository = repository;
    }

    public PointSpendDecision spend(PointSpendCommand command) {
        if (command.memberId() <= 0 || command.spendAmount() <= 0) {
            throw new IllegalArgumentException("memberId and spendAmount must be positive");
        }
        if (command.strategy() == StrategyType.DB_ROW_LOCK) {
            return spendWithRowLock(command);
        }
        if (command.strategy() == StrategyType.CONDITIONAL_UPDATE) {
            return spendWithConditionalUpdate(command);
        }
        if (command.strategy() == StrategyType.IDEMPOTENCY_REPLAY) {
            return spendWithIdempotencyReplay(command);
        }
        throw new IllegalArgumentException("strategy is not supported for Point Spend service: " + command.strategy());
    }

    private PointSpendDecision spendWithRowLock(PointSpendCommand command) {
        long balance = repository.balanceForUpdate(command.memberId());
        if (balance < command.spendAmount()) {
            return PointSpendDecision.rejected(true, balance);
        }
        boolean debited = repository.tryDebit(command.memberId(), command.spendAmount());
        if (!debited) {
            return PointSpendDecision.rejected(true, balance);
        }
        return recordSuccessfulSpend(command, true, balance - command.spendAmount());
    }

    private PointSpendDecision spendWithConditionalUpdate(PointSpendCommand command) {
        boolean debited = repository.tryDebit(command.memberId(), command.spendAmount());
        if (!debited) {
            return PointSpendDecision.rejected(false, 0L);
        }
        return recordSuccessfulSpend(command, false, 0L);
    }

    private PointSpendDecision spendWithIdempotencyReplay(PointSpendCommand command) {
        if (repository.requestHashMismatchCount(command.idempotencyKey(), command.requestHash()) > 0) {
            return PointSpendDecision.idempotencyHashMismatchRejected();
        }
        if (repository.replayCount(command.idempotencyKey()) > 0) {
            return PointSpendDecision.accepted(true, false, 0L, new UUID(0L, 0L));
        }
        return spendWithConditionalUpdate(command);
    }

    private PointSpendDecision recordSuccessfulSpend(PointSpendCommand command, boolean rowLockRead, long finalBalance) {
        UUID eventId = UUID.randomUUID();
        repository.insertLedger(eventId, command.memberId(), -command.spendAmount(), command.idempotencyKey());
        repository.insertIdempotencyRecord(command.idempotencyKey(), command.requestHash(), "point-ledger:" + eventId);
        return PointSpendDecision.accepted(false, rowLockRead, finalBalance, eventId);
    }
}
