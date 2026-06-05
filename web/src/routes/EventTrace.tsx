import { useMemo, useState } from 'react';

type TraceRow = {
  id: string;
  at: string;
  stage: 'request' | 'db' | 'redis' | 'rabbitmq' | 'outbox' | 'final';
  action: string;
  result: string;
};

type Props = {
  events: TraceRow[];
  runId?: string;
};

const traceStageOptions: Array<{ value: TraceRow['stage'] | 'all'; label: string }> = [
  { value: 'all', label: 'All stages' },
  { value: 'request', label: 'Request' },
  { value: 'db', label: 'DB' },
  { value: 'redis', label: 'Redis' },
  { value: 'rabbitmq', label: 'RabbitMQ' },
  { value: 'outbox', label: 'Outbox' },
  { value: 'final', label: 'Final' },
];

export function EventTrace({ events, runId }: Props) {
  const [stageFilter, setStageFilter] = useState<TraceRow['stage'] | 'all'>('all');
  const visibleEvents = useMemo(
    () => events.filter((event) => stageFilter === 'all' || event.stage === stageFilter),
    [events, stageFilter],
  );
  const stageCounts = useMemo(
    () =>
      events.reduce<Record<TraceRow['stage'], number>>(
        (acc, event) => {
          acc[event.stage] += 1;
          return acc;
        },
        { request: 0, db: 0, redis: 0, rabbitmq: 0, outbox: 0, final: 0 },
      ),
    [events],
  );

  return (
    <section className="panel">
      <h2>Event Trace</h2>
      <p>Run ID: {runId ?? 'N/A'}</p>

      <h3>Trace summary</h3>
      <div className="metric-grid">
        <article className="metric-card">
          <h3>Total events</h3>
          <p>{events.length}</p>
        </article>
        <article className="metric-card">
          <h3>DB</h3>
          <p>{stageCounts.db}</p>
        </article>
        <article className="metric-card">
          <h3>Redis</h3>
          <p>{stageCounts.redis}</p>
        </article>
        <article className="metric-card">
          <h3>RabbitMQ</h3>
          <p>{stageCounts.rabbitmq}</p>
        </article>
        <article className="metric-card">
          <h3>Outbox</h3>
          <p>{stageCounts.outbox}</p>
        </article>
        <article className="metric-card">
          <h3>Final</h3>
          <p>{stageCounts.final}</p>
        </article>
      </div>

      <label className="trace-filter" htmlFor="stage-filter">
        <span>Stage filter</span>
        <select
          id="stage-filter"
          value={stageFilter}
          onChange={(event) => setStageFilter(event.target.value as TraceRow['stage'] | 'all')}
        >
          {traceStageOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>

      <table>
        <thead>
          <tr>
            <th>Time</th>
            <th>Stage</th>
            <th>Action</th>
            <th>Result</th>
          </tr>
        </thead>
        <tbody>
          {visibleEvents.length ? (
            visibleEvents.map((event) => (
              <tr key={event.id}>
                <td>{event.at}</td>
                <td>{event.stage}</td>
                <td>{event.action}</td>
                <td>{event.result}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={4}>No events match the selected stage.</td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
