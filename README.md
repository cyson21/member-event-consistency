# Member Event Consistency

회원 이벤트가 복잡하게 엮인 서비스에서 DB lock, DB 제약, Redis 분산락, RabbitMQ 직렬화, `TransactionalEventListener`, Outbox를 어디까지 써야 하는지 비교하는 백엔드 포트폴리오 프로젝트입니다.

## Problem

서비스가 커질수록 로그인, 쿠폰, 포인트, 알림, 출석, 배치가 모두 `memberId`와 연결됩니다. 하지만 모든 작업을 회원 단위 전역락으로 묶으면 병목이 커지고, 반대로 아무 제어 없이 처리하면 중복 지급, 초과 발급, 잔액 음수, 이벤트 유실이 생깁니다.

이 프로젝트는 `memberId` 자체가 아니라 business invariant를 기준으로 제어 범위를 나눕니다.

```text
memberId 전체를 잠그지 않는다.
깨지면 안 되는 규칙별로 최소 제어 범위를 잡는다.
마지막 방어선은 PostgreSQL 제약과 트랜잭션에 둔다.
```

## MVP Scenarios

| Scenario | Invariant | Main Strategies |
|---|---|---|
| First Login Reward | one reward per member | naive, DB unique, Redis lock + DB unique, outbox |
| Coupon Campaign Issue | one issue per member and bounded campaign stock | DB conditional update, Redis lock, RabbitMQ worker |
| Point Spend | non-negative balance and idempotent retry | row lock, conditional update, idempotency key |

## Phase 2 Scenario Gate

The MVP stays fixed at the three scenarios above. More realistic service complexity is handled through a Phase 2 gate, one candidate at a time.

| Priority | Candidate | Why It Adds Value |
|---|---|---|
| 1 | Coupon Redemption / Usage | Adds coupon state transitions, double-use prevention, cancellation/expiration, and out-of-order event handling. |
| 2 | Batch Expiration vs User Use | Reproduces a scheduler/batch job racing with a real-time user request. |
| 3 | Daily Attendance Reward | Adds daily time-window rewards, delayed requests, retry idempotency, and date-bound uniqueness. |

Do not add external payment, SMS, push, or email providers for this project phase. Keep provider-like behavior fake or local-only so the project remains focused on concurrency, retries, state transitions, and evidence.

## Control Devices

| Device | MVP Role |
|---|---|
| PostgreSQL | final consistency source, unique constraint, conditional update, row lock, outbox, `skip locked` |
| Redis/Redisson | business-key distributed lock, wait/lease/timeout observation |
| RabbitMQ | command queue, retry queue, DLQ, prefetch and worker concurrency comparison |
| `TransactionalEventListener` | internal after-commit event comparison |
| Outbox | reliable follow-up work when event loss is unacceptable |
| Kafka | excluded from MVP; possible Phase 2 audit/read-model stream |

## Conservative Design Notes

- Redis/Redisson is not treated as the final correctness mechanism. PostgreSQL constraints and transactions remain the final guard.
- RabbitMQ does not automatically serialize by business key. MVP coupon workers must either use campaign-level concurrency `1` or document the lane strategy they use.
- `TransactionalEventListener` is not durable enough for important external follow-up work. Durable follow-up work goes through Outbox.
- Local Docker results are comparative demo evidence, not production benchmark claims.
- `RABBITMQ_DB_GUARD` returns `202 Accepted` first, so accept latency and final completion latency must be measured separately.

## Architecture Sketch

```text
Nginx or ALB simulator
  -> API server x 3~4
  -> PostgreSQL
  -> Redis
  -> RabbitMQ
  -> Worker pool
  -> React dashboard
  -> concurrency runner
```

The first implementation should stay as one Spring Boot backend with clear modules. API server instances can be scaled through Docker Compose to reproduce multi-WAS behavior without turning the first slice into a large MSA project.

## Comparison Modes

```text
NAIVE
DB_GUARD
REDIS_LOCK_DB_GUARD
RABBITMQ_DB_GUARD
```

## Expected Evidence

- duplicate reward count stays `0` under guarded strategies
- coupon issue count never exceeds campaign capacity
- point balance never goes below `0`
- repeated idempotency key does not double spend
- DB lock wait, Redis lock wait/fail, RabbitMQ queue lag/retry/DLQ are visible per run
- outbox rows move through pending, published, and failed states in controlled tests
- async strategies separate accepted count from completed count

## Current Status

Foundation code exists for the Spring Boot backend, React dashboard, local Compose stack, Flyway schema, MVP scenario harnesses, API route facade, SQL recording runner, and dependency-free verification suite.

- React dashboard is fixture/static-centered for portfolio replay and is kept as the baseline verification output.
- Local Spring/Flyway/JDBC/Redis/RabbitMQ smoke runs are fixture/scenario-centered checks and are not presented as 운영 규모 실증.
- Docker-backed Testcontainers integration is a separate live-infra path: `mvn -f backend/pom.xml -Dtest='*DbConcurrencyIT' test` and `MvpLiveInfrastructureIT` validate API behavior with PostgreSQL, Redis, and RabbitMQ containers when Docker is available. The command still scopes Docker via `TESTCONTAINERS_DOCKERCONFIG_SOURCE=autoIgnoringUserProperties` etc. (`TESTCONTAINERS_RYUK_DISABLED`, `DOCKER_HOST`, `api.version`) for reproducible local execution.
- Dependency-free SQL-recording is kept as deterministic flow/fixture evidence. It does not substitute for live DB inference.

`npm test` remains the broad local verification entrypoint. `mvn -f backend/pom.xml test` now also runs the 46 dependency-free Java harnesses through `DependencyFreeHarnessSuiteTest`, plus the direct JUnit tests. The Docker-backed DB concurrency tests run explicitly with `mvn -f backend/pom.xml -Dtest='*DbConcurrencyIT' test` and skip when Docker is unavailable. On the local Colima setup used for review closeout, the Testcontainers command passed by scoping Docker settings to the command: `TESTCONTAINERS_DOCKERCONFIG_SOURCE=autoIgnoringUserProperties TESTCONTAINERS_RYUK_DISABLED=true DOCKER_HOST=unix:///Users/chanyang.son/.colima/default/docker.sock env 'api.version=1.44' mvn -f backend/pom.xml -Dtest='*IT' test`.

`.github/workflows/review-remediation.yml` promotes the Docker-backed `*IT` tests into CI with an Ubuntu runner and a Docker runtime check before the Testcontainers command. The `MvpLiveInfrastructureIT` route check starts PostgreSQL, Redis, and RabbitMQ containers and only accepts RabbitMQ route evidence after actuator health is `UP`. The same workflow also runs Maven regression, dashboard typecheck, and the dependency-free regression without relying on sibling local repos.

For the live Spring RabbitMQ path, queue lag and RabbitMQ latency metrics are derived from queue-event lag snapshots recorded by the run tracker. The dependency-free dashboard and SQL-recording fixtures label their local comparison values as fixture baselines, not measured queue performance.

- do-not-claim: 운영 규모 처리량·복원력은 현재 문서에서 보장되지 않으며, 실서비스 성능 증거는 별도 성능 테스트가 필요합니다.

