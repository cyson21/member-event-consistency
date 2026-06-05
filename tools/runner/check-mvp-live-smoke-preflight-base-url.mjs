import { execFileSync } from 'node:child_process';

const baseUrl = 'http://127.0.0.1:18080';
const raw = execFileSync('npm', ['run', 'test:live-smoke:preflight', '--', '--base-url', baseUrl], {
  encoding: 'utf8',
});
const preflight = JSON.parse(raw.trim().split('\n').at(-1));

if (preflight.baseUrl !== baseUrl) {
  throw new Error(`Expected preflight baseUrl=${baseUrl}, got ${preflight.baseUrl}`);
}

if (preflight.springServerHealthUrl !== `${baseUrl}/actuator/health`) {
  throw new Error(`Unexpected Spring health URL: ${preflight.springServerHealthUrl}`);
}

if (preflight.nextLiveSmokeCommand !== `node tools/runner/run-mvp-live-smoke.mjs --base-url ${baseUrl}`) {
  throw new Error(`Unexpected next live smoke command: ${preflight.nextLiveSmokeCommand}`);
}

const liveCommands = preflight.liveSmokeBlockedChecks.map((check) => check.command);
if (!liveCommands.includes(`GET ${baseUrl}/actuator/health`)) {
  throw new Error('Live smoke blockers must use the requested base URL actuator health check');
}

if (!preflight.prerequisites.includes(`local Spring server at ${baseUrl}`)) {
  throw new Error('Preflight prerequisites must use the requested base URL for the local Spring server');
}

console.log(JSON.stringify({
  status: 'passed',
  baseUrl: preflight.baseUrl,
  springServerHealthUrl: preflight.springServerHealthUrl,
}));
