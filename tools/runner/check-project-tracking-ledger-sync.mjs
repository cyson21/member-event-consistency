import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const ledgerRoot = join(repoRoot, '.ai-runs');
const tracking = readFileSync(join(repoRoot, 'docs/project-tracking.md'), 'utf8');

const missing = [];
const checked = [];

for (const entry of readdirSync(ledgerRoot).sort()) {
  if (entry === 'templates') {
    continue;
  }
  const runDir = join(ledgerRoot, entry);
  if (!statSync(runDir).isDirectory()) {
    continue;
  }
  checked.push(entry);
  if (!tracking.includes(`.ai-runs/${entry}/`)) {
    missing.push(entry);
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
