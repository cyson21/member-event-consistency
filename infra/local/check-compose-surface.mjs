import { readFileSync } from 'node:fs';

const compose = readFileSync(new URL('./docker-compose.yml', import.meta.url), 'utf8');
const nginx = readFileSync(new URL('./nginx/default.conf', import.meta.url), 'utf8');
const readme = readFileSync(new URL('./README.md', import.meta.url), 'utf8');

function requireFragment(sourceName, source, fragment) {
  if (!source.includes(fragment)) {
    throw new Error(`${sourceName} is missing required fragment: ${fragment}`);
  }
}

function rejectFragment(sourceName, source, fragment) {
  if (source.toLowerCase().includes(fragment.toLowerCase())) {
    throw new Error(`${sourceName} must not include out-of-scope fragment: ${fragment}`);
  }
}

for (const service of [
  'postgres:',
  'redis:',
  'rabbitmq:',
  'api-1:',
  'api-2:',
  'api-3:',
  'campaign-worker:',
  'nginx:',
]) {
  requireFragment('docker-compose.yml', compose, service);
}

for (const infrastructureFragment of [
  'postgres:16-alpine',
  'redis:7-alpine',
  'rabbitmq:3.12-management-alpine',
  'nginx:1.27-alpine',
  'condition: service_healthy',
  'SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/',
  'SPRING_DATA_REDIS_HOST: redis',
  'SPRING_RABBITMQ_HOST: rabbitmq',
  'APP_SERVICE_ROLE: api',
  'API_INSTANCE_ID: api-1',
  'API_INSTANCE_ID: api-2',
  'API_INSTANCE_ID: api-3',
  'APP_SERVICE_ROLE: campaign-worker',
  'CAMPAIGN_WORKER_CONCURRENCY: "1"',
  'CAMPAIGN_WORKER_LANE_STRATEGY: campaign-id',
  './nginx/default.conf:/etc/nginx/nginx.conf:ro',
]) {
  requireFragment('docker-compose.yml', compose, infrastructureFragment);
}

for (const healthcheckFragment of [
  'pg_isready',
  'redis-cli',
  'rabbitmq-diagnostics -q ping',
  'nginx -t',
]) {
  requireFragment('docker-compose.yml', compose, healthcheckFragment);
}

for (const nginxFragment of [
  'upstream backend_pool',
  'server api-1:8080;',
  'server api-2:8080;',
  'server api-3:8080;',
  'location /health',
  'proxy_pass http://backend_pool;',
]) {
  requireFragment('nginx/default.conf', nginx, nginxFragment);
}

for (const readmeFragment of [
  'No Kafka',
  'No complex MSA split',
  'No 2PC / distributed transactions',
  'No real external providers',
  'RabbitMQ is not documented as automatic business-key serialization',
  'Redis lock is used only as pre-DB contention relief',
  'production performance proof',
  'CAMPAIGN_WORKER_CONCURRENCY=1',
  'CAMPAIGN_WORKER_LANE_STRATEGY=campaign-id',
]) {
  requireFragment('README.md', readme, readmeFragment);
}

for (const outOfScope of [
  'kafka:',
  'zookeeper:',
  'schema-registry:',
  'payment-provider',
  'sms-provider',
  'email-provider',
  'push-provider',
  'lock:member',
]) {
  rejectFragment('docker-compose.yml', compose, outOfScope);
  rejectFragment('nginx/default.conf', nginx, outOfScope);
}
