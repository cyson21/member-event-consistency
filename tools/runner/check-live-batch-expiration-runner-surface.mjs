import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const runnerPath = join(repoRoot, 'tools/runner/run-live-batch-expiration.mjs');

if (!existsSync(runnerPath)) {
  throw new Error('run-live-batch-expiration.mjs must exist for selected Phase 2 live smoke.');
}

const runner = readFileSync(runnerPath, 'utf8');

for (const fragment of [
  '--dry-run',
  '--base-url',
  'localOnly: true',
  'phase2: true',
  'freshLocalDb: true',
  'BATCH_EXPIRATION',
  '/api/scenarios/batch-expiration/runs',
  'DB_GUARD',
  'USER_USE',
  'BATCH_EXPIRATION',
  'expectedCouponUsedCount',
  'expectedCouponExpiredCount',
  'expectedTerminalStateConflictCount',
  'expectedRejectedCount',
  'couponUsedCount',
  'couponExpiredCount',
  'terminalStateConflictCount',
  'rejectionReason',
  'winner',
  'fetch(',
]) {
  if (!runner.includes(fragment)) {
    throw new Error(`Batch Expiration live runner is missing required fragment: ${fragment}`);
  }
}

for (const forbidden of [
  'RABBITMQ_DB_GUARD',
  'REDIS_LOCK_DB_GUARD',
  'IDEMPOTENCY_REPLAY',
  'kafka',
  'lock:member',
  'payment_provider',
  'sms_provider',
  'email_provider',
  'push_provider',
]) {
  if (runner.includes(forbidden)) {
    throw new Error(`Batch Expiration live runner must not include out-of-scope fragment: ${forbidden}`);
  }
}
