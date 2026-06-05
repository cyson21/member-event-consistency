import { execFileSync } from 'node:child_process';

const raw = execFileSync(
  process.execPath,
  ['tools/runner/check-dependency-bootstrap-readiness.mjs'],
  { encoding: 'utf8' },
);
const readiness = JSON.parse(raw.trim().split('\n').at(-1));

if (!Array.isArray(readiness.webTypecheckPendingSources)) {
  throw new Error('Readiness summary must include webTypecheckPendingSources.');
}

if (readiness.webTypecheckPendingSourceCount !== readiness.webTypecheckPendingSources.length) {
  throw new Error(
    `Expected webTypecheckPendingSourceCount to match source list length, got ${readiness.webTypecheckPendingSourceCount} and ${readiness.webTypecheckPendingSources.length}`,
  );
}

for (const source of [
  'web/src/App.tsx',
  'web/src/main.tsx',
  'web/src/routes/ComparisonMatrix.tsx',
  'web/src/routes/EventTrace.tsx',
  'web/src/routes/RunResult.tsx',
  'web/src/routes/ScenarioConsole.tsx',
]) {
  if (!readiness.webTypecheckPendingSources.includes(source)) {
    throw new Error(`Pending web typecheck inventory is missing ${source}`);
  }
}

const webBlocker = readiness.blockedChecks.find((check) => check.command === 'npm --prefix web test');
if (readiness.webReady === false && !webBlocker) {
  throw new Error('Readiness summary must retain web npm blocked check while web is not ready.');
}

if (readiness.webReady === true && webBlocker) {
  throw new Error('Readiness summary must clear web npm blocked check after web bootstrap is ready.');
}

console.log(JSON.stringify({
  status: 'passed',
  webReady: readiness.webReady,
  webTypecheckPendingSourceCount: readiness.webTypecheckPendingSourceCount,
}));
