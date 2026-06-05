import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-root-verification-entrypoint-surface.mjs',
  'check-root-verification-entrypoint.mjs',
  'const expectedCompletedChecks = 70',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing root verification entrypoint fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-root-verification-entrypoint.mjs', import.meta.url), 'utf8');

for (const fragment of [
  '../../package.json',
  'test:readiness',
  'test:live-smoke:preflight',
  'check-mvp-live-smoke-preflight-readiness.mjs',
  'test:backend:offline',
  'mvn -f backend/pom.xml -o test',
  'test:web',
  'docs/next-agent-bootstrap.md',
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`Root verification entrypoint guard is missing fragment: ${fragment}`);
  }
}
