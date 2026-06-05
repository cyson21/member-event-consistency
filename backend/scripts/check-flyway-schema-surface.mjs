import { readFileSync } from 'node:fs';

const schema = readFileSync(
  new URL('../src/main/resources/db/migration/V1__init.sql', import.meta.url),
  'utf8',
);
const seedData = readFileSync(
  new URL('../src/main/resources/db/migration/V2__local_mvp_seed_data.sql', import.meta.url),
  'utf8',
);
const normalized = schema.replace(/\s+/g, ' ').trim().toLowerCase();
const normalizedSeedData = seedData.replace(/\s+/g, ' ').trim().toLowerCase();

function requireFragment(fragment) {
  if (!normalized.includes(fragment.toLowerCase())) {
    throw new Error(`V1__init.sql is missing required schema fragment: ${fragment}`);
  }
}

function requireSeedFragment(fragment) {
  if (!normalizedSeedData.includes(fragment.toLowerCase())) {
    throw new Error(`V2__local_mvp_seed_data.sql is missing required seed fragment: ${fragment}`);
  }
}

function rejectFragment(fragment) {
  if (normalized.includes(fragment.toLowerCase()) || normalizedSeedData.includes(fragment.toLowerCase())) {
    throw new Error(`Flyway migrations must not include out-of-scope fragment: ${fragment}`);
  }
}

for (const table of [
  'create table members',
  'create table point_accounts',
  'create table point_ledger',
  'create table reward_issue_attempts',
  'create table reward_issues',
  'create table coupon_campaigns',
  'create table coupon_issues',
  'create table idempotency_records',
  'create table outbox_events',
  'create table scenario_runs',
  'create table scenario_metrics',
  'create table lock_attempts',
  'create table queue_events',
]) {
  requireFragment(table);
}

for (const invariantGuard of [
  'email varchar(255) not null unique',
  'constraint chk_point_accounts_balance_non_negative check (balance >= 0)',
  'event_id uuid not null unique',
  'unique (member_id, reward_type)',
  'attempt_id uuid not null unique',
  'code varchar(80) not null unique',
  'constraint chk_coupon_campaigns_capacity_non_negative check (capacity >= 0)',
  'constraint chk_coupon_campaigns_issued_count_non_negative check (issued_count >= 0)',
  'constraint chk_coupon_campaigns_issued_count_capacity check (issued_count <= capacity)',
  'unique (campaign_id, member_id)',
  'idempotency_key varchar(120) not null unique',
  'request_hash varchar(128) not null',
  'idempotency_key varchar(120) unique',
  'run_sequence bigserial unique',
]) {
  requireFragment(invariantGuard);
}

for (const observabilityFragment of [
  'payload jsonb not null',
  'retry_count integer not null default 0',
  'next_attempt_at timestamptz',
  'accepted_count integer not null default 0',
  'completed_count integer not null default 0',
  'lock_key varchar(180) not null',
  'wait_ms bigint not null',
  'lease_ms bigint not null',
  'business_key varchar(180)',
  'lag_ms bigint not null default 0',
]) {
  requireFragment(observabilityFragment);
}

for (const index of [
  'create index idx_scenario_runs_scenario_strategy_started_at on scenario_runs (scenario, strategy, started_at desc)',
  'create index idx_point_ledger_member_id on point_ledger (member_id)',
  'create index idx_reward_issue_attempts_member_reward on reward_issue_attempts (member_id, reward_type)',
  'create index idx_reward_issues_member_id on reward_issues (member_id)',
  'create index idx_coupon_issues_campaign_id on coupon_issues (campaign_id)',
  'create index idx_outbox_events_status_next_attempt on outbox_events (status, next_attempt_at)',
  'create index idx_lock_attempts_run_id on lock_attempts (run_id)',
  'create index idx_queue_events_run_id on queue_events (run_id)',
]) {
  requireFragment(index);
}

for (const outOfScope of [
  'kafka',
  'lock:member',
  'payment_provider',
  'sms_provider',
  'email_provider',
  'push_provider',
]) {
  rejectFragment(outOfScope);
}

for (const seedFragment of [
  'local-only MVP smoke seed data',
  'insert into members (id, email, status)',
  '93001',
  '93002',
  '93003',
  '94001',
  '94004',
  'campaign_id::bigint * 100000 + request_index',
  '95001',
  '95005',
  '95006',
  '95007',
  'generate_series(1, 8)',
  'insert into coupon_campaigns (id, code, capacity, issued_count, status)',
  'MVP-COUPON-RABBITMQ',
  'insert into point_accounts (member_id, balance)',
  'on conflict',
  'setval(pg_get_serial_sequence',
]) {
  requireSeedFragment(seedFragment);
}
