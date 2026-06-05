import { readFileSync } from 'node:fs';

const app = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const consoleRoute = readFileSync(new URL('../src/routes/ScenarioConsole.tsx', import.meta.url), 'utf8');
const resultRoute = readFileSync(new URL('../src/routes/RunResult.tsx', import.meta.url), 'utf8');
const comparisonRoute = readFileSync(new URL('../src/routes/ComparisonMatrix.tsx', import.meta.url), 'utf8');
const eventTraceRoute = readFileSync(new URL('../src/routes/EventTrace.tsx', import.meta.url), 'utf8');
const sqlRecordingSyncScript = readFileSync(
  new URL('./check-sql-recording-dashboard-sync.mjs', import.meta.url),
  'utf8',
);

const requiredFragments = [
  "'DB_ROW_LOCK'",
  "'CONDITIONAL_UPDATE'",
  "'IDEMPOTENCY_REPLAY'",
  "'COUPON_CAMPAIGN_ISSUE|NAIVE'",
  "'COUPON_CAMPAIGN_ISSUE|DB_GUARD'",
  "'COUPON_CAMPAIGN_ISSUE|REDIS_LOCK_DB_GUARD'",
  "'COUPON_CAMPAIGN_ISSUE|RABBITMQ_DB_GUARD'",
  "'POINT_SPEND|NAIVE'",
  "'POINT_SPEND|DB_ROW_LOCK'",
  "'POINT_SPEND|CONDITIONAL_UPDATE'",
  "'POINT_SPEND|IDEMPOTENCY_REPLAY'",
  'strategyOptionsByScenario',
  'finalPointBalance',
  'idempotencyReplayCount',
  'couponIssuedCount',
  'comparisonEntries',
  'concurrencyProbeSummary',
  'concurrencyProbeEntries',
  'MVP_CONCURRENCY',
  'FIRST_LOGIN_REWARD_CONCURRENT',
  'COUPON_HOT_CAMPAIGN',
  'POINT_CONCURRENT',
  'passingInvariantCount: 3',
  'MVP_SMOKE',
  'brokenNaiveCount: 3',
  'passingGuardedCount: 8',
  'asyncAcceptedCount: 1',
  'phase2EntryCount: 0',
  'sqlRecordingSummary',
  'sqlEvidence',
  'duplicate-prone attempt insert -> fake follow-up outbox rows',
  'campaign row lock -> coupon issue insert -> issued count update',
  'hash check -> replay lookup -> conditional debit once -> replay lookup',
  "backend: 'SQL_RECORDING'",
  'routeCount: 11',
  'sqlStatementCount',
  'Compare Runs',
];

for (const fragment of requiredFragments) {
  if (!app.includes(fragment)) {
    throw new Error(`App.tsx is missing required dashboard fixture fragment: ${fragment}`);
  }
}

if (app.includes("'POINT_SPEND|DB_GUARD'")) {
  throw new Error('Point Spend dashboard fixture must not use DB_GUARD as the point strategy label');
}

if (!consoleRoute.includes('strategies: ScenarioConsoleState[\'strategy\'][]')) {
  throw new Error('ScenarioConsole must still receive the filtered strategy list from App');
}

for (const fragment of [
  'Scenario preset',
  'Validation status',
  'Supported strategies',
  'scenarioPresets',
  'validationIssues',
  'estimatedRunShape',
  'MVP-only fixture',
]) {
  if (!consoleRoute.includes(fragment)) {
    throw new Error(`ScenarioConsole is missing required console fragment: ${fragment}`);
  }
}

for (const fragment of ['Final point balance', 'Idempotency replay', 'Coupon issued', 'Over issue']) {
  if (!resultRoute.includes(fragment)) {
    throw new Error(`RunResult is missing metric display: ${fragment}`);
  }
}

for (const fragment of [
  'Local fixture',
  'not measured',
  'measurementLabel',
]) {
  if (!resultRoute.includes(fragment) && !app.includes(fragment)) {
    throw new Error(`RabbitMQ fixture latency display is missing label fragment: ${fragment}`);
  }
}

if (!resultRoute.includes('Idempotency hash mismatch') || !app.includes('idempotencyHashMismatchCount')) {
  throw new Error('Point Spend dashboard fixtures must expose idempotency hash mismatch metrics');
}

for (const fragment of [
  'Run Result Comparison',
  'MVP_SMOKE',
  'Naive failures',
  'Guarded passes',
  'Async accepted',
  'SQL recording',
  'MVP concurrency probe',
  'Local only',
  'SQL statements',
  'Passing invariants',
  'Phase 2 entries',
  'Accepted / Completed',
  'SQL evidence',
]) {
  if (!comparisonRoute.includes(fragment)) {
    throw new Error(`ComparisonMatrix is missing required display fragment: ${fragment}`);
  }
}

for (const fragment of [
  'Trace summary',
  'Stage filter',
  'traceStageOptions',
  'visibleEvents',
  'stageCounts',
  'RabbitMQ',
  'Redis',
  'Outbox',
]) {
  if (!eventTraceRoute.includes(fragment)) {
    throw new Error(`EventTrace is missing required display fragment: ${fragment}`);
  }
}

for (const fragment of [
  'ScenarioCli',
  '--backend',
  'SQL_RECORDING',
  '--suite',
  'MVP_SMOKE',
  'sqlStatementCount',
  'routeEntries',
  'comparisonEntries',
  'parseComparisonEntries',
  'acceptedCount',
  'completedCount',
  'JSON.parse',
]) {
  if (!sqlRecordingSyncScript.includes(fragment)) {
    throw new Error(`SQL recording dashboard sync script is missing fragment: ${fragment}`);
  }
}

const mvpConcurrencySyncScript = readFileSync(
  new URL('./check-mvp-concurrency-dashboard-sync.mjs', import.meta.url),
  'utf8',
);

for (const fragment of [
  'ScenarioCli',
  '--probe',
  'MVP_CONCURRENCY',
  'concurrencyProbeEntries',
  'parseConcurrencyEntries',
  'passingInvariantCount',
  'phase2EntryCount',
  'JSON.parse',
]) {
  if (!mvpConcurrencySyncScript.includes(fragment)) {
    throw new Error(`MVP concurrency dashboard sync script is missing fragment: ${fragment}`);
  }
}
