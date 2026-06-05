package com.example.consistency.runner;

import com.example.consistency.scenario.ScenarioType;
import com.example.consistency.scenario.StrategyType;

record MvpSmokeSuiteEntry(
        ScenarioType scenario,
        StrategyType strategy,
        int statusCode,
        boolean invariantPassed,
        long acceptedCount,
        long completedCount,
        String summary
) {
    boolean isNaiveFailure() {
        return strategy == StrategyType.NAIVE && !invariantPassed;
    }

    boolean isGuardedPass() {
        return strategy != StrategyType.NAIVE && invariantPassed;
    }

    boolean isAsyncAccepted() {
        return statusCode == 202;
    }
}

