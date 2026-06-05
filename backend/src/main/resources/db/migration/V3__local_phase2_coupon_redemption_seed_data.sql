-- local-only Phase 2 Coupon Redemption smoke seed data.

insert into members (id, email, status)
values
    (96001, 'coupon-redemption-db-guard-96001@local.test', 'ACTIVE'),
    (96002, 'coupon-redemption-idempotency-96002@local.test', 'ACTIVE'),
    (96003, 'coupon-redemption-mismatch-96003@local.test', 'ACTIVE')
on conflict (id) do nothing;

insert into coupon_campaigns (id, code, capacity, issued_count, status)
values
    (96000, 'PHASE2-COUPON-REDEMPTION', 3, 3, 'ACTIVE')
on conflict (id) do nothing;

insert into coupon_issues (id, campaign_id, member_id, status, idempotency_key)
values
    (96001, 96000, 96001, 'ISSUED', 'phase2-coupon-redemption-96001'),
    (96002, 96000, 96002, 'ISSUED', 'phase2-coupon-redemption-96002'),
    (96003, 96000, 96003, 'ISSUED', 'phase2-coupon-redemption-96003')
on conflict (id) do nothing;

select setval(pg_get_serial_sequence('members', 'id'), greatest((select max(id) from members), 1), true);
select setval(pg_get_serial_sequence('coupon_campaigns', 'id'), greatest((select max(id) from coupon_campaigns), 1), true);
select setval(pg_get_serial_sequence('coupon_issues', 'id'), greatest((select max(id) from coupon_issues), 1), true);
