package com.example.consistency.persistence;

import com.example.consistency.scenario.ScenarioRunRecord;

public interface SqlExecutor {

    boolean insert(SqlStatement statement);

    long insertReturningLong(SqlStatement statement);

    long queryLong(SqlStatement statement);

    ScenarioRunRecord queryLatestScenarioRun(SqlStatement statement);
}

