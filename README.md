# Member Event Consistency

[![CI](https://github.com/cyson21/member-event-consistency/actions/workflows/review-remediation.yml/badge.svg)](https://github.com/cyson21/member-event-consistency/actions/workflows/review-remediation.yml)

동시에 들어온 최초 보상·쿠폰 발급·포인트 차감 요청이 중복 지급, 초과 발급과 음수 잔액을 만들지 않도록 PostgreSQL을 최종 보호 경계로 구현한 Java/Spring 프로젝트입니다.

개인 프로젝트로 Spring API, PostgreSQL 제약·행 잠금, Redis 잠금과 RabbitMQ 순차 처리 경로를 직접 설계·구현했습니다.

[웹 사례](https://cyson21.github.io/projects/member-event-consistency/) · [전체 포트폴리오 PDF](https://github.com/cyson21/portfolio-hub/releases/download/latest/portfolio-complete.pdf) · [최신 이력서](https://github.com/cyson21/portfolio-hub/releases/download/latest/resume.pdf)

## 문제

하나의 전역 잠금은 서로 무관한 회원·캠페인 작업까지 순서대로 처리하게 만듭니다. 반대로 애플리케이션 검사만으로는 동시 저장과 재시도를 막기 어렵습니다. 업무 식별자별로 경합을 제어하되 PostgreSQL 제약과 행 잠금을 최종 방어선으로 유지해야 합니다.

## 설계

```text
Concurrent requests -> Scenario service
                   -> PostgreSQL unique/check/FOR UPDATE/idempotency
                   -> optional Redisson lock by memberId or campaignId
Campaign command   -> RabbitMQ -> 단일 로컬 처리자 -> PostgreSQL
Reward commit      -> after-commit listener or Outbox follow-up
```

- 보상은 `memberId`, 쿠폰은 `campaignId` 단위로 잠금 범위를 나눕니다.
- 최초 보상은 고유 제약, 포인트는 행 잠금과 값 제약, 재전송은 멱등 처리 기록으로 방어합니다.
- Redis와 RabbitMQ는 DB 앞의 선택형 경합 제어이며 정합성의 최종 근거는 PostgreSQL 상태입니다.

## 실패 조건

| 조건 | 보호 규칙 |
|---|---|
| 최초 로그인 요청 동시 도착 | 회원당 보상과 후속 처리는 한 번만 기록되어야 함 |
| 인기 캠페인 동시 발급 | 회원당 1회, 캠페인 총수량 이하를 함께 만족해야 함 |
| 포인트 사용 경합·재전송 | 잔액이 음수가 되지 않고 같은 멱등 키로 중복 차감되지 않아야 함 |
| 쿠폰 사용과 만료 배치 경합 | 하나의 상태 전이만 성공해야 함 |
| 잠금 안에서 오류·트랜잭션 취소 | 잠금을 해제하고 커밋 이후 후속 처리를 실행하지 않아야 함 |

## 검증 결과

| 검증 | 확인 결과 |
|---|---|
| PostgreSQL 통합 테스트 | 최초 보상 동시 저장의 고유 제약과 포인트 행 잠금·음수 잔액 방지를 실제 DB에서 확인 |
| RabbitMQ 통합 경로 | PostgreSQL·Redis·RabbitMQ를 기동하고 단일 처리자의 캠페인 수량과 성공·실패 합계를 확인 |
| 불변식 회귀 | `InvariantCheckerTest` 7건으로 보상·쿠폰·포인트 위반 판정을 확인 |

## 대표 코드와 테스트

- 코드: [SqlRewardIssueRepository](backend/src/main/java/com/example/consistency/reward/SqlRewardIssueRepository.java) - 회원별 최초 보상 unique 제약과 포인트 상태 변경을 저장 경계에서 처리합니다.
- 테스트: [FirstLoginRewardDbConcurrencyIT](backend/src/test/java/com/example/consistency/integration/FirstLoginRewardDbConcurrencyIT.java) - 동시 최초 보상 요청이 실제 PostgreSQL에서 한 건으로 수렴하는지 검증합니다.

## 실행

CI와 같은 결과를 재현하려면 Java 17과 Maven이 필요합니다. 기본 회귀와 실제 의존성 검증을 분리합니다. 최신 JDK에서 Mockito/Byte Buddy 호환성이 달라질 수 있으므로 로컬에서도 `JAVA_HOME`을 17로 맞춥니다.

```bash
mvn -f backend/pom.xml test
```

```bash
mvn -f backend/pom.xml -Dtest='*IT' test
```

`*IT`는 Docker가 없으면 건너뛸 수 있으므로 Maven 요약에서 실행 4건, `Skipped: 0`을 확인합니다. 의존성 없는 시나리오 비교는 다음 명령으로 실행합니다.

```bash
node tools/runner/check-dependency-free-regression.mjs
```

Compose 구성과 이미지 준비 조건은 [Local Infrastructure](infra/local/README.md)를 따릅니다.

## 제한 사항

- 실제 Redis 다중 프로세스 경합, 잠금 만료와 장애 중 소유권 이전은 검증하지 않았습니다.
- Redisson 자동 연장 기능은 API 사용 방식을 단위 테스트한 것이며, 장시간 작업 중 실제 잠금 연장 성공을 측정한 결과가 아닙니다.
- RabbitMQ 처리자 수 `1`은 로컬 캠페인 경로의 선택이며 일반적인 키 순서 보장이나 자동 분할 처리를 의미하지 않습니다.
- Testcontainers 결과는 기능·정합성 검증이며 처리량, 지연시간, 고가용성, 운영 SLO 증거가 아닙니다.
- 후속 쿠폰 사용·만료 경합은 서비스·SQL 회귀 범위이며 모든 경로가 실제 의존성 통합 테스트에 포함되지는 않습니다.
- 외부 보상 제공자와 분산 트랜잭션 코디네이터는 구현하지 않았습니다.
