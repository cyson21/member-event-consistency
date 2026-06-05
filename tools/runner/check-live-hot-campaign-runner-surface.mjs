import { readFileSync } from 'node:fs';

const runner = readFileSync(new URL('./run-live-hot-campaign.mjs', import.meta.url), 'utf8');
const localSuite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'COUPON_CAMPAIGN_ISSUE',
  'coupon-campaign-issue',
  'HOT_CAMPAIGN',
  '--dry-run',
  '--base-url',
  'localOnly: true',
  'phase2: false',
  'freshLocalDb: true',
  'Strategy DB guard hot campaign',
  'Strategy Redis campaign lock hot campaign',
  'Strategy RabbitMQ single-lane hot campaign',
  'expectedInvariantPassed: true',
  'expectedAcceptedCount',
  'expectedCompletedCount',
  'expectedCouponIssuedCount',
  'expectedOverIssueCount: 0',
  'expectedRejectedCount',
  'expectedRedisLockAttemptCount',
  'expectedRabbitMqLaneCount',
  'expectedQueueRetryCount',
  'expectedDlqCount',
  'expectedRabbitMqAcceptedLatencyMs',
  'expectedRabbitMqCompletionLatencyMs',
  'fetch(',
  'parsed.invariantPassed',
  'parsed.couponIssuedCount',
  'parsed.overIssueCount',
  'parsed.rejectedCount',
  'parsed.rabbitMqLaneCount',
]) {
  if (!runner.includes(fragment)) {
    throw new Error(`Live hot campaign runner is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'check-live-hot-campaign-runner-surface.mjs',
  'run-live-hot-campaign.mjs',
  '--dry-run',
  'expectedCompletedChecks',
]) {
  if (!localSuite.includes(fragment)) {
    throw new Error(`Local verification suite is missing live hot campaign fragment: ${fragment}`);
  }
}
