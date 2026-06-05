package com.example.consistency.persistence;

import com.example.consistency.scenario.ScenarioRunRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class RecordingSqlExecutor implements SqlExecutor {

    private final List<SqlStatement> statements = new ArrayList<>();
    private final Queue<Boolean> insertResults = new ArrayDeque<>();
    private final Queue<Long> longResults = new ArrayDeque<>();
    private final Queue<ScenarioRunRecord> reportRecords = new ArrayDeque<>();

    @Override
    public boolean insert(SqlStatement statement) {
        statements.add(statement);
        return insertResults.isEmpty() || insertResults.remove();
    }

    @Override
    public long insertReturningLong(SqlStatement statement) {
        statements.add(statement);
        if (longResults.isEmpty()) {
            return statements.size();
        }
        return longResults.remove();
    }

    @Override
    public long queryLong(SqlStatement statement) {
        statements.add(statement);
        if (longResults.isEmpty()) {
            return 0L;
        }
        return longResults.remove();
    }

    @Override
    public ScenarioRunRecord queryLatestScenarioRun(SqlStatement statement) {
        statements.add(statement);
        if (reportRecords.isEmpty()) {
            throw new IllegalArgumentException("No recorded scenario run result");
        }
        return reportRecords.remove();
    }

    public void nextInsertResult(boolean result) {
        insertResults.add(result);
    }

    public void nextLongResult(long result) {
        longResults.add(result);
    }

    public void nextReportRecord(ScenarioRunRecord record) {
        reportRecords.add(record);
    }

    public SqlStatement lastStatement() {
        return statements.get(statements.size() - 1);
    }

    public SqlStatement statementAt(int index) {
        return statements.get(index);
    }

    public long statementCount() {
        return statements.size();
    }
}

