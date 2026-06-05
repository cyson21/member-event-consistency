import { execFileSync } from 'node:child_process';

const output = execFileSync('node', ['tools/runner/run-mvp-live-smoke.mjs', '--dry-run'], {
  encoding: 'utf8',
});
const summary = JSON.parse(output);

if (summary.freshLocalDb !== true) {
  throw new Error('MVP live-smoke dry-run must expose freshLocalDb=true');
}

if (summary.localOnly !== true || summary.phase2 !== false) {
  throw new Error('MVP live-smoke precondition guard must stay local-only and exclude Phase 2');
}

if (summary.routeCount !== 11 || summary.scenarioCount !== 3) {
  throw new Error(`Expected fixed MVP route matrix, got routes=${summary.routeCount}, scenarios=${summary.scenarioCount}`);
}

console.log(JSON.stringify({
  status: 'passed',
  freshLocalDb: summary.freshLocalDb,
  routeCount: summary.routeCount,
  scenarioCount: summary.scenarioCount,
}));

