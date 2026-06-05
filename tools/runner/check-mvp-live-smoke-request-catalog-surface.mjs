import { readFileSync } from 'node:fs';

const verifier = readFileSync(new URL('./check-mvp-live-smoke-request-catalog.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'docs/internal/live-smoke/mvp-route-requests.http',
  'ScenarioRunController.java',
  'V2__local_mvp_seed_data.sql',
  'first-login-reward/runs',
  'coupon-campaign-issue/runs',
  'point-spend/runs',
  'RABBITMQ_DB_GUARD',
  'IDEMPOTENCY_REPLAY',
  'fresh local DB',
  'phase2',
  'local-only',
]) {
  if (!verifier.includes(fragment)) {
    throw new Error(`MVP live smoke request catalog verifier is missing required fragment: ${fragment}`);
  }
}
