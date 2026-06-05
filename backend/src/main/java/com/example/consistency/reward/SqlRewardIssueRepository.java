package com.example.consistency.reward;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlRewardIssueRepository implements RewardIssueRepository {

    private final SqlExecutor executor;

    public SqlRewardIssueRepository(SqlExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean insertNaive(long memberId, RewardType rewardType) {
        return executor.insert(SqlStatement.of(
                """
                insert into reward_issue_attempts (attempt_id, member_id, reward_type, status)
                values (?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                memberId,
                rewardType.name(),
                "ISSUED"
        ));
    }

    @Override
    public boolean insertUnique(long memberId, RewardType rewardType) {
        return executor.insert(SqlStatement.of(
                """
                insert into reward_issues (member_id, reward_type, status)
                values (?, ?, ?)
                on conflict (member_id, reward_type) do nothing
                """,
                memberId,
                rewardType.name(),
                "ISSUED"
        ));
    }

    @Override
    public long issuedCount() {
        return executor.queryLong(SqlStatement.of("""
                select count(*) from reward_issue_attempts
                """));
    }

    @Override
    public long duplicateCount() {
        return executor.queryLong(SqlStatement.of("""
                select greatest(count(*) - count(distinct member_id || ':' || reward_type), 0)
                from reward_issue_attempts
                where reward_type = ?
                """, RewardType.FIRST_LOGIN.name()));
    }
}
