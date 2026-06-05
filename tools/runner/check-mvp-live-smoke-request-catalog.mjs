import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const catalogPath = join(repoRoot, 'docs/internal/live-smoke/mvp-route-requests.http');
const controllerPath = join(repoRoot, 'backend/src/main/java/com/example/consistency/web/ScenarioRunController.java');
const seedPath = join(repoRoot, 'backend/src/main/resources/db/migration/V2__local_mvp_seed_data.sql');

const catalog = readFileSync(catalogPath, 'utf8');
const controller = readFileSync(controllerPath, 'utf8');
const seed = readFileSync(seedPath, 'utf8');

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

const routeExpectations = [
  {
    route: '/api/scenarios/first-login-reward/runs',
    ids: [93001, 93002, 93003],
    strategies: ['NAIVE', 'DB_GUARD', 'REDIS_LOCK_DB_GUARD'],
    countFragment: '"requestCount": 5',
  },
  {
    route: '/api/scenarios/coupon-campaign-issue/runs',
    ids: [94001, 94002, 94003, 94004],
    strategies: ['NAIVE', 'DB_GUARD', 'REDIS_LOCK_DB_GUARD', 'RABBITMQ_DB_GUARD'],
    countFragment: '"requestCount": 8',
  },
  {
    route: '/api/scenarios/point-spend/runs',
    ids: [95001, 95005, 95006, 95007],
    strategies: ['NAIVE', 'DB_ROW_LOCK', 'CONDITIONAL_UPDATE', 'IDEMPOTENCY_REPLAY'],
    countFragment: '"requestCount": 2',
  },
];

for (const fragment of [
  'fresh local DB',
  'local-only MVP live smoke requests',
  'http://localhost:8080',
  'Content-Type: application/json',
]) {
  requireFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, fragment);
}

for (const expectation of routeExpectations) {
  requireFragment('ScenarioRunController.java', controller, expectation.route);
  requireFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, expectation.route);
  requireFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, expectation.countFragment);
  for (const id of expectation.ids) {
    requireFragment('V2__local_mvp_seed_data.sql', seed, String(id));
    requireFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, String(id));
  }
  for (const strategy of expectation.strategies) {
    requireFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, `"strategy": "${strategy}"`);
  }
}

for (const fragment of [
  '"capacity": 3',
  '"transientRetryCount": 1',
  '"dlqCount": 0',
  '"initialBalance": 1000',
  '"spendAmount": 700',
  '"idempotencyKey": "spend-95007-001"',
]) {
  requireFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, fragment);
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
  rejectFragment('docs/internal/live-smoke/mvp-route-requests.http', catalog, outOfScope);
}

console.log(JSON.stringify({
  localOnly: true,
  phase2: false,
  routeCount: 11,
  scenarioCount: 3,
  freshLocalDb: true,
}));
