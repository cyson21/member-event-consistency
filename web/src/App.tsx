import { useState } from 'react';
import './App.css';
import { ScenarioConsole, type ScenarioConsoleState } from './routes/ScenarioConsole';
import { RunResult } from './routes/RunResult';
import { EventTrace } from './routes/EventTrace';
import { ComparisonMatrix } from './routes/ComparisonMatrix';

type ScenarioKey = 'FIRST_LOGIN_REWARD' | 'COUPON_CAMPAIGN_ISSUE' | 'POINT_SPEND';
type Strategy =
  | 'NAIVE'
  | 'DB_GUARD'
  | 'REDIS_LOCK_DB_GUARD'
  | 'RABBITMQ_DB_GUARD'
  | 'DB_ROW_LOCK'
  | 'CONDITIONAL_UPDATE'
  | 'IDEMPOTENCY_REPLAY';

type RouteKey = 'console' | 'result' | 'comparison' | 'trace';

type ResultCounter = {
  success: number;
  failure: number;
  duplicate: number;
  overLimit: number;
  timeout: number;
};

type LatencyMetric = {
  p50Ms: number;
  p95Ms: number;
};

type RabbitMqMetric = {
  acceptedLatencyMs: number;
  completionLatencyMs: number;
  queueLagMs: number;
  retryCount: number;
  dlqCount: number;
  measurementLabel: string;
};

type SharedMetric = {
  dbLockWaitMs: number;
  redisLockWaitMs: number;
  rabbitMqLagMs: number;
  couponIssuedCount: number;
  finalPointBalance: number;
  negativeBalanceCount: number;
  pointLedgerEntryCount: number;
  idempotencyReplayCount: number;
  idempotencyHashMismatchCount: number;
  outboxPending: number;
  outboxPublished: number;
  outboxFailed: number;
};

type TraceEvent = {
  id: string;
  at: string;
  stage:
    | 'request'
    | 'db'
    | 'redis'
    | 'rabbitmq'
    | 'outbox'
    | 'final';
  action: string;
  result: string;
};

type ScenarioRun = {
  runId: string;
  scenario: ScenarioKey;
  strategy: Strategy;
  selectedAt: string;
  completedAt: string;
  controls: {
    concurrentRequests: number;
    apiInstances: number;
    workerCount: number;
    prefetch: number;
    lockWaitMs: number;
  };
  counters: ResultCounter;
  invariantPassed: boolean;
  latencies: LatencyMetric;
  metrics: SharedMetric;
  rabbitmq?: RabbitMqMetric;
};

type ComparisonSummary = {
  suite: 'MVP_SMOKE';
  scenarioCount: number;
  entryCount: number;
  brokenNaiveCount: number;
  passingGuardedCount: number;
  asyncAcceptedCount: number;
  phase2EntryCount: number;
};

type SqlRecordingSummary = {
  backend: 'SQL_RECORDING';
  localOnly: boolean;
  routeCount: number;
  brokenNaiveCount: number;
  passingGuardedCount: number;
  asyncAcceptedCount: number;
  phase2EntryCount: number;
  sqlStatementCount: number;
};

type ComparisonEntry = {
  scenario: ScenarioKey;
  strategy: Strategy;
  statusCode: number;
  invariantPassed: boolean;
  acceptedCount: number;
  completedCount: number;
  evidence: string;
  sqlEvidence: string;
};

type ConcurrencyProbeSummary = {
  probe: 'MVP_CONCURRENCY';
  localOnly: boolean;
  scenarioCount: number;
  entryCount: number;
  passingInvariantCount: number;
  phase2EntryCount: number;
};

type ConcurrencyProbeEntry = {
  probe: 'FIRST_LOGIN_REWARD_CONCURRENT' | 'COUPON_HOT_CAMPAIGN' | 'POINT_CONCURRENT';
  scenario: ScenarioKey;
  strategy: Strategy;
  invariantPassed: boolean;
  summary: string;
};

type RouteState = {
  page: RouteKey;
  activeRunId?: string;
};

const scenarios: Array<{
  value: ScenarioKey;
  label: string;
  description: string;
}> = [
  {
    value: 'FIRST_LOGIN_REWARD',
    label: 'First Login Reward',
    description: '최초 1회 보상 지급 + 알림 + outbox 경로를 비교',
  },
  {
    value: 'COUPON_CAMPAIGN_ISSUE',
    label: 'Coupon Campaign Issue',
    description: '회원당 1회 발급 + 캠페인 수량 제한 + RabbitMQ 전략 비교',
  },
  {
    value: 'POINT_SPEND',
    label: 'Point Spend',
    description: '포인트 음수 방지 + 재시도 멱등성 비교',
  },
];

const strategyOptionsByScenario: Record<ScenarioKey, Strategy[]> = {
  FIRST_LOGIN_REWARD: ['NAIVE', 'DB_GUARD', 'REDIS_LOCK_DB_GUARD'],
  COUPON_CAMPAIGN_ISSUE: ['NAIVE', 'DB_GUARD', 'REDIS_LOCK_DB_GUARD', 'RABBITMQ_DB_GUARD'],
  POINT_SPEND: ['NAIVE', 'DB_ROW_LOCK', 'CONDITIONAL_UPDATE', 'IDEMPOTENCY_REPLAY'],
};

const defaultForm: ScenarioConsoleState = {
  scenario: 'FIRST_LOGIN_REWARD',
  strategy: 'NAIVE',
  concurrentRequests: 80,
  apiInstances: 2,
  workerCount: 4,
  prefetch: 8,
  lockWaitMs: 250,
};

const mockRuns: Record<string, ScenarioRun> = {
  'FIRST_LOGIN_REWARD|NAIVE': {
    runId: 'RUN-001',
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'NAIVE',
    selectedAt: '2026-05-31T02:20:00.000Z',
    completedAt: '2026-05-31T02:20:00.410Z',
    controls: {
      concurrentRequests: 80,
      apiInstances: 2,
      workerCount: 4,
      prefetch: 8,
      lockWaitMs: 250,
    },
    counters: { success: 64, failure: 8, duplicate: 8, overLimit: 0, timeout: 0 },
    invariantPassed: false,
    latencies: { p50Ms: 132, p95Ms: 248 },
    metrics: {
      dbLockWaitMs: 64,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'FIRST_LOGIN_REWARD|DB_GUARD': {
    runId: 'RUN-002',
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'DB_GUARD',
    selectedAt: '2026-05-31T02:21:00.000Z',
    completedAt: '2026-05-31T02:21:00.492Z',
    controls: {
      concurrentRequests: 80,
      apiInstances: 2,
      workerCount: 4,
      prefetch: 8,
      lockWaitMs: 250,
    },
    counters: { success: 72, failure: 8, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 118, p95Ms: 220 },
    metrics: {
      dbLockWaitMs: 41,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'FIRST_LOGIN_REWARD|REDIS_LOCK_DB_GUARD': {
    runId: 'RUN-003',
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'REDIS_LOCK_DB_GUARD',
    selectedAt: '2026-05-31T02:22:00.000Z',
    completedAt: '2026-05-31T02:22:00.560Z',
    controls: {
      concurrentRequests: 80,
      apiInstances: 2,
      workerCount: 4,
      prefetch: 8,
      lockWaitMs: 250,
    },
    counters: { success: 73, failure: 5, duplicate: 0, overLimit: 0, timeout: 2 },
    invariantPassed: true,
    latencies: { p50Ms: 104, p95Ms: 208 },
    metrics: {
      dbLockWaitMs: 29,
      redisLockWaitMs: 14,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 1,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'COUPON_CAMPAIGN_ISSUE|NAIVE': {
    runId: 'RUN-100',
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'NAIVE',
    selectedAt: '2026-06-01T03:10:00.000Z',
    completedAt: '2026-06-01T03:10:00.080Z',
    controls: {
      concurrentRequests: 8,
      apiInstances: 3,
      workerCount: 0,
      prefetch: 0,
      lockWaitMs: 0,
    },
    counters: { success: 8, failure: 0, duplicate: 0, overLimit: 5, timeout: 0 },
    invariantPassed: false,
    latencies: { p50Ms: 12, p95Ms: 24 },
    metrics: {
      dbLockWaitMs: 0,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 8,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'COUPON_CAMPAIGN_ISSUE|DB_GUARD': {
    runId: 'RUN-102',
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'DB_GUARD',
    selectedAt: '2026-06-01T03:11:00.000Z',
    completedAt: '2026-06-01T03:11:00.130Z',
    controls: {
      concurrentRequests: 8,
      apiInstances: 3,
      workerCount: 0,
      prefetch: 0,
      lockWaitMs: 0,
    },
    counters: { success: 3, failure: 5, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 24, p95Ms: 48 },
    metrics: {
      dbLockWaitMs: 17,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 3,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'COUPON_CAMPAIGN_ISSUE|REDIS_LOCK_DB_GUARD': {
    runId: 'RUN-103',
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'REDIS_LOCK_DB_GUARD',
    selectedAt: '2026-06-01T03:12:00.000Z',
    completedAt: '2026-06-01T03:12:00.170Z',
    controls: {
      concurrentRequests: 8,
      apiInstances: 3,
      workerCount: 0,
      prefetch: 0,
      lockWaitMs: 250,
    },
    counters: { success: 3, failure: 5, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 30, p95Ms: 62 },
    metrics: {
      dbLockWaitMs: 8,
      redisLockWaitMs: 15,
      rabbitMqLagMs: 0,
      couponIssuedCount: 3,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'COUPON_CAMPAIGN_ISSUE|RABBITMQ_DB_GUARD': {
    runId: 'RUN-101',
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'RABBITMQ_DB_GUARD',
    selectedAt: '2026-05-31T02:23:00.000Z',
    completedAt: '2026-05-31T02:23:01.820Z',
    controls: {
      concurrentRequests: 150,
      apiInstances: 3,
      workerCount: 5,
      prefetch: 12,
      lockWaitMs: 300,
    },
    counters: { success: 121, failure: 29, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 95, p95Ms: 460 },
    metrics: {
      dbLockWaitMs: 58,
      redisLockWaitMs: 11,
      rabbitMqLagMs: 312,
      couponIssuedCount: 3,
      finalPointBalance: 0,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 0,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 1,
      outboxPublished: 2,
      outboxFailed: 0,
    },
    rabbitmq: {
      acceptedLatencyMs: 62,
      completionLatencyMs: 1810,
      queueLagMs: 312,
      retryCount: 1,
      dlqCount: 0,
      measurementLabel: 'Local fixture - not measured',
    },
  },
  'POINT_SPEND|NAIVE': {
    runId: 'RUN-200',
    scenario: 'POINT_SPEND',
    strategy: 'NAIVE',
    selectedAt: '2026-06-01T03:20:00.000Z',
    completedAt: '2026-06-01T03:20:00.060Z',
    controls: {
      concurrentRequests: 2,
      apiInstances: 2,
      workerCount: 0,
      prefetch: 0,
      lockWaitMs: 0,
    },
    counters: { success: 2, failure: 0, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: false,
    latencies: { p50Ms: 10, p95Ms: 18 },
    metrics: {
      dbLockWaitMs: 0,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: -400,
      negativeBalanceCount: 1,
      pointLedgerEntryCount: 2,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'POINT_SPEND|DB_ROW_LOCK': {
    runId: 'RUN-201',
    scenario: 'POINT_SPEND',
    strategy: 'DB_ROW_LOCK',
    selectedAt: '2026-05-31T02:24:00.000Z',
    completedAt: '2026-05-31T02:24:00.470Z',
    controls: {
      concurrentRequests: 120,
      apiInstances: 2,
      workerCount: 2,
      prefetch: 0,
      lockWaitMs: 150,
    },
    counters: { success: 1, failure: 1, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 82, p95Ms: 150 },
    metrics: {
      dbLockWaitMs: 15,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: 300,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 1,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'POINT_SPEND|CONDITIONAL_UPDATE': {
    runId: 'RUN-202',
    scenario: 'POINT_SPEND',
    strategy: 'CONDITIONAL_UPDATE',
    selectedAt: '2026-06-01T03:21:00.000Z',
    completedAt: '2026-06-01T03:21:00.090Z',
    controls: {
      concurrentRequests: 2,
      apiInstances: 2,
      workerCount: 0,
      prefetch: 0,
      lockWaitMs: 0,
    },
    counters: { success: 1, failure: 1, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 24, p95Ms: 40 },
    metrics: {
      dbLockWaitMs: 0,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: 300,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 1,
      idempotencyReplayCount: 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
  'POINT_SPEND|IDEMPOTENCY_REPLAY': {
    runId: 'RUN-203',
    scenario: 'POINT_SPEND',
    strategy: 'IDEMPOTENCY_REPLAY',
    selectedAt: '2026-06-01T03:22:00.000Z',
    completedAt: '2026-06-01T03:22:00.075Z',
    controls: {
      concurrentRequests: 2,
      apiInstances: 2,
      workerCount: 0,
      prefetch: 0,
      lockWaitMs: 0,
    },
    counters: { success: 1, failure: 0, duplicate: 1, overLimit: 0, timeout: 0 },
    invariantPassed: true,
    latencies: { p50Ms: 18, p95Ms: 34 },
    metrics: {
      dbLockWaitMs: 0,
      redisLockWaitMs: 0,
      rabbitMqLagMs: 0,
      couponIssuedCount: 0,
      finalPointBalance: 300,
      negativeBalanceCount: 0,
      pointLedgerEntryCount: 1,
      idempotencyReplayCount: 1,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
  },
};

const mockTraces: Record<string, TraceEvent[]> = {
  'RUN-100': [
    { id: 'E1', at: '00:00.000', stage: 'request', action: 'request burst', result: '8 issue attempts' },
    { id: 'E2', at: '00:00.012', stage: 'db', action: 'naive insert issue rows', result: 'issued=8, capacity=3' },
    { id: 'E3', at: '00:00.080', stage: 'final', action: 'invariant check', result: 'FAILED / overIssue=5' },
  ],
  'RUN-103': [
    { id: 'E1', at: '00:00.000', stage: 'request', action: 'request burst', result: '8 issue attempts' },
    { id: 'E2', at: '00:00.015', stage: 'redis', action: 'campaign lock attempts', result: 'lock:coupon-campaign:{campaignId}' },
    { id: 'E3', at: '00:00.060', stage: 'db', action: 'conditional capacity guard', result: 'issued=3, rejected=5' },
    { id: 'E4', at: '00:00.170', stage: 'final', action: 'invariant check', result: 'PASSED / overIssue=0' },
  ],
  'RUN-101': [
    { id: 'E1', at: '00:00.000', stage: 'request', action: 'request accepted', result: '202 Accepted' },
    { id: 'E2', at: '00:00.010', stage: 'rabbitmq', action: 'publish command', result: 'queue=campaign.issue.command acked' },
    { id: 'E3', at: '00:00.062', stage: 'request', action: 'return immediate response', result: 'client latency=62ms (202 Accepted)' },
    { id: 'E4', at: '00:00.220', stage: 'rabbitmq', action: 'consume', result: 'consumer started, campaign=SUMMER23' },
    { id: 'E5', at: '00:00.420', stage: 'db', action: 'apply DB guard', result: 'issued_count=98/100' },
    { id: 'E6', at: '00:00.620', stage: 'outbox', action: 'write outbox', result: 'pending=1' },
    { id: 'E7', at: '00:00.900', stage: 'outbox', action: 'publish outbox event', result: 'pending=0, published=1' },
    { id: 'E8', at: '00:01.200', stage: 'rabbitmq', action: 'retry', result: 'retryCount=1, reason=temporary lock timeout' },
    { id: 'E9', at: '00:01.820', stage: 'final', action: 'completion', result: 'status=ISSUED / final latency=1810ms' },
  ],
  'RUN-002': [
    { id: 'E1', at: '00:00.000', stage: 'request', action: 'request received', result: 'DB_GUARD selected' },
    { id: 'E2', at: '00:00.050', stage: 'db', action: 'check reward unique', result: 'existing=none' },
    { id: 'E3', at: '00:00.200', stage: 'outbox', action: 'upsert reward issue', result: 'success' },
    { id: 'E4', at: '00:00.350', stage: 'final', action: 'transaction commit', result: 'success' },
  ],
  'RUN-203': [
    { id: 'E1', at: '00:00.000', stage: 'request', action: 'two retry requests', result: 'same idempotency key' },
    { id: 'E2', at: '00:00.018', stage: 'db', action: 'first spend', result: 'balance 1000 -> 300, ledger=1' },
    { id: 'E3', at: '00:00.034', stage: 'db', action: 'idempotency replay', result: 'replay=1, no second ledger row' },
    { id: 'E4', at: '00:00.075', stage: 'final', action: 'invariant check', result: 'PASSED / negativeBalance=0' },
  ],
};

const comparisonSummary: ComparisonSummary = {
  suite: 'MVP_SMOKE',
  scenarioCount: 3,
  entryCount: 11,
  brokenNaiveCount: 3,
  passingGuardedCount: 8,
  asyncAcceptedCount: 1,
  phase2EntryCount: 0,
};

const sqlRecordingSummary: SqlRecordingSummary = {
  backend: 'SQL_RECORDING',
  localOnly: true,
  routeCount: 11,
  brokenNaiveCount: 3,
  passingGuardedCount: 8,
  asyncAcceptedCount: 1,
  phase2EntryCount: 0,
  sqlStatementCount: 185,
};

const comparisonEntries: ComparisonEntry[] = [
  {
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'NAIVE',
    statusCode: 200,
    invariantPassed: false,
    acceptedCount: 5,
    completedCount: 5,
    evidence: 'issued=5, duplicate=4',
    sqlEvidence: 'duplicate-prone attempt insert -> fake follow-up outbox rows',
  },
  {
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'DB_GUARD',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 5,
    completedCount: 5,
    evidence: 'issued=1, duplicate=0',
    sqlEvidence: 'unique reward issue insert -> fake follow-up outbox rows',
  },
  {
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'REDIS_LOCK_DB_GUARD',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 5,
    completedCount: 5,
    evidence: 'issued=1, duplicate=0',
    sqlEvidence: 'unique reward issue insert -> fake follow-up outbox rows',
  },
  {
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'NAIVE',
    statusCode: 200,
    invariantPassed: false,
    acceptedCount: 8,
    completedCount: 8,
    evidence: 'issued=8, overIssue=5, lane=0',
    sqlEvidence: '',
  },
  {
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'DB_GUARD',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 8,
    completedCount: 8,
    evidence: 'issued=3, overIssue=0, lane=0',
    sqlEvidence: 'campaign row lock -> coupon issue insert -> issued count update',
  },
  {
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'REDIS_LOCK_DB_GUARD',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 8,
    completedCount: 8,
    evidence: 'issued=3, overIssue=0, lane=0',
    sqlEvidence: 'campaign row lock -> coupon issue insert -> issued count update',
  },
  {
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'RABBITMQ_DB_GUARD',
    statusCode: 202,
    invariantPassed: true,
    acceptedCount: 8,
    completedCount: 8,
    evidence: 'issued=3, overIssue=0, lane=1',
    sqlEvidence: 'campaign row lock -> coupon issue insert -> issued count update',
  },
  {
    scenario: 'POINT_SPEND',
    strategy: 'NAIVE',
    statusCode: 200,
    invariantPassed: false,
    acceptedCount: 2,
    completedCount: 2,
    evidence: 'balance=-400, negative=1, replay=0, hashMismatch=0',
    sqlEvidence: '',
  },
  {
    scenario: 'POINT_SPEND',
    strategy: 'DB_ROW_LOCK',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 2,
    completedCount: 2,
    evidence: 'balance=300, negative=0, replay=0, hashMismatch=0',
    sqlEvidence: 'select balance for update -> conditional debit -> ledger insert -> idempotency record',
  },
  {
    scenario: 'POINT_SPEND',
    strategy: 'CONDITIONAL_UPDATE',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 2,
    completedCount: 2,
    evidence: 'balance=300, negative=0, replay=0, hashMismatch=0',
    sqlEvidence: 'conditional debit -> ledger insert -> idempotency record',
  },
  {
    scenario: 'POINT_SPEND',
    strategy: 'IDEMPOTENCY_REPLAY',
    statusCode: 200,
    invariantPassed: true,
    acceptedCount: 2,
    completedCount: 2,
    evidence: 'balance=300, negative=0, replay=1, hashMismatch=0',
    sqlEvidence: 'hash check -> replay lookup -> conditional debit once -> replay lookup',
  },
];

const concurrencyProbeSummary: ConcurrencyProbeSummary = {
  probe: 'MVP_CONCURRENCY',
  localOnly: true,
  scenarioCount: 3,
  entryCount: 3,
  passingInvariantCount: 3,
  phase2EntryCount: 0,
};

const concurrencyProbeEntries: ConcurrencyProbeEntry[] = [
  {
    probe: 'FIRST_LOGIN_REWARD_CONCURRENT',
    scenario: 'FIRST_LOGIN_REWARD',
    strategy: 'REDIS_LOCK_DB_GUARD',
    invariantPassed: true,
    summary: 'issued=1, duplicate=0, rejected=7',
  },
  {
    probe: 'COUPON_HOT_CAMPAIGN',
    scenario: 'COUPON_CAMPAIGN_ISSUE',
    strategy: 'RABBITMQ_DB_GUARD',
    invariantPassed: true,
    summary: 'issued=3, overIssue=0, lane=1',
  },
  {
    probe: 'POINT_CONCURRENT',
    scenario: 'POINT_SPEND',
    strategy: 'CONDITIONAL_UPDATE',
    invariantPassed: true,
    summary: 'balance=300, negative=0, rejected=7',
  },
];

const mockRunsById = Object.values(mockRuns).reduce<Record<string, ScenarioRun>>((acc, run) => {
  acc[run.runId] = run;
  return acc;
}, {});

function getMockRun(scenario: ScenarioKey, strategy: Strategy, controls: ScenarioConsoleState): ScenarioRun {
  const key = `${scenario}|${strategy}`;
  const known = mockRuns[key];
  if (known) {
    return known;
  }

  return {
    runId: `RUN-${scenario.slice(0, 2)}-FALLBACK`,
    scenario,
    strategy,
    selectedAt: '2026-05-31T02:25:00.000Z',
    completedAt: '2026-05-31T02:25:00.300Z',
    controls,
    counters: strategy === 'NAIVE'
      ? { success: 60, failure: 20, duplicate: 10, overLimit: 5, timeout: 5 }
      : { success: 80, failure: 20, duplicate: 0, overLimit: 0, timeout: 0 },
    invariantPassed: strategy !== 'NAIVE',
    latencies: { p50Ms: 150, p95Ms: 320 },
    metrics: {
      dbLockWaitMs: 35,
      redisLockWaitMs: 12,
      rabbitMqLagMs: 0,
      couponIssuedCount: scenario === 'COUPON_CAMPAIGN_ISSUE' ? 3 : 0,
      finalPointBalance: scenario === 'POINT_SPEND' ? 300 : 0,
      negativeBalanceCount: scenario === 'POINT_SPEND' && strategy === 'NAIVE' ? 1 : 0,
      pointLedgerEntryCount: scenario === 'POINT_SPEND' ? 1 : 0,
      idempotencyReplayCount: strategy === 'IDEMPOTENCY_REPLAY' ? 1 : 0,
      idempotencyHashMismatchCount: 0,
      outboxPending: 0,
      outboxPublished: 0,
      outboxFailed: 0,
    },
    rabbitmq: strategy === 'RABBITMQ_DB_GUARD'
      ? {
          acceptedLatencyMs: 45,
          completionLatencyMs: 900,
          queueLagMs: 120,
          retryCount: 2,
          dlqCount: 0,
          measurementLabel: 'Local fixture - not measured',
        }
      : undefined,
  };
}

function formatDurationMs(start: string, end: string) {
  return new Date(end).getTime() - new Date(start).getTime();
}

export default function App() {
  const [routeState, setRouteState] = useState<RouteState>({ page: 'console' });
  const [form, setForm] = useState<ScenarioConsoleState>(defaultForm);
  const [activeRunId, setActiveRunId] = useState<string>();

  const currentRun = activeRunId ? mockRunsById[activeRunId] : undefined;

  const activeTrace = activeRunId ? mockTraces[activeRunId] ?? [] : [];
  const selectedRun = currentRun ?? (form.scenario && form.strategy ? getMockRun(form.scenario, form.strategy, form) : undefined);

  function updateForm(next: Partial<ScenarioConsoleState>) {
    setForm((prev) => {
      const scenario = next.scenario ?? prev.scenario;
      const options = strategyOptionsByScenario[scenario];
      const strategy = next.strategy ?? (options.includes(prev.strategy) ? prev.strategy : options[0]);
      return { ...prev, ...next, strategy };
    });
  }

  function executeMockRun() {
    const nextRun = getMockRun(form.scenario, form.strategy, form);
    setActiveRunId(nextRun.runId);
    setRouteState((s) => ({ ...s, page: 'result' }));
  }

  const hasRun = Boolean(activeRunId);

  return (
    <div className="app-shell">
      <header className="topbar">
        <h1>Member Event Consistency Dashboard</h1>
        <p>Skeleton dashboard for MVP scenarios (static fixtures only)</p>
      </header>

      <nav className="tabs" aria-label="Main navigation">
        <button
          type="button"
          className={routeState.page === 'console' ? 'active' : ''}
          onClick={() => setRouteState({ page: 'console' })}
        >
          Scenario Console
        </button>
        <button
          type="button"
          className={routeState.page === 'result' ? 'active' : ''}
          onClick={() => setRouteState({ page: 'result' })}
          disabled={!hasRun}
        >
          Run Result
        </button>
        <button
          type="button"
          className={routeState.page === 'comparison' ? 'active' : ''}
          onClick={() => setRouteState({ page: 'comparison' })}
        >
          Compare Runs
        </button>
        <button
          type="button"
          className={routeState.page === 'trace' ? 'active' : ''}
          onClick={() => setRouteState({ page: 'trace' })}
          disabled={!hasRun}
        >
          Event Trace
        </button>
      </nav>

      <main>
        {routeState.page === 'console' ? (
          <ScenarioConsole
            scenarios={scenarios}
            strategies={strategyOptionsByScenario[form.scenario]}
            form={form}
            onChange={updateForm}
            onRun={executeMockRun}
          />
        ) : routeState.page === 'result' && selectedRun ? (
          <RunResult run={selectedRun} completionMs={formatDurationMs(selectedRun.selectedAt, selectedRun.completedAt)} />
        ) : routeState.page === 'comparison' ? (
          <ComparisonMatrix
            summary={comparisonSummary}
            sqlRecording={sqlRecordingSummary}
            entries={comparisonEntries}
            concurrency={concurrencyProbeSummary}
            concurrencyEntries={concurrencyProbeEntries}
          />
        ) : routeState.page === 'trace' ? (
          <EventTrace events={activeTrace} runId={activeRunId} />
        ) : null}
      </main>

      <footer className="footer">
        <p>Constraints: MVP scenarios = FIRST_LOGIN_REWARD / COUPON_CAMPAIGN_ISSUE / POINT_SPEND. No Phase 2 scenarios.</p>
      </footer>
    </div>
  );
}
