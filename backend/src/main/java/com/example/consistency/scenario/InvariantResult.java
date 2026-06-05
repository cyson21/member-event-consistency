package com.example.consistency.scenario;

public record InvariantResult(
        ScenarioType scenario,
        StrategyType strategy,
        boolean passed,
        long duplicateCount,
        long overIssueCount,
        long negativeBalanceCount,
        long doubleUseCount,
        long terminalStateConflictCount,
        long timeoutCount,
        long acceptedCount,
        long completedCount,
        long dbWaitMsP95,
        long redisWaitMsP95,
        long queueLagMsP95,
        String message
) {
    public InvariantResult(
            ScenarioType scenario,
            StrategyType strategy,
            boolean passed,
            long duplicateCount,
            long overIssueCount,
            long negativeBalanceCount,
            long timeoutCount,
            long acceptedCount,
            long completedCount,
            long dbWaitMsP95,
            long redisWaitMsP95,
            long queueLagMsP95,
            String message
    ) {
        this(
                scenario,
                strategy,
                passed,
                duplicateCount,
                overIssueCount,
                negativeBalanceCount,
                0,
                0,
                timeoutCount,
                acceptedCount,
                completedCount,
                dbWaitMsP95,
                redisWaitMsP95,
                queueLagMsP95,
                message
        );
    }
}
