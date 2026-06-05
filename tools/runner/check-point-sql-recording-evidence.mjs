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
const outputDir = mkdtempSync(join(tmpdir(), 'mec-point-sql-recording-evidence-'));

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

function expectEntry(summary, strategy, expectedSqlEvidence) {
  const entry = summary.routeEntries.find((candidate) => (
    candidate.scenario === 'POINT_SPEND' && candidate.strategy === strategy
  ));
  if (!entry) {
    throw new Error(`Missing POINT_SPEND route entry for ${strategy}`);
  }
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

  expectEntry(summary, 'DB_ROW_LOCK', 'select balance for update -> conditional debit -> ledger insert -> idempotency record');
  expectEntry(summary, 'CONDITIONAL_UPDATE', 'conditional debit -> ledger insert -> idempotency record');
  expectEntry(summary, 'IDEMPOTENCY_REPLAY', 'hash check -> replay lookup -> conditional debit once -> replay lookup');

  console.log(JSON.stringify({
    status: 'passed',
    checkedPointStrategies: 3,
    routeEntries: summary.routeEntries.length,
  }));
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
