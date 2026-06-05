import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const index = JSON.parse(readFileSync(join(repoRoot, 'docs/internal/ai-runs-index.json'), 'utf8'));
const tracking = readFileSync(join(repoRoot, 'docs/project-tracking.md'), 'utf8');

if (index.archivedFrom !== '.ai-runs/') {
  throw new Error('AI runs index must record archivedFrom: .ai-runs/');
}

const missing = [];
const checked = [];

for (const run of index.runs ?? []) {
  checked.push(run.id);
  if (!tracking.includes(run.trackingRef)) {
    missing.push(run.id);
  }
}

if (missing.length > 0) {
  throw new Error(JSON.stringify({
    missing,
    checkedRunLedgers: checked.length,
  }));
}

console.log(JSON.stringify({
  status: 'passed',
  checkedRunLedgers: checked.length,
}));
