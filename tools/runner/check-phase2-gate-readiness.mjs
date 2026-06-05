import { existsSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const readinessRaw = execFileSync(
  process.execPath,
  ['tools/runner/check-dependency-bootstrap-readiness.mjs'],
  { encoding: 'utf8' },
);
const readiness = JSON.parse(readinessRaw.trim().split('\n').at(-1));

const todo = readFileSync(new URL('../../TODO.md', import.meta.url), 'utf8');
const projectTracking = readFileSync(new URL('../../docs/project-tracking.md', import.meta.url), 'utf8');
const adr0002 = new URL('../../docs/internal/adr/0002-phase2-scenario-selection.md', import.meta.url);
const adr0003 = new URL('../../docs/internal/adr/0003-phase2-batch-expiration-selection.md', import.meta.url);

const p6Start = todo.indexOf('## P6. Phase 2 Scenario Gate');
if (p6Start === -1) {
  throw new Error('TODO.md must keep the P6 Phase 2 Scenario Gate section.');
}
const p6 = todo.slice(p6Start);

for (const fragment of [
  '- [x] Finish MVP evidence before selecting a Phase 2 scenario.',
  '- [x] Re-read `docs/internal/reviews/2026-05-29-reality-scenario-expansion-review.md`.',
  '- [x] Select exactly one Phase 2 candidate.',
  '- [x] Prefer `Coupon Redemption / Usage` unless MVP evidence shows a clearer gap.',
  '- [x] Write an ADR that names the new invariant, failure mode, control device, metrics, and excluded follow-ups.',
  '- [x] Keep unselected candidates in the parking lot instead of adding them to the MVP.',
  '- [x] Keep external providers fake or local-only.',
]) {
  if (!p6.includes(fragment)) {
    throw new Error(`P6 gate must record the approved Phase 2 selection item: ${fragment}`);
  }
}

if (!existsSync(adr0002)) {
  throw new Error('Phase 2 selection ADR must exist after the approved candidate selection.');
}
if (!existsSync(adr0003)) {
  throw new Error('Batch Expiration Phase 2 selection ADR must exist after the approved next candidate selection.');
}

if (readiness.ready !== true || readiness.backendReady !== true || readiness.webReady !== true) {
  throw new Error('Phase 2 gate now expects dependency bootstrap to be ready before live MVP evidence collection.');
}

if (!Number.isInteger(readiness.mavenJunitPendingTestCount) || readiness.mavenJunitPendingTestCount < 1) {
  throw new Error('Phase 2 gate must keep Maven/JUnit pending tests visible before selection.');
}

for (const fragment of [
  'P6 Phase 2 Scenario Gate | Done',
  'Selected `Coupon Redemption / Usage`; Phase 2 implementation remains separate',
  'P8 Batch Expiration vs User Use',
  'Selected `Batch Expiration vs User Use` as the next Phase 2 scenario',
  'Phase 2 can add one realistic scenario only after the MVP produces evidence for each invariant.',
  'ADR `0002-phase2-scenario-selection.md` selects exactly one Phase 2 candidate: `Coupon Redemption / Usage`.',
  'Re-read the reality scenario expansion review: MVP stays fixed, candidates are selected one at a time, and `Coupon Redemption / Usage` remains the recommended first Phase 2 candidate unless current evidence shows a clearer gap.',
  'Run: `.ai-runs/2026-06-02-flyway-seed-bigint-fix/`',
  'Run: `.ai-runs/2026-06-02-redisson-lock-gateway/`',
  'Run: `.ai-runs/2026-06-02-reward-after-commit-outbox/`',
  'Run: `.ai-runs/2026-06-02-coupon-rabbitmq-worker/`',
  'Run: `.ai-runs/2026-06-02-rabbitmq-retry-dlq-handling/`',
  'Run: `.ai-runs/2026-06-02-live-hot-campaign-runner/`',
  'Run: `.ai-runs/2026-06-02-live-concurrent-spend-runner/`',
]) {
  if (!projectTracking.includes(fragment)) {
    throw new Error(`Project tracking must preserve parked Phase 2 gate fragment: ${fragment}`);
  }
}

console.log(JSON.stringify({
  status: 'selected',
  mvpEvidenceComplete: true,
  reviewRereadComplete: true,
  selectedCandidate: 'Coupon Redemption / Usage',
  nextSelectedCandidate: 'Batch Expiration vs User Use',
  dependencyReady: readiness.ready,
  backendReady: readiness.backendReady,
  webReady: readiness.webReady,
  mavenJunitPendingTestCount: readiness.mavenJunitPendingTestCount,
  selectionAdrExists: existsSync(adr0002),
  nextSelectionAdrExists: existsSync(adr0003),
}));
