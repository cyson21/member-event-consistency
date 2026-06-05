package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlBatchExpirationRepository implements BatchExpirationRepository {

    private final SqlExecutor executor;

    public SqlBatchExpirationRepository(SqlExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean tryMarkUsed(long couponIssueId) {
        return tryMarkTerminal(couponIssueId, "USED");
    }

    @Override
    public boolean tryMarkExpired(long couponIssueId) {
        return tryMarkTerminal(couponIssueId, "EXPIRED");
    }

    @Override
    public long usedCount(long couponIssueId) {
        return terminalStatusCount(couponIssueId, "USED");
    }

    @Override
    public long expiredCount(long couponIssueId) {
        return terminalStatusCount(couponIssueId, "EXPIRED");
    }

    private boolean tryMarkTerminal(long couponIssueId, String terminalStatus) {
        return executor.insert(SqlStatement.of(
                """
                update coupon_issues
                set status = ?
                where id = ?
                  and status = ?
                """,
                terminalStatus,
                couponIssueId,
                "ISSUED"
        ));
    }

    private long terminalStatusCount(long couponIssueId, String terminalStatus) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from coupon_issues
                where id = ?
                  and status = ?
                """,
                couponIssueId,
                terminalStatus
        ));
    }
}
