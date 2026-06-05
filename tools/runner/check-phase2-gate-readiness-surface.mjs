import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-phase2-gate-readiness-surface.mjs',
  'check-phase2-gate-readiness.mjs',
  'const expectedCompletedChecks = 69',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing Phase 2 gate readiness fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-phase2-gate-readiness.mjs', import.meta.url), 'utf8');

for (const fragment of [
  '0002-phase2-scenario-selection.md',
  '0003-phase2-batch-expiration-selection.md',
  '- [x] Finish MVP evidence before selecting a Phase 2 scenario.',
  '- [x] Re-read `docs/internal/reviews/2026-05-29-reality-scenario-expansion-review.md`.',
  '- [x] Select exactly one Phase 2 candidate.',
  'Coupon Redemption / Usage',
  'Batch Expiration vs User Use',
  'mvpEvidenceComplete: true',
  'reviewRereadComplete: true',
  "selectedCandidate: 'Coupon Redemption / Usage'",
  "nextSelectedCandidate: 'Batch Expiration vs User Use'",
  'nextSelectionAdrExists',
  'mavenJunitPendingTestCount',
  'P6 Phase 2 Scenario Gate | Done',
  "status: 'selected'",
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`Phase 2 gate readiness guard is missing fragment: ${fragment}`);
  }
}
