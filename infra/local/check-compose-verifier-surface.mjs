import { readFileSync } from 'node:fs';

const verifier = readFileSync(new URL('./check-compose-surface.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'docker-compose.yml',
  'nginx/default.conf',
  'CAMPAIGN_WORKER_CONCURRENCY',
  'CAMPAIGN_WORKER_LANE_STRATEGY',
  'api-1',
  'api-2',
  'api-3',
  'rabbitmq',
  'rejectFragment',
  'production performance proof',
]) {
  if (!verifier.includes(fragment)) {
    throw new Error(`Compose surface verifier is missing fragment: ${fragment}`);
  }
}
