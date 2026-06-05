import { readFileSync } from 'node:fs';

const script = readFileSync(new URL('./check-dependency-free-regression.mjs', import.meta.url), 'utf8');

for (const fragment of [
  'RecordingSqlExecutor.java',
  'backend/src/main/java/com/example/consistency/lock',
  'backend/src/test/java/com/example/consistency/lock',
  'public static void main',
  'ScenarioCli',
  '--backend',
  'SQL_RECORDING',
  '--suite',
  'MVP_SMOKE',
  'sqlStatementCount',
  'assertRouteEvidence',
  'assertRouteSqlEvidence',
  'routeEntries',
  'evidence',
  'sqlEvidence',
  'duplicate-prone attempt insert -> fake follow-up outbox rows',
  'unique reward issue insert -> fake follow-up outbox rows',
  'campaign row lock -> coupon issue insert -> issued count update',
  'select balance for update -> conditional debit -> ledger insert -> idempotency record',
  'hashMismatch=0',
  'sqlStatementCount\', 185',
  'mkdtempSync',
]) {
  if (!script.includes(fragment)) {
    throw new Error(`Dependency-free regression script is missing fragment: ${fragment}`);
  }
}
