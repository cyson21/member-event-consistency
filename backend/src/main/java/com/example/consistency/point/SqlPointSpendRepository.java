package com.example.consistency.point;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

import java.util.UUID;

public final class SqlPointSpendRepository implements PointSpendRepository {

    private final SqlExecutor executor;

    public SqlPointSpendRepository(SqlExecutor executor) {
        this.executor = executor;
    }

    @Override
    public long balanceForUpdate(long memberId) {
        return executor.queryLong(SqlStatement.of(
                """
                select balance from point_accounts
                where member_id = ?
                for update
                """,
                memberId
        ));
    }

    @Override
    public boolean tryDebit(long memberId, long spendAmount) {
        return executor.insert(SqlStatement.of(
                """
                update point_accounts
                set balance = balance - ?,
                    version = version + 1,
                    updated_at = now()
                where member_id = ?
                  and balance >= ?
                """,
                spendAmount,
                memberId,
                spendAmount
        ));
    }

    @Override
    public boolean insertLedger(UUID eventId, long memberId, long amount, String idempotencyKey) {
        return executor.insert(SqlStatement.of(
                """
                insert into point_ledger (event_id, member_id, amount, ledger_type, idempotency_key)
                values (?, ?, ?, ?, ?)
                on conflict (idempotency_key) do nothing
                """,
                eventId,
                memberId,
                amount,
                "SPEND",
                idempotencyKey
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
}
