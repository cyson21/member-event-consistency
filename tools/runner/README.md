# Dependency-Free Scenario Runner

This runner uses the current Java API shells without Spring MVC, PostgreSQL, Redis, or RabbitMQ. It is intended for local MVP evidence before dependency bootstrap.

## Compile

```bash
rm -rf /tmp/mec-runner
mkdir -p /tmp/mec-runner
javac --release 17 -d /tmp/mec-runner \
  $(find backend/src/main/java/com/example/consistency/scenario \
          backend/src/main/java/com/example/consistency/reward \
          backend/src/main/java/com/example/consistency/coupon \
          backend/src/main/java/com/example/consistency/point \
          backend/src/main/java/com/example/consistency/persistence \
          backend/src/main/java/com/example/consistency/api \
          backend/src/main/java/com/example/consistency/runner \
          -name '*.java' | sort)
```

## Regression Check

Use the reusable local verification suite before claiming dependency-free MVP evidence:

```bash
node tools/runner/check-local-verification-suite.mjs
```


Use the narrower backend regression command when you only need Java harness coverage:

```bash
node tools/runner/check-dependency-free-regression.mjs
```

The script compiles the dependency-free Java production surface, including lock/queue observability SQL boundaries, includes the SQL test helper, executes every main-method harness test, and verifies the SQL recording MVP suite counters, 185 local SQL statements, route matrix, representative route evidence strings, and Reward/Coupon/Point SQL evidence fields.

Use the readiness classifier when you need to know whether full Maven/npm verification can run without a dependency bootstrap:

```bash
node tools/runner/check-dependency-bootstrap-readiness.mjs
```

The readiness command does not install dependencies. It reports `ready=false` with blocked checks while the Spring Boot parent POM is absent from the local Maven cache or `web/node_modules/.bin/tsc` is absent.

Flyway local seed data for later live route smoke checks is guarded by:

```bash
node backend/scripts/check-flyway-schema-surface.mjs
node tools/runner/check-mvp-live-smoke-seed-sync.mjs
node tools/runner/check-mvp-live-smoke-request-catalog.mjs
node tools/runner/run-mvp-live-smoke.mjs --dry-run
```

The seed migration covers only fixed local MVP IDs used by the runner examples and SQL recording suite. The seed sync command checks those IDs against `ScenarioCli` so route matrix and Flyway seed data cannot drift silently. It is not production data or live performance evidence.


After dependency bootstrap and a local Spring server are available, run the same catalog against a live endpoint:

```bash
node tools/runner/run-mvp-live-smoke.mjs --base-url http://localhost:8080
```

Live mode validates expected HTTP status values, JSON response `statusCode`, response scenario/strategy identity, accepted/completed counts, and invariant outcomes. The accepted/completed count expectations are derived from the fixed request catalog `requestCount`; naive strategies are expected to fail their invariant, and guarded strategies are expected to pass. It is still local smoke evidence, not production performance proof.

For First Login Reward, dry-run and live mode also carry expected reward-specific evidence fields: issued rewards, duplicate rewards, Redis lock attempts, fake after-commit notification count, and outbox event count. Coupon Campaign Issue and Point Spend carry their scenario metrics too, including RabbitMQ lane/latency evidence and Point Spend balance/replay evidence.

Related source-surface checks:

```bash
node tools/runner/check-dependency-bootstrap-readiness-surface.mjs
node tools/runner/check-local-verification-suite-surface.mjs
node tools/runner/check-local-verification-suite-order.mjs
node tools/runner/check-local-verification-suite-blocked-summary-surface.mjs
node tools/runner/check-ai-runs-ledger-surface.mjs
node tools/runner/check-ai-runs-ledger.mjs
node tools/runner/check-root-verification-entrypoint-surface.mjs
node tools/runner/check-root-verification-entrypoint.mjs
node tools/runner/check-dependency-free-regression-manifest-surface.mjs
node tools/runner/check-dependency-free-regression-manifest.mjs
node tools/runner/check-maven-junit-pending-inventory-surface.mjs
node tools/runner/check-maven-junit-pending-inventory.mjs
node tools/runner/check-testcontainers-ci-path.mjs
node tools/runner/check-phase2-gate-readiness-surface.mjs
node tools/runner/check-phase2-gate-readiness.mjs
node tools/runner/check-web-typecheck-pending-inventory-surface.mjs
node tools/runner/check-web-typecheck-pending-inventory.mjs
node tools/runner/check-mvp-live-smoke-seed-sync-surface.mjs
node tools/runner/check-mvp-live-smoke-request-catalog-surface.mjs
node tools/runner/check-mvp-live-smoke-runner-surface.mjs
node tools/runner/check-mvp-live-smoke-reward-evidence.mjs
node tools/runner/check-mvp-live-smoke-coupon-point-evidence.mjs
node tools/runner/check-mvp-live-smoke-response-identity.mjs
node tools/runner/check-mvp-live-smoke-response-status.mjs
node tools/runner/check-mvp-live-smoke-fresh-db-precondition-surface.mjs
node tools/runner/check-mvp-live-smoke-fresh-db-precondition.mjs
node tools/runner/check-mvp-live-smoke-preflight-readiness-surface.mjs
node tools/runner/check-mvp-live-smoke-preflight-readiness.mjs
node tools/runner/check-mvp-live-smoke-preflight-compose-readiness.mjs
node tools/runner/check-mvp-live-smoke-preflight-live-blockers.mjs
node tools/runner/check-mvp-live-smoke-preflight-spring-server.mjs
node tools/runner/check-mvp-live-smoke-preflight-base-url.mjs
node tools/runner/check-mvp-live-smoke-preflight-async-count.mjs
node tools/runner/check-mvp-live-smoke-preflight-async-route.mjs
node tools/runner/check-reward-sql-recording-evidence.mjs
node tools/runner/check-coupon-sql-recording-evidence.mjs
node tools/runner/check-point-sql-recording-evidence.mjs
node backend/scripts/check-flyway-schema-surface.mjs
node backend/scripts/check-spring-controller-surface.mjs
node infra/local/check-compose-surface.mjs
node tools/runner/check-mvp-scope-surface.mjs
node tools/runner/check-control-device-guardrails.mjs
node tools/runner/check-local-terminology.mjs
node tools/runner/check-stockrush-boundary.mjs
```

`node web/scripts/check-sql-recording-dashboard-sync.mjs` also compares dashboard route fixtures against SQL recording `sqlEvidence` fields so the comparison table cannot silently drift from the local SQL recording output.

## Examples

MVP smoke suite:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --suite MVP_SMOKE
```

First Login Reward:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --scenario FIRST_LOGIN_REWARD \
  --strategy DB_GUARD \
  --member-id 93001 \
  --request-count 5
```

Coupon Campaign Issue:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --scenario COUPON_CAMPAIGN_ISSUE \
  --strategy RABBITMQ_DB_GUARD \
  --campaign-id 94001 \
  --capacity 3 \
  --request-count 8
```

Point Spend:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --scenario POINT_SPEND \
  --strategy IDEMPOTENCY_REPLAY \
  --member-id 95001 \
  --initial-balance 1000 \
  --spend-amount 700 \
  --request-count 2 \
  --idempotency-key spend-95001-001
```

SQL recording smoke:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --backend SQL_RECORDING \
  --scenario COUPON_CAMPAIGN_ISSUE \
  --strategy RABBITMQ_DB_GUARD \
  --campaign-id 94001 \
  --capacity 3 \
  --request-count 1
```

SQL recording MVP suite:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --backend SQL_RECORDING \
  --suite MVP_SMOKE
```

MVP concurrency probe suite:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --probe MVP_CONCURRENCY
```

First Login Reward concurrent probe:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --probe FIRST_LOGIN_REWARD_CONCURRENT \
  --strategy REDIS_LOCK_DB_GUARD \
  --member-id 93012 \
  --request-count 8
```

Point concurrent probe:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --probe POINT_CONCURRENT \
  --strategy CONDITIONAL_UPDATE \
  --member-id 97002 \
  --initial-balance 1000 \
  --spend-amount 700 \
  --request-count 8
```

Coupon hot campaign probe:

```bash
java -cp /tmp/mec-runner com.example.consistency.runner.ScenarioCli \
  --probe COUPON_HOT_CAMPAIGN \
  --strategy RABBITMQ_DB_GUARD \
  --campaign-id 98003 \
  --capacity 3 \
  --request-count 9
```

## Boundaries

- No live database, Redis, or RabbitMQ is used.
- `--backend SQL_RECORDING` records SQL adapter statements locally; it is not live PostgreSQL proof.
- `--suite MVP_SMOKE --backend SQL_RECORDING` runs the fixed 11 MVP routes through SQL-backed router composition and reports local route entries, invariant status, accepted/completed counts, route evidence strings, Reward/Coupon/Point SQL evidence fields, and SQL statement counts.
- `--probe MVP_CONCURRENCY` runs the fixed three local MVP probes together and keeps `phase2EntryCount` at `0`.
- `--probe FIRST_LOGIN_REWARD_CONCURRENT`, `--probe POINT_CONCURRENT`, and `--probe COUPON_HOT_CAMPAIGN` run local service probes only; they are not live database, Redis, or broker concurrency proof.
- RabbitMQ evidence is the local single-lane simulation from the Coupon Campaign harness.
- `--suite MVP_SMOKE` includes only First Login Reward, Coupon Campaign Issue, and Point Spend.
- Local output is scenario evidence, not production performance proof.
