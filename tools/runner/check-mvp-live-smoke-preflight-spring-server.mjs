import { execFileSync } from 'node:child_process';

const raw = execFileSync('npm', ['run', 'test:live-smoke:preflight'], {
  encoding: 'utf8',
});
const preflight = JSON.parse(raw.trim().split('\n').at(-1));

if (typeof preflight.springServerReady !== 'boolean') {
  throw new Error('MVP live-smoke preflight must expose springServerReady');
}

if (preflight.springServerHealthUrl !== 'http://localhost:8080/actuator/health') {
  throw new Error(`Unexpected Spring health URL: ${preflight.springServerHealthUrl}`);
}

if (preflight.liveSmokeReady !== preflight.springServerReady) {
  throw new Error(
    `Expected liveSmokeReady to match springServerReady in dependency-ready preflight, got liveSmokeReady=${preflight.liveSmokeReady}, springServerReady=${preflight.springServerReady}`,
  );
}

const liveCommands = preflight.liveSmokeBlockedChecks.map((check) => check.command);
if (preflight.springServerReady === false && !liveCommands.includes('GET http://localhost:8080/actuator/health')) {
  throw new Error('Live smoke blockers must include the local Spring actuator health check when the server is absent');
}
if (preflight.springServerReady === true && liveCommands.includes('GET http://localhost:8080/actuator/health')) {
  throw new Error('Live smoke blockers must clear the local Spring actuator health check when the server is present');
}

console.log(JSON.stringify({
  status: 'passed',
  springServerReady: preflight.springServerReady,
  liveSmokeReady: preflight.liveSmokeReady,
  springServerHealthUrl: preflight.springServerHealthUrl,
}));
