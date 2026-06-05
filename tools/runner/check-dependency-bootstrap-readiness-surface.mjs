import { existsSync, readFileSync } from 'node:fs';

const readinessPath = new URL('./check-dependency-bootstrap-readiness.mjs', import.meta.url);
if (!existsSync(readinessPath)) {
  throw new Error('Dependency bootstrap readiness script is missing');
}

const readiness = readFileSync(readinessPath, 'utf8');
for (const fragment of [
  'spring-boot-starter-parent',
  'springBootVersion',
  'node_modules',
  'tsc',
  'backendReady',
  'webReady',
  'ready',
  'blockedChecks',
  'mvn -f backend/pom.xml -o test',
  'npm --prefix web test',
  'does not install dependencies',
]) {
  if (!readiness.includes(fragment)) {
    throw new Error(`Dependency bootstrap readiness script is missing fragment: ${fragment}`);
  }
}
