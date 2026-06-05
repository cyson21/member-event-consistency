import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const seedPath = join(repoRoot, 'backend/src/main/resources/db/migration/V2__local_mvp_seed_data.sql');
const runnerPath = join(repoRoot, 'backend/src/main/java/com/example/consistency/runner/ScenarioCli.java');

const seed = readFileSync(seedPath, 'utf8');
const runner = readFileSync(runnerPath, 'utf8');

function requireFragment(sourceName, source, fragment) {
  if (!source.includes(fragment)) {
    throw new Error(`${sourceName} is missing required fragment: ${fragment}`);
  }
}

function rejectFragment(sourceName, source, fragment) {
  if (source.toLowerCase().includes(fragment.toLowerCase())) {
    throw new Error(`${sourceName} must not include out-of-scope fragment: ${fragment}`);
  }
}

for (const fragment of [
  'FIRST_LOGIN_REWARD',
  'COUPON_CAMPAIGN_ISSUE',
  'POINT_SPEND',
  '93001L + strategy.ordinal()',
  '94001L + strategy.ordinal()',
  '95001L + strategy.ordinal()',
  '"requestCount", "5"',
  '"capacity", "3"',
  '"requestCount", "8"',
  '"initialBalance", "1000"',
  '"spendAmount", "700"',
  '"requestCount", "2"',
]) {
  requireFragment('ScenarioCli.java', runner, fragment);
}

for (const fragment of [
  'local-only MVP smoke seed data',
  '(93001,',
  '(93002,',
  '(93003,',
  '(95001,',
  '(95005,',
  '(95006,',
  '(95007,',
  'from (values (94001), (94002), (94003), (94004))',
  'campaign_id::bigint * 100000 + request_index',
  'generate_series(1, 8)',
  "(94001, 'MVP-COUPON-NAIVE', 3, 0, 'ACTIVE')",
  "(94002, 'MVP-COUPON-DB-GUARD', 3, 0, 'ACTIVE')",
  "(94003, 'MVP-COUPON-REDIS-GUARD', 3, 0, 'ACTIVE')",
  "(94004, 'MVP-COUPON-RABBITMQ', 3, 0, 'ACTIVE')",
  '(95001, 1000)',
  '(95005, 1000)',
  '(95006, 1000)',
  '(95007, 1000)',
  'on conflict',
]) {
  requireFragment('V2__local_mvp_seed_data.sql', seed, fragment);
}

for (const outOfScope of [
  'coupon-redemption',
  'batch-expiration',
  'daily-attendance',
  'kafka',
  'payment_provider',
  'sms_provider',
  'email_provider',
  'push_provider',
]) {
  rejectFragment('V2__local_mvp_seed_data.sql', seed, outOfScope);
}

console.log(JSON.stringify({
  localOnly: true,
  phase2: false,
  rewardMembers: [93001, 93002, 93003],
  couponCampaigns: [94001, 94002, 94003, 94004],
  couponRecipientsPerCampaign: 8,
  pointAccounts: [95001, 95005, 95006, 95007],
}));
