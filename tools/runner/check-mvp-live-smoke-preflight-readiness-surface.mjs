import { existsSync, readFileSync } from 'node:fs';

const preflightPath = new URL('./check-mvp-live-smoke-preflight-readiness.mjs', import.meta.url);
if (!existsSync(preflightPath)) {
  throw new Error('MVP live-smoke preflight readiness script is missing');
}

const preflight = readFileSync(preflightPath, 'utf8');
for (const fragment of [
  'check-dependency-bootstrap-readiness.mjs',
  'run-mvp-live-smoke.mjs',
  '--dry-run',
  '--base-url',
  'optionValue',
  'baseUrl',
  'freshLocalDb',
  'asyncAcceptedCount',
  'asyncAcceptedRoutes',
  'expectedRabbitMqAcceptedLatencyMs',
  'expectedRabbitMqCompletionLatencyMs',
  'liveSmokeDependencyReady',
  'liveSmokeReady',
  'liveSmokeBlockedChecks',
  'composeReady',
  'dockerDaemonReady',
  'dockerDaemonCommand',
  'docker info',
  'Docker daemon reachable for local infrastructure containers',
  'springServerReady',
  'springServerHealthUrl',
  '/actuator/health',
  'composeConfigCommand',
  'Docker Compose config valid',
  'docker',
  'compose',
  'nextLiveSmokeCommand',
  'run-mvp-live-smoke.mjs --base-url ${baseUrl}',
]) {
  if (!preflight.includes(fragment)) {
    throw new Error(`MVP live-smoke preflight readiness script is missing fragment: ${fragment}`);
  }
}

const packageJson = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'));
if (packageJson.scripts?.['test:live-smoke:preflight'] !== 'node tools/runner/check-mvp-live-smoke-preflight-readiness.mjs') {
  throw new Error('Root package.json must expose test:live-smoke:preflight');
}

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');
for (const fragment of [
  'check-mvp-live-smoke-preflight-readiness-surface.mjs',
  'check-mvp-live-smoke-preflight-readiness.mjs',
  'check-mvp-live-smoke-preflight-compose-readiness.mjs',
  'check-mvp-live-smoke-preflight-live-blockers.mjs',
  'check-mvp-live-smoke-preflight-spring-server.mjs',
  'check-mvp-live-smoke-preflight-base-url.mjs',
  'check-mvp-live-smoke-preflight-async-count.mjs',
  'check-mvp-live-smoke-preflight-async-route.mjs',
  'const expectedCompletedChecks = 66',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing live-smoke preflight fragment: ${fragment}`);
  }
}
