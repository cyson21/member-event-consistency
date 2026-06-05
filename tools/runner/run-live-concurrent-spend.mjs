const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const baseUrl = optionValue('--base-url') || 'http://localhost:8080';

const requests = [
  {
    title: 'Strategy DB row lock concurrent spend',
    probe: 'CONCURRENT_SPEND',
    path: '/api/scenarios/point-spend/runs',
    body: {
      memberId: 95005,
      strategy: 'DB_ROW_LOCK',
      initialBalance: 1000,
      spendAmount: 700,
      requestCount: 2,
      idempotencyKey: '',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedRejectedCount: 1,
    expectedFinalPointBalance: 300,
    expectedNegativeBalanceCount: 0,
    expectedPointLedgerEntryCount: 1,
    expectedIdempotencyReplayCount: 0,
    expectedIdempotencyHashMismatchCount: 0,
    expectedDbWaitMsP95: 15,
  },
  {
    title: 'Strategy conditional update concurrent spend',
    probe: 'CONCURRENT_SPEND',
    path: '/api/scenarios/point-spend/runs',
    body: {
      memberId: 95006,
      strategy: 'CONDITIONAL_UPDATE',
      initialBalance: 1000,
      spendAmount: 700,
      requestCount: 2,
      idempotencyKey: '',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedRejectedCount: 1,
    expectedFinalPointBalance: 300,
    expectedNegativeBalanceCount: 0,
    expectedPointLedgerEntryCount: 1,
    expectedIdempotencyReplayCount: 0,
    expectedIdempotencyHashMismatchCount: 0,
    expectedDbWaitMsP95: 0,
  },
  {
    title: 'Strategy idempotency replay concurrent spend',
    probe: 'CONCURRENT_SPEND',
    path: '/api/scenarios/point-spend/runs',
    body: {
      memberId: 95007,
      strategy: 'IDEMPOTENCY_REPLAY',
      initialBalance: 1000,
      spendAmount: 700,
      requestCount: 2,
      idempotencyKey: 'live-concurrent-spend-95007',
    },
    expectedStatus: 200,
    expectedInvariantPassed: true,
    expectedAcceptedCount: 2,
    expectedCompletedCount: 2,
    expectedRejectedCount: 0,
    expectedFinalPointBalance: 300,
    expectedNegativeBalanceCount: 0,
    expectedPointLedgerEntryCount: 1,
    expectedIdempotencyReplayCount: 1,
    expectedIdempotencyHashMismatchCount: 0,
    expectedDbWaitMsP95: 0,
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
  assertField(request, parsed, 'scenario', 'POINT_SPEND');
  assertField(request, parsed, 'strategy', request.body.strategy);
  assertField(request, parsed, 'invariantPassed', request.expectedInvariantPassed);
  assertField(request, parsed, 'acceptedCount', request.expectedAcceptedCount);
  assertField(request, parsed, 'completedCount', request.expectedCompletedCount);
  assertField(request, parsed, 'rejectedCount', request.expectedRejectedCount);
  assertField(request, parsed, 'finalPointBalance', request.expectedFinalPointBalance);
  assertField(request, parsed, 'negativeBalanceCount', request.expectedNegativeBalanceCount);
  assertField(request, parsed, 'pointLedgerEntryCount', request.expectedPointLedgerEntryCount);
  assertField(request, parsed, 'idempotencyReplayCount', request.expectedIdempotencyReplayCount);
  assertField(request, parsed, 'idempotencyHashMismatchCount', request.expectedIdempotencyHashMismatchCount);
  assertField(request, parsed, 'dbWaitMsP95', request.expectedDbWaitMsP95);
  return {
    title: request.title,
    probe: request.probe,
    strategy: parsed.strategy,
    statusCode: response.status,
    invariantPassed: parsed.invariantPassed,
    acceptedCount: parsed.acceptedCount,
    completedCount: parsed.completedCount,
    rejectedCount: parsed.rejectedCount,
    finalPointBalance: parsed.finalPointBalance,
    negativeBalanceCount: parsed.negativeBalanceCount,
    pointLedgerEntryCount: parsed.pointLedgerEntryCount,
    idempotencyReplayCount: parsed.idempotencyReplayCount,
    idempotencyHashMismatchCount: parsed.idempotencyHashMismatchCount,
    dbWaitMsP95: parsed.dbWaitMsP95,
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
    probe: 'CONCURRENT_SPEND',
    scenario: 'POINT_SPEND',
    strategyCount: requests.length,
    passingInvariantCount: results.filter((result) => result.invariantPassed).length,
    expectedStatuses: requests.map((request) => ({
      title: request.title,
      path: request.path,
      scenario: 'POINT_SPEND',
      strategy: request.body.strategy,
      expectedStatus: request.expectedStatus,
      expectedInvariantPassed: request.expectedInvariantPassed,
      expectedAcceptedCount: request.expectedAcceptedCount,
      expectedCompletedCount: request.expectedCompletedCount,
      expectedRejectedCount: request.expectedRejectedCount,
      expectedFinalPointBalance: request.expectedFinalPointBalance,
      expectedNegativeBalanceCount: request.expectedNegativeBalanceCount,
      expectedPointLedgerEntryCount: request.expectedPointLedgerEntryCount,
      expectedIdempotencyReplayCount: request.expectedIdempotencyReplayCount,
      expectedIdempotencyHashMismatchCount: request.expectedIdempotencyHashMismatchCount,
      expectedDbWaitMsP95: request.expectedDbWaitMsP95,
    })),
    results,
  };
}
