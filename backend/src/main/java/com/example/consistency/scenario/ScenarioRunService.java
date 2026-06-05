package com.example.consistency.scenario;

public final class ScenarioRunService {

    private ScenarioRunService() {
    }

    public static boolean isSupported(ScenarioType scenario, StrategyType strategy) {
        if (scenario == ScenarioType.POINT_SPEND && strategy == StrategyType.RABBITMQ_DB_GUARD) {
            return false;
        }
        return true;
    }

    public static ComparisonMode comparisonMode(StrategyType strategy) {
        if (strategy.isAsyncAccepted()) {
            return ComparisonMode.ACCEPTED_THEN_COMPLETED;
        }
        return ComparisonMode.SYNCHRONOUS_FINAL_RESULT;
    }

    public enum ComparisonMode {
        SYNCHRONOUS_FINAL_RESULT,
        ACCEPTED_THEN_COMPLETED
    }
}

