import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'let activeCheck = ""',
  'try {',
  'catch (error)',
  'status: "blocked"',
  'blockedAt: activeCheck',
  'completedChecks: completedChecks.length',
  'expectedCompletedChecks',
  'commands: completedChecks',
  'process.exit(error.status || 1)',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite blocked summary is missing fragment: ${fragment}`);
  }
}
