import { readFileSync } from 'node:fs';

const suite = readFileSync(new URL('./check-local-verification-suite.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'check-maven-junit-pending-inventory-surface.mjs',
  'check-maven-junit-pending-inventory.mjs',
  'const expectedCompletedChecks = 70',
]) {
  if (!suite.includes(fragment)) {
    throw new Error(`Local verification suite is missing Maven/JUnit pending inventory fragment: ${fragment}`);
  }
}

const readiness = readFileSync(new URL('./check-dependency-bootstrap-readiness.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'mavenJunitPendingTestClasses',
  'mavenJunitPendingTestCount',
  'org.junit.jupiter.api.Test',
  'public static void main',
  'backend/src/test/java',
]) {
  if (!readiness.includes(fragment)) {
    throw new Error(`Dependency readiness classifier is missing Maven/JUnit inventory fragment: ${fragment}`);
  }
}

const guard = readFileSync(new URL('./check-maven-junit-pending-inventory.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'MemberEventConsistencyApplicationTest',
  'InvariantCheckerTest',
  'ScenarioTypeTest',
  'SchemaMigrationTest',
  'mvn -f backend/pom.xml -o test',
]) {
  if (!guard.includes(fragment)) {
    throw new Error(`Maven/JUnit pending inventory guard is missing fragment: ${fragment}`);
  }
}
