import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const catalogPath = join(repoRoot, 'docs/internal/live-smoke/mvp-route-requests.http');
const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const baseUrl = optionValue('--base-url') || 'http://localhost:8080';

const catalog = readFileSync(catalogPath, 'utf8');
const requests = parseCatalog(catalog);

if (requests.length !== 11) {
  throw new Error(`Expected 11 MVP live smoke requests, got ${requests.length}`);
}

for (const request of requests) {
  if (request.url.includes('coupon-redemption') || request.url.includes('batch-expiration')) {
    throw new Error(`Phase 2 route is not allowed in MVP live smoke: ${request.url}`);
  }
}

if (dryRun) {
  console.log(JSON.stringify(summary(requests, [])));
} else {
  const results = [];
  for (const request of requests) {
    results.push(await executeRequest(request));
  }
  console.log(JSON.stringify(summary(requests, results)));
}

function optionValue(name) {
  const index = process.argv.indexOf(name);
  if (index === -1 || index + 1 >= process.argv.length) {
    return '';
  }
  return process.argv[index + 1];
}

function parseCatalog(source) {
  return source
    .split(/^### /m)
    .slice(1)
    .map((section) => {
      const title = section.slice(0, section.indexOf('\n')).trim();
      const postMatch = section.match(/POST\s+(\S+)/);
      const bodyStart = section.indexOf('{');
      const bodyEnd = section.lastIndexOf('}');
      if (!postMatch || bodyStart === -1 || bodyEnd === -1) {
        throw new Error(`Invalid live smoke request section: ${title}`);
      }
      const body = JSON.parse(section.slice(bodyStart, bodyEnd + 1));
      return {
        title,
        method: 'POST',
        url: postMatch[1],
        path: new URL(postMatch[1]).pathname,
        scenario: scenarioName(new URL(postMatch[1]).pathname),
        expectedScenario: scenarioName(new URL(postMatch[1]).pathname),
        expectedStrategy: body.strategy,
        body,
        expectedStatus: body.strategy === 'RABBITMQ_DB_GUARD' ? 202 : 200,
        expectedResponseStatusCode: body.strategy === 'RABBITMQ_DB_GUARD' ? 202 : 200,
        expectedInvariantPassed: body.strategy === 'NAIVE' ? false : true,
        expectedAcceptedCount: body.requestCount,
        expectedCompletedCount: body.requestCount,
        expectedRewardEvidence: expectedRewardEvidence(new URL(postMatch[1]).pathname, body),
        expectedCouponEvidence: expectedCouponEvidence(new URL(postMatch[1]).pathname, body),
        expectedPointEvidence: expectedPointEvidence(new URL(postMatch[1]).pathname, body),
      };
    });
}

function expectedRewardEvidence(path, body) {
  if (!path.includes('first-login-reward')) {
    return {};
  }
  const guardedIssueCount = body.strategy === 'NAIVE' ? body.requestCount : 1;
  return {
    rewardIssuedCount: guardedIssueCount,
    duplicateRewardCount: body.strategy === 'NAIVE' ? Math.max(body.requestCount - 1, 0) : 0,
    redisLockAttemptCount: body.strategy === 'REDIS_LOCK_DB_GUARD' ? body.requestCount : 0,
    afterCommitNotificationCount: guardedIssueCount,
    outboxEventCount: guardedIssueCount,
  };
}

function expectedCouponEvidence(path, body) {
  if (!path.includes('coupon-campaign-issue')) {
    return {};
  }
  if (body.strategy === 'NAIVE') {
    return {
      couponIssuedCount: body.requestCount,
      overIssueCount: Math.max(body.requestCount - body.capacity, 0),
      rejectedCount: 0,
      redisLockAttemptCount: 0,
      rabbitMqLaneCount: 0,
      queueRetryCount: 0,
      dlqCount: 0,
      queueLagMsP95: 0,
      rabbitMqAcceptedLatencyMs: 0,
      rabbitMqCompletionLatencyMs: 0,
    };
  }

  const processableCommandCount = body.strategy === 'RABBITMQ_DB_GUARD'
    ? body.requestCount - body.dlqCount
    : body.requestCount;
  const couponIssuedCount = Math.min(body.capacity, processableCommandCount);
  const rejectedCount = processableCommandCount - couponIssuedCount;
  const rabbitMqLaneCount = body.strategy === 'RABBITMQ_DB_GUARD' ? 1 : 0;
  const queueLagMsP95 = rabbitMqLaneCount > 0
    ? Math.max(1, body.requestCount + body.transientRetryCount + body.dlqCount) * 10
    : 0;
  const rabbitMqAcceptedLatencyMs = rabbitMqLaneCount > 0 ? 12 : 0;
  return {
    couponIssuedCount,
    overIssueCount: 0,
    rejectedCount,
    redisLockAttemptCount: body.strategy === 'REDIS_LOCK_DB_GUARD' ? body.requestCount : 0,
    rabbitMqLaneCount,
    queueRetryCount: rabbitMqLaneCount > 0 ? body.transientRetryCount : 0,
    dlqCount: rabbitMqLaneCount > 0 ? body.dlqCount : 0,
    queueLagMsP95,
    rabbitMqAcceptedLatencyMs,
    rabbitMqCompletionLatencyMs: rabbitMqLaneCount > 0 ? rabbitMqAcceptedLatencyMs + queueLagMsP95 : 0,
    rabbitMqLatencyMeasurement: rabbitMqLaneCount > 0 ? 'LOCAL_FIXTURE_BASELINE_NOT_MEASURED' : 'NOT_APPLICABLE',
  };
}

function expectedPointEvidence(path, body) {
  if (!path.includes('point-spend')) {
    return {};
  }
  if (body.strategy === 'NAIVE') {
    const finalPointBalance = body.initialBalance - (body.spendAmount * body.requestCount);
    return {
      finalPointBalance,
      negativeBalanceCount: finalPointBalance < 0 ? 1 : 0,
      pointLedgerEntryCount: body.requestCount,
      rejectedCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      dbWaitMsP95: 0,
    };
  }

  const successfulSpendCount = body.strategy === 'IDEMPOTENCY_REPLAY'
    ? (body.requestCount === 0 ? 0 : 1)
    : Math.min(body.requestCount, Math.floor(body.initialBalance / body.spendAmount));
  const finalPointBalance = body.initialBalance - (body.spendAmount * successfulSpendCount);
  return {
    finalPointBalance,
    negativeBalanceCount: finalPointBalance < 0 ? 1 : 0,
    pointLedgerEntryCount: successfulSpendCount,
    rejectedCount: body.strategy === 'IDEMPOTENCY_REPLAY' ? 0 : body.requestCount - successfulSpendCount,
    idempotencyReplayCount: body.strategy === 'IDEMPOTENCY_REPLAY'
      ? Math.max(0, body.requestCount - successfulSpendCount)
      : 0,
    idempotencyHashMismatchCount: 0,
    dbWaitMsP95: body.strategy === 'DB_ROW_LOCK' ? 15 : 0,
  };
}

async function executeRequest(request) {
  const target = new URL(request.path, baseUrl).toString();
  const response = await fetch(target, {
    method: request.method,
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
  for (const field of ['acceptedCount', 'completedCount']) {
    if (typeof parsed[field] !== 'number') {
      throw new Error(`${request.title} missing numeric ${field}: ${text}`);
    }
  }
  if (typeof parsed.invariantPassed !== 'boolean') {
    throw new Error(`${request.title} missing boolean invariantPassed: ${text}`);
  }
  if (parsed.statusCode !== request.expectedResponseStatusCode) {
    throw new Error(
      `${request.title} expected response statusCode=${request.expectedResponseStatusCode}, got ${parsed.statusCode}: ${text}`,
    );
  }
  if (parsed.scenario !== request.expectedScenario) {
    throw new Error(`${request.title} expected scenario=${request.expectedScenario}, got ${parsed.scenario}: ${text}`);
  }
  if (parsed.strategy !== request.expectedStrategy) {
    throw new Error(`${request.title} expected strategy=${request.expectedStrategy}, got ${parsed.strategy}: ${text}`);
  }
  if (parsed.invariantPassed !== request.expectedInvariantPassed) {
    throw new Error(
      `${request.title} expected invariantPassed=${request.expectedInvariantPassed}, got ${parsed.invariantPassed}: ${text}`,
    );
  }
  if (parsed.acceptedCount !== request.expectedAcceptedCount) {
    throw new Error(
      `${request.title} expected acceptedCount=${request.expectedAcceptedCount}, got ${parsed.acceptedCount}: ${text}`,
    );
  }
  if (parsed.completedCount !== request.expectedCompletedCount) {
    throw new Error(
      `${request.title} expected completedCount=${request.expectedCompletedCount}, got ${parsed.completedCount}: ${text}`,
    );
  }
  for (const [field, expected] of Object.entries({
    ...request.expectedRewardEvidence,
    ...request.expectedCouponEvidence,
    ...request.expectedPointEvidence,
  })) {
    if (field === 'rabbitMqLatencyMeasurement') {
      continue;
    }
    if (typeof parsed[field] !== 'number') {
      throw new Error(`${request.title} missing numeric ${field}: ${text}`);
    }
    if (request.body.strategy === 'RABBITMQ_DB_GUARD' && [
      'queueLagMsP95',
      'rabbitMqAcceptedLatencyMs',
      'rabbitMqCompletionLatencyMs',
    ].includes(field)) {
      continue;
    }
    if (parsed[field] !== expected) {
      throw new Error(`${request.title} expected ${field}=${expected}, got ${parsed[field]}: ${text}`);
    }
  }
  if (request.body.strategy === 'RABBITMQ_DB_GUARD') {
    assertRabbitMqLatencyShape(request, parsed, text);
  }
  return {
    title: request.title,
    statusCode: response.status,
    expectedStatus: request.expectedStatus,
    responseStatusCode: parsed.statusCode,
    expectedResponseStatusCode: request.expectedResponseStatusCode,
    scenario: parsed.scenario,
    expectedScenario: request.expectedScenario,
    strategy: parsed.strategy,
    expectedStrategy: request.expectedStrategy,
    acceptedCount: parsed.acceptedCount,
    completedCount: parsed.completedCount,
    expectedAcceptedCount: request.expectedAcceptedCount,
    expectedCompletedCount: request.expectedCompletedCount,
    invariantPassed: parsed.invariantPassed,
    expectedInvariantPassed: request.expectedInvariantPassed,
    expectedRewardEvidence: request.expectedRewardEvidence,
    expectedCouponEvidence: request.expectedCouponEvidence,
    expectedPointEvidence: request.expectedPointEvidence,
  };
}

function summary(parsedRequests, results) {
  return {
    localOnly: true,
    phase2: false,
    freshLocalDb: true,
    dryRun,
    baseUrl,
    routeCount: parsedRequests.length,
    scenarioCount: new Set(parsedRequests.map((request) => scenarioName(request.path))).size,
    asyncAcceptedCount: parsedRequests.filter((request) => request.expectedStatus === 202).length,
    expectedStatuses: parsedRequests.map((request) => ({
      title: request.title,
      path: request.path,
      scenario: request.scenario,
      expectedScenario: request.expectedScenario,
      strategy: request.body.strategy,
      expectedStrategy: request.expectedStrategy,
      expectedStatus: request.expectedStatus,
      expectedResponseStatusCode: request.expectedResponseStatusCode,
      expectedInvariantPassed: request.expectedInvariantPassed,
      expectedAcceptedCount: request.expectedAcceptedCount,
      expectedCompletedCount: request.expectedCompletedCount,
      expectedRewardIssuedCount: request.expectedRewardEvidence.rewardIssuedCount,
      expectedDuplicateRewardCount: request.expectedRewardEvidence.duplicateRewardCount,
      expectedRedisLockAttemptCount: request.expectedRewardEvidence.redisLockAttemptCount
        ?? request.expectedCouponEvidence.redisLockAttemptCount,
      expectedAfterCommitNotificationCount: request.expectedRewardEvidence.afterCommitNotificationCount,
      expectedOutboxEventCount: request.expectedRewardEvidence.outboxEventCount,
      expectedCouponIssuedCount: request.expectedCouponEvidence.couponIssuedCount,
      expectedOverIssueCount: request.expectedCouponEvidence.overIssueCount,
      expectedRejectedCount: request.expectedCouponEvidence.rejectedCount ?? request.expectedPointEvidence.rejectedCount,
      expectedRabbitMqLaneCount: request.expectedCouponEvidence.rabbitMqLaneCount,
      expectedQueueRetryCount: request.expectedCouponEvidence.queueRetryCount,
      expectedDlqCount: request.expectedCouponEvidence.dlqCount,
      expectedQueueLagMsP95: request.expectedCouponEvidence.queueLagMsP95,
      expectedRabbitMqAcceptedLatencyMs: request.expectedCouponEvidence.rabbitMqAcceptedLatencyMs,
      expectedRabbitMqCompletionLatencyMs: request.expectedCouponEvidence.rabbitMqCompletionLatencyMs,
      expectedRabbitMqLatencyMeasurement: request.expectedCouponEvidence.rabbitMqLatencyMeasurement,
      expectedFinalPointBalance: request.expectedPointEvidence.finalPointBalance,
      expectedNegativeBalanceCount: request.expectedPointEvidence.negativeBalanceCount,
      expectedPointLedgerEntryCount: request.expectedPointEvidence.pointLedgerEntryCount,
      expectedIdempotencyReplayCount: request.expectedPointEvidence.idempotencyReplayCount,
      expectedIdempotencyHashMismatchCount: request.expectedPointEvidence.idempotencyHashMismatchCount,
      expectedDbWaitMsP95: request.expectedPointEvidence.dbWaitMsP95,
    })),
    results,
  };
}

function assertRabbitMqLatencyShape(request, parsed, text) {
  for (const field of ['queueLagMsP95', 'rabbitMqAcceptedLatencyMs', 'rabbitMqCompletionLatencyMs']) {
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

function scenarioName(path) {
  if (path.includes('first-login-reward')) {
    return 'FIRST_LOGIN_REWARD';
  }
  if (path.includes('coupon-campaign-issue')) {
    return 'COUPON_CAMPAIGN_ISSUE';
  }
  if (path.includes('point-spend')) {
    return 'POINT_SPEND';
  }
  return 'UNKNOWN';
}
