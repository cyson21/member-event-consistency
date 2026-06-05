import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const forbidden = '\uACC4\uC57D';
const ignoredDirs = new Set([
  '.git',
  'node_modules',
  'target',
  'dist',
  'build',
  '.gradle',
  '.idea',
]);

function filesUnder(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    const relative = path.slice(repoRoot.length + 1);
    if (ignoredDirs.has(entry) || relative.includes(`${join('.ai-runs', 'templates')}`)) {
      return [];
    }
    const stats = statSync(path);
    if (stats.isDirectory()) {
      return filesUnder(path);
    }
    if (!stats.isFile()) {
      return [];
    }
    return [path];
  });
}

const matches = [];
for (const file of filesUnder(repoRoot)) {
  let text;
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    continue;
  }
  if (text.includes(forbidden)) {
    matches.push(file.slice(repoRoot.length + 1));
  }
}

if (matches.length > 0) {
  throw new Error(`Forbidden local terminology found in: ${matches.join(', ')}`);
}
