import { readFileSync } from 'node:fs';

const runner = readFileSync(new URL('./run-mvp-live-smoke.mjs', import.meta.url), 'utf8');
const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'freshLocalDb: true',
  'localOnly: true',
  'phase2: false',
]) {
  if (!runner.includes(fragment)) {
    throw new Error(`MVP live-smoke runner is missing fresh DB precondition fragment: ${fragment}`);
  }
}

for (const fragment of [
  'check-mvp-live-smoke-fresh-db-precondition-surface.mjs',
  'check-mvp-live-smoke-fresh-db-precondition.mjs',
  'const expectedCompletedChecks = 70',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing fresh DB precondition fragment: ${fragment}`);
  }
}
