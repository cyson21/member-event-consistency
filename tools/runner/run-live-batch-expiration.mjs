const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const baseUrl = optionValue('--base-url') || 'http://localhost:8080';

const requests = [
  {
    title: 'Batch Expiration user use wins',
    path: '/api/scenarios/batch-expiration/runs',
    body: {
      couponIssueId: 96101,
      strategy: 'DB_GUARD',
      winner: 'USER_USE',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedCouponUsedCount: 1,
    expectedCouponExpiredCount: 0,
    expectedTerminalStateConflictCount: 0,
    expectedRejectedCount: 1,
    expectedRejectionReason: 'expiration rejected because coupon already used',
  },
  {
    title: 'Batch Expiration scheduled expiration wins',
    path: '/api/scenarios/batch-expiration/runs',
    body: {
      couponIssueId: 96102,
      strategy: 'DB_GUARD',
      winner: 'BATCH_EXPIRATION',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedCouponUsedCount: 0,
    expectedCouponExpiredCount: 1,
    expectedTerminalStateConflictCount: 0,
    expectedRejectedCount: 1,
    expectedRejectionReason: 'use rejected because coupon already expired',
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
  assertField(request, parsed, 'scenario', 'BATCH_EXPIRATION');
  assertField(request, parsed, 'strategy', request.body.strategy);
  assertField(request, parsed, 'invariantPassed', request.expectedInvariantPassed);
  assertField(request, parsed, 'acceptedCount', request.expectedAcceptedCount);
  assertField(request, parsed, 'completedCount', request.expectedCompletedCount);
  assertField(request, parsed, 'couponUsedCount', request.expectedCouponUsedCount);
  assertField(request, parsed, 'couponExpiredCount', request.expectedCouponExpiredCount);
  assertField(request, parsed, 'terminalStateConflictCount', request.expectedTerminalStateConflictCount);
  assertField(request, parsed, 'rejectedCount', request.expectedRejectedCount);
  assertField(request, parsed, 'rejectionReason', request.expectedRejectionReason);
  return {
    title: request.title,
    scenario: parsed.scenario,
    strategy: parsed.strategy,
    winner: request.body.winner,
    statusCode: response.status,
    invariantPassed: parsed.invariantPassed,
    acceptedCount: parsed.acceptedCount,
    completedCount: parsed.completedCount,
    couponUsedCount: parsed.couponUsedCount,
    couponExpiredCount: parsed.couponExpiredCount,
    terminalStateConflictCount: parsed.terminalStateConflictCount,
    rejectedCount: parsed.rejectedCount,
    rejectionReason: parsed.rejectionReason,
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
    scenario: 'BATCH_EXPIRATION',
    routeCount: requests.length,
    strategyCount: new Set(requests.map((request) => request.body.strategy)).size,
    winnerCount: new Set(requests.map((request) => request.body.winner)).size,
    passingInvariantCount: results.filter((result) => result.invariantPassed).length,
    expectedStatuses: requests.map((request) => ({
      title: request.title,
      path: request.path,
      scenario: 'BATCH_EXPIRATION',
      strategy: request.body.strategy,
      winner: request.body.winner,
      expectedStatus: request.expectedStatus,
      expectedInvariantPassed: request.expectedInvariantPassed,
      expectedAcceptedCount: request.expectedAcceptedCount,
      expectedCompletedCount: request.expectedCompletedCount,
      expectedCouponUsedCount: request.expectedCouponUsedCount,
      expectedCouponExpiredCount: request.expectedCouponExpiredCount,
      expectedTerminalStateConflictCount: request.expectedTerminalStateConflictCount,
      expectedRejectedCount: request.expectedRejectedCount,
      expectedRejectionReason: request.expectedRejectionReason,
    })),
    results,
  };
}
