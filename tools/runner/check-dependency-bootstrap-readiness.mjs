import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// This check does not install dependencies. It only classifies current bootstrap readiness.
const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const backendPom = join(repoRoot, 'backend/pom.xml');
const webPackage = join(repoRoot, 'web/package.json');
const backendTestRoot = join(repoRoot, 'backend/src/test/java');
const webSourceRoot = join(repoRoot, 'web/src');

function requiredText(path, pattern, description) {
  const text = readFileSync(path, 'utf8');
  const match = text.match(pattern);
  if (!match) {
    throw new Error(`Missing ${description} in ${path}`);
  }
  return match[1] ?? match[0];
}

function javaFilesUnder(absoluteDir) {
  return readdirSync(absoluteDir)
    .flatMap((entry) => {
      const absolutePath = join(absoluteDir, entry);
      if (statSync(absolutePath).isDirectory()) {
        return javaFilesUnder(absolutePath);
      }
      return absolutePath.endsWith('.java') ? [absolutePath] : [];
    })
    .sort();
}

function testClassName(testPath) {
  const relative = testPath
    .slice(backendTestRoot.length + 1)
    .replaceAll(sep, '.');
  return relative.slice(0, -'.java'.length);
}

function sourceFilesUnder(absoluteDir) {
  return readdirSync(absoluteDir)
    .flatMap((entry) => {
      const absolutePath = join(absoluteDir, entry);
      if (statSync(absolutePath).isDirectory()) {
        return sourceFilesUnder(absolutePath);
      }
      return absolutePath.endsWith('.ts') || absolutePath.endsWith('.tsx') ? [absolutePath] : [];
    })
    .sort();
}

function relativePath(absolutePath) {
  return absolutePath.slice(repoRoot.length + 1).replaceAll(sep, '/');
}

const mavenJunitPendingTestClasses = javaFilesUnder(backendTestRoot)
  .filter((file) => {
    const text = readFileSync(file, 'utf8');
    return text.includes('org.junit.jupiter.api.Test') && !text.includes('public static void main');
  })
  .map(testClassName);

const webTypecheckPendingSources = sourceFilesUnder(webSourceRoot).map(relativePath);

const springBootVersion = requiredText(
  backendPom,
  /<artifactId>spring-boot-starter-parent<\/artifactId>\s*<version>([^<]+)<\/version>/,
  'spring-boot-starter-parent version',
);
const mavenParentPom = join(
  process.env.HOME ?? '',
  '.m2/repository/org/springframework/boot/spring-boot-starter-parent',
  springBootVersion,
  `spring-boot-starter-parent-${springBootVersion}.pom`,
);
const backendReady = existsSync(mavenParentPom);

const webPackageJson = JSON.parse(readFileSync(webPackage, 'utf8'));
const webTestScript = webPackageJson.scripts?.test ?? '';
if (!webTestScript.includes('tsc')) {
  throw new Error('web npm test must still use tsc --noEmit');
}
const tscBin = join(repoRoot, 'web/node_modules/.bin/tsc');
const webReady = existsSync(tscBin);

const blockedChecks = [];
if (!backendReady) {
  blockedChecks.push({
    command: 'mvn -f backend/pom.xml -o test',
    reason: `spring-boot-starter-parent ${springBootVersion} is absent from the local Maven cache`,
  });
}
if (!webReady) {
  blockedChecks.push({
    command: 'npm --prefix web test',
    reason: 'web/node_modules/.bin/tsc is absent',
  });
}

console.log(JSON.stringify({
  ready: backendReady && webReady,
  backendReady,
  webReady,
  springBootVersion,
  mavenParentPom,
  webTestScript,
  tscBin,
  mavenJunitPendingTestCount: mavenJunitPendingTestClasses.length,
  mavenJunitPendingTestClasses,
  webTypecheckPendingSourceCount: webTypecheckPendingSources.length,
  webTypecheckPendingSources,
  blockedChecks,
}));
