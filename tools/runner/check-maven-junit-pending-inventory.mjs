import { execFileSync } from 'node:child_process';

const raw = execFileSync(
  process.execPath,
  ['tools/runner/check-dependency-bootstrap-readiness.mjs'],
  { encoding: 'utf8' },
);
const readiness = JSON.parse(raw.trim().split('\n').at(-1));

if (!Array.isArray(readiness.mavenJunitPendingTestClasses)) {
  throw new Error('Readiness summary must include mavenJunitPendingTestClasses.');
}

if (readiness.mavenJunitPendingTestCount !== readiness.mavenJunitPendingTestClasses.length) {
  throw new Error(
    `Expected mavenJunitPendingTestCount to match class list length, got ${readiness.mavenJunitPendingTestCount} and ${readiness.mavenJunitPendingTestClasses.length}`,
  );
}

for (const className of [
  'com.example.consistency.MemberEventConsistencyApplicationTest',
  'com.example.consistency.scenario.InvariantCheckerTest',
  'com.example.consistency.scenario.ScenarioTypeTest',
  'com.example.consistency.schema.SchemaMigrationTest',
]) {
  if (!readiness.mavenJunitPendingTestClasses.includes(className)) {
    throw new Error(`Pending Maven/JUnit inventory is missing ${className}`);
  }
}

const mavenBlocker = readiness.blockedChecks.find((check) => check.command === 'mvn -f backend/pom.xml -o test');
if (readiness.backendReady === false && !mavenBlocker) {
  throw new Error('Readiness summary must retain backend Maven blocked check while backend is not ready.');
}

if (readiness.backendReady === true && mavenBlocker) {
  throw new Error('Readiness summary must clear backend Maven blocked check after backend bootstrap is ready.');
}

console.log(JSON.stringify({
  status: 'passed',
  backendReady: readiness.backendReady,
  mavenJunitPendingTestCount: readiness.mavenJunitPendingTestCount,
}));
