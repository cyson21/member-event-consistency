-- local-only Phase 2 Batch Expiration smoke seed data.

insert into members (id, email, status)
values
    (96101, 'batch-expiration-user-use-96101@local.test', 'ACTIVE'),
    (96102, 'batch-expiration-scheduled-96102@local.test', 'ACTIVE')
on conflict (id) do nothing;

insert into coupon_campaigns (id, code, capacity, issued_count, status)
values
    (96100, 'PHASE2-BATCH-EXPIRATION', 2, 2, 'ACTIVE')
on conflict (id) do nothing;

insert into coupon_issues (id, campaign_id, member_id, status, idempotency_key)
values
    (96101, 96100, 96101, 'ISSUED', 'phase2-batch-expiration-96101'),
    (96102, 96100, 96102, 'ISSUED', 'phase2-batch-expiration-96102')
on conflict (id) do nothing;

select setval(pg_get_serial_sequence('members', 'id'), greatest((select max(id) from members), 1), true);
select setval(pg_get_serial_sequence('coupon_campaigns', 'id'), greatest((select max(id) from coupon_campaigns), 1), true);
select setval(pg_get_serial_sequence('coupon_issues', 'id'), greatest((select max(id) from coupon_issues), 1), true);
