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
const outputDir = mkdtempSync(join(tmpdir(), 'mec-mvp-concurrency-'));

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

function dashboardValue(summarySource, key) {
  const booleanMatch = summarySource.match(new RegExp(`${key}:\\s*(true|false)`));
  if (booleanMatch) {
    return booleanMatch[1] === 'true';
  }

  const numberMatch = summarySource.match(new RegExp(`${key}:\\s*(\\d+)`));
  if (numberMatch) {
    return Number(numberMatch[1]);
  }

  const stringMatch = summarySource.match(new RegExp(`${key}:\\s*'([^']+)'`));
  if (stringMatch) {
    return stringMatch[1];
  }

  throw new Error(`Dashboard MVP concurrency summary is missing ${key}`);
}

function concurrencySummarySection(appSource) {
  const start = appSource.indexOf('const concurrencyProbeSummary');
  if (start < 0) {
    throw new Error('Dashboard concurrencyProbeSummary fixture is missing');
  }
  const end = appSource.indexOf('};', start);
  if (end < 0) {
    throw new Error('Dashboard concurrencyProbeSummary fixture is not closed');
  }
  return appSource.slice(start, end);
}

function parseConcurrencyEntries(appSource) {
  const start = appSource.indexOf('const concurrencyProbeEntries');
  if (start < 0) {
    throw new Error('Dashboard concurrencyProbeEntries fixture is missing');
  }
  const end = appSource.indexOf('];', start);
  if (end < 0) {
    throw new Error('Dashboard concurrencyProbeEntries fixture is not closed');
  }

  const section = appSource.slice(start, end);
  const entryPattern = /\{\s*probe:\s*'([^']+)',\s*scenario:\s*'([^']+)',\s*strategy:\s*'([^']+)',\s*invariantPassed:\s*(true|false),\s*summary:\s*'([^']+)'/g;
  const entries = [];
  let match;
  while ((match = entryPattern.exec(section)) !== null) {
    entries.push({
      probe: match[1],
      scenario: match[2],
      strategy: match[3],
      invariantPassed: match[4] === 'true',
      summary: match[5],
    });
  }
  return entries;
}

function entryKey(entry) {
  return `${entry.probe}|${entry.scenario}`;
}

function assertEntriesMatchDashboard(cliSummary, appEntries) {
  if (!Array.isArray(cliSummary.entries)) {
    throw new Error('MVP concurrency suite did not emit entries');
  }
  if (cliSummary.entries.length !== appEntries.length) {
    throw new Error(
      `MVP concurrency entry count drift: app=${appEntries.length}, cli=${cliSummary.entries.length}`,
    );
  }

  const cliByKey = new Map(cliSummary.entries.map((entry) => [entryKey(entry), entry]));
  for (const appEntry of appEntries) {
    const cliEntry = cliByKey.get(entryKey(appEntry));
    if (!cliEntry) {
      throw new Error(`MVP concurrency entry missing for ${entryKey(appEntry)}`);
    }
    for (const key of ['strategy', 'invariantPassed', 'summary']) {
      if (cliEntry[key] !== appEntry[key]) {
        throw new Error(
          `MVP concurrency drift for ${entryKey(appEntry)} ${key}: app=${appEntry[key]}, cli=${cliEntry[key]}`,
        );
      }
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
      '--probe',
      'MVP_CONCURRENCY',
    ],
    { cwd: repoRoot, encoding: 'utf8' },
  );
  const cliSummary = JSON.parse(raw);
  const appSource = readFileSync(join(repoRoot, 'web/src/App.tsx'), 'utf8');
  const appSummary = concurrencySummarySection(appSource);
  const appEntries = parseConcurrencyEntries(appSource);

  for (const key of [
    'probe',
    'localOnly',
    'scenarioCount',
    'entryCount',
    'passingInvariantCount',
    'phase2EntryCount',
  ]) {
    const appValue = dashboardValue(appSummary, key);
    if (appValue !== cliSummary[key]) {
      throw new Error(
        `Dashboard MVP concurrency drift for ${key}: app=${appValue}, cli=${cliSummary[key]}`,
      );
    }
  }

  if (cliSummary.statusCode !== 200) {
    throw new Error(`MVP concurrency suite returned statusCode=${cliSummary.statusCode}`);
  }
  assertEntriesMatchDashboard(cliSummary, appEntries);
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
