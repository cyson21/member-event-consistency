import { execFileSync } from 'node:child_process';

const readiness = jsonFromCommand(['tools/runner/check-dependency-bootstrap-readiness.mjs']);
const dryRun = jsonFromCommand(['tools/runner/run-mvp-live-smoke.mjs', '--dry-run']);
const baseUrl = optionValue('--base-url') || 'http://localhost:8080';
const composeConfigCommand = 'docker compose -f infra/local/docker-compose.yml config -q';
const composeReady = commandSucceeds(['docker', 'compose', '-f', 'infra/local/docker-compose.yml', 'config', '-q']);
const dockerDaemonCommand = 'docker info';
const dockerDaemonReady = commandSucceeds(['docker', 'info']);
const nextLiveSmokeCommand = `node tools/runner/run-mvp-live-smoke.mjs --base-url ${baseUrl}`;
const springServerHealthUrl = new URL('/actuator/health', baseUrl).toString();
const springServerReady = await httpOk(springServerHealthUrl);
const asyncAcceptedRoutes = dryRun.expectedStatuses
  .filter((route) => route.expectedStatus === 202)
  .map((route) => ({
    title: route.title,
    path: route.path,
    scenario: route.expectedScenario,
    strategy: route.expectedStrategy,
    expectedStatus: route.expectedStatus,
    expectedResponseStatusCode: route.expectedResponseStatusCode,
    expectedRabbitMqLaneCount: route.expectedRabbitMqLaneCount,
    expectedRabbitMqAcceptedLatencyMs: route.expectedRabbitMqAcceptedLatencyMs,
    expectedRabbitMqCompletionLatencyMs: route.expectedRabbitMqCompletionLatencyMs,
  }));

if (dryRun.localOnly !== true || dryRun.phase2 !== false || dryRun.freshLocalDb !== true) {
  throw new Error('MVP live-smoke dry-run must stay local-only, exclude Phase 2, and require freshLocalDb=true');
}

if (dryRun.routeCount !== 11 || dryRun.scenarioCount !== 3) {
  throw new Error(`Expected fixed MVP live-smoke matrix, got routes=${dryRun.routeCount}, scenarios=${dryRun.scenarioCount}`);
}

const status = readiness.ready ? 'ready' : 'blocked';
const blockedChecks = [...readiness.blockedChecks];
const liveSmokeBlockedChecks = readiness.blockedChecks
  .filter((check) => check.command !== 'npm --prefix web test');
if (!composeReady) {
  const composeBlocker = {
    command: composeConfigCommand,
    reason: 'Docker Compose config validation failed',
  };
  blockedChecks.push(composeBlocker);
  liveSmokeBlockedChecks.push(composeBlocker);
}
if (!dockerDaemonReady) {
  liveSmokeBlockedChecks.push({
    command: dockerDaemonCommand,
    reason: 'Docker daemon is not reachable; local infrastructure containers cannot be started',
  });
}
const liveSmokeDependencyReady = readiness.backendReady && composeReady;
if (!springServerReady) {
  liveSmokeBlockedChecks.push({
    command: `GET ${springServerHealthUrl}`,
    reason: 'local Spring actuator health endpoint is not reachable',
  });
}
const liveSmokeReady = liveSmokeDependencyReady && springServerReady;

console.log(JSON.stringify({
  status,
  dependencyReady: readiness.ready,
  liveSmokeDependencyReady,
  liveSmokeReady,
  composeReady,
  dockerDaemonReady,
  springServerReady,
  backendReady: readiness.backendReady,
  webReady: readiness.webReady,
  baseUrl,
  localOnly: dryRun.localOnly,
  phase2: dryRun.phase2,
  freshLocalDb: dryRun.freshLocalDb,
  routeCount: dryRun.routeCount,
  scenarioCount: dryRun.scenarioCount,
  asyncAcceptedCount: dryRun.asyncAcceptedCount,
  asyncAcceptedRoutes,
  blockedChecks,
  liveSmokeBlockedChecks,
  composeConfigCommand,
  dockerDaemonCommand,
  springServerHealthUrl,
  prerequisites: [
    'dependency bootstrap ready',
    'Docker Compose config valid',
    'Docker daemon reachable for local infrastructure containers',
    'fresh local DB after Flyway migration',
    `local Spring server at ${baseUrl}`,
  ],
  nextLiveSmokeCommand,
}));

function jsonFromCommand(args) {
  const raw = execFileSync(process.execPath, args, { encoding: 'utf8' });
  return JSON.parse(raw.trim().split('\n').at(-1));
}

function optionValue(name) {
  const index = process.argv.indexOf(name);
  if (index === -1 || index + 1 >= process.argv.length) {
    return '';
  }
  return process.argv[index + 1];
}

function commandSucceeds(args) {
  try {
    execFileSync(args[0], args.slice(1), { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

async function httpOk(url) {
  try {
    const response = await fetch(url, {
      signal: AbortSignal.timeout(500),
    });
    return response.ok;
  } catch {
    return false;
  }
}
