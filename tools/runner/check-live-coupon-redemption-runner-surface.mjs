import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const runnerPath = join(repoRoot, 'tools/runner/run-live-coupon-redemption.mjs');

if (!existsSync(runnerPath)) {
  throw new Error('run-live-coupon-redemption.mjs must exist for selected Phase 2 live smoke.');
}

const runner = readFileSync(runnerPath, 'utf8');

for (const fragment of [
  '--dry-run',
  '--base-url',
  'localOnly: true',
  'phase2: true',
  'freshLocalDb: true',
  'COUPON_REDEMPTION',
  '/api/scenarios/coupon-redemption/runs',
  'DB_GUARD',
  'IDEMPOTENCY_REPLAY',
  'expectedCouponUsedCount',
  'expectedDoubleUseCount',
  'expectedTerminalStateConflictCount',
  'expectedRejectedCount',
  'expectedIdempotencyReplayCount',
  'expectedIdempotencyHashMismatchCount',
  'couponUsedCount',
  'doubleUseCount',
  'terminalStateConflictCount',
  'idempotencyReplayCount',
  'idempotencyHashMismatchCount',
  'requestCount',
  'idempotencyKey',
  'firstRequestHash',
  'retryRequestHash',
  'fetch(',
]) {
  if (!runner.includes(fragment)) {
    throw new Error(`Coupon Redemption live runner is missing required fragment: ${fragment}`);
  }
}

for (const forbidden of [
  'RABBITMQ_DB_GUARD',
  'REDIS_LOCK_DB_GUARD',
  'kafka',
  'lock:member',
  'payment_provider',
  'sms_provider',
  'email_provider',
  'push_provider',
]) {
  if (runner.includes(forbidden)) {
    throw new Error(`Coupon Redemption live runner must not include out-of-scope fragment: ${forbidden}`);
  }
}
