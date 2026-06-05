package com.example.consistency.runner;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;
import com.example.consistency.scenario.ScenarioRunRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LocalRecordingSqlExecutor implements SqlExecutor {

    private static final long DEFAULT_COUPON_CAPACITY = 3;
    private static final long DEFAULT_POINT_BALANCE = 1000;

    private final List<SqlStatement> statements = new ArrayList<>();
    private final Set<String> rewardKeys = new HashSet<>();
    private final Set<String> couponIssueKeys = new HashSet<>();
    private final Map<Long, Long> couponIssuedCounts = new HashMap<>();
    private final Map<Long, String> couponIssueStatuses = new HashMap<>();
    private final Map<Long, Long> pointBalances = new HashMap<>();
    private final Map<String, String> idempotencyHashes = new HashMap<>();
    private long nextSequence = 1;

    @Override
    public boolean insert(SqlStatement statement) {
        statements.add(statement);
        String sql = statement.sql();
        if (sql.contains("insert into reward_issues") && statement.params().size() >= 2) {
            String key = statement.params().get(0) + ":" + statement.params().get(1);
            return !sql.contains("on conflict") || rewardKeys.add(key);
        }
        if (sql.contains("update coupon_issues") && statement.params().size() >= 3) {
            String terminalStatus = String.valueOf(statement.params().get(0));
            long couponIssueId = longParam(statement, 1);
            String requiredStatus = String.valueOf(statement.params().get(2));
            String currentStatus = couponIssueStatuses.getOrDefault(couponIssueId, "ISSUED");
            if (!currentStatus.equals(requiredStatus)) {
                return false;
            }
            couponIssueStatuses.put(couponIssueId, terminalStatus);
            return true;
        }
        if (sql.contains("update point_accounts") && statement.params().size() >= 3) {
            long spendAmount = longParam(statement, 0);
            long memberId = longParam(statement, 1);
            long balance = pointBalances.getOrDefault(memberId, DEFAULT_POINT_BALANCE);
            if (balance < spendAmount) {
                return false;
            }
            pointBalances.put(memberId, balance - spendAmount);
            return true;
        }
        if (sql.contains("insert into idempotency_records") && statement.params().size() >= 2) {
            idempotencyHashes.putIfAbsent(
                    String.valueOf(statement.params().get(0)),
                    String.valueOf(statement.params().get(1))
            );
        }
        return true;
    }

    @Override
    public long insertReturningLong(SqlStatement statement) {
        statements.add(statement);
        return nextSequence++;
    }

    @Override
    public long queryLong(SqlStatement statement) {
        statements.add(statement);
        String sql = statement.sql();
        if (sql.contains("select count(*) from updated")) {
            long campaignId = longParam(statement, 0);
            long memberId = longParam(statement, 1);
            String issueKey = campaignId + ":" + memberId;
            long issuedCount = couponIssuedCounts.getOrDefault(campaignId, 0L);
            if (issuedCount >= DEFAULT_COUPON_CAPACITY || !couponIssueKeys.add(issueKey)) {
                return 0;
            }
            couponIssuedCounts.put(campaignId, issuedCount + 1);
            return 1;
        }
        if (sql.contains("select issued_count from coupon_campaigns")) {
            return couponIssuedCounts.getOrDefault(longParam(statement, 0), 0L);
        }
        if (sql.contains("select greatest(issued_count - capacity, 0)")) {
            long issuedCount = couponIssuedCounts.getOrDefault(longParam(statement, 0), 0L);
            return Math.max(issuedCount - DEFAULT_COUPON_CAPACITY, 0);
        }
        if (sql.contains("select count(*) from coupon_issues") && statement.params().size() >= 2) {
            long couponIssueId = longParam(statement, 0);
            String terminalStatus = String.valueOf(statement.params().get(1));
            return terminalStatus.equals(couponIssueStatuses.get(couponIssueId)) ? 1 : 0;
        }
        if (sql.contains("select balance from point_accounts")) {
            return pointBalances.getOrDefault(longParam(statement, 0), DEFAULT_POINT_BALANCE);
        }
        if (sql.contains("request_hash <>")) {
            String key = statement.params().isEmpty() ? "" : String.valueOf(statement.params().get(0));
            String requestHash = statement.params().size() < 2 ? "" : String.valueOf(statement.params().get(1));
            String existingHash = idempotencyHashes.get(key);
            return existingHash != null && !existingHash.equals(requestHash) ? 1 : 0;
        }
        if (sql.contains("select count(*) from idempotency_records")) {
            String key = statement.params().isEmpty() ? "" : String.valueOf(statement.params().get(0));
            return idempotencyHashes.containsKey(key) ? 1 : 0;
        }
        if (sql.contains("select count(*) from scenario_runs")) {
            return nextSequence - 1;
        }
        return 0;
    }

    @Override
    public ScenarioRunRecord queryLatestScenarioRun(SqlStatement statement) {
        statements.add(statement);
        throw new IllegalArgumentException("Local recording executor does not hydrate scenario reports");
    }

    long statementCount() {
        return statements.size();
    }

    List<String> sqlSince(long startIndex) {
        return statements.subList((int) startIndex, statements.size())
                .stream()
                .map(SqlStatement::sql)
                .toList();
    }

    private long longParam(SqlStatement statement, int index) {
        return ((Number) statement.params().get(index)).longValue();
    }
}
