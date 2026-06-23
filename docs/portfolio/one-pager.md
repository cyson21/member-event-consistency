# Member Event Consistency One-Pager

## Summary

Member Event Consistency is a backend-focused portfolio project for comparing practical consistency controls in member-centered event flows. It keeps the MVP to three scenarios and uses one Spring Boot backend, PostgreSQL, Redis, RabbitMQ, Outbox, and a React dashboard as the intended live stack.

The current implementation has dependency-free scenario harnesses, report models, API shells, SQL/JDBC boundaries, dashboard fixtures, and verification records. Live Spring/Flyway/JDBC/RabbitMQ wiring is validated through Testcontainers-based integration paths.
Current state is more explicit: React dashboard evidence is fixture/static centered for reproducible replay, while Testcontainers live IT paths are now used for container-backed API validation.

## Problem

Many product events share a `memberId`: login rewards, coupon issue, point spend, notification, attendance, progress, and batch operations. A broad member-level lock would reduce concurrency too much, while no control device can lead to duplicate rewards, coupon over-issue, negative point balance, or lost follow-up work.

This project controls each business invariant at the smallest useful scope and keeps PostgreSQL as the final consistency source.

## MVP Scenarios

| Scenario | Invariant | Broken Path | Guarded Evidence |
|---|---|---|---|
| First Login Reward | one first-login reward per member | `NAIVE` can issue duplicates | DB unique guard and Redis-entry guard plus DB unique keep duplicates at zero |
| Coupon Campaign Issue | campaign issue count never exceeds capacity | `NAIVE` over-issues beyond capacity | DB guard, Redis campaign lock plus DB guard, and RabbitMQ single-lane plus DB guard keep over-issue at zero |
| Point Spend | balance never goes negative and retry does not double spend | `NAIVE` can create negative balance | DB row-lock, conditional update, and idempotency replay keep balance non-negative |

## Control Devices Compared

| Device | Role |
|---|---|
| PostgreSQL unique/check/conditional update/row lock | final invariant defense |
| Redis/Redisson lock | pre-DB contention relief and lock-attempt observation |
| RabbitMQ | hot campaign buffering with explicit single-lane handling in MVP |
| TransactionalEventListener | same-service after-commit comparison for non-durable follow-up work |
| Outbox | durable follow-up work boundary |

Kafka is excluded from the MVP because this project focuses on member-event control devices rather than audit/read-model streaming.

## Evidence Built So Far

- Dependency-free scenario runners for First Login Reward, Coupon Campaign Issue, and Point Spend.
- API-shell handlers for all three MVP scenarios.
- Common `ScenarioRunReport` and `ScenarioMetric` model.
- In-memory and SQL/JDBC report persistence boundaries.
- Flyway V1 schema draft for member, point, reward, coupon, idempotency, outbox, lock, queue, and scenario result tables.
- React dashboard fixtures aligned with current backend evidence.
- `MvpLiveInfrastructureIT`와 `*DbConcurrencyIT` 계열 Testcontainers 통합 테스트로 PostgreSQL/Redis/RabbitMQ 기반 live stack 경로를 분리 검증한다.

## Verification Snapshot

| Area | Current Evidence |
|---|---|
| Backend self-contained Java checks | pass with JDK-only compile/run commands |
| Docker Compose syntax | `docker compose -f infra/local/docker-compose.yml config -q` passes |
| Maven tests | local `mvn -f backend/pom.xml test` + dependency-free harness + Testcontainers IT path |
| Frontend build/tests | fixture-backed dashboard check + local build prerequisites |
| Testcontainers IT | dependency가 있어야 실행 가능한 PostgreSQL/Redis/RabbitMQ API/infra 경로 분리 증빙 |
| StockRush boundary | unchanged |

## What This Demonstrates

- Picking control scope by invariant, not by broad `memberId` locking.
- Separating Redis contention relief from DB-backed correctness.
- Separating `202 Accepted` latency from final completion latency for RabbitMQ.
- Keeping provider integrations fake/local-only while focusing on concurrency and retry evidence.
- Recording implementation decisions and verification output in durable run ledgers.

## Next Work

1. Keep fixture/static dashboard wording and Testcontainers live IT wording synchronized in README, one-pager, and interview guide.
2. Convert the remaining high-value `main()` harnesses to direct JUnit assertions where that improves reviewer readability.
3. Add a live dashboard/API wiring slice only if a future portfolio review needs interactive backend-backed UI evidence.
4. Keep Phase 2 parked until MVP evidence (fixture/static + live IT 분리)이 모두 정리되고 재현되도록 유지.

- do-not-claim: 실서비스 성능, SLI/SLO 달성, 대규모 동시성 내성은 현재 문서 범위 밖이다.
