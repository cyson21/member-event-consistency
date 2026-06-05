import { readFileSync } from 'node:fs';

const verifier = readFileSync(new URL('./check-flyway-schema-surface.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'V1__init.sql',
  'reward_issues',
  'unique (member_id, reward_type)',
  'chk_point_accounts_balance_non_negative',
  'chk_coupon_campaigns_issued_count_capacity',
  'idempotency_records',
  'outbox_events',
  'lock_attempts',
  'queue_events',
  'idx_outbox_events_status_next_attempt',
]) {
  if (!verifier.includes(fragment)) {
    throw new Error(`Flyway schema verifier is missing fragment: ${fragment}`);
  }
}
