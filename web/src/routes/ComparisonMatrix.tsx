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
  scenario: string;
  strategy: string;
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
  probe: string;
  scenario: string;
  strategy: string;
  invariantPassed: boolean;
  summary: string;
};

type Props = {
  summary: ComparisonSummary;
  sqlRecording: SqlRecordingSummary;
  entries: ComparisonEntry[];
  concurrency: ConcurrencyProbeSummary;
  concurrencyEntries: ConcurrencyProbeEntry[];
};

function summaryCard(label: string, value: string | number) {
  return (
    <article className="metric-card">
      <h3>{label}</h3>
      <p>{value}</p>
    </article>
  );
}

function statusLabel(passed: boolean) {
  return passed ? 'PASS' : 'FAIL';
}

export function ComparisonMatrix({
  summary,
  sqlRecording,
  entries,
  concurrency,
  concurrencyEntries,
}: Props) {
  return (
    <section className="panel">
      <h2>Run Result Comparison</h2>

      <div className="metric-grid">
        {summaryCard('Suite', summary.suite)}
        {summaryCard('Scenarios', summary.scenarioCount)}
        {summaryCard('Entries', summary.entryCount)}
        {summaryCard('Naive failures', summary.brokenNaiveCount)}
        {summaryCard('Guarded passes', summary.passingGuardedCount)}
        {summaryCard('Async accepted', summary.asyncAcceptedCount)}
        {summaryCard('Phase 2 entries', summary.phase2EntryCount)}
      </div>

      <h3>SQL recording</h3>
      <div className="metric-grid">
        {summaryCard('Backend', sqlRecording.backend)}
        {summaryCard('Local only', sqlRecording.localOnly ? 'true' : 'false')}
        {summaryCard('Routes', sqlRecording.routeCount)}
        {summaryCard('Naive failures', sqlRecording.brokenNaiveCount)}
        {summaryCard('Guarded passes', sqlRecording.passingGuardedCount)}
        {summaryCard('Async accepted', sqlRecording.asyncAcceptedCount)}
        {summaryCard('Phase 2 entries', sqlRecording.phase2EntryCount)}
        {summaryCard('SQL statements', sqlRecording.sqlStatementCount)}
      </div>

      <h3>MVP concurrency probe</h3>
      <div className="metric-grid">
        {summaryCard('Probe', concurrency.probe)}
        {summaryCard('Local only', concurrency.localOnly ? 'true' : 'false')}
        {summaryCard('Scenarios', concurrency.scenarioCount)}
        {summaryCard('Entries', concurrency.entryCount)}
        {summaryCard('Passing invariants', concurrency.passingInvariantCount)}
        {summaryCard('Phase 2 entries', concurrency.phase2EntryCount)}
      </div>

      <table className="comparison-table">
        <thead>
          <tr>
            <th>Probe</th>
            <th>Scenario</th>
            <th>Strategy</th>
            <th>Status</th>
            <th>Summary</th>
          </tr>
        </thead>
        <tbody>
          {concurrencyEntries.map((entry) => (
            <tr key={`${entry.probe}-${entry.scenario}`}>
              <td>{entry.probe}</td>
              <td>{entry.scenario}</td>
              <td>{entry.strategy}</td>
              <td>
                <span className={entry.invariantPassed ? 'status-pass' : 'status-fail'}>
                  {statusLabel(entry.invariantPassed)}
                </span>
              </td>
              <td>{entry.summary}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <table className="comparison-table">
        <thead>
          <tr>
            <th>Scenario</th>
            <th>Strategy</th>
            <th>Status</th>
            <th>HTTP</th>
            <th>Accepted / Completed</th>
            <th>Evidence</th>
            <th>SQL evidence</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr key={`${entry.scenario}-${entry.strategy}`}>
              <td>{entry.scenario}</td>
              <td>{entry.strategy}</td>
              <td>
                <span className={entry.invariantPassed ? 'status-pass' : 'status-fail'}>
                  {statusLabel(entry.invariantPassed)}
                </span>
              </td>
              <td>{entry.statusCode}</td>
              <td>
                {entry.acceptedCount} / {entry.completedCount}
              </td>
              <td>{entry.evidence}</td>
              <td>{entry.sqlEvidence || 'none'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
