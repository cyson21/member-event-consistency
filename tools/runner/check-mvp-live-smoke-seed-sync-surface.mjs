import { readFileSync } from 'node:fs';

const sync = readFileSync(new URL('./check-mvp-live-smoke-seed-sync.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'V2__local_mvp_seed_data.sql',
  'ScenarioCli.java',
  'FIRST_LOGIN_REWARD',
  'COUPON_CAMPAIGN_ISSUE',
  'POINT_SPEND',
  '93001L + strategy.ordinal()',
  '94001L + strategy.ordinal()',
  '95001L + strategy.ordinal()',
  'MVP-COUPON-RABBITMQ',
  'generate_series(1, 8)',
  'phase2',
  'local-only',
]) {
  if (!sync.includes(fragment)) {
    throw new Error(`MVP live smoke seed sync is missing required fragment: ${fragment}`);
  }
}
