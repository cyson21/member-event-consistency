# Member Event Consistency

[![CI](https://github.com/cyson21/member-event-consistency/actions/workflows/review-remediation.yml/badge.svg)](https://github.com/cyson21/member-event-consistency/actions/workflows/review-remediation.yml)

첫 로그인 보상, 포인트 차감, 한정 수량 쿠폰에 동시 요청을 보내 데이터가 어떻게 남는지 확인하는 Java/Spring 프로젝트입니다. 첫 로그인 보상의 중복 지급은 PostgreSQL 유니크 제약으로 막고, 포인트 차감에는 행 잠금과 잔액 `CHECK` 제약을 적용했습니다.

- **PostgreSQL** — 같은 회원에게 보상 요청이 동시에 들어와도 지급 기록은 한 건만 남습니다. 한 건만 감당할 수 있는 잔액에 차감 요청 두 건을 보내 한 건만 처리되는지도 확인합니다. [보상 구현](backend/src/main/java/com/example/consistency/reward/SqlRewardIssueRepository.java) · [보상 테스트](backend/src/test/java/com/example/consistency/integration/FirstLoginRewardDbConcurrencyIT.java) · [포인트 테스트](backend/src/test/java/com/example/consistency/integration/PointSpendDbConcurrencyIT.java)
- **Redis** — 실제 Redis 컨테이너와 별도 Redisson 클라이언트를 사용해, 작업이 watchdog 만료 시간을 넘겨도 같은 키를 다른 클라이언트가 가져갈 수 없는지 확인합니다. [보상 잠금 테스트](backend/src/test/java/com/example/consistency/integration/RedisRewardLockIntegrationIT.java) · [캠페인 잠금 테스트](backend/src/test/java/com/example/consistency/integration/RedisCouponCampaignLockIntegrationIT.java)
- **RabbitMQ** — 단일 워커를 띄운 테스트에서 수량 3장에 요청 8건을 보내 3건 발급, 5건 거절, 초과 발급 0건을 확인합니다. [캠페인 통합 테스트](backend/src/test/java/com/example/consistency/integration/MvpLiveInfrastructureIT.java)

RabbitMQ 결과는 단일 워커의 발급 시나리오에 해당합니다.

## 풀려던 문제

락 하나로 전부 줄 세우면, 서로 상관없는 회원이나 캠페인 작업까지 같이 기다리게 됩니다. 반대로 애플리케이션 코드에서 `if`로 확인만 하면 동시에 들어온 저장이나 재시도를 막지 못합니다. 그래서 잠금은 회원, 캠페인 단위로 좁히고, 최종 확인은 PostgreSQL 제약과 행 잠금에 맡겼습니다.

## 구조

```text
Concurrent requests -> Scenario service
                   -> PostgreSQL unique/check/FOR UPDATE/idempotency
                   -> optional Redisson lock by memberId or campaignId
Campaign command   -> RabbitMQ -> 단일 로컬 처리자 -> PostgreSQL
Reward commit      -> after-commit listener or Outbox follow-up
```

- 보상은 `memberId`, 쿠폰은 `campaignId` 단위로 잠급니다.
- 첫 로그인 보상은 유니크 제약, 포인트는 행 잠금과 CHECK 제약, 재전송은 멱등 키 기록으로 막습니다.
- Redis와 RabbitMQ는 DB 부담을 덜어 주는 선택 사항입니다. 데이터가 맞는지는 결국 PostgreSQL이 판단합니다.
- RabbitMQ 경로는 Spring 인스턴스 하나에서 listener concurrency=1로만 확인했습니다. 여러 인스턴스에 걸친 순서 보장은 아닙니다.

## 실패 상황별 결과

| 상황 | 결과 |
|---|---|
| 첫 로그인 요청이 동시에 도착 | 보상과 후속 처리가 회원당 한 번만 기록됩니다 |
| 인기 캠페인에 동시 발급 요청 | 회원당 1장, 캠페인 전체 수량 이하로만 나갑니다 |
| 포인트 사용이 겹치거나 재전송됨 | 잔액이 마이너스가 되지 않고, 같은 멱등 키로 두 번 빠지지 않습니다 |
| 쿠폰 사용과 만료 배치가 겹침 | 둘 중 하나만 성공합니다 |
| 잠금 안에서 오류가 나 트랜잭션이 취소됨 | 잠금이 풀리고, 커밋 뒤 후속 처리는 실행되지 않습니다 |

## 확인한 방법

| 검증 | 확인한 내용 |
|---|---|
| PostgreSQL 통합 테스트 | 첫 로그인 보상의 유니크 제약, 포인트 행 잠금과 마이너스 방지를 실제 DB에서 확인 |
| RabbitMQ 통합 경로 | PostgreSQL, Redis, RabbitMQ를 띄우고 캠페인 발급 수량과 성공, 실패 합계를 확인 |
| 규칙 위반 검사 | `InvariantCheckerTest` 7건으로 보상, 쿠폰, 포인트 규칙 위반을 잡는지 확인 |

## 실행

CI와 같은 결과를 보려면 Java 17과 Maven이 필요합니다. 최신 JDK에서는 Mockito, Byte Buddy 호환성이 달라질 수 있어서 로컬에서도 `JAVA_HOME`을 17로 맞춥니다.

```bash
mvn -f backend/pom.xml test
```

실제 의존성을 띄우는 테스트는 따로 돌립니다.

```bash
mvn -f backend/pom.xml -Dtest='*IT' test
```

`*IT`는 Docker가 없으면 건너뛰기 때문에, Maven 요약에서 테스트가 실행됐고 `Skipped: 0`인지 확인합니다. 외부 의존성 없이 시나리오를 비교하려면 다음을 실행합니다.

```bash
node tools/runner/check-dependency-free-regression.mjs
```

Compose 구성과 이미지 준비는 [Local Infrastructure](infra/local/README.md)에 있습니다.

## 해 보지 않은 것

- Redis 잠금 통합 테스트는 한 JVM 안의 별도 클라이언트로 실행합니다. 여러 애플리케이션 프로세스의 경쟁이나 Redis 장애 전환은 검증 범위 밖입니다.
- RabbitMQ 처리자를 1개로 둔 건 로컬 캠페인 경로에서의 선택입니다. 키별 순서 보장이나 자동 분할 처리는 아닙니다.
- Testcontainers 테스트는 결과가 맞는지 보는 용도입니다. 처리량, 지연 시간, 고가용성은 재지 않았습니다.
- 쿠폰 사용과 만료가 겹치는 경우는 서비스, SQL 테스트로만 확인했고, 모든 경로를 실제 의존성으로 돌려 보지는 않았습니다.
- 외부 보상 시스템 연동과 분산 트랜잭션은 구현하지 않았습니다.

[웹 포트폴리오에서 프로젝트 상세 보기](https://cyson21.github.io/projects/member-event-consistency/)
