import { execFileSync } from 'node:child_process';

const raw = execFileSync(
  process.execPath,
  ['tools/runner/check-dependency-free-regression.mjs'],
  { encoding: 'utf8' },
);
const summary = JSON.parse(raw.trim().split('\n').at(-1));

if (!Array.isArray(summary.executedMainTestClasses)) {
  throw new Error('Regression summary must include executedMainTestClasses.');
}

if (summary.executedMainTests !== summary.executedMainTestClasses.length) {
  throw new Error(
    `Expected executedMainTests to match class manifest length, got ${summary.executedMainTests} and ${summary.executedMainTestClasses.length}`,
  );
}

const expectedClasses = [
  'com.example.consistency.api.ScenarioApiRouterFactoryTest',
  'com.example.consistency.coupon.CouponCampaignServiceTest',
  'com.example.consistency.point.PointSpendServiceTest',
  'com.example.consistency.reward.FirstLoginRewardServiceTest',
  'com.example.consistency.runner.ScenarioCliTest',
  'com.example.consistency.scenario.ScenarioRunReportRepositoryTest',
];

for (const className of expectedClasses) {
  if (!summary.executedMainTestClasses.includes(className)) {
    throw new Error(`Regression manifest is missing executed test class: ${className}`);
  }
}

const packageCounts = summary.executedMainTestPackageCounts ?? {};
for (const packageName of [
  'api',
  'coupon',
  'lock',
  'persistence',
  'point',
  'reward',
  'runner',
  'scenario',
]) {
  if (!Number.isInteger(packageCounts[packageName]) || packageCounts[packageName] < 1) {
    throw new Error(`Regression manifest is missing package coverage count: ${packageName}`);
  }
}

console.log(JSON.stringify({
  status: 'passed',
  executedMainTests: summary.executedMainTests,
  packageCounts,
}));
