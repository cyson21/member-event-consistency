const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const baseUrl = optionValue('--base-url') || 'http://localhost:8080';

const requests = [
  {
    title: 'Strategy DB guard hot campaign',
    probe: 'HOT_CAMPAIGN',
    path: '/api/scenarios/coupon-campaign-issue/runs',
    body: {
      campaignId: 94002,
      strategy: 'DB_GUARD',
      capacity: 3,
      requestCount: 8,
      transientRetryCount: 0,
      dlqCount: 0,
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 8,
    expectedCompletedCount: 8,
    expectedCouponIssuedCount: 3,
    expectedOverIssueCount: 0,
    expectedRejectedCount: 5,
    expectedRedisLockAttemptCount: 0,
    expectedRabbitMqLaneCount: 0,
    expectedQueueRetryCount: 0,
    expectedDlqCount: 0,
    expectedRabbitMqAcceptedLatencyMs: 0,
    expectedRabbitMqCompletionLatencyMs: 0,
    expectedRabbitMqLatencyMeasurement: 'NOT_APPLICABLE',
  },
  {
    title: 'Strategy Redis campaign lock hot campaign',
    probe: 'HOT_CAMPAIGN',
    path: '/api/scenarios/coupon-campaign-issue/runs',
    body: {
      campaignId: 94003,
      strategy: 'REDIS_LOCK_DB_GUARD',
      capacity: 3,
      requestCount: 8,
      transientRetryCount: 0,
      dlqCount: 0,
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 8,
    expectedCompletedCount: 8,
    expectedCouponIssuedCount: 3,
    expectedOverIssueCount: 0,
    expectedRejectedCount: 5,
    expectedRedisLockAttemptCount: 8,
    expectedRabbitMqLaneCount: 0,
    expectedQueueRetryCount: 0,
    expectedDlqCount: 0,
    expectedRabbitMqAcceptedLatencyMs: 0,
    expectedRabbitMqCompletionLatencyMs: 0,
    expectedRabbitMqLatencyMeasurement: 'NOT_APPLICABLE',
  },
  {
    title: 'Strategy RabbitMQ single-lane hot campaign',
    probe: 'HOT_CAMPAIGN',
    path: '/api/scenarios/coupon-campaign-issue/runs',
    body: {
      campaignId: 94004,
      strategy: 'RABBITMQ_DB_GUARD',
      capacity: 3,
      requestCount: 8,
      transientRetryCount: 1,
      dlqCount: 0,
    },
    expectedStatus: 202,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 8,
    expectedCompletedCount: 8,
    expectedCouponIssuedCount: 3,
    expectedOverIssueCount: 0,
    expectedRejectedCount: 5,
    expectedRedisLockAttemptCount: 0,
    expectedRabbitMqLaneCount: 1,
    expectedQueueRetryCount: 1,
    expectedDlqCount: 0,
    expectedRabbitMqAcceptedLatencyMs: 12,
    expectedRabbitMqCompletionLatencyMs: 102,
    expectedRabbitMqLatencyMeasurement: 'LOCAL_FIXTURE_BASELINE_NOT_MEASURED',
  },
];

if (dryRun) {
  console.log(JSON.stringify(summary([])));
} else {
  const results = [];
  for (const request of requests) {
    results.push(await executeRequest(request));
  }
  console.log(JSON.stringify(summary(results)));
}

function optionValue(name) {
  const index = process.argv.indexOf(name);
  if (index === -1 || index + 1 >= process.argv.length) {
    return '';
  }
  return process.argv[index + 1];
}

async function executeRequest(request) {
  const target = new URL(request.path, baseUrl).toString();
  const response = await fetch(target, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request.body),
  });
  const text = await response.text();
  if (response.status !== request.expectedStatus) {
    throw new Error(`${request.title} expected HTTP ${request.expectedStatus}, got ${response.status}: ${text}`);
  }
  const parsed = JSON.parse(text);
  assertField(request, parsed, 'statusCode', request.expectedStatus);
  assertField(request, parsed, 'scenario', 'COUPON_CAMPAIGN_ISSUE');
  assertField(request, parsed, 'strategy', request.body.strategy);
  assertField(request, parsed, 'invariantPassed', request.expectedInvariantPassed);
  assertField(request, parsed, 'acceptedCount', request.expectedAcceptedCount);
  assertField(request, parsed, 'completedCount', request.expectedCompletedCount);
  assertField(request, parsed, 'couponIssuedCount', request.expectedCouponIssuedCount);
  assertField(request, parsed, 'overIssueCount', request.expectedOverIssueCount);
  assertField(request, parsed, 'rejectedCount', request.expectedRejectedCount);
  assertField(request, parsed, 'redisLockAttemptCount', request.expectedRedisLockAttemptCount);
  assertField(request, parsed, 'rabbitMqLaneCount', request.expectedRabbitMqLaneCount);
  assertField(request, parsed, 'queueRetryCount', request.expectedQueueRetryCount);
  assertField(request, parsed, 'dlqCount', request.expectedDlqCount);
  if (request.body.strategy === 'RABBITMQ_DB_GUARD') {
    assertRabbitMqLatencyShape(request, parsed, text);
  } else {
    assertField(request, parsed, 'rabbitMqAcceptedLatencyMs', request.expectedRabbitMqAcceptedLatencyMs);
    assertField(request, parsed, 'rabbitMqCompletionLatencyMs', request.expectedRabbitMqCompletionLatencyMs);
  }
  return {
    title: request.title,
    probe: request.probe,
    strategy: parsed.strategy,
    statusCode: response.status,
    invariantPassed: parsed.invariantPassed,
    acceptedCount: parsed.acceptedCount,
    completedCount: parsed.completedCount,
    couponIssuedCount: parsed.couponIssuedCount,
    overIssueCount: parsed.overIssueCount,
    rejectedCount: parsed.rejectedCount,
    redisLockAttemptCount: parsed.redisLockAttemptCount,
    rabbitMqLaneCount: parsed.rabbitMqLaneCount,
    queueRetryCount: parsed.queueRetryCount,
    dlqCount: parsed.dlqCount,
    rabbitMqAcceptedLatencyMs: parsed.rabbitMqAcceptedLatencyMs,
    rabbitMqCompletionLatencyMs: parsed.rabbitMqCompletionLatencyMs,
  };
}

function assertField(request, parsed, field, expected) {
  if (parsed[field] !== expected) {
    throw new Error(`${request.title} expected ${field}=${expected}, got ${parsed[field]}: ${JSON.stringify(parsed)}`);
  }
}

function summary(results) {
  return {
    localOnly: true,
    phase2: false,
    freshLocalDb: true,
    dryRun,
    baseUrl,
    probe: 'HOT_CAMPAIGN',
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategyCount: requests.length,
    passingInvariantCount: results.filter((result) => result.invariantPassed).length,
    expectedStatuses: requests.map((request) => ({
      title: request.title,
      path: request.path,
      scenario: 'COUPON_CAMPAIGN_ISSUE',
      strategy: request.body.strategy,
      expectedStatus: request.expectedStatus,
      expectedInvariantPassed: request.expectedInvariantPassed,
      expectedAcceptedCount: request.expectedAcceptedCount,
      expectedCompletedCount: request.expectedCompletedCount,
      expectedCouponIssuedCount: request.expectedCouponIssuedCount,
      expectedOverIssueCount: request.expectedOverIssueCount,
      expectedRejectedCount: request.expectedRejectedCount,
      expectedRedisLockAttemptCount: request.expectedRedisLockAttemptCount,
      expectedRabbitMqLaneCount: request.expectedRabbitMqLaneCount,
      expectedQueueRetryCount: request.expectedQueueRetryCount,
      expectedDlqCount: request.expectedDlqCount,
      expectedRabbitMqAcceptedLatencyMs: request.expectedRabbitMqAcceptedLatencyMs,
      expectedRabbitMqCompletionLatencyMs: request.expectedRabbitMqCompletionLatencyMs,
      expectedRabbitMqLatencyMeasurement: request.expectedRabbitMqLatencyMeasurement,
    })),
    results,
  };
}

function assertRabbitMqLatencyShape(request, parsed, text) {
  for (const field of ['rabbitMqAcceptedLatencyMs', 'rabbitMqCompletionLatencyMs']) {
    if (typeof parsed[field] !== 'number') {
      throw new Error(`${request.title} missing numeric ${field}: ${text}`);
    }
    if (parsed[field] < 0) {
      throw new Error(`${request.title} expected ${field} to be non-negative, got ${parsed[field]}: ${text}`);
    }
  }
  if (parsed.rabbitMqCompletionLatencyMs < parsed.rabbitMqAcceptedLatencyMs) {
    throw new Error(
      `${request.title} expected completion latency >= accepted latency, got accepted=${parsed.rabbitMqAcceptedLatencyMs}, completion=${parsed.rabbitMqCompletionLatencyMs}: ${text}`,
    );
  }
}
