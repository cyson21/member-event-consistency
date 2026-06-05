package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlCouponRedemptionRepository implements CouponRedemptionRepository {

    private final SqlExecutor executor;

    public SqlCouponRedemptionRepository(SqlExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean tryMarkUsed(long couponIssueId) {
        return executor.insert(SqlStatement.of(
                """
                update coupon_issues
                set status = ?
                where id = ?
                  and status = ?
                """,
                "USED",
                couponIssueId,
                "ISSUED"
        ));
    }

    @Override
    public boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef) {
        return executor.insert(SqlStatement.of(
                """
                insert into idempotency_records (idempotency_key, request_hash, response_ref, status)
                values (?, ?, ?, ?)
                on conflict (idempotency_key) do nothing
                """,
                idempotencyKey,
                requestHash,
                responseRef,
                "COMPLETED"
        ));
    }

    @Override
    public long replayCount(String idempotencyKey) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from idempotency_records
                where idempotency_key = ?
                """,
                idempotencyKey
        ));
    }

    @Override
    public long requestHashMismatchCount(String idempotencyKey, String requestHash) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from idempotency_records
                where idempotency_key = ?
                  and request_hash <> ?
                """,
                idempotencyKey,
                requestHash
        ));
    }

    @Override
    public long usedCount(long couponIssueId) {
        return executor.queryLong(SqlStatement.of(
                """
                select count(*) from coupon_issues
                where id = ?
                  and status = ?
                """,
                couponIssueId,
                "USED"
        ));
    }
}
