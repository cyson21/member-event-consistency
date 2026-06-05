-- local-only MVP smoke seed data for the fixed route IDs used by the portfolio demo.

insert into members (id, email, status)
values
    (93001, 'reward-naive-93001@local.test', 'ACTIVE'),
    (93002, 'reward-db-guard-93002@local.test', 'ACTIVE'),
    (93003, 'reward-redis-guard-93003@local.test', 'ACTIVE'),
    (95001, 'point-naive-95001@local.test', 'ACTIVE'),
    (95005, 'point-row-lock-95005@local.test', 'ACTIVE'),
    (95006, 'point-conditional-95006@local.test', 'ACTIVE'),
    (95007, 'point-idempotency-95007@local.test', 'ACTIVE')
on conflict (id) do nothing;

insert into members (id, email, status)
select member_id,
       'coupon-' || member_id || '@local.test',
       'ACTIVE'
from (
    select campaign_id::bigint * 100000 + request_index as member_id
    from (values (94001), (94002), (94003), (94004)) as campaign(campaign_id),
         generate_series(1, 8) as request_index
) as coupon_members
on conflict (id) do nothing;

insert into coupon_campaigns (id, code, capacity, issued_count, status)
values
    (94001, 'MVP-COUPON-NAIVE', 3, 0, 'ACTIVE'),
    (94002, 'MVP-COUPON-DB-GUARD', 3, 0, 'ACTIVE'),
    (94003, 'MVP-COUPON-REDIS-GUARD', 3, 0, 'ACTIVE'),
    (94004, 'MVP-COUPON-RABBITMQ', 3, 0, 'ACTIVE')
on conflict (id) do nothing;

insert into point_accounts (member_id, balance)
values
    (95001, 1000),
    (95005, 1000),
    (95006, 1000),
    (95007, 1000)
on conflict (member_id) do nothing;

select setval(pg_get_serial_sequence('members', 'id'), greatest((select max(id) from members), 1), true);
select setval(pg_get_serial_sequence('coupon_campaigns', 'id'), greatest((select max(id) from coupon_campaigns), 1), true);
