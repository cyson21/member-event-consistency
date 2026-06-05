import { readFileSync } from 'node:fs';

const runner = readFileSync(new URL('./run-live-concurrent-spend.mjs', import.meta.url), 'utf8');
const localSuite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'POINT_SPEND',
  'point-spend',
  'CONCURRENT_SPEND',
  '--dry-run',
  '--base-url',
  'localOnly: true',
  'phase2: false',
  'freshLocalDb: true',
  'Strategy DB row lock concurrent spend',
  'Strategy conditional update concurrent spend',
  'Strategy idempotency replay concurrent spend',
  'expectedInvariantPassed: true',
  'expectedAcceptedCount',
  'expectedCompletedCount',
  'expectedRejectedCount',
  'expectedFinalPointBalance',
  'expectedNegativeBalanceCount: 0',
  'expectedPointLedgerEntryCount',
  'expectedIdempotencyReplayCount',
  'expectedIdempotencyHashMismatchCount: 0',
  'expectedDbWaitMsP95',
  'fetch(',
  'parsed.invariantPassed',
  'parsed.finalPointBalance',
  'parsed.negativeBalanceCount',
  'parsed.pointLedgerEntryCount',
  'parsed.idempotencyReplayCount',
]) {
  if (!runner.includes(fragment)) {
    throw new Error(`Live concurrent spend runner is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'check-live-concurrent-spend-runner-surface.mjs',
  'run-live-concurrent-spend.mjs',
  '--dry-run',
  'expectedCompletedChecks',
]) {
  if (!localSuite.includes(fragment)) {
    throw new Error(`Local verification suite is missing live concurrent spend fragment: ${fragment}`);
  }
}
