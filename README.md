# Member Event Consistency

최초 로그인 보상, 쿠폰 발급, 포인트 차감에 요청이 겹칠 때 업무 불변식을 어디에서 지킬지 비교하는 Java/Spring 프로젝트입니다. [웹 사례](https://cyson21.github.io/projects/member-event-consistency/)

## 문제

하나의 전역 잠금은 서로 무관한 회원·캠페인 작업까지 직렬화합니다. 반대로 애플리케이션 검사만으로는 동시 insert와 재시도를 막기 어렵습니다. 업무 식별자별 제어를 적용하되 PostgreSQL 제약과 행 잠금을 최종 방어선으로 유지해야 합니다.

## 설계

```text
Concurrent requests -> Scenario service
                   -> PostgreSQL unique/check/FOR UPDATE/idempotency
                   -> optional Redisson lock by memberId or campaignId
Campaign command   -> RabbitMQ -> single local worker -> PostgreSQL
Reward commit      -> after-commit listener or Outbox follow-up
```

- 보상은 `memberId`, 쿠폰은 `campaignId` 단위로 잠금 범위를 나눕니다.
- 최초 보상은 unique 제약, 포인트는 행 잠금과 check 제약, 재전송은 idempotency record로 방어합니다.
- Redis와 RabbitMQ는 DB 앞의 선택형 경합 제어이며 정합성의 최종 근거는 PostgreSQL 상태입니다.

## 실패 조건

| 조건 | 보호 규칙 |
|---|---|
| 최초 로그인 요청 동시 도착 | 회원당 보상과 후속 처리는 한 번만 기록되어야 함 |
| 인기 캠페인 동시 발급 | 회원당 1회, 캠페인 총수량 이하를 함께 만족해야 함 |
| 포인트 사용 경합·재전송 | 잔액이 음수가 되지 않고 같은 key로 중복 차감되지 않아야 함 |
| 쿠폰 사용과 만료 배치 경합 | 하나의 상태 전이만 성공해야 함 |
| lock callback 예외·트랜잭션 rollback | lock을 해제하고 after-commit 후속 처리를 실행하지 않아야 함 |

## 검증 결과

| 검증 | 확인 결과 |
|---|---|
| PostgreSQL Testcontainers | 최초 보상 동시 insert의 unique 제약과 포인트 행 잠금·음수 잔액 check를 실제 DB에서 확인 |
| RabbitMQ 통합 경로 | PostgreSQL·Redis·RabbitMQ를 기동하고 단일 worker의 캠페인 수량 정합성과 DLQ terminal 합산을 확인 |
| 불변식 회귀 | `InvariantCheckerTest` 7건으로 보상·쿠폰·포인트 위반 판정을 확인 |

Redis lock은 명시적 lease가 없는 Redisson `tryLock(waitTime, unit)` 호출로 watchdog 갱신 경로를 사용합니다. 단위 테스트는 이 호출 계약, 현재 스레드 소유권 확인, 예외 시 unlock과 interrupt 복원을 검증합니다. 실제 Redis에서 여러 프로세스가 경합할 때의 watchdog 갱신 주기·장애 복구·성능은 현재 검증 범위에 포함하지 않습니다.

### 재현 가능한 검증 리포트

`Review Remediation` workflow는 pull request, `main` push, 수동 실행에서 기본 회귀와 Testcontainers 통합 검증을 별도 job으로 실행합니다. 같은 ref의 이전 실행은 취소하고, 취소되지 않은 실행은 각 job의 Maven Surefire/Failsafe XML 원본과 집계 JSON을 14일간 artifact로 보관합니다. Java는 17, 대시보드 Node는 22로 고정합니다.

JSON은 schema version, 프로젝트와 commit, UTC 생성 시각, 검증 범위, 전체 합계, 이름·소스 순으로 정렬된 leaf suite 결과를 포함합니다. Generator 자체 회귀 테스트도 CI에서 실행하며 XML 입력 누락, 파싱 실패, 선언 합계와 testcase 불일치, 중첩 suite 이중 집계, 0건 리포트를 실패로 처리합니다. 테스트 실패 시 Surefire/Failsafe 진단 파일을 먼저 출력한 뒤 생성 가능한 evidence를 보관합니다.

현재 로컬 Maven 결과는 다음 명령으로 같은 형식의 JSON으로 변환할 수 있습니다.

```bash
python3 scripts/portfolio-evidence/generate_report.py \
  --input 'backend/target/*-reports/TEST-*.xml' \
  --output backend/target/portfolio-evidence/local.json \
  --project member-event-consistency \
  --scope local-current
```

동일 입력에서 byte 단위로 같은 JSON이 필요하면 `SOURCE_DATE_EPOCH=<epoch>` 또는 `--generated-at-utc YYYY-MM-DDTHH:MM:SSZ`를 지정합니다. CI는 대상 commit 시각을 `SOURCE_DATE_EPOCH`로 사용합니다. 출력 경로는 저장소 내부만 허용합니다.

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

- 실제 Redis 다중 프로세스 경합, lock lease 만료, 장애 중 소유권 이전은 검증하지 않았습니다.
- watchdog 사용은 Redisson API 호출 계약을 단위 테스트한 것이며, 장시간 작업 중 실제 lease 연장 성공을 측정한 결과가 아닙니다.
- RabbitMQ worker 동시성 `1`은 로컬 캠페인 경로의 선택이며 일반적인 key 순서 보장이나 자동 partitioning을 의미하지 않습니다.
- Testcontainers 결과는 기능·정합성 검증이며 처리량, 지연시간, 고가용성, 운영 SLO 증거가 아닙니다.
- 후속 쿠폰 사용·만료 경합은 서비스·SQL 회귀 범위이며 모든 경로가 실제 의존성 통합 테스트에 포함되지는 않습니다.
- 외부 보상 제공자와 분산 트랜잭션 코디네이터는 구현하지 않았습니다.
