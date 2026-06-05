import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const workflowPath = resolve(repoRoot, '.github/workflows/review-remediation.yml');
const workflow = readFileSync(workflowPath, 'utf8');

const requiredSnippets = [
  'testcontainers-integration:',
  'runs-on: ubuntu-latest',
  'actions/checkout@v4',
  'actions/setup-java@v4',
  "java-version: '17'",
  'docker info',
  "mvn -f backend/pom.xml -Dtest='*IT' test",
  'standard-regression:',
  'actions/setup-node@v4',
  "node-version: '22'",
  'npm ci --prefix web',
  'mvn -f backend/pom.xml test',
  'npm --prefix web test',
  'node tools/runner/check-dependency-free-regression.mjs',
];

for (const snippet of requiredSnippets) {
  if (!workflow.includes(snippet)) {
    throw new Error(`Review remediation CI workflow is missing: ${snippet}`);
  }
}

console.log(JSON.stringify({
  status: 'passed',
  workflow: '.github/workflows/review-remediation.yml',
  dockerBackedTestCommand: "mvn -f backend/pom.xml -Dtest='*IT' test",
}));
