import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-project-tracking-ledger-sync-surface.mjs',
  'check-project-tracking-ledger-sync.mjs',
  'const expectedCompletedChecks = 70',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing project tracking ledger sync fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-project-tracking-ledger-sync.mjs', import.meta.url), 'utf8');

for (const fragment of [
  '.ai-runs',
  'docs/project-tracking.md',
  'templates',
  'missing',
  'checkedRunLedgers',
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`Project tracking ledger sync guard is missing fragment: ${fragment}`);
  }
}
