import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');

const output = execFileSync(process.execPath, [
  'tools/runner/run-mvp-live-smoke.mjs',
  '--dry-run',
], {
  cwd: repoRoot,
  encoding: 'utf8',
});

const summary = JSON.parse(output);
const entries = summary.expectedStatuses;

assertEntry('COUPON_CAMPAIGN_ISSUE', 'NAIVE', {
  expectedCouponIssuedCount: 8,
  expectedOverIssueCount: 5,
  expectedRejectedCount: 0,
  expectedRedisLockAttemptCount: 0,
  expectedRabbitMqLaneCount: 0,
  expectedQueueRetryCount: 0,
  expectedDlqCount: 0,
  expectedQueueLagMsP95: 0,
  expectedRabbitMqAcceptedLatencyMs: 0,
  expectedRabbitMqCompletionLatencyMs: 0,
});
assertEntry('COUPON_CAMPAIGN_ISSUE', 'DB_GUARD', {
  expectedCouponIssuedCount: 3,
  expectedOverIssueCount: 0,
  expectedRejectedCount: 5,
  expectedRedisLockAttemptCount: 0,
  expectedRabbitMqLaneCount: 0,
  expectedQueueRetryCount: 0,
  expectedDlqCount: 0,
  expectedQueueLagMsP95: 0,
  expectedRabbitMqAcceptedLatencyMs: 0,
  expectedRabbitMqCompletionLatencyMs: 0,
});
assertEntry('COUPON_CAMPAIGN_ISSUE', 'REDIS_LOCK_DB_GUARD', {
  expectedCouponIssuedCount: 3,
  expectedOverIssueCount: 0,
  expectedRejectedCount: 5,
  expectedRedisLockAttemptCount: 8,
  expectedRabbitMqLaneCount: 0,
  expectedQueueRetryCount: 0,
  expectedDlqCount: 0,
  expectedQueueLagMsP95: 0,
  expectedRabbitMqAcceptedLatencyMs: 0,
  expectedRabbitMqCompletionLatencyMs: 0,
});
assertEntry('COUPON_CAMPAIGN_ISSUE', 'RABBITMQ_DB_GUARD', {
  expectedCouponIssuedCount: 3,
  expectedOverIssueCount: 0,
  expectedRejectedCount: 5,
  expectedRedisLockAttemptCount: 0,
  expectedRabbitMqLaneCount: 1,
  expectedQueueRetryCount: 1,
  expectedDlqCount: 0,
  expectedQueueLagMsP95: 90,
  expectedRabbitMqAcceptedLatencyMs: 12,
  expectedRabbitMqCompletionLatencyMs: 102,
});
assertEntry('POINT_SPEND', 'NAIVE', {
  expectedFinalPointBalance: -400,
  expectedNegativeBalanceCount: 1,
  expectedPointLedgerEntryCount: 2,
  expectedRejectedCount: 0,
  expectedIdempotencyReplayCount: 0,
  expectedIdempotencyHashMismatchCount: 0,
  expectedDbWaitMsP95: 0,
});
assertEntry('POINT_SPEND', 'DB_ROW_LOCK', {
  expectedFinalPointBalance: 300,
  expectedNegativeBalanceCount: 0,
  expectedPointLedgerEntryCount: 1,
  expectedRejectedCount: 1,
  expectedIdempotencyReplayCount: 0,
  expectedIdempotencyHashMismatchCount: 0,
  expectedDbWaitMsP95: 15,
});
assertEntry('POINT_SPEND', 'CONDITIONAL_UPDATE', {
  expectedFinalPointBalance: 300,
  expectedNegativeBalanceCount: 0,
  expectedPointLedgerEntryCount: 1,
  expectedRejectedCount: 1,
  expectedIdempotencyReplayCount: 0,
  expectedIdempotencyHashMismatchCount: 0,
  expectedDbWaitMsP95: 0,
});
assertEntry('POINT_SPEND', 'IDEMPOTENCY_REPLAY', {
  expectedFinalPointBalance: 300,
  expectedNegativeBalanceCount: 0,
  expectedPointLedgerEntryCount: 1,
  expectedRejectedCount: 0,
  expectedIdempotencyReplayCount: 1,
  expectedIdempotencyHashMismatchCount: 0,
  expectedDbWaitMsP95: 0,
});

function assertEntry(scenario, strategy, expectedFields) {
  const entry = entries.find((candidate) => candidate.scenario === scenario && candidate.strategy === strategy);
  if (!entry) {
    throw new Error(`Missing ${scenario} ${strategy} live-smoke summary entry`);
  }
  for (const [field, value] of Object.entries(expectedFields)) {
    if (entry[field] !== value) {
      throw new Error(`${scenario} ${strategy} expected ${field}=${value}, got ${entry[field]}`);
    }
  }
}
