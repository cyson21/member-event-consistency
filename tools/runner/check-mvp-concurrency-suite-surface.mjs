import { readFileSync } from 'node:fs';

const scenarioCli = readFileSync(
  new URL('../../backend/src/main/java/com/example/consistency/runner/ScenarioCli.java', import.meta.url),
  'utf8',
);
const scenarioCliTest = readFileSync(
  new URL('../../backend/src/test/java/com/example/consistency/runner/ScenarioCliTest.java', import.meta.url),
  'utf8',
);
const dashboardSync = readFileSync(
  new URL('../../web/scripts/check-mvp-concurrency-dashboard-sync.mjs', import.meta.url),
  'utf8',
);
const localSuite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'MVP_CONCURRENCY',
  'mvpConcurrencyProbeSuite',
  'FIRST_LOGIN_REWARD_CONCURRENT',
  'COUPON_HOT_CAMPAIGN',
  'POINT_CONCURRENT',
  'phase2EntryCount',
  'passingInvariantCount',
]) {
  if (!scenarioCli.includes(fragment)) {
    throw new Error(`ScenarioCli is missing MVP concurrency fragment: ${fragment}`);
  }
}

for (const fragment of [
  'mvpConcurrencyProbeCommandReturnsFixedMvpProbeSuiteEvidence',
  '\\"probe\\":\\"MVP_CONCURRENCY\\"',
  '\\"scenarioCount\\":3',
  '\\"entryCount\\":3',
  '\\"passingInvariantCount\\":3',
  '\\"phase2EntryCount\\":0',
]) {
  if (!scenarioCliTest.includes(fragment)) {
    throw new Error(`ScenarioCliTest is missing MVP concurrency assertion: ${fragment}`);
  }
}

for (const fragment of [
  'ScenarioCli',
  '--probe',
  'MVP_CONCURRENCY',
  'concurrencyProbeSummary',
  'concurrencyProbeEntries',
  'parseConcurrencyEntries',
  'assertEntriesMatchDashboard',
  'phase2EntryCount',
]) {
  if (!dashboardSync.includes(fragment)) {
    throw new Error(`MVP concurrency dashboard sync is missing fragment: ${fragment}`);
  }
}

for (const fragment of [
  'check-mvp-concurrency-suite-surface.mjs',
  'check-mvp-concurrency-dashboard-sync.mjs',
  'completedChecks',
]) {
  if (!localSuite.includes(fragment)) {
    throw new Error(`Local verification suite is missing MVP concurrency suite fragment: ${fragment}`);
  }
}
