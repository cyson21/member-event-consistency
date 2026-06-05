package com.example.consistency.scenario;

import java.util.UUID;

public record ScenarioRunRecord(
        UUID id,
        long sequence,
        ScenarioRunReport report
) {

    public ScenarioRunRecord(long sequence, ScenarioRunReport report) {
        this(null, sequence, report);
    }
}
