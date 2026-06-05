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
const rewardEntries = summary.expectedStatuses.filter((entry) => entry.path.includes('first-login-reward'));

if (rewardEntries.length !== 3) {
  throw new Error(`Expected 3 First Login Reward live-smoke entries, got ${rewardEntries.length}`);
}

const expectations = new Map([
  ['NAIVE', {
    expectedRewardIssuedCount: 5,
    expectedDuplicateRewardCount: 4,
    expectedRedisLockAttemptCount: 0,
    expectedAfterCommitNotificationCount: 5,
    expectedOutboxEventCount: 5,
  }],
  ['DB_GUARD', {
    expectedRewardIssuedCount: 1,
    expectedDuplicateRewardCount: 0,
    expectedRedisLockAttemptCount: 0,
    expectedAfterCommitNotificationCount: 1,
    expectedOutboxEventCount: 1,
  }],
  ['REDIS_LOCK_DB_GUARD', {
    expectedRewardIssuedCount: 1,
    expectedDuplicateRewardCount: 0,
    expectedRedisLockAttemptCount: 5,
    expectedAfterCommitNotificationCount: 1,
    expectedOutboxEventCount: 1,
  }],
]);

for (const entry of rewardEntries) {
  const expected = expectations.get(entry.strategy);
  if (!expected) {
    throw new Error(`Unexpected First Login Reward strategy in live-smoke summary: ${entry.strategy}`);
  }
  for (const [field, value] of Object.entries(expected)) {
    if (entry[field] !== value) {
      throw new Error(`${entry.strategy} expected ${field}=${value}, got ${entry[field]}`);
    }
  }
}
