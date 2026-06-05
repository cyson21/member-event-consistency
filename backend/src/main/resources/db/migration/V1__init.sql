create table members (
    id bigserial primary key,
    email varchar(255) not null unique,
    status varchar(32) not null,
    created_at timestamptz not null default now()
);

create table point_accounts (
    member_id bigint primary key references members (id),
    balance bigint not null default 0,
    version bigint not null default 0,
    updated_at timestamptz not null default now(),
    constraint chk_point_accounts_balance_non_negative check (balance >= 0)
);

create table point_ledger (
    id bigserial primary key,
    event_id uuid not null unique,
    member_id bigint not null references members (id),
    amount bigint not null,
    ledger_type varchar(32) not null,
    idempotency_key varchar(120) unique,
    created_at timestamptz not null default now()
);

create table reward_issues (
    id bigserial primary key,
    member_id bigint not null references members (id),
    reward_type varchar(64) not null,
    status varchar(32) not null,
    issued_at timestamptz not null default now(),
    unique (member_id, reward_type)
);

create table reward_issue_attempts (
    id bigserial primary key,
    attempt_id uuid not null unique,
    member_id bigint not null references members (id),
    reward_type varchar(64) not null,
    status varchar(32) not null,
    issued_at timestamptz not null default now()
);

create table coupon_campaigns (
    id bigserial primary key,
    code varchar(80) not null unique,
    capacity integer not null,
    issued_count integer not null default 0,
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_coupon_campaigns_capacity_non_negative check (capacity >= 0),
    constraint chk_coupon_campaigns_issued_count_non_negative check (issued_count >= 0),
    constraint chk_coupon_campaigns_issued_count_capacity check (issued_count <= capacity)
);

create table coupon_issues (
    id bigserial primary key,
    campaign_id bigint not null references coupon_campaigns (id),
    member_id bigint not null references members (id),
    status varchar(32) not null,
    idempotency_key varchar(120) unique,
    issued_at timestamptz not null default now(),
    unique (campaign_id, member_id)
);

create table idempotency_records (
    id bigserial primary key,
    idempotency_key varchar(120) not null unique,
    request_hash varchar(128) not null,
    response_ref varchar(255),
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table outbox_events (
    id bigserial primary key,
    event_id uuid not null unique,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(120) not null,
    event_type varchar(120) not null,
    payload jsonb not null,
    status varchar(32) not null,
    retry_count integer not null default 0,
    next_attempt_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table scenario_runs (
    run_sequence bigserial unique,
    id uuid primary key,
    scenario varchar(80) not null,
    strategy varchar(80) not null,
    status varchar(32) not null,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    accepted_count integer not null default 0,
    completed_count integer not null default 0
);

create table scenario_metrics (
    id bigserial primary key,
    run_id uuid not null references scenario_runs (id),
    metric_name varchar(120) not null,
    metric_value numeric(20, 4) not null,
    created_at timestamptz not null default now()
);

create index idx_scenario_runs_scenario_strategy_started_at on scenario_runs (scenario, strategy, started_at desc);

create table lock_attempts (
    id bigserial primary key,
    run_id uuid references scenario_runs (id),
    lock_key varchar(180) not null,
    owner_id varchar(120) not null,
    wait_ms bigint not null,
    lease_ms bigint not null,
    result varchar(32) not null,
    created_at timestamptz not null default now()
);

create table queue_events (
    id bigserial primary key,
    run_id uuid references scenario_runs (id),
    queue_name varchar(120) not null,
    message_id varchar(120) not null,
    business_key varchar(180),
    status varchar(32) not null,
    retry_count integer not null default 0,
    lag_ms bigint not null default 0,
    created_at timestamptz not null default now()
);

create index idx_point_ledger_member_id on point_ledger (member_id);
create index idx_reward_issue_attempts_member_reward on reward_issue_attempts (member_id, reward_type);
create index idx_reward_issues_member_id on reward_issues (member_id);
create index idx_coupon_issues_campaign_id on coupon_issues (campaign_id);
create index idx_outbox_events_status_next_attempt on outbox_events (status, next_attempt_at);
create index idx_lock_attempts_run_id on lock_attempts (run_id);
create index idx_queue_events_run_id on queue_events (run_id);
