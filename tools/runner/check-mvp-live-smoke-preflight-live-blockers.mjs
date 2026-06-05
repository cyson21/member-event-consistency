import { execFileSync } from 'node:child_process';

const raw = execFileSync('npm', ['run', 'test:live-smoke:preflight'], {
  encoding: 'utf8',
});
const preflight = JSON.parse(raw.trim().split('\n').at(-1));

if (!Array.isArray(preflight.liveSmokeBlockedChecks)) {
  throw new Error('MVP live-smoke preflight must expose liveSmokeBlockedChecks');
}

if (preflight.liveSmokeDependencyReady !== true) {
  throw new Error(`Expected liveSmokeDependencyReady=true after dependency bootstrap, got ${preflight.liveSmokeDependencyReady}`);
}

const liveCommands = preflight.liveSmokeBlockedChecks.map((check) => check.command);
if (liveCommands.includes('mvn -f backend/pom.xml -o test')) {
  throw new Error('Live smoke blockers must clear backend Maven verification after backend bootstrap');
}
if (liveCommands.includes('npm --prefix web test')) {
  throw new Error('Web TypeScript verification must not be a live-smoke-specific blocker');
}
if (typeof preflight.dockerDaemonReady !== 'boolean') {
  throw new Error('Live smoke preflight must expose dockerDaemonReady');
}
if (preflight.dockerDaemonReady === false && !liveCommands.includes('docker info')) {
  throw new Error('Live smoke blockers must include docker info when the Docker daemon is not reachable');
}
if (preflight.dockerDaemonReady === true && liveCommands.includes('docker info')) {
  throw new Error('Live smoke blockers must clear docker info when the Docker daemon is reachable');
}
if (preflight.springServerReady === false && !liveCommands.includes('GET http://localhost:8080/actuator/health')) {
  throw new Error('Live smoke blockers must include local Spring actuator health until the server is running');
}
if (preflight.springServerReady === true && liveCommands.includes('GET http://localhost:8080/actuator/health')) {
  throw new Error('Live smoke blockers must clear local Spring actuator health when the server is running');
}

const projectCommands = preflight.blockedChecks.map((check) => check.command);
if (projectCommands.includes('npm --prefix web test')) {
  throw new Error('Full project blockers must clear web TypeScript verification after web bootstrap');
}
if (projectCommands.length !== 0) {
  throw new Error(`Expected no dependency blocked checks after bootstrap, got ${projectCommands.join(', ')}`);
}

console.log(JSON.stringify({
  status: 'passed',
  liveSmokeDependencyReady: preflight.liveSmokeDependencyReady,
  dockerDaemonReady: preflight.dockerDaemonReady,
  springServerReady: preflight.springServerReady,
  liveSmokeBlockedChecks: preflight.liveSmokeBlockedChecks.length,
  projectBlockedChecks: preflight.blockedChecks.length,
}));
