import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');

function source(relativePath) {
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function requireFragment(sourceName, text, fragment) {
  if (!text.includes(fragment)) {
    throw new Error(`${sourceName} is missing required fragment: ${fragment}`);
  }
}

function rejectImplementationFragment(sourceName, text, fragment) {
  if (text.toLowerCase().includes(fragment.toLowerCase())) {
    throw new Error(`${sourceName} must not include implementation fragment: ${fragment}`);
  }
}

function filesUnder(relativeDir) {
  const absoluteDir = join(repoRoot, relativeDir);
  return readdirSync(absoluteDir)
    .flatMap((entry) => {
      const absolutePath = join(absoluteDir, entry);
      if (statSync(absolutePath).isDirectory()) {
        return filesUnder(resolve(absolutePath).slice(repoRoot.length + 1));
      }
      return absolutePath.match(/\.(java|tsx|ts|yml|conf)$/) ? [absolutePath] : [];
    })
    .sort();
}

const adr = source('docs/internal/adr/0001-control-device-scope.md');
for (const fragment of [
  'PostgreSQL is the final consistency source.',
  'Redis/Redisson locks reduce concurrent entry before PostgreSQL work.',
  'They do not prove correctness by themselves',
  'It does not automatically serialize messages by business key.',
  'consumer concurrency `1`',
  '202 Accepted` latency and final completion latency are different measurements',
  'Local Docker measurements',
  'not production performance evidence',
  'Provider-like behavior stays fake/local-only in the MVP.',
]) {
  requireFragment('0001-control-device-scope.md', adr, fragment);
}

const infraReadme = source('infra/local/README.md');
for (const fragment of [
  'No Kafka',
  'No complex MSA split',
  'No 2PC / distributed transactions',
  'No real external providers',
  'RabbitMQ is not documented as automatic business-key serialization',
  'Redis lock is used only as pre-DB contention relief',
  'CAMPAIGN_WORKER_CONCURRENCY=1',
  'CAMPAIGN_WORKER_LANE_STRATEGY=campaign-id',
]) {
  requireFragment('infra/local/README.md', infraReadme, fragment);
}

const rewardLock = source('backend/src/main/java/com/example/consistency/reward/RecordingRewardLockGateway.java');
requireFragment('RecordingRewardLockGateway.java', rewardLock, 'lock:first-login-reward:');

const couponLock = source('backend/src/main/java/com/example/consistency/coupon/LocalCouponCampaignLockGateway.java');
requireFragment('LocalCouponCampaignLockGateway.java', couponLock, 'lock:coupon-campaign:');

const couponService = source('backend/src/main/java/com/example/consistency/coupon/CouponCampaignService.java');
for (const fragment of [
  'StrategyType.RABBITMQ_DB_GUARD',
  'issueWithDbGuard(command, false, "", 1)',
]) {
  requireFragment('CouponCampaignService.java', couponService, fragment);
}

const strategyType = source('backend/src/main/java/com/example/consistency/scenario/StrategyType.java');
requireFragment('StrategyType.java', strategyType, 'RABBITMQ_DB_GUARD(true)');

const scenarioRunService = source('backend/src/main/java/com/example/consistency/scenario/ScenarioRunService.java');
for (const fragment of [
  'ACCEPTED_THEN_COMPLETED',
  'SYNCHRONOUS_FINAL_RESULT',
  'strategy.isAsyncAccepted()',
]) {
  requireFragment('ScenarioRunService.java', scenarioRunService, fragment);
}

const invariantChecker = source('backend/src/main/java/com/example/consistency/scenario/InvariantChecker.java');
for (const fragment of [
  'input.strategy().isAsyncAccepted()',
  'input.completedCount() > input.acceptedCount()',
]) {
  requireFragment('InvariantChecker.java', invariantChecker, fragment);
}

const couponApiHandler = source('backend/src/main/java/com/example/consistency/coupon/CouponCampaignApiHandler.java');
for (const fragment of [
  'report.strategy().isAsyncAccepted() ? 202 : 200',
  'ScenarioMetricName.ACCEPTED_COUNT',
  'ScenarioMetricName.COMPLETED_COUNT',
]) {
  requireFragment('CouponCampaignApiHandler.java', couponApiHandler, fragment);
}

const runResult = source('web/src/routes/RunResult.tsx');
for (const fragment of [
  '202 Accepted:',
  'Final completion',
  'Completion:',
  'run.rabbitmq.acceptedLatencyMs',
  'run.rabbitmq.completionLatencyMs',
]) {
  requireFragment('RunResult.tsx', runResult, fragment);
}

const fakeProvider = source('backend/src/main/java/com/example/consistency/reward/SqlRewardFollowUpRecorder.java');
requireFragment('SqlRewardFollowUpRecorder.java', fakeProvider, 'LOCAL_FAKE');

for (const implementationFile of [
  ...filesUnder('backend/src/main/java'),
  ...filesUnder('web/src'),
  ...filesUnder('infra/local'),
]) {
  const relativePath = implementationFile.slice(repoRoot.length + 1);
  const text = readFileSync(implementationFile, 'utf8');
  for (const forbidden of [
    'lock:member:',
    'kafka:',
    'zookeeper:',
    'schema-registry:',
    'payment-provider',
    'sms-provider',
    'email-provider',
    'push-provider',
    'Twilio',
    'SendGrid',
    'FCM',
  ]) {
    rejectImplementationFragment(relativePath, text, forbidden);
  }
}
