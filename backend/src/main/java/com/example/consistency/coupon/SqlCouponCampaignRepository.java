package com.example.consistency.coupon;

import com.example.consistency.persistence.SqlExecutor;
import com.example.consistency.persistence.SqlStatement;

public final class SqlCouponCampaignRepository implements CouponCampaignRepository {

    private final SqlExecutor executor;

    public SqlCouponCampaignRepository(SqlExecutor executor) {
        this.executor = executor;
    }

    public boolean tryReserveCapacity(long campaignId) {
        return executor.insert(SqlStatement.of(
                """
                update coupon_campaigns
                set issued_count = issued_count + 1,
                    updated_at = now()
                where id = ?
                  and status = 'ACTIVE'
                  and issued_count < capacity
                """,
                campaignId
        ));
    }

    public boolean insertIssue(long campaignId, long memberId, String idempotencyKey) {
        return executor.insert(SqlStatement.of(
                """
                insert into coupon_issues (campaign_id, member_id, status, idempotency_key)
                values (?, ?, ?, ?)
                on conflict (campaign_id, member_id) do nothing
                """,
                campaignId,
                memberId,
                "ISSUED",
                idempotencyKey
        ));
    }

    @Override
    public CouponCampaignIssueResult issueWithCapacityGuard(long campaignId, long memberId, String idempotencyKey) {
        long issued = executor.queryLong(SqlStatement.of(
                """
                with eligible as (
                    select id
                    from coupon_campaigns
                    where id = ?
                      and status = 'ACTIVE'
                      and issued_count < capacity
                    for update
                ),
                inserted as (
                    insert into coupon_issues (campaign_id, member_id, status, idempotency_key)
                    select id, ?, 'ISSUED', ? from eligible
                    on conflict (campaign_id, member_id) do nothing
                    returning campaign_id
                ),
                updated as (
                    update coupon_campaigns c
                    set issued_count = issued_count + 1,
                        updated_at = now()
                    from inserted i
                    where c.id = i.campaign_id
                    returning c.id
                )
                select count(*) from updated
                """,
                campaignId,
                memberId,
                idempotencyKey
        ));
        if (issued > 0) {
            return CouponCampaignIssueResult.success();
        }
        return CouponCampaignIssueResult.capacityRejected();
    }

    @Override
    public long issuedCount(long campaignId) {
        return executor.queryLong(SqlStatement.of(
                """
                select issued_count from coupon_campaigns
                where id = ?
                """,
                campaignId
        ));
    }

    @Override
    public long overIssueCount(long campaignId) {
        return executor.queryLong(SqlStatement.of(
                """
                select greatest(issued_count - capacity, 0)
                from coupon_campaigns
                where id = ?
                """,
                campaignId
        ));
    }
}
