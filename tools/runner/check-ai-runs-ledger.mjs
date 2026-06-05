import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const ledgerRoot = join(repoRoot, '.ai-runs');

const requiredFiles = [
  'goal.md',
  'agent-plan.md',
  'decisions.md',
  'changed-files.md',
  'verification.md',
];

const runIdPattern = /^\d{4}-\d{2}-\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*$/;

const missing = [];
const malformed = [];
const empty = [];
const checked = [];

for (const entry of readdirSync(ledgerRoot).sort()) {
  if (entry === 'templates') {
    continue;
  }

  const runDir = join(ledgerRoot, entry);
  if (!statSync(runDir).isDirectory()) {
    continue;
  }

  if (!runIdPattern.test(entry)) {
    malformed.push(entry);
  }

  for (const fileName of requiredFiles) {
    const filePath = join(runDir, fileName);
    if (!existsSync(filePath)) {
      missing.push(`${entry}/${fileName}`);
      continue;
    }
    if (readFileSync(filePath, 'utf8').trim().length === 0) {
      empty.push(`${entry}/${fileName}`);
    }
  }
  checked.push(entry);
}

if (malformed.length > 0 || missing.length > 0 || empty.length > 0) {
  throw new Error(JSON.stringify({
    malformed,
    missing,
    empty,
  }));
}

console.log(JSON.stringify({
  status: 'passed',
  checkedRunLedgers: checked.length,
  requiredFiles,
}));
