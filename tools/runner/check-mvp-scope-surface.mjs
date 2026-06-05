import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');

const files = {
  scenarioType: 'backend/src/main/java/com/example/consistency/scenario/ScenarioType.java',
  strategyType: 'backend/src/main/java/com/example/consistency/scenario/StrategyType.java',
  router: 'backend/src/main/java/com/example/consistency/api/ScenarioApiRouter.java',
  controller: 'backend/src/main/java/com/example/consistency/web/ScenarioRunController.java',
  cli: 'backend/src/main/java/com/example/consistency/runner/ScenarioCli.java',
  app: 'web/src/App.tsx',
  tracking: 'docs/project-tracking.md',
  todo: 'TODO.md',
};

const mvpScenarios = [
  'FIRST_LOGIN_REWARD',
  'COUPON_CAMPAIGN_ISSUE',
  'POINT_SPEND',
];

const selectedPhase2Scenarios = [
  'COUPON_REDEMPTION',
  'BATCH_EXPIRATION',
];

const implementedScenarios = [
  ...mvpScenarios,
  ...selectedPhase2Scenarios,
];

const apiRoutes = [
  '/api/scenarios/first-login-reward/runs',
  '/api/scenarios/coupon-campaign-issue/runs',
  '/api/scenarios/point-spend/runs',
];

const unselectedPhase2Candidates = [
  'DAILY_ATTENDANCE',
  'daily-attendance',
  'Daily Attendance Reward',
];

function source(relativePath) {
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function requireFragment(sourceName, text, fragment) {
  if (!text.includes(fragment)) {
    throw new Error(`${sourceName} is missing required fragment: ${fragment}`);
  }
}

function rejectImplementationFragment(sourceName, text, fragment) {
  if (text.includes(fragment)) {
    throw new Error(`${sourceName} must not include implementation fragment: ${fragment}`);
  }
}

function javaEnumValues(text) {
  const body = text.slice(text.indexOf('{') + 1, text.indexOf('}'));
  return body
    .split(',')
    .map((entry) => entry.trim().replace(/\(.*/, '').replace(/;.*/, ''))
    .filter(Boolean);
}

function filesUnder(relativeDir) {
  const absoluteDir = join(repoRoot, relativeDir);
  return readdirSync(absoluteDir)
    .flatMap((entry) => {
      const absolutePath = join(absoluteDir, entry);
      if (statSync(absolutePath).isDirectory()) {
        return filesUnder(resolve(absolutePath).slice(repoRoot.length + 1));
      }
      return absolutePath.match(/\.(java|tsx|ts)$/) ? [absolutePath] : [];
    })
    .sort();
}

const scenarioType = source(files.scenarioType);
const scenarioValues = javaEnumValues(scenarioType);
if (JSON.stringify(scenarioValues) !== JSON.stringify(implementedScenarios)) {
  throw new Error(
    `ScenarioType.java must define the fixed MVP scenarios plus the selected Phase 2 scenario, got ${scenarioValues.join(', ')}`,
  );
}

const strategyType = source(files.strategyType);
for (const strategy of [
  'NAIVE(false)',
  'DB_GUARD(false)',
  'REDIS_LOCK_DB_GUARD(false)',
  'RABBITMQ_DB_GUARD(true)',
  'DB_ROW_LOCK(false)',
  'CONDITIONAL_UPDATE(false)',
  'IDEMPOTENCY_REPLAY(false)',
]) {
  requireFragment('StrategyType.java', strategyType, strategy);
}

const router = source(files.router);
for (const route of apiRoutes) {
  requireFragment('ScenarioApiRouter.java', router, route);
}
if ((router.match(/\/api\/scenarios\//g) ?? []).length !== apiRoutes.length) {
  throw new Error('ScenarioApiRouter.java must expose exactly the three MVP scenario routes');
}

const controller = source(files.controller);
for (const route of [
  '/first-login-reward/runs',
  '/coupon-campaign-issue/runs',
  '/point-spend/runs',
]) {
  requireFragment('ScenarioRunController.java', controller, route);
}
requireFragment('ScenarioRunController.java', controller, '/coupon-redemption/runs');
requireFragment('ScenarioRunController.java', controller, '/batch-expiration/runs');
if ((controller.match(/@PostMapping/g) ?? []).length !== 5) {
  throw new Error('ScenarioRunController.java must expose three MVP POST mappings plus the selected Phase 2 POST mappings');
}

const cli = source(files.cli);
for (const scenario of implementedScenarios) {
  if (selectedPhase2Scenarios.includes(scenario)) {
    continue;
  }
  requireFragment('ScenarioCli.java', cli, scenario);
}
for (const suiteFragment of [
  'entry("scenarioCount", 3)',
  'entry("phase2EntryCount", 0)',
  'StrategyType.RABBITMQ_DB_GUARD',
  'StrategyType.DB_ROW_LOCK',
  'StrategyType.CONDITIONAL_UPDATE',
  'StrategyType.IDEMPOTENCY_REPLAY',
]) {
  requireFragment('ScenarioCli.java', cli, suiteFragment);
}

const app = source(files.app);
for (const dashboardFragment of [
  "type ScenarioKey = 'FIRST_LOGIN_REWARD' | 'COUPON_CAMPAIGN_ISSUE' | 'POINT_SPEND';",
  'scenarioCount: 3',
  'entryCount: 11',
  'phase2EntryCount: 0',
  "'FIRST_LOGIN_REWARD'",
  "'COUPON_CAMPAIGN_ISSUE'",
  "'POINT_SPEND'",
]) {
  requireFragment('App.tsx', app, dashboardFragment);
}

const tracking = source(files.tracking);
for (const gateFragment of [
  'The MVP remains fixed at three scenarios.',
  'Phase 2 can add one realistic scenario only after the MVP produces evidence for each invariant.',
  'Coupon Redemption / Usage',
  'P6 Phase 2 Scenario Gate | Done',
]) {
  requireFragment('docs/project-tracking.md', tracking, gateFragment);
}

const todo = source(files.todo);
const p6Section = todo.slice(todo.indexOf('## P6. Phase 2 Scenario Gate'));
requireFragment('TODO.md', p6Section, '- [x] Finish MVP evidence before selecting a Phase 2 scenario.');
requireFragment('TODO.md', p6Section, '- [x] Re-read `docs/internal/reviews/2026-05-29-reality-scenario-expansion-review.md`.');
for (const fragment of [
  '- [x] Select exactly one Phase 2 candidate.',
  '- [x] Prefer `Coupon Redemption / Usage` unless MVP evidence shows a clearer gap.',
  '- [x] Write an ADR that names the new invariant, failure mode, control device, metrics, and excluded follow-ups.',
  '- [x] Keep unselected candidates in the parking lot instead of adding them to the MVP.',
  '- [x] Keep external providers fake or local-only.',
]) {
  requireFragment('TODO.md', p6Section, fragment);
}
for (const fragment of [
  '- [ ] Select exactly one Phase 2 candidate.',
  '- [ ] Prefer `Coupon Redemption / Usage` unless MVP evidence shows a clearer gap.',
  '- [ ] Write an ADR that names the new invariant, failure mode, control device, metrics, and excluded follow-ups.',
  '- [ ] Keep unselected candidates in the parking lot instead of adding them to the MVP.',
  '- [ ] Keep external providers fake or local-only.',
]) {
  if (p6Section.includes(fragment)) {
    throw new Error(`TODO.md P6 Phase 2 gate must record completed selection: ${fragment}`);
  }
}

for (const implementationFile of [
  ...filesUnder('backend/src/main/java'),
  ...filesUnder('web/src'),
]) {
  const text = readFileSync(implementationFile, 'utf8');
  for (const fragment of unselectedPhase2Candidates) {
    rejectImplementationFragment(implementationFile.slice(repoRoot.length + 1), text, fragment);
  }
}
