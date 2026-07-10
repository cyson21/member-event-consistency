# Member Event Consistency

Member Event Consistency는 회원 기반 서비스의 중복 처리, 동시성, 비동기 유실, 특정 회원에 요청이 몰리는 문제를 재현하고 해결 방법을 비교하는 Spring Boot 백엔드 프로젝트입니다.

## 프로젝트 요약

| 항목 | 내용 |
|---|---|
| 해결하려는 문제 | 로그인 보상, 쿠폰 발급, 포인트 차감처럼 `memberId`에 묶인 이벤트를 전역락 없이 안전하게 처리해야 함 |
| 주요 기술 | Java 17, Spring Boot, PostgreSQL 제약, Redis/Redisson lock, RabbitMQ, Outbox, Testcontainers |
| 주요 기능 | 중복 처리 방지, 동시성 제어, 집중 요청 완화, SQL 기반 업무 규칙 확인 |
| 확인 방법 | 외부 서비스 없이 실행하는 시나리오 테스트, SQL 기록, Docker 기반 `*IT`, 대시보드 테스트, CI |
| 빠른 실행 | `npm test`로 전체 로컬 테스트 실행 |
| 제한 사항 | 로컬 예제 데이터와 Testcontainers 결과를 운영 규모 성능·복구 결과로 확대해 설명하지 않음 |

## 주요 내용

| 주제 | 관련 문서와 코드 | 설명 |
|---|---|---|
| 업무 규칙별 제어 | [대표 시나리오](#대표-시나리오), [InvariantCheckerTest](backend/src/test/java/com/example/consistency/scenario/InvariantCheckerTest.java) | lock 방식보다 먼저 지켜야 할 업무 규칙을 정의 |
| 기본 테스트 | `npm test`, [로컬 테스트 도구](tools/runner/check-dependency-free-regression.mjs) | Docker 없이 시나리오와 SQL 기록, 공개 API 구성을 확인 |
| 실제 의존성 테스트 | [Local Infra README](infra/local/README.md), `*IT` 테스트 | PostgreSQL, Redis, RabbitMQ를 사용하는 Testcontainers 실행 방법 |

## 주요 코드와 테스트

| 구분 | 코드 또는 기능 | 테스트 또는 실행 방법 |
|---|---|---|
| 최초 로그인 보상 중복 방지 | First Login Reward scenario | [FirstLoginRewardConcurrentProbeTest](backend/src/test/java/com/example/consistency/reward/FirstLoginRewardConcurrentProbeTest.java), [FirstLoginRewardDbConcurrencyIT](backend/src/test/java/com/example/consistency/integration/FirstLoginRewardDbConcurrencyIT.java) |
| 캠페인 수량과 회원당 1회 제한 | Coupon Campaign Issue scenario | [CouponCampaignHotCampaignProbeTest](backend/src/test/java/com/example/consistency/coupon/CouponCampaignHotCampaignProbeTest.java), [CouponCampaignSqlWiringTest](backend/src/test/java/com/example/consistency/coupon/CouponCampaignSqlWiringTest.java) |
| 음수 잔액과 재시도 중복 차감 방지 | Point Spend scenario | [PointSpendConcurrentProbeTest](backend/src/test/java/com/example/consistency/point/PointSpendConcurrentProbeTest.java), [PointSpendDbConcurrencyIT](backend/src/test/java/com/example/consistency/integration/PointSpendDbConcurrencyIT.java) |

## 실행 환경

| 구분 | 준비 사항 | 확인할 내용 |
|---|---|---|
| 기본 로컬 | Node와 Maven | 외부 서비스 없는 시나리오, SQL 기록, 대시보드 예제 데이터 테스트 |
| Docker/Testcontainers | Docker, PostgreSQL, Redis, RabbitMQ | 실제 의존성을 사용한 동시성 테스트 |
| 대시보드 | web 의존성 설치 | 정적 예제 데이터를 사용하는 비교 화면이며 운영 대시보드는 아님 |

## 구현 결과

| 구현 내용 | 결과 | 확인 방법 |
|---|---|---|
| 핵심 시나리오 | First Login Reward, Coupon Campaign Issue, Point Spend 3개로 테스트 범위 고정 | 프로젝트 진행 문서, 실행 도구 검사 |
| 처리 방식 비교 | NAIVE, DB 제약, Redis lock + DB 제약, RabbitMQ + DB 제약 비교 | 로컬 회귀 테스트, SQL 기록 |
| DB 최종 방어선 | 포인트 음수, 중복 지급, 초과 발급을 PostgreSQL 제약과 트랜잭션으로 차단 | 백엔드 테스트, Flyway schema 검사 |
| Docker 기반 테스트 | PostgreSQL, Redis, RabbitMQ Testcontainers 실행 경로 제공 | `*IT`, 실행 명령에 한정한 Colima 설정 |

## 프로젝트 배경

실무 서비스에서는 로그인, 보상, 쿠폰, 포인트, 알림, 배치가 대부분 회원과 연결됩니다. 모든 작업을 `lock:member:{memberId}` 하나로 묶으면 병목이 커지고, 반대로 아무 제어 없이 처리하면 중복 지급, 초과 발급, 잔액 음수, 이벤트 유실이 생깁니다.

이 프로젝트는 `memberId`가 아니라 깨지면 안 되는 업무 규칙을 기준으로 최소 제어 범위를 잡고, PostgreSQL DB 제약/락, Redis 분산락, RabbitMQ 직렬화, `TransactionalEventListener`, Outbox를 비교합니다.

## 주요 설계

| 설계 | 선택 이유 | 구현과 테스트 |
|---|---|---|
| 업무 규칙 우선 | lock 기술보다 깨지면 안 되는 업무 규칙을 먼저 정해야 함 | 시나리오 규칙 검사 도구 |
| PostgreSQL 제약 | 외부 제어 장치가 실패해도 최종 데이터 정합성을 지키기 위함 | DB 제약, 트랜잭션 테스트 |
| Redis/RabbitMQ 비교 | 집중 요청 완화와 요청 직렬화의 장단점을 비교하기 위함 | Redis·RabbitMQ 처리 방식, 대시보드 |
| SQL 기록 | 결과뿐 아니라 실행된 SQL과 상태 변화를 남기기 위함 | SQL 기록 결과 |

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

| 영역 | 구현 내용 | 확인 방법 |
|---|---|---|
| Backend | Spring Boot 백엔드, 시나리오 API, service 실행기 | Maven 테스트, 공개 API 검사 |
| Scenarios | First Login Reward, Coupon Campaign Issue, Point Spend | 외부 서비스 없는 시나리오 테스트, SQL 기록 |
| Phase 2 | Coupon Redemption / Usage, Batch Expiration vs User Use | Phase 2 선택 기준, 실행 전 점검 |
| Persistence | Flyway schema, JDBC 저장소, 시나리오 결과 저장 | schema 검사, SQL 기록 |
| Infra | PostgreSQL, Redis, RabbitMQ Compose와 Testcontainers | `*DbConcurrencyIT`, `MvpLiveInfrastructureIT` |
| Dashboard | React 정적 대시보드 | web typecheck, 대시보드 동기화 검사 |
| CI | Maven, 대시보드 typecheck, 로컬 회귀, Docker 기반 IT 실행 | `.github/workflows/review-remediation.yml` |

## 대표 시나리오

| 시나리오 | 깨지면 안 되는 규칙 | 비교 장치 |
|---|---|---|
| First Login Reward | 회원당 최초 1회 포인트 + 쿠폰 + 알림 지급 | naive, DB unique, Redis lock + DB unique, Outbox |
| Coupon Campaign Issue | 회원당 1회 발급 + 캠페인 전체 수량 제한 | DB conditional update, Redis lock, RabbitMQ worker |
| Point Spend | 잔액 음수 방지 + 재시도 중복 차감 방지 | DB row lock, conditional update, idempotency key |
| Coupon Redemption / Usage | 발급 후 사용·취소·만료 상태 변화와 이중 사용 방지 | Phase 2에 추가 |
| Batch Expiration vs User Use | 만료 배치와 실시간 사용 요청의 경합 | Phase 2에 추가 |

MVP는 첫 3개 시나리오로 고정합니다. 복잡한 시나리오는 Phase 2에서 한 번에 하나씩 추가합니다.

## 부하 및 동시성 테스트

| 테스트 항목 | 방법 | 결과 | 확인 방법 |
|---|---|---|---|
| First Login Reward | 동시 최초 로그인 요청 비교 | 중복 보상 방지 방식별 차이 기록 | 로컬 시나리오 테스트 |
| Coupon Campaign Issue | 캠페인 수량 제한과 회원당 1회 발급 비교 | 초과 발급/중복 발급 차단 | SQL recording |
| Point Spend | 동시 포인트 차감과 재시도 비교 | 음수 잔액 방지 | backend tests |
| Docker 기반 IT | PostgreSQL·Redis·RabbitMQ Testcontainers | 실제 의존성을 사용한 경합 경로 확인 | `*IT` 테스트 |

## 기술 선택과 문제 해결

| 주제 | 고민한 점 | 적용 내용 | 확인 방법과 남은 과제 |
|---|---|---|---|
| 회원 전역 lock 배제 | 모든 회원 이벤트를 하나의 lock key로 묶으면 특정 회원 요청이 병목이 됨 | 업무 규칙별로 필요한 범위만 제어 | 시나리오 비교와 대시보드 |
| DB 제약 우선 | Redis나 RabbitMQ 장애 시 데이터 정합성까지 맡길 수 없음 | PostgreSQL 제약과 트랜잭션을 최종 방어선으로 사용 | schema 검사, 백엔드 테스트 |
| Testcontainers 환경 | Colima의 사용자 설정에 따라 Docker 탐지가 달라짐 | 실행 명령에 필요한 환경 변수를 함께 지정 | 문서화된 `*IT` 명령 |

## 빠른 실행

```bash
npm test
```

이 명령은 전체 로컬 테스트를 실행합니다. Docker나 Spring server가 없으면 실시간 스모크 테스트는 “준비 안 됨”으로 표시하고, 외부 서비스가 필요 없는 회귀 테스트와 공개 API 검사는 계속 실행합니다.

선택형 Testcontainers 경로:

```bash
TESTCONTAINERS_DOCKERCONFIG_SOURCE=autoIgnoringUserProperties \
TESTCONTAINERS_RYUK_DISABLED=true \
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" \
env 'api.version=1.44' \
mvn -f backend/pom.xml -Dtest='*IT' test
```

## 테스트

| 구분 | 명령 또는 결과 | 설명 |
|---|---|---|
| 전체 로컬 테스트 | `npm test` | 66개 로컬 검사, StockRush 변경 경로 검사 포함 |
| 백엔드 로컬 | `mvn -f backend/pom.xml test` | 외부 서비스 없는 시나리오와 JUnit 테스트 |
| 외부 서비스 없는 회귀 테스트 | `node tools/runner/check-dependency-free-regression.mjs` | 핵심 테스트 46개와 SQL 기록 요약 |
| Docker/Testcontainers | `mvn -f backend/pom.xml -Dtest='*IT' test` | PostgreSQL/Redis/RabbitMQ 컨테이너 필요 |
| 대시보드 | `npm --prefix web test`, 대시보드 동기화 script | 정적 예제 데이터 비교 화면 |
| CI | `.github/workflows/review-remediation.yml` | Docker 실행 환경 확인 후 `*IT` 실행 |

`npm test`는 66개 로컬 검사를 실행합니다. Docker나 Spring server가 없으면 실시간 스모크 테스트를 실행하지 못한 이유를 별도로 표시합니다.

## 운영/배포

| 항목 | 내용 | 확인 방법 |
|---|---|---|
| 로컬 테스트 | `npm test` 단일 진입점 | 전체 로컬 테스트 |
| Docker 의존성 | PostgreSQL, Redis, RabbitMQ compose/Testcontainers | `infra/local`, `*IT` |
| CI | review-remediation workflow | `.github/workflows/review-remediation.yml` |
| 대시보드 | 정적 예제 데이터 비교 UI | web 테스트, 대시보드 동기화 검사 |

## 담당 범위

개인 프로젝트이며, 직접 구현하고 테스트한 범위는 다음과 같습니다.

| 분야 | 구현 내용 | 확인 방법 |
|---|---|---|
| 동시성 모델링 | 업무 규칙 기준으로 제어 범위 설계 | MVP 3개 시나리오 고정 |
| 데이터 정합성 | DB 제약과 보조 제어 장치 비교 | SQL 기록과 Testcontainers 테스트 |
| 테스트 자동화 | 로컬 테스트부터 Docker 기반 테스트까지 단계별 구성 | `npm test`, `*IT`, CI workflow |

## 프로젝트 구조

```text
backend/              Spring Boot 백엔드, 시나리오 서비스, Flyway schema
web/                  React 대시보드
infra/local/          PostgreSQL, Redis, RabbitMQ Compose
tools/runner/         테스트 실행 도구, 스모크 테스트 사전 점검, 규칙 검사
docs/                 공개 문서와 실행 안내
```

## 참고 문서

| 순서 | 문서 | 내용 |
|---|---|---|
| 1 | [Portfolio One-Pager](docs/portfolio/one-pager.md) | 포트폴리오 요약 |
| 2 | [Runner README](tools/runner/README.md) | 로컬 테스트 구성 |
| 3 | [Local Infra README](infra/local/README.md) | PostgreSQL, Redis, RabbitMQ 로컬 구성 |

## 제한 사항

- StockRush 수정
- MVP에서 Kafka 구현
- 복잡한 MSA 분리
- 2PC 또는 분산 트랜잭션
- 실제 결제, 문자, 푸시, 이메일 provider 연동
- 모든 회원 작업을 `lock:member:{memberId}` 하나로 묶는 방식
- 실서비스 트래픽, 장애 복구, 운영 SLO를 현재 문서 범위로 주장하는 것
