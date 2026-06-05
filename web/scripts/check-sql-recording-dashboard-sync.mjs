import { execFileSync } from 'node:child_process';
import {
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const outputDir = mkdtempSync(join(tmpdir(), 'mec-sql-recording-'));

const sourceRoots = [
  'backend/src/main/java/com/example/consistency/scenario',
  'backend/src/main/java/com/example/consistency/reward',
  'backend/src/main/java/com/example/consistency/coupon',
  'backend/src/main/java/com/example/consistency/point',
  'backend/src/main/java/com/example/consistency/persistence',
  'backend/src/main/java/com/example/consistency/api',
  'backend/src/main/java/com/example/consistency/runner',
];

function javaSources(dir) {
  const absoluteDir = join(repoRoot, dir);
  return readdirSync(absoluteDir)
    .flatMap((entry) => {
      const path = join(absoluteDir, entry);
      if (statSync(path).isDirectory()) {
        return javaSources(resolve(path).slice(repoRoot.length + 1));
      }
      return path.endsWith('.java') ? [path] : [];
    })
    .sort();
}

function dashboardValue(appSource, key) {
  const stringMatch = appSource.match(new RegExp(`${key}:\\s*'([^']+)'`));
  if (stringMatch) {
    return stringMatch[1];
  }

  const booleanMatch = appSource.match(new RegExp(`${key}:\\s*(true|false)`));
  if (booleanMatch) {
    return booleanMatch[1] === 'true';
  }

  const numberMatch = appSource.match(new RegExp(`${key}:\\s*(\\d+)`));
  if (numberMatch) {
    return Number(numberMatch[1]);
  }

  throw new Error(`Dashboard SQL recording summary is missing ${key}`);
}

function parseComparisonEntries(appSource) {
  const start = appSource.indexOf('const comparisonEntries');
  if (start < 0) {
    throw new Error('Dashboard comparisonEntries fixture is missing');
  }
  const end = appSource.indexOf('];', start);
  if (end < 0) {
    throw new Error('Dashboard comparisonEntries fixture is not closed');
  }

  const section = appSource.slice(start, end);
  const entryPattern = /\{\s*scenario:\s*'([^']+)',\s*strategy:\s*'([^']+)',\s*statusCode:\s*(\d+),\s*invariantPassed:\s*(true|false),\s*acceptedCount:\s*(\d+),\s*completedCount:\s*(\d+),\s*evidence:\s*'([^']+)',\s*sqlEvidence:\s*'([^']*)'/g;
  const entries = [];
  let match;
  while ((match = entryPattern.exec(section)) !== null) {
    entries.push({
      scenario: match[1],
      strategy: match[2],
      statusCode: Number(match[3]),
      invariantPassed: match[4] === 'true',
      acceptedCount: Number(match[5]),
      completedCount: Number(match[6]),
      evidence: match[7],
      sqlEvidence: match[8],
    });
  }
  return entries;
}

function routeEntryKey(entry) {
  return `${entry.scenario}|${entry.strategy}`;
}

function assertRouteEntriesMatchDashboard(cliSummary, comparisonEntries) {
  const routeEntries = cliSummary.routeEntries;
  if (!Array.isArray(routeEntries)) {
    throw new Error('SQL recording suite did not emit routeEntries');
  }
  if (routeEntries.length !== comparisonEntries.length) {
    throw new Error(
      `SQL recording route entry count drift: app=${comparisonEntries.length}, cli=${routeEntries.length}`,
    );
  }

  const cliByKey = new Map(routeEntries.map((entry) => [routeEntryKey(entry), entry]));
  for (const appEntry of comparisonEntries) {
    const cliEntry = cliByKey.get(routeEntryKey(appEntry));
    if (!cliEntry) {
      throw new Error(`SQL recording route entry missing for ${routeEntryKey(appEntry)}`);
    }

    for (const key of ['statusCode', 'invariantPassed', 'acceptedCount', 'completedCount', 'evidence', 'sqlEvidence']) {
      if (cliEntry[key] !== appEntry[key]) {
        throw new Error(
          `SQL recording route entry drift for ${routeEntryKey(appEntry)} ${key}: app=${appEntry[key]}, cli=${cliEntry[key]}`,
        );
      }
    }

    if (!String(cliEntry.route).startsWith('/api/scenarios/')) {
      throw new Error(`SQL recording route entry has unexpected route=${cliEntry.route}`);
    }
  }
}

try {
  const sources = sourceRoots.flatMap(javaSources);
  execFileSync('javac', ['--release', '17', '-d', outputDir, ...sources], {
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
  const cliSummary = JSON.parse(raw);
  const appSource = readFileSync(join(repoRoot, 'web/src/App.tsx'), 'utf8');
  const comparisonEntries = parseComparisonEntries(appSource);

  for (const key of [
    'backend',
    'localOnly',
    'routeCount',
    'brokenNaiveCount',
    'passingGuardedCount',
    'asyncAcceptedCount',
    'phase2EntryCount',
    'sqlStatementCount',
  ]) {
    const appValue = dashboardValue(appSource, key);
    if (appValue !== cliSummary[key]) {
      throw new Error(
        `Dashboard SQL recording drift for ${key}: app=${appValue}, cli=${cliSummary[key]}`,
      );
    }
  }

  if (cliSummary.statusCode !== 200) {
    throw new Error(`SQL recording suite returned statusCode=${cliSummary.statusCode}`);
  }
  if (cliSummary.suite !== 'MVP_SMOKE') {
    throw new Error(`SQL recording suite returned suite=${cliSummary.suite}`);
  }
  if (cliSummary.scenarioCount !== 3) {
    throw new Error(`SQL recording suite returned scenarioCount=${cliSummary.scenarioCount}`);
  }
  assertRouteEntriesMatchDashboard(cliSummary, comparisonEntries);
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
