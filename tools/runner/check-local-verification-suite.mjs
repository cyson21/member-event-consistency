import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');

const checks = [
  ['node', 'tools/runner/check-local-verification-suite-surface.mjs'],
  ['node', 'tools/runner/check-local-verification-suite-order.mjs'],
  ['node', 'tools/runner/check-local-verification-suite-blocked-summary-surface.mjs'],
  ['node', 'tools/runner/check-ai-runs-ledger-surface.mjs'],
  ['node', 'tools/runner/check-ai-runs-ledger.mjs'],
  ['node', 'tools/runner/check-project-tracking-ledger-sync-surface.mjs'],
  ['node', 'tools/runner/check-project-tracking-ledger-sync.mjs'],
  ['node', 'tools/runner/check-root-verification-entrypoint.mjs'],
  ['node', 'tools/runner/check-regression-script-surface.mjs'],
  ['node', 'tools/runner/check-dependency-free-regression-manifest.mjs'],
  ['node', 'tools/runner/check-maven-junit-pending-inventory.mjs'],
  ['node', 'tools/runner/check-testcontainers-ci-path.mjs'],
  ['node', 'tools/runner/check-phase2-gate-readiness-surface.mjs'],
  ['node', 'tools/runner/check-phase2-gate-readiness.mjs'],
  ['node', 'tools/runner/check-web-typecheck-pending-inventory.mjs'],
  ['node', 'tools/runner/check-mvp-scope-verifier-surface.mjs'],
  ['node', 'tools/runner/check-control-device-guardrails-surface.mjs'],
  ['node', 'backend/scripts/check-schema-verifier-surface.mjs'],
  ['node', 'infra/local/check-compose-verifier-surface.mjs'],
  ['node', 'tools/runner/check-dependency-bootstrap-readiness-surface.mjs'],
  ['node', 'tools/runner/check-mvp-concurrency-suite-surface.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-seed-sync-surface.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-request-catalog-surface.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-runner-surface.mjs'],
  ['node', 'tools/runner/check-live-hot-campaign-runner-surface.mjs'],
  ['node', 'tools/runner/check-live-concurrent-spend-runner-surface.mjs'],
  ['node', 'tools/runner/check-live-coupon-redemption-runner-surface.mjs'],
  ['node', 'tools/runner/check-live-batch-expiration-runner-surface.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-reward-evidence.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-coupon-point-evidence.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-response-identity.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-response-status.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-fresh-db-precondition-surface.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-fresh-db-precondition.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-readiness-surface.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-readiness.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-compose-readiness.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-live-blockers.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-spring-server.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-base-url.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-async-count.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-preflight-async-route.mjs'],
  ['node', 'tools/runner/check-dependency-bootstrap-readiness.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-seed-sync.mjs'],
  ['node', 'tools/runner/check-mvp-live-smoke-request-catalog.mjs'],
  ['node', 'tools/runner/run-mvp-live-smoke.mjs', '--dry-run'],
  ['node', 'tools/runner/run-live-hot-campaign.mjs', '--dry-run'],
  ['node', 'tools/runner/run-live-concurrent-spend.mjs', '--dry-run'],
  ['node', 'tools/runner/run-live-coupon-redemption.mjs', '--dry-run'],
  ['node', 'tools/runner/run-live-batch-expiration.mjs', '--dry-run'],
  ['node', 'tools/runner/check-reward-sql-recording-evidence.mjs'],
  ['node', 'tools/runner/check-coupon-sql-recording-evidence.mjs'],
  ['node', 'tools/runner/check-point-sql-recording-evidence.mjs'],
  ['node', 'tools/runner/check-batch-expiration-sql-recording-evidence.mjs'],
  ['node', 'tools/runner/check-dependency-free-regression.mjs'],
  ['node', 'tools/runner/check-mvp-scope-surface.mjs'],
  ['node', 'tools/runner/check-control-device-guardrails.mjs'],
  ['node', 'tools/runner/check-local-terminology.mjs'],
  ['node', 'backend/scripts/check-flyway-schema-surface.mjs'],
  ['node', 'backend/scripts/check-spring-controller-surface.mjs'],
  ['node', 'infra/local/check-compose-surface.mjs'],
  ['node', 'web/scripts/check-dashboard-fixtures.mjs'],
  ['node', 'web/scripts/check-sql-recording-dashboard-sync.mjs'],
  ['node', 'web/scripts/check-mvp-concurrency-dashboard-sync.mjs'],
  ['docker', 'compose', '-f', 'infra/local/docker-compose.yml', 'config', '-q'],
  ['node', 'tools/runner/check-stockrush-boundary.mjs'],
];

const expectedCompletedChecks = 66;
const completedChecks = [];
let activeCheck = "";

try {
  for (const [command, ...args] of checks) {
    activeCheck = [command, ...args].join(' ');
    const executable = command === 'node' ? process.execPath : command;
    execFileSync(executable, args, {
      cwd: repoRoot,
      stdio: 'inherit',
    });
    completedChecks.push(activeCheck);
  }
} catch (error) {
  console.log(JSON.stringify({
    status: "blocked",
    expectedCompletedChecks,
    completedChecks: completedChecks.length,
    blockedAt: activeCheck,
    commands: completedChecks,
  }));
  process.exit(error.status || 1);
}

if (completedChecks.length !== expectedCompletedChecks) {
  throw new Error(`Expected ${expectedCompletedChecks} local verification checks, got ${completedChecks.length}`);
}

console.log(JSON.stringify({
  expectedCompletedChecks,
  completedChecks: completedChecks.length,
  commands: completedChecks,
}));
