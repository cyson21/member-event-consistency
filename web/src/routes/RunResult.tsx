import type { ReactNode } from 'react';

type Counter = {
  success: number;
  failure: number;
  duplicate: number;
  overLimit: number;
  timeout: number;
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

type RabbitMetric = {
  acceptedLatencyMs: number;
  completionLatencyMs: number;
  queueLagMs: number;
  retryCount: number;
  dlqCount: number;
  measurementLabel: string;
};

type RunPayload = {
  runId: string;
  scenario: string;
  strategy: string;
  selectedAt: string;
  completedAt: string;
  controls: {
    concurrentRequests: number;
    apiInstances: number;
    workerCount: number;
    prefetch: number;
    lockWaitMs: number;
  };
  counters: Counter;
  invariantPassed: boolean;
  latencies: {
    p50Ms: number;
    p95Ms: number;
  };
  metrics: SharedMetric;
  rabbitmq?: RabbitMetric;
};

type Props = {
  run: RunPayload;
  completionMs: number;
};

function metricCard(title: string, value: ReactNode) {
  return (
    <article className="metric-card">
      <h3>{title}</h3>
      <p>{value}</p>
    </article>
  );
}

export function RunResult({ run, completionMs }: Props) {
  const showRabbit = run.strategy === 'RABBITMQ_DB_GUARD';
  const showCoupon = run.scenario === 'COUPON_CAMPAIGN_ISSUE';
  const showPoint = run.scenario === 'POINT_SPEND';

  return (
    <section className="panel">
      <h2>Run Result</h2>

      <div className="metric-grid">
        {metricCard('Run ID', run.runId)}
        {metricCard('Scenario', run.scenario)}
        {metricCard('Strategy', run.strategy)}
        {metricCard('Invariant Passed', run.invariantPassed ? 'YES' : 'NO')}
      </div>

      <h3>Run controls</h3>
      <div className="metric-grid">
        {metricCard('Concurrent requests', run.controls.concurrentRequests)}
        {metricCard('API instances', run.controls.apiInstances)}
        {metricCard('Worker count', run.controls.workerCount)}
        {metricCard('Prefetch', run.controls.prefetch)}
        {metricCard('Lock wait (ms)', run.controls.lockWaitMs)}
      </div>

      <h3>Result summary</h3>
      <div className="metric-grid">
        {metricCard('Success', run.counters.success)}
        {metricCard('Failure', run.counters.failure)}
        {metricCard('Duplicate', run.counters.duplicate)}
        {metricCard('Over limit', run.counters.overLimit)}
        {metricCard('Timeout', run.counters.timeout)}
      </div>

      <h3>Latency</h3>
      <div className="metric-grid">
        {metricCard('P50 (ms)', run.latencies.p50Ms)}
        {metricCard('P95 (ms)', run.latencies.p95Ms)}
        {metricCard('Observed completion time (ms)', completionMs)}
      </div>

      {showRabbit && run.rabbitmq ? (
        <>
          <h3>RabbitMQ latency split</h3>
          <div className="metric-grid">
            {metricCard('Measurement', run.rabbitmq.measurementLabel)}
            {metricCard(
              'RabbitMQ request accepted',
              <>
                202 Accepted: <strong>{run.rabbitmq.acceptedLatencyMs}ms</strong>
              </>,
            )}
            {metricCard(
              'Final completion',
              <>
                Completion: <strong>{run.rabbitmq.completionLatencyMs}ms</strong>
              </>,
            )}
            {metricCard(
              'Latency gap',
              <>
                {run.rabbitmq.completionLatencyMs - run.rabbitmq.acceptedLatencyMs}ms
              </>,
            )}
            {metricCard('Queue lag', `${run.rabbitmq.queueLagMs}ms`)}
            {metricCard('Retry / DLQ', `${run.rabbitmq.retryCount} / ${run.rabbitmq.dlqCount}`)}
          </div>
        </>
      ) : null}

      <h3>Device metrics</h3>
      <div className="metric-grid">
        {metricCard('DB lock wait (ms)', run.metrics.dbLockWaitMs)}
        {metricCard('Redis lock wait (ms)', run.metrics.redisLockWaitMs)}
        {metricCard('RabbitMQ lag', `${run.metrics.rabbitMqLagMs}ms`)}
        {metricCard('Outbox pending', run.metrics.outboxPending)}
        {metricCard('Outbox published', run.metrics.outboxPublished)}
        {metricCard('Outbox failed', run.metrics.outboxFailed)}
      </div>

      {showCoupon ? (
        <>
          <h3>Coupon metrics</h3>
          <div className="metric-grid">
            {metricCard('Coupon issued', run.metrics.couponIssuedCount)}
            {metricCard('Over issue', run.counters.overLimit)}
            {metricCard('Capacity rejection', run.counters.failure)}
          </div>
        </>
      ) : null}

      {showPoint ? (
        <>
          <h3>Point metrics</h3>
          <div className="metric-grid">
            {metricCard('Final point balance', run.metrics.finalPointBalance)}
            {metricCard('Negative balance', run.metrics.negativeBalanceCount)}
            {metricCard('Point ledger entries', run.metrics.pointLedgerEntryCount)}
            {metricCard('Idempotency replay', run.metrics.idempotencyReplayCount)}
            {metricCard('Idempotency hash mismatch', run.metrics.idempotencyHashMismatchCount)}
          </div>
        </>
      ) : null}
    </section>
  );
}
