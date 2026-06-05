import type { ChangeEvent } from 'react';
import './route.css';

export type ScenarioConsoleState = {
  scenario: 'FIRST_LOGIN_REWARD' | 'COUPON_CAMPAIGN_ISSUE' | 'POINT_SPEND';
  strategy:
    | 'NAIVE'
    | 'DB_GUARD'
    | 'REDIS_LOCK_DB_GUARD'
    | 'RABBITMQ_DB_GUARD'
    | 'DB_ROW_LOCK'
    | 'CONDITIONAL_UPDATE'
    | 'IDEMPOTENCY_REPLAY';
  concurrentRequests: number;
  apiInstances: number;
  workerCount: number;
  prefetch: number;
  lockWaitMs: number;
};

type Props = {
  scenarios: Array<{ value: ScenarioConsoleState['scenario']; label: string; description: string }>;
  strategies: ScenarioConsoleState['strategy'][];
  form: ScenarioConsoleState;
  onChange: (next: Partial<ScenarioConsoleState>) => void;
  onRun: () => void;
};

const scenarioPresets: Record<
  ScenarioConsoleState['scenario'],
  {
    invariant: string;
    failureMode: string;
    suggestedRequests: number;
    runShape: string;
  }
> = {
  FIRST_LOGIN_REWARD: {
    invariant: 'Only one reward issue per member',
    failureMode: 'duplicate reward issue',
    suggestedRequests: 5,
    runShape: 'single member burst',
  },
  COUPON_CAMPAIGN_ISSUE: {
    invariant: 'Issued coupons must not exceed campaign capacity',
    failureMode: 'hot campaign over-issue',
    suggestedRequests: 8,
    runShape: 'campaign capacity burst',
  },
  POINT_SPEND: {
    invariant: 'Point balance must not become negative',
    failureMode: 'retry double-spend or overspend race',
    suggestedRequests: 2,
    runShape: 'same member concurrent spend',
  },
};

function numInput(id: string, value: number, onChange: (v: number) => void) {
  return (
    <label htmlFor={id}>
      <span>{id}</span>
      <input
        id={id}
        type="number"
        min={0}
        value={value}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(Number(event.target.value))}
      />
    </label>
  );
}

export function ScenarioConsole({
  scenarios,
  strategies,
  form,
  onChange,
  onRun,
}: Props) {
  const preset = scenarioPresets[form.scenario];
  const estimatedRunShape = `${preset.runShape}, requests=${form.concurrentRequests}, api=${form.apiInstances}`;
  const validationIssues = [
    form.concurrentRequests <= 0 ? 'concurrentRequests must be positive' : '',
    form.apiInstances <= 0 ? 'apiInstances must be positive' : '',
    form.strategy === 'RABBITMQ_DB_GUARD' && form.workerCount <= 0
      ? 'RabbitMQ fixture requires a worker count greater than zero'
      : '',
    form.strategy === 'RABBITMQ_DB_GUARD' && form.prefetch <= 0
      ? 'RabbitMQ fixture requires prefetch greater than zero'
      : '',
  ].filter(Boolean);

  return (
    <section className="panel">
      <h2>Scenario Console</h2>
      <p>
        Choose one of MVP scenarios and strategy, then click <strong>Run scenario (fixture)</strong>.
      </p>

      <div className="grid">
        <label htmlFor="scenario">
          <span>Scenario</span>
          <select
            id="scenario"
            value={form.scenario}
            onChange={(event) =>
              onChange({
                scenario: event.target.value as ScenarioConsoleState['scenario'],
              })
            }
          >
            {scenarios.map((scenario) => (
              <option key={scenario.value} value={scenario.value}>
                {scenario.label}
              </option>
            ))}
          </select>
        </label>

        <label htmlFor="strategy">
          <span>Strategy</span>
          <select
            id="strategy"
            value={form.strategy}
            onChange={(event) =>
              onChange({
                strategy: event.target.value as ScenarioConsoleState['strategy'],
              })
            }
          >
            {strategies.map((strategy) => (
              <option key={strategy} value={strategy}>
                {strategy}
              </option>
            ))}
          </select>
        </label>

        {numInput('concurrentRequests', form.concurrentRequests, (v) => onChange({ concurrentRequests: v }))}
        {numInput('apiInstances', form.apiInstances, (v) => onChange({ apiInstances: v }))}
        {numInput('workerCount', form.workerCount, (v) => onChange({ workerCount: v }))}
        {numInput('prefetch', form.prefetch, (v) => onChange({ prefetch: v }))}
        {numInput('lockWaitMs', form.lockWaitMs, (v) => onChange({ lockWaitMs: v }))}
      </div>

      <p className="hint">
        {scenarios.find((item) => item.value === form.scenario)?.description ?? ''}
      </p>

      <h3>Scenario preset</h3>
      <div className="metric-grid">
        <article className="metric-card">
          <h3>Invariant</h3>
          <p>{preset.invariant}</p>
        </article>
        <article className="metric-card">
          <h3>Failure mode</h3>
          <p>{preset.failureMode}</p>
        </article>
        <article className="metric-card">
          <h3>Suggested request count</h3>
          <p>{preset.suggestedRequests}</p>
        </article>
        <article className="metric-card">
          <h3>Estimated fixture run</h3>
          <p>{estimatedRunShape}</p>
        </article>
      </div>

      <h3>Supported strategies</h3>
      <div className="strategy-list" aria-label="Supported strategies">
        {strategies.map((strategy) => (
          <span key={strategy}>{strategy}</span>
        ))}
      </div>

      <h3>Validation status</h3>
      {validationIssues.length ? (
        <ul className="validation-list">
          {validationIssues.map((issue) => (
            <li key={issue}>{issue}</li>
          ))}
        </ul>
      ) : (
        <p className="hint">MVP-only fixture is ready to run.</p>
      )}

      <button className="run-button" type="button" onClick={onRun}>
        Run scenario (fixture)
      </button>
    </section>
  );
}
