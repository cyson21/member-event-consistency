import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-web-typecheck-pending-inventory-surface.mjs',
  'check-web-typecheck-pending-inventory.mjs',
  'const expectedCompletedChecks = 70',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing web typecheck pending inventory fragment: ${fragment}`);
  }
}

const readiness = readFileSync(new URL('./check-dependency-bootstrap-readiness.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'webTypecheckPendingSources',
  'webTypecheckPendingSourceCount',
  'web/src',
  '.tsx',
  '.ts',
]) {
  if (!readiness.includes(fragment)) {
    throw new Error(`Dependency readiness classifier is missing web typecheck inventory fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-web-typecheck-pending-inventory.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'web/src/App.tsx',
  'web/src/main.tsx',
  'ComparisonMatrix.tsx',
  'ScenarioConsole.tsx',
  'npm --prefix web test',
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`Web typecheck pending inventory guard is missing fragment: ${fragment}`);
  }
}
