import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const sourceRoot = resolve(repoRoot, process.env.MEC_AI_RUNS_SOURCE || '.ai-runs');
const outputPath = resolve(repoRoot, 'docs/internal/ai-runs-index.json');

const requiredFiles = [
  'goal.md',
  'agent-plan.md',
  'decisions.md',
  'changed-files.md',
  'verification.md',
];

const runIdPattern = /^\d{4}-\d{2}-\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*$/;

if (!existsSync(sourceRoot)) {
  throw new Error(`AI run source directory is missing: ${sourceRoot}`);
}

const runs = [];
const invalid = [];

for (const entry of readdirSync(sourceRoot).sort()) {
  if (entry === 'templates') {
    continue;
  }

  const runDir = join(sourceRoot, entry);
  if (!statSync(runDir).isDirectory()) {
    continue;
  }
  if (!runIdPattern.test(entry)) {
    invalid.push(entry);
    continue;
  }

  const files = {};
  for (const fileName of requiredFiles) {
    const filePath = join(runDir, fileName);
    if (!existsSync(filePath)) {
      files[fileName] = { present: false, bytes: 0 };
      continue;
    }
    files[fileName] = {
      present: true,
      bytes: Buffer.byteLength(readFileSync(filePath, 'utf8'), 'utf8'),
    };
  }

  runs.push({
    id: entry,
    trackingRef: `.ai-runs/${entry}/`,
    files,
  });
}

if (invalid.length > 0) {
  throw new Error(`Invalid AI run ids: ${invalid.join(', ')}`);
}

const index = {
  version: 1,
  generatedBy: 'tools/runner/build-ai-runs-index.mjs',
  source: relative(repoRoot, sourceRoot),
  archivedFrom: '.ai-runs/',
  requiredFiles,
  runCount: runs.length,
  runs,
};

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(index, null, 2)}\n`);

console.log(JSON.stringify({
  status: 'generated',
  output: relative(repoRoot, outputPath),
  source: index.source,
  runCount: index.runCount,
}));
