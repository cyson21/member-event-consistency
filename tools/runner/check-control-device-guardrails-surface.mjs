import { readFileSync } from 'node:fs';

const verifier = readFileSync(new URL('./check-control-device-guardrails.mjs', import.meta.url), 'utf8');

for (const fragment of [
  '0001-control-device-scope.md',
  'RecordingRewardLockGateway.java',
  'LocalCouponCampaignLockGateway.java',
  'lock:first-login-reward:',
  'lock:coupon-campaign:',
  'RABBITMQ_DB_GUARD(true)',
  'acceptedCount',
  'completedCount',
  'LOCAL_FAKE',
  'rejectImplementationFragment',
  'Kafka',
  '2PC',
]) {
  if (!verifier.includes(fragment)) {
    throw new Error(`Control-device guardrail verifier is missing fragment: ${fragment}`);
  }
}
