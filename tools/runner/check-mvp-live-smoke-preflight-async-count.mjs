import { execFileSync } from 'node:child_process';

const raw = execFileSync(process.execPath, ['tools/runner/check-mvp-live-smoke-preflight-readiness.mjs'], {
  encoding: 'utf8',
});
const preflight = JSON.parse(raw.trim().split('\n').at(-1));

if (preflight.asyncAcceptedCount !== 1) {
  throw new Error(`Expected preflight asyncAcceptedCount=1, got ${preflight.asyncAcceptedCount}`);
}

if (preflight.routeCount !== 11 || preflight.scenarioCount !== 3) {
  throw new Error(`Expected fixed MVP matrix, got routes=${preflight.routeCount}, scenarios=${preflight.scenarioCount}`);
}

if (preflight.localOnly !== true || preflight.phase2 !== false) {
  throw new Error('MVP live-smoke preflight must stay local-only and exclude Phase 2');
}

console.log(JSON.stringify({
  status: 'passed',
  asyncAcceptedCount: preflight.asyncAcceptedCount,
  routeCount: preflight.routeCount,
  scenarioCount: preflight.scenarioCount,
}));
