import { execFileSync } from 'node:child_process';
import {
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const outputDir = mkdtempSync(join(tmpdir(), 'mec-dependency-free-regression-'));

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

const testRoots = [
  'backend/src/test/java/com/example/consistency/api',
  'backend/src/test/java/com/example/consistency/coupon',
  'backend/src/test/java/com/example/consistency/lock',
  'backend/src/test/java/com/example/consistency/point',
  'backend/src/test/java/com/example/consistency/persistence',
  'backend/src/test/java/com/example/consistency/reward',
  'backend/src/test/java/com/example/consistency/runner',
  'backend/src/test/java/com/example/consistency/scenario',
];

const helpers = [
  'backend/src/test/java/com/example/consistency/persistence/RecordingSqlExecutor.java',
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

function packageName(testClassName) {
  const prefix = 'com.example.consistency.';
  if (!testClassName.startsWith(prefix)) {
    throw new Error(`Unexpected test class package: ${testClassName}`);
  }
  return testClassName.slice(prefix.length).split('.')[0];
}

function assertCounter(summary, key, expected) {
  if (summary[key] !== expected) {
    throw new Error(`Expected ${key}=${expected}, got ${summary[key]}`);
  }
}

function assertRouteEvidence(summary, scenario, strategy, expectedEvidence) {
  if (!Array.isArray(summary.routeEntries)) {
    throw new Error('Expected SQL recording summary routeEntries');
  }
  const routeEntry = summary.routeEntries.find((entry) => (
    entry.scenario === scenario && entry.strategy === strategy
  ));
  if (!routeEntry) {
    throw new Error(`Expected SQL recording route entry for ${scenario}|${strategy}`);
  }
  if (routeEntry.evidence !== expectedEvidence) {
    throw new Error(
      `Expected evidence=${expectedEvidence} for ${scenario}|${strategy}, got ${routeEntry.evidence}`,
    );
  }
}

function assertRouteSqlEvidence(summary, scenario, strategy, expectedSqlEvidence) {
  if (!Array.isArray(summary.routeEntries)) {
    throw new Error('Expected SQL recording summary routeEntries');
  }
  const routeEntry = summary.routeEntries.find((entry) => (
    entry.scenario === scenario && entry.strategy === strategy
  ));
  if (!routeEntry) {
    throw new Error(`Expected SQL recording route entry for ${scenario}|${strategy}`);
  }
  if (routeEntry.sqlEvidence !== expectedSqlEvidence) {
    throw new Error(
      `Expected sqlEvidence=${expectedSqlEvidence} for ${scenario}|${strategy}, got ${routeEntry.sqlEvidence}`,
    );
  }
}

try {
  const productionFiles = productionRoots.flatMap(filesUnder);
  const testFiles = testRoots
    .flatMap(filesUnder)
    .filter((file) => readFileSync(file, 'utf8').includes('public static void main'));
  const executedMainTestClasses = testFiles.map(className);
  const executedMainTestPackageCounts = executedMainTestClasses.reduce((counts, testClassName) => {
    const key = packageName(testClassName);
    counts[key] = (counts[key] ?? 0) + 1;
    return counts;
  }, {});
  const helperFiles = helpers.map((file) => join(repoRoot, file));

  execFileSync(
    'javac',
    ['--release', '17', '-d', outputDir, ...productionFiles, ...helperFiles, ...testFiles],
    { cwd: repoRoot, stdio: 'inherit' },
  );

  for (const testClassName of executedMainTestClasses) {
    execFileSync('java', ['-cp', outputDir, testClassName], {
      cwd: repoRoot,
      stdio: 'inherit',
    });
  }

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

  assertCounter(summary, 'statusCode', 200);
  assertCounter(summary, 'scenarioCount', 3);
  assertCounter(summary, 'routeCount', 11);
  assertCounter(summary, 'brokenNaiveCount', 3);
  assertCounter(summary, 'passingGuardedCount', 8);
  assertCounter(summary, 'asyncAcceptedCount', 1);
  assertCounter(summary, 'phase2EntryCount', 0);
  assertCounter(summary, 'sqlStatementCount', 185);
  assertCounter(summary.routeEntries, 'length', 11);
  assertRouteEvidence(summary, 'FIRST_LOGIN_REWARD', 'DB_GUARD', 'issued=1, duplicate=0');
  assertRouteEvidence(summary, 'COUPON_CAMPAIGN_ISSUE', 'RABBITMQ_DB_GUARD', 'issued=3, overIssue=0, lane=1');
  assertRouteEvidence(summary, 'POINT_SPEND', 'IDEMPOTENCY_REPLAY', 'balance=300, negative=0, replay=1, hashMismatch=0');
  assertRouteSqlEvidence(
    summary,
    'FIRST_LOGIN_REWARD',
    'NAIVE',
    'duplicate-prone attempt insert -> fake follow-up outbox rows',
  );
  assertRouteSqlEvidence(
    summary,
    'FIRST_LOGIN_REWARD',
    'DB_GUARD',
    'unique reward issue insert -> fake follow-up outbox rows',
  );
  assertRouteSqlEvidence(
    summary,
    'FIRST_LOGIN_REWARD',
    'REDIS_LOCK_DB_GUARD',
    'unique reward issue insert -> fake follow-up outbox rows',
  );
  assertRouteSqlEvidence(
    summary,
    'COUPON_CAMPAIGN_ISSUE',
    'DB_GUARD',
    'campaign row lock -> coupon issue insert -> issued count update',
  );
  assertRouteSqlEvidence(
    summary,
    'COUPON_CAMPAIGN_ISSUE',
    'REDIS_LOCK_DB_GUARD',
    'campaign row lock -> coupon issue insert -> issued count update',
  );
  assertRouteSqlEvidence(
    summary,
    'COUPON_CAMPAIGN_ISSUE',
    'RABBITMQ_DB_GUARD',
    'campaign row lock -> coupon issue insert -> issued count update',
  );
  assertRouteSqlEvidence(
    summary,
    'POINT_SPEND',
    'DB_ROW_LOCK',
    'select balance for update -> conditional debit -> ledger insert -> idempotency record',
  );
  assertRouteSqlEvidence(
    summary,
    'POINT_SPEND',
    'CONDITIONAL_UPDATE',
    'conditional debit -> ledger insert -> idempotency record',
  );
  assertRouteSqlEvidence(
    summary,
    'POINT_SPEND',
    'IDEMPOTENCY_REPLAY',
    'hash check -> replay lookup -> conditional debit once -> replay lookup',
  );

  console.log(JSON.stringify({
    compiledProductionFiles: productionFiles.length,
    executedMainTests: testFiles.length,
    executedMainTestClasses,
    executedMainTestPackageCounts,
    sqlRecordingSummary: summary,
  }));
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
