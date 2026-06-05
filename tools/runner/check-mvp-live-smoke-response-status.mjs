import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');

const output = execFileSync(process.execPath, [
  'tools/runner/run-mvp-live-smoke.mjs',
  '--dry-run',
], {
  cwd: repoRoot,
  encoding: 'utf8',
});
const summary = JSON.parse(output);

if (summary.expectedStatuses.length !== 11) {
  throw new Error(`Expected 11 MVP routes, got ${summary.expectedStatuses.length}`);
}

for (const entry of summary.expectedStatuses) {
  if (entry.expectedResponseStatusCode !== entry.expectedStatus) {
    throw new Error(`${entry.title} expectedResponseStatusCode must match expectedStatus`);
  }
}

const runner = readFileSync(resolve(repoRoot, 'tools/runner/run-mvp-live-smoke.mjs'), 'utf8');

for (const fragment of [
  'parsed.statusCode',
  'expectedResponseStatusCode',
  'expected response statusCode',
]) {
  if (!runner.includes(fragment)) {
    throw new Error(`MVP live smoke runner is missing response status fragment: ${fragment}`);
  }
}
