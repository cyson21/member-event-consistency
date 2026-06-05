import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

const stockRushIndex = suite.indexOf('check-stockrush-boundary.mjs');
if (stockRushIndex === -1) {
  throw new Error('Local verification suite is missing StockRush boundary guard');
}

const requiredBeforeStockRush = [
  'backend/scripts/check-flyway-schema-surface.mjs',
  'backend/scripts/check-spring-controller-surface.mjs',
  'infra/local/check-compose-surface.mjs',
  'web/scripts/check-dashboard-fixtures.mjs',
  'web/scripts/check-sql-recording-dashboard-sync.mjs',
  'web/scripts/check-mvp-concurrency-dashboard-sync.mjs',
  'tools/runner/check-ai-runs-ledger.mjs',
  'tools/runner/check-project-tracking-ledger-sync.mjs',
  'tools/runner/check-root-verification-entrypoint.mjs',
  'tools/runner/check-dependency-free-regression-manifest.mjs',
  'tools/runner/check-maven-junit-pending-inventory.mjs',
  'tools/runner/check-phase2-gate-readiness.mjs',
  'tools/runner/check-web-typecheck-pending-inventory.mjs',
  'tools/runner/check-mvp-live-smoke-fresh-db-precondition.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-readiness.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-compose-readiness.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-live-blockers.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-spring-server.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-base-url.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-async-count.mjs',
  'tools/runner/check-mvp-live-smoke-preflight-async-route.mjs',
  'tools/runner/check-live-hot-campaign-runner-surface.mjs',
  'tools/runner/run-live-hot-campaign.mjs',
  'tools/runner/check-live-concurrent-spend-runner-surface.mjs',
  'tools/runner/run-live-concurrent-spend.mjs',
  'tools/runner/check-live-coupon-redemption-runner-surface.mjs',
  'tools/runner/run-live-coupon-redemption.mjs',
  'tools/runner/check-live-batch-expiration-runner-surface.mjs',
  'tools/runner/run-live-batch-expiration.mjs',
  'tools/runner/check-reward-sql-recording-evidence.mjs',
  'tools/runner/check-coupon-sql-recording-evidence.mjs',
  'tools/runner/check-point-sql-recording-evidence.mjs',
  'tools/runner/check-batch-expiration-sql-recording-evidence.mjs',
  'docker\', \'compose\', \'-f\', \'infra/local/docker-compose.yml\', \'config\', \'-q',
];

for (const fragment of requiredBeforeStockRush) {
  const index = suite.indexOf(fragment);
  if (index === -1) {
    throw new Error(`Local verification suite is missing project-local check: ${fragment}`);
  }
  if (index > stockRushIndex) {
    throw new Error(`StockRush boundary must run after project-local check: ${fragment}`);
  }
}
