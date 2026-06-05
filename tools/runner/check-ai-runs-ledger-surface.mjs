import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-ai-runs-ledger.mjs',
  'const expectedCompletedChecks = 66',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing ledger guard fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-ai-runs-ledger.mjs', import.meta.url), 'utf8');

for (const fragment of [
  '.ai-runs',
  'ai-runs-index.json',
  'goal.md',
  'agent-plan.md',
  'decisions.md',
  'changed-files.md',
  'verification.md',
  'runCount',
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`AI runs ledger guard is missing fragment: ${fragment}`);
  }
}
