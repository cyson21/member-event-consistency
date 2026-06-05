import { readFileSync } from 'node:fs';

const verifier = readFileSync(new URL('./check-mvp-scope-surface.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'ScenarioType.java',
  'FIRST_LOGIN_REWARD',
  'COUPON_CAMPAIGN_ISSUE',
  'POINT_SPEND',
  'phase2EntryCount',
  'ScenarioApiRouter.java',
  'ScenarioCli.java',
  'App.tsx',
  'rejectImplementationFragment',
  'Coupon Redemption / Usage',
  'P6 Phase 2 Scenario Gate',
]) {
  if (!verifier.includes(fragment)) {
    throw new Error(`MVP scope verifier is missing fragment: ${fragment}`);
  }
}
