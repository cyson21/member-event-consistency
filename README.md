# Member Event Consistency

Member Event Consistency는 회원 기반 서비스에서 중복 처리, 동시성, 비동기 유실, hot key 병목을 재현하고 제어장치별 선택 기준을 비교하는 Spring Boot 백엔드 포트폴리오 프로젝트입니다.

## 한눈에 보기

| 항목 | 내용 |
|---|---|
| 문제 | 로그인 보상, 쿠폰 발급, 포인트 차감처럼 `memberId`에 묶인 이벤트를 전역락 없이 안전하게 처리해야 함 |
| 핵심 역량 | Java 17, Spring Boot, PostgreSQL guard, Redis/Redisson lock, RabbitMQ, Outbox, Testcontainers |
| 백엔드 초점 | 중복 처리, 동시성 제어, hot key 완화, SQL 기반 불변조건 검증 |
| 대표 증거 | dependency-free scenario harness, SQL recording, Docker-backed `*IT`, dashboard fixture/typecheck, CI workflow |
| 실행 기준 | `npm test`가 로컬 검증 suite의 단일 진입점 |
| 범위 경계 | 로컬 fixture/Testcontainers 증거와 운영 규모 성능·복원력 주장을 분리 |

## 핵심 성과

| 성과 | 상태 | 근거 |
|---|---|---|
| MVP 시나리오 고정 | First Login Reward, Coupon Campaign Issue, Point Spend 3개로 검증 범위 고정 | project tracking, runner guards |
| 제어장치 비교 | NAIVE, DB guard, Redis lock + DB guard, RabbitMQ + DB guard 비교 | dependency-free regression, SQL recording |
| DB 최종 방어선 | 포인트 음수, 중복 지급, 초과 발급을 PostgreSQL 제약/트랜잭션으로 차단 | backend tests, Flyway/schema guards |
| Docker-backed proof | PostgreSQL/Redis/RabbitMQ Testcontainers 경로 제공 | `*IT` command-scoped Colima settings |

## 왜 만들었나

실무 서비스에서는 로그인, 보상, 쿠폰, 포인트, 알림, 배치가 대부분 회원과 연결됩니다. 모든 작업을 `lock:member:{memberId}` 하나로 묶으면 병목이 커지고, 반대로 아무 제어 없이 처리하면 중복 지급, 초과 발급, 잔액 음수, 이벤트 유실이 생깁니다.

이 프로젝트는 `memberId`가 아니라 깨지면 안 되는 업무 규칙을 기준으로 최소 제어 범위를 잡고, PostgreSQL DB 제약/락, Redis 분산락, RabbitMQ 직렬화, `TransactionalEventListener`, Outbox를 비교합니다.

## 백엔드 설계 포인트

| 포인트 | 선택 이유 | 구현/검증 |
|---|---|---|
| 불변조건 우선 | lock 기술보다 깨지면 안 되는 업무 규칙이 먼저여야 함 | scenario invariant runner |
| PostgreSQL guard | 외부 제어장치 실패 시에도 최종 데이터 정합성을 지키기 위함 | DB constraints, transaction tests |
| Redis/RabbitMQ 보조 비교 | hot key 완화와 요청 직렬화의 장단점을 비교하기 위함 | Redis/RabbitMQ strategies, dashboard |
| SQL recording | 결과만이 아니라 어떤 SQL/상태 전이가 발생했는지 남기기 위함 | SQL recording evidence |

## 아키텍처

```text
Scenario 선택
-> 전략 선택: NAIVE / DB_GUARD / REDIS_LOCK_DB_GUARD / RABBITMQ_DB_GUARD
-> concurrency runner 실행
-> Spring Boot API
-> PostgreSQL / Redis / RabbitMQ 처리
-> invariant 결과와 lock/queue/outbox 지표 저장
-> React dashboard에서 비교
```

마지막 방어선은 PostgreSQL 제약과 트랜잭션입니다. Redis/Redisson과 RabbitMQ는 진입 완충, hot key 완화, 비동기 처리 비교를 위한 보조 장치로 둡니다.

## 구현 범위

| 영역 | 구현 내용 | 증거 |
|---|---|---|
| Backend | Spring Boot backend, scenario API facade, service-backed executors | Maven tests, surface guards |
| Scenarios | First Login Reward, Coupon Campaign Issue, Point Spend | dependency-free harness, SQL recording |
| Phase 2 | Coupon Redemption / Usage, Batch Expiration vs User Use | selected Phase 2 gate, live-runner dry-runs |
| Persistence | Flyway schema, JDBC repository boundary, scenario result storage | schema guards, SQL recording evidence |
| Infra | PostgreSQL, Redis, RabbitMQ compose and Testcontainers path | `*DbConcurrencyIT`, `MvpLiveInfrastructureIT` |
| Dashboard | React fixture/static dashboard for comparison replay | web typecheck, dashboard sync checks |
| CI | review-remediation workflow runs Maven, dashboard typecheck, dependency-free regression, Docker-backed IT path | `.github/workflows/review-remediation.yml` |

## 대표 시나리오

| 시나리오 | 깨지면 안 되는 규칙 | 비교 장치 |
|---|---|---|
| First Login Reward | 회원당 최초 1회 포인트 + 쿠폰 + 알림 지급 | naive, DB unique, Redis lock + DB unique, Outbox |
| Coupon Campaign Issue | 회원당 1회 발급 + 캠페인 전체 수량 제한 | DB conditional update, Redis lock, RabbitMQ worker |
| Point Spend | 잔액 음수 방지 + 재시도 중복 차감 방지 | DB row lock, conditional update, idempotency key |
| Coupon Redemption / Usage | 발급 후 사용/취소/만료 상태 전이와 이중 사용 방지 | selected Phase 2 |
| Batch Expiration vs User Use | 만료 배치와 실시간 사용 요청의 경합 | selected Phase 2 |

MVP는 첫 3개 시나리오로 고정합니다. 현실 서비스형 복잡도는 Phase 2 gate에서 한 번에 하나씩만 추가합니다.

## 부하/동시성 검증

| 검증 대상 | 방식 | 결과 | 근거 |
|---|---|---|---|
| First Login Reward | 동시 최초 로그인 요청 비교 | 중복 보상 방지 전략별 차이 기록 | dependency-free harness |
| Coupon Campaign Issue | 캠페인 수량 제한과 회원당 1회 발급 비교 | 초과 발급/중복 발급 차단 | SQL recording |
| Point Spend | 동시 포인트 차감과 재시도 비교 | 음수 잔액 방지 | backend tests |
| Docker-backed IT | PostgreSQL/Redis/RabbitMQ Testcontainers | 실제 의존성 기반 경합 경로 검증 | `*IT` tests |

## 기술적 의사결정과 트러블슈팅

| 주제 | 문제/선택지 | 적용 | 검증/남은 리스크 |
|---|---|---|---|
| 전역 member lock 배제 | 모든 회원 이벤트를 하나의 lock key로 묶으면 hot key 병목이 커짐 | 업무 규칙별 최소 제어 범위 | scenario 비교와 dashboard |
| DB guard 우선 | Redis/RabbitMQ 장애 시 데이터 정합성까지 맡길 수 없음 | PostgreSQL 제약/트랜잭션을 최종 방어선으로 둠 | schema guard, backend tests |
| Testcontainers 환경 | Colima/user-wide 설정에 따라 Docker 탐지가 흔들림 | command-scoped env로 검증 경로 고정 | documented `*IT` command |

## 빠른 실행

```bash
npm test
```

이 명령은 로컬 verification suite를 실행합니다. Docker daemon이나 Spring server가 없으면 live smoke readiness는 “준비 안 됨”으로 보고하고, dependency-free regression과 표면 guard는 계속 검증합니다.

선택형 Testcontainers 경로:

```bash
TESTCONTAINERS_DOCKERCONFIG_SOURCE=autoIgnoringUserProperties \
TESTCONTAINERS_RYUK_DISABLED=true \
DOCKER_HOST=unix:///Users/chanyang.son/.colima/default/docker.sock \
env 'api.version=1.44' \
mvn -f backend/pom.xml -Dtest='*IT' test
```

## 검증

| 구분 | 명령/증거 | 비고 |
|---|---|---|
| 전체 로컬 suite | `npm test` | 66개 로컬 체크, StockRush boundary guard 포함 |
| Backend offline | `mvn -f backend/pom.xml test` | dependency-free harness와 직접 JUnit tests |
| Dependency-free regression | `node tools/runner/check-dependency-free-regression.mjs` | 46 main tests, SQL recording summary |
| Docker/Testcontainers | `mvn -f backend/pom.xml -Dtest='*IT' test` | PostgreSQL/Redis/RabbitMQ 컨테이너 필요 |
| Dashboard | `npm --prefix web test`, dashboard sync scripts | fixture/static 비교 화면 |
| CI | `.github/workflows/review-remediation.yml` | Docker runtime check 뒤 `*IT` 실행 |

최근 정리 기준으로 `npm test`는 66/66 통과하며, live smoke preflight는 Docker daemon과 Spring server 부재를 별도 blocker로 보고합니다.

## 운영/배포

| 항목 | 내용 | 근거 |
|---|---|---|
| 로컬 검증 | `npm test` 단일 진입점 | local verification suite |
| Docker 의존성 | PostgreSQL, Redis, RabbitMQ compose/Testcontainers | `infra/local`, `*IT` |
| CI | review-remediation workflow | `.github/workflows/review-remediation.yml` |
| Dashboard | fixture/static 비교 UI | web tests, dashboard sync |

## 담당 범위

개인 포트폴리오 프로젝트로, 아래 역량을 증명하는 데 초점을 둡니다.

| 영역 | 증명하려는 역량 | 결과 |
|---|---|---|
| 동시성 모델링 | 업무 규칙 기준으로 제어 범위 설계 | MVP 3개 시나리오 고정 |
| 데이터 정합성 | DB guard와 보조 제어장치 비교 | SQL recording과 Testcontainers proof |
| 검증 자동화 | dependency-free부터 Docker-backed까지 계층화 | `npm test`, `*IT`, CI workflow |

## 프로젝트 구조

```text
backend/              Spring Boot backend, scenario services, Flyway schema
web/                  React dashboard
infra/local/          PostgreSQL, Redis, RabbitMQ compose
tools/runner/         verification suite, smoke dry-runs, guards
docs/                 public portfolio docs and runbooks
```

## 문서 읽는 순서

| 순서 | 문서 | 목적 |
|---|---|---|
| 1 | [Portfolio One-Pager](docs/portfolio/one-pager.md) | 포트폴리오 요약 |
| 2 | [Runner README](tools/runner/README.md) | 로컬 검증 suite 구성 |
| 3 | [Local Infra README](infra/local/README.md) | PostgreSQL, Redis, RabbitMQ 로컬 구성 |

## 범위 밖

- StockRush 수정
- MVP에서 Kafka 구현
- 복잡한 MSA 분리
- 2PC 또는 분산 트랜잭션
- 실제 결제, 문자, 푸시, 이메일 provider 연동
- 모든 회원 작업을 `lock:member:{memberId}` 하나로 묶는 방식
- 실서비스 트래픽, 장애 복구, 운영 SLO를 현재 문서 범위로 주장하는 것
