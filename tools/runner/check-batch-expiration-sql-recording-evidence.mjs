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
const outputDir = mkdtempSync(join(tmpdir(), 'mec-batch-expiration-sql-recording-evidence-'));

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

function runBatchExpiration(winner) {
  const raw = execFileSync(
    'java',
    [
      '-cp',
      outputDir,
      'com.example.consistency.runner.ScenarioCli',
      '--backend',
      'SQL_RECORDING',
      '--scenario',
      'BATCH_EXPIRATION',
      '--strategy',
      'DB_GUARD',
      '--coupon-issue-id',
      winner === 'USER_USE' ? '13031' : '13032',
      '--winner',
      winner,
    ],
    { cwd: repoRoot, encoding: 'utf8' },
  );
  return JSON.parse(raw);
}

function assertField(object, key, expected) {
  if (object[key] !== expected) {
    throw new Error(`Expected ${key}=${expected}, got ${object[key]}`);
  }
}

try {
  const productionFiles = productionRoots.flatMap(filesUnder);
  const helperFile = join(
    repoRoot,
    'backend/src/test/java/com/example/consistency/persistence/RecordingSqlExecutor.java',
  );
  const wiringTestFile = join(
    repoRoot,
    'backend/src/test/java/com/example/consistency/coupon/BatchExpirationSqlWiringTest.java',
  );
  const cliTestFile = join(
    repoRoot,
    'backend/src/test/java/com/example/consistency/runner/ScenarioCliTest.java',
  );

  execFileSync(
    'javac',
    ['--release', '17', '-d', outputDir, ...productionFiles, helperFile, wiringTestFile, cliTestFile],
    { cwd: repoRoot, stdio: 'inherit' },
  );

  execFileSync('java', ['-cp', outputDir, className(wiringTestFile)], {
    cwd: repoRoot,
    stdio: 'inherit',
  });

  const userUse = runBatchExpiration('USER_USE');
  assertField(userUse, 'statusCode', 200);
  assertField(userUse, 'scenario', 'BATCH_EXPIRATION');
  assertField(userUse, 'strategy', 'DB_GUARD');
  assertField(userUse, 'winner', 'USER_USE');
  assertField(userUse, 'invariantPassed', true);
  assertField(userUse, 'couponUsedCount', 1);
  assertField(userUse, 'couponExpiredCount', 0);
  assertField(userUse, 'rejectedCount', 1);
  assertField(userUse, 'sqlEvidence', 'conditional coupon issue terminal transition');
  assertField(userUse, 'backend', 'SQL_RECORDING');
  assertField(userUse, 'localOnly', true);

  const batchExpiration = runBatchExpiration('BATCH_EXPIRATION');
  assertField(batchExpiration, 'statusCode', 200);
  assertField(batchExpiration, 'winner', 'BATCH_EXPIRATION');
  assertField(batchExpiration, 'couponUsedCount', 0);
  assertField(batchExpiration, 'couponExpiredCount', 1);
  assertField(batchExpiration, 'rejectedCount', 1);
  assertField(batchExpiration, 'sqlEvidence', 'conditional coupon issue terminal transition');

  const suiteRaw = execFileSync(
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
  const suite = JSON.parse(suiteRaw);
  assertField(suite, 'scenarioCount', 3);
  assertField(suite, 'routeCount', 11);
  assertField(suite, 'phase2EntryCount', 0);

  console.log(JSON.stringify({
    status: 'passed',
    checkedScenario: 'BATCH_EXPIRATION',
    checkedWinners: 2,
    mvpRouteCount: suite.routeCount,
    phase2EntryCount: suite.phase2EntryCount,
  }));
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
