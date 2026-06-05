import { readFileSync } from 'node:fs';

const rootPackage = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'));
const scripts = rootPackage.scripts ?? {};

const expectedScripts = {
  test: 'node tools/runner/check-local-verification-suite.mjs',
  'test:readiness': 'node tools/runner/check-dependency-bootstrap-readiness.mjs',
  'test:live-smoke:preflight': 'node tools/runner/check-mvp-live-smoke-preflight-readiness.mjs',
  'test:backend:offline': 'mvn -f backend/pom.xml -o test',
  'test:web': 'npm --prefix web test',
};

for (const [name, command] of Object.entries(expectedScripts)) {
  if (scripts[name] !== command) {
    throw new Error(`Root package script ${name} must be ${command}`);
  }
}

if (rootPackage.private !== true) {
  throw new Error('Root package must be private to avoid accidental publication.');
}

if (rootPackage.dependencies || rootPackage.devDependencies) {
  throw new Error('Root verification entrypoint must not add root dependencies.');
}

const bootstrap = readFileSync(new URL('../../docs/next-agent-bootstrap.md', import.meta.url), 'utf8');

for (const fragment of [
  'npm test',
  'npm run test:readiness',
  'npm run test:live-smoke:preflight',
  'npm run test:backend:offline',
  'npm run test:web',
]) {
  if (!bootstrap.includes(fragment)) {
    throw new Error(`Bootstrap is missing root verification command: ${fragment}`);
  }
}

console.log(JSON.stringify({
  status: 'passed',
  scripts: Object.keys(expectedScripts),
}));
