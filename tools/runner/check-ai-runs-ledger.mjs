import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const ledgerRoot = join(repoRoot, '.ai-runs');
const indexPath = join(repoRoot, 'docs/internal/ai-runs-index.json');

const requiredFiles = [
  'goal.md',
  'agent-plan.md',
  'decisions.md',
  'changed-files.md',
  'verification.md',
];

const runIdPattern = /^\d{4}-\d{2}-\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*$/;

if (!existsSync(indexPath)) {
  throw new Error('AI runs index is missing: docs/internal/ai-runs-index.json');
}

const index = JSON.parse(readFileSync(indexPath, 'utf8'));
const runs = index.runs ?? [];
const missing = [];
const malformed = [];
const empty = [];
const duplicateIds = [];
const seen = new Set();

if (JSON.stringify(index.requiredFiles) !== JSON.stringify(requiredFiles)) {
  throw new Error('AI runs index requiredFiles must match the local guard');
}

for (const run of runs) {
  if (!runIdPattern.test(run.id)) {
    malformed.push(run.id);
  }
  if (seen.has(run.id)) {
    duplicateIds.push(run.id);
  }
  seen.add(run.id);

  for (const fileName of requiredFiles) {
    const file = run.files?.[fileName];
    if (!file?.present) {
      missing.push(`${run.id}/${fileName}`);
      continue;
    }
    if (file.bytes <= 0) {
      empty.push(`${run.id}/${fileName}`);
    }
  }
}

if (index.runCount !== runs.length) {
  throw new Error(`AI runs index runCount mismatch: ${index.runCount} != ${runs.length}`);
}

if (existsSync(ledgerRoot)) {
  const localRunDirs = readdirSync(ledgerRoot)
    .filter((entry) => entry !== 'templates')
    .filter((entry) => statSync(join(ledgerRoot, entry)).isDirectory());
  if (localRunDirs.length > 0) {
    throw new Error(`AI run ledgers must stay outside the source tree; found local dirs: ${localRunDirs.join(', ')}`);
  }
}

if (malformed.length > 0 || duplicateIds.length > 0 || missing.length > 0 || empty.length > 0) {
  throw new Error(JSON.stringify({
    malformed,
    duplicateIds,
    missing,
    empty,
  }));
}

console.log(JSON.stringify({
  status: 'passed',
  checkedRunLedgers: runs.length,
  requiredFiles,
  index: 'docs/internal/ai-runs-index.json',
}));
