package com.example.consistency.scenario;

public enum StrategyType {
    NAIVE(false),
    DB_GUARD(false),
    REDIS_LOCK_DB_GUARD(false),
    RABBITMQ_DB_GUARD(true),
    DB_ROW_LOCK(false),
    CONDITIONAL_UPDATE(false),
    IDEMPOTENCY_REPLAY(false);

    private final boolean asyncAccepted;

    StrategyType(boolean asyncAccepted) {
        this.asyncAccepted = asyncAccepted;
    }

    public boolean isAsyncAccepted() {
        return asyncAccepted;
    }
}
