import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-dependency-free-regression-manifest-surface.mjs',
  'check-dependency-free-regression-manifest.mjs',
  'const expectedCompletedChecks = 70',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing regression manifest fragment: ${fragment}`);
  }
}

const regression = readFileSync(new URL('./check-dependency-free-regression.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'executedMainTestClasses',
  'executedMainTestPackageCounts',
  'testFiles.map(className)',
]) {
  if (!regression.includes(fragment)) {
    throw new Error(`Dependency-free regression runner is missing manifest fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-dependency-free-regression-manifest.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'ScenarioApiRouterFactoryTest',
  'CouponCampaignServiceTest',
  'PointSpendServiceTest',
  'FirstLoginRewardServiceTest',
  'ScenarioCliTest',
  'ScenarioRunReportRepositoryTest',
  'executedMainTestPackageCounts',
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`Regression manifest guard is missing fragment: ${fragment}`);
  }
}
