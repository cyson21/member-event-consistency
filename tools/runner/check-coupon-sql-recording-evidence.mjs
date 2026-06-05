import { execFileSync } from 'node:child_process';
import {
  mkdtempSync,
  readdirSync,
  rmSync,
  statSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const outputDir = mkdtempSync(join(tmpdir(), 'mec-coupon-sql-recording-evidence-'));

const productionRoots = [
  'backend/src/main/java/com/example/consistency/scenario',
  'backend/src/main/java/com/example/consistency/lock',
  'backend/src/main/java/com/example/consistency/reward',
  'backend/src/main/java/com/example/consistency/coupon',
  'backend/src/main/java/com/example/consistency/point',
  'backend/src/main/java/com/example/consistency/persistence',
  'backend/src/main/java/com/example/consistency/api',
  'backend/src/main/java/com/example/consistency/runner',
];

function filesUnder(relativeDir) {
  const absoluteDir = join(repoRoot, relativeDir);
  return readdirSync(absoluteDir)
    .flatMap((entry) => {
      const absolutePath = join(absoluteDir, entry);
      if (statSync(absolutePath).isDirectory()) {
        return filesUnder(resolve(absolutePath).slice(repoRoot.length + 1));
      }
      return absolutePath.endsWith('.java') ? [absolutePath] : [];
    })
    .sort();
}

function className(testPath) {
  const relative = testPath
    .slice(join(repoRoot, 'backend/src/test/java').length + 1)
    .replaceAll(sep, '.');
  return relative.slice(0, -'.java'.length);
}

function routeEntry(summary, strategy) {
  const entry = summary.routeEntries.find((candidate) => (
    candidate.scenario === 'COUPON_CAMPAIGN_ISSUE' && candidate.strategy === strategy
  ));
  if (!entry) {
    throw new Error(`Missing COUPON_CAMPAIGN_ISSUE route entry for ${strategy}`);
  }
  return entry;
}

function expectSqlEvidence(summary, strategy, expectedSqlEvidence) {
  const entry = routeEntry(summary, strategy);
  if (entry.sqlEvidence !== expectedSqlEvidence) {
    throw new Error(
      `Expected ${strategy} sqlEvidence=[${expectedSqlEvidence}], got [${entry.sqlEvidence}]`,
    );
  }
}

try {
  const productionFiles = productionRoots.flatMap(filesUnder);
  const testFile = join(
    repoRoot,
    'backend/src/test/java/com/example/consistency/runner/ScenarioCliTest.java',
  );
  const helperFile = join(
    repoRoot,
    'backend/src/test/java/com/example/consistency/persistence/RecordingSqlExecutor.java',
  );

  execFileSync(
    'javac',
    ['--release', '17', '-d', outputDir, ...productionFiles, helperFile, testFile],
    { cwd: repoRoot, stdio: 'inherit' },
  );

  execFileSync('java', ['-cp', outputDir, className(testFile)], {
    cwd: repoRoot,
    stdio: 'inherit',
  });

  const raw = execFileSync(
    'java',
    [
      '-cp',
      outputDir,
      'com.example.consistency.runner.ScenarioCli',
      '--backend',
      'SQL_RECORDING',
      '--suite',
      'MVP_SMOKE',
    ],
    { cwd: repoRoot, encoding: 'utf8' },
  );
  const summary = JSON.parse(raw);
  const expected = 'campaign row lock -> coupon issue insert -> issued count update';

  expectSqlEvidence(summary, 'DB_GUARD', expected);
  expectSqlEvidence(summary, 'REDIS_LOCK_DB_GUARD', expected);
  expectSqlEvidence(summary, 'RABBITMQ_DB_GUARD', expected);

  const rabbit = routeEntry(summary, 'RABBITMQ_DB_GUARD');
  if (rabbit.statusCode !== 202 || rabbit.evidence !== 'issued=3, overIssue=0, lane=1') {
    throw new Error(`RabbitMQ route must keep accepted status and lane evidence: ${JSON.stringify(rabbit)}`);
  }

  console.log(JSON.stringify({
    status: 'passed',
    checkedCouponStrategies: 3,
    routeEntries: summary.routeEntries.length,
  }));
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
