import { readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const stockrushRoot = resolve(repoRoot, '../stockrush');
const sinceText = process.env.MEC_STOCKRUSH_GUARD_SINCE || '2026-06-19 00:00:00';
const since = new Date(sinceText);
const allowedTouchedFiles = new Set([
  'AGENTS.md',
  'docs/reviews/2026-06-01-review-fixes.md',
  'infra/demo/docker-compose.yml',
]);

if (Number.isNaN(since.getTime())) {
  throw new Error(`Invalid MEC_STOCKRUSH_GUARD_SINCE value: ${sinceText}`);
}

const ignoredDirs = new Set([
  '.git',
  'node_modules',
  'target',
  'dist',
  'build',
  '.gradle',
  '.idea',
]);

function filesUnder(dir, depth) {
  if (depth < 0) {
    return [];
  }
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (ignoredDirs.has(entry)) {
      return [];
    }
    const stats = statSync(path);
    if (stats.isDirectory()) {
      return filesUnder(path, depth - 1);
    }
    return stats.isFile() ? [{ path, mtime: stats.mtime }] : [];
  });
}

const touched = filesUnder(stockrushRoot, 2)
  .filter((file) => file.mtime > since)
  .map((file) => file.path.slice(stockrushRoot.length + 1))
  .sort();

const unexpectedTouched = touched.filter((file) => !allowedTouchedFiles.has(file));

if (unexpectedTouched.length > 0) {
  throw new Error(`StockRush boundary touched files since ${sinceText}: ${unexpectedTouched.join(', ')}`);
}
