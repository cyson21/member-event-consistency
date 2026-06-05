import { execFileSync } from 'node:child_process';

const raw = execFileSync('npm', ['run', 'test:live-smoke:preflight'], {
  encoding: 'utf8',
});
const preflight = JSON.parse(raw.trim().split('\n').at(-1));

if (preflight.composeReady !== true) {
  throw new Error('MVP live-smoke preflight must expose composeReady=true');
}

if (preflight.composeConfigCommand !== 'docker compose -f infra/local/docker-compose.yml config -q') {
  throw new Error(`Unexpected compose config command: ${preflight.composeConfigCommand}`);
}

if (!preflight.prerequisites.includes('Docker Compose config valid')) {
  throw new Error('Preflight prerequisites must include Docker Compose config validity');
}

console.log(JSON.stringify({
  status: 'passed',
  composeReady: preflight.composeReady,
  composeConfigCommand: preflight.composeConfigCommand,
}));

