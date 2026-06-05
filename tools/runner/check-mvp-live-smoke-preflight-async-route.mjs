import { execFileSync } from 'node:child_process';

const raw = execFileSync(process.execPath, ['tools/runner/check-mvp-live-smoke-preflight-readiness.mjs'], {
  encoding: 'utf8',
});
const preflight = JSON.parse(raw.trim().split('\n').at(-1));

if (!Array.isArray(preflight.asyncAcceptedRoutes)) {
  throw new Error('Expected preflight asyncAcceptedRoutes array');
}

if (preflight.asyncAcceptedRoutes.length !== 1) {
  throw new Error(`Expected exactly one async accepted route, got ${preflight.asyncAcceptedRoutes.length}`);
}

const [route] = preflight.asyncAcceptedRoutes;
const expected = {
  title: 'Coupon Campaign Issue - RabbitMQ single-lane local path plus DB guard',
  path: '/api/scenarios/coupon-campaign-issue/runs',
  scenario: 'COUPON_CAMPAIGN_ISSUE',
  strategy: 'RABBITMQ_DB_GUARD',
  expectedStatus: 202,
  expectedResponseStatusCode: 202,
  expectedRabbitMqLaneCount: 1,
  expectedRabbitMqAcceptedLatencyMs: 12,
  expectedRabbitMqCompletionLatencyMs: 102,
};

for (const [field, value] of Object.entries(expected)) {
  if (route[field] !== value) {
    throw new Error(`Expected async route ${field}=${value}, got ${route[field]}`);
  }
}

if (route.expectedRabbitMqAcceptedLatencyMs >= route.expectedRabbitMqCompletionLatencyMs) {
  throw new Error('RabbitMQ accepted latency must stay lower than final completion latency');
}

console.log(JSON.stringify({
  status: 'passed',
  asyncAcceptedRoutes: preflight.asyncAcceptedRoutes.length,
  strategy: route.strategy,
  acceptedLatencyMs: route.expectedRabbitMqAcceptedLatencyMs,
  completionLatencyMs: route.expectedRabbitMqCompletionLatencyMs,
}));
