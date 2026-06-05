const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const baseUrl = optionValue('--base-url') || 'http://localhost:8080';

const requests = [
  {
    title: 'Coupon Redemption DB guard terminal transition',
    path: '/api/scenarios/coupon-redemption/runs',
    body: {
      couponIssueId: 96001,
      strategy: 'DB_GUARD',
      requestCount: 2,
      idempotencyKey: '',
      firstRequestHash: '',
      retryRequestHash: '',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedCouponUsedCount: 1,
    expectedDoubleUseCount: 0,
    expectedTerminalStateConflictCount: 0,
    expectedRejectedCount: 1,
    expectedIdempotencyReplayCount: 0,
    expectedIdempotencyHashMismatchCount: 0,
  },
  {
    title: 'Coupon Redemption idempotency replay',
    path: '/api/scenarios/coupon-redemption/runs',
    body: {
      couponIssueId: 96002,
      strategy: 'IDEMPOTENCY_REPLAY',
      requestCount: 2,
      idempotencyKey: 'redeem-96002',
      firstRequestHash: 'member=96002|coupon=96002',
      retryRequestHash: 'member=96002|coupon=96002',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedCouponUsedCount: 1,
    expectedDoubleUseCount: 0,
    expectedTerminalStateConflictCount: 0,
    expectedRejectedCount: 0,
    expectedIdempotencyReplayCount: 1,
    expectedIdempotencyHashMismatchCount: 0,
  },
  {
    title: 'Coupon Redemption idempotency hash mismatch',
    path: '/api/scenarios/coupon-redemption/runs',
    body: {
      couponIssueId: 96003,
      strategy: 'IDEMPOTENCY_REPLAY',
      requestCount: 2,
      idempotencyKey: 'redeem-96003',
      firstRequestHash: 'member=96003|coupon=96003',
      retryRequestHash: 'member=96004|coupon=96003',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedCouponUsedCount: 1,
    expectedDoubleUseCount: 0,
    expectedTerminalStateConflictCount: 0,
    expectedRejectedCount: 1,
    expectedIdempotencyReplayCount: 0,
    expectedIdempotencyHashMismatchCount: 1,
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
  assertField(request, parsed, 'scenario', 'COUPON_REDEMPTION');
  assertField(request, parsed, 'strategy', request.body.strategy);
  assertField(request, parsed, 'invariantPassed', request.expectedInvariantPassed);
  assertField(request, parsed, 'acceptedCount', request.expectedAcceptedCount);
  assertField(request, parsed, 'completedCount', request.expectedCompletedCount);
  assertField(request, parsed, 'couponUsedCount', request.expectedCouponUsedCount);
  assertField(request, parsed, 'doubleUseCount', request.expectedDoubleUseCount);
  assertField(request, parsed, 'terminalStateConflictCount', request.expectedTerminalStateConflictCount);
  assertField(request, parsed, 'rejectedCount', request.expectedRejectedCount);
  assertField(request, parsed, 'idempotencyReplayCount', request.expectedIdempotencyReplayCount);
  assertField(request, parsed, 'idempotencyHashMismatchCount', request.expectedIdempotencyHashMismatchCount);
  return {
    title: request.title,
    scenario: parsed.scenario,
    strategy: parsed.strategy,
    statusCode: response.status,
    invariantPassed: parsed.invariantPassed,
    acceptedCount: parsed.acceptedCount,
    completedCount: parsed.completedCount,
    couponUsedCount: parsed.couponUsedCount,
    doubleUseCount: parsed.doubleUseCount,
    terminalStateConflictCount: parsed.terminalStateConflictCount,
    rejectedCount: parsed.rejectedCount,
    idempotencyReplayCount: parsed.idempotencyReplayCount,
    idempotencyHashMismatchCount: parsed.idempotencyHashMismatchCount,
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
    phase2: true,
    freshLocalDb: true,
    dryRun,
    baseUrl,
    scenario: 'COUPON_REDEMPTION',
    routeCount: requests.length,
    strategyCount: new Set(requests.map((request) => request.body.strategy)).size,
    passingInvariantCount: results.filter((result) => result.invariantPassed).length,
    expectedStatuses: requests.map((request) => ({
      title: request.title,
      path: request.path,
      scenario: 'COUPON_REDEMPTION',
      strategy: request.body.strategy,
      expectedStatus: request.expectedStatus,
      expectedInvariantPassed: request.expectedInvariantPassed,
      expectedAcceptedCount: request.expectedAcceptedCount,
      expectedCompletedCount: request.expectedCompletedCount,
      expectedCouponUsedCount: request.expectedCouponUsedCount,
      expectedDoubleUseCount: request.expectedDoubleUseCount,
      expectedTerminalStateConflictCount: request.expectedTerminalStateConflictCount,
      expectedRejectedCount: request.expectedRejectedCount,
      expectedIdempotencyReplayCount: request.expectedIdempotencyReplayCount,
      expectedIdempotencyHashMismatchCount: request.expectedIdempotencyHashMismatchCount,
      requestCount: request.body.requestCount,
      idempotencyKey: request.body.idempotencyKey,
      firstRequestHash: request.body.firstRequestHash,
      retryRequestHash: request.body.retryRequestHash,
    })),
    results,
  };
}
