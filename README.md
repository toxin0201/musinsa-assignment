# 무료 포인트 시스템 API

적립 · 적립취소 · 사용 · 사용취소를 지원하는 무료 포인트 API다. 회원별 총잔액만 두지 않고 적립 건 단위로 잔액을 관리해,
어떤 적립이 어느 주문에서 얼마나 쓰였는지를 1원 단위로 복원할 수 있게 만들었다. 인증 없이 단일 Spring Boot 애플리케이션과
H2 인메모리 DB로 실행되며, 관리자 기능은 경로로만 분리했다.

## 요약

**빌드 · 실행 · 테스트**

```bash
./gradlew bootRun          # http://localhost:8080 (기본 포트)
./gradlew test             # 단위 + 슬라이스 + 통합 테스트 233건
./gradlew clean build      # 전체 빌드 (jar: build/libs/point-0.0.1-SNAPSHOT.jar)
```

**API 한눈에 보기** (경로 접두 `/api/v1`)

| 기능 | 메서드 · 경로 |
|---|---|
| 적립 | `POST /members/{memberId}/points/earn` |
| 관리자 수기 적립 | `POST /admin/members/{memberId}/points/earn` |
| 적립취소 | `POST /members/{memberId}/points/earn/{pointKey}/cancel` |
| 사용 | `POST /members/{memberId}/points/use` |
| 사용취소 | `POST /members/{memberId}/points/use/{pointKey}/cancel` |
| 잔액 조회 | `GET /members/{memberId}/points/balance` |
| 적립별 사용 내역 조회 | `GET /members/{memberId}/points/earn/{pointKey}/usages` |
| 개인 보유 한도 변경 | `PUT /admin/members/{memberId}/points/limit` |

**핵심 설계 결정 5줄**

1. 잔액을 별도 컬럼에 두지 않고, `point_earning.remaining_amount` 의 합으로 계산한다(`status=ACTIVE` 이고 미만료).
2. 적립·적립취소·사용·사용취소를 `point_transaction` 한 테이블에 쌓고, `point_transaction_detail` 로 "어느 적립이 얼마나 움직였는지"를 1원 단위로 남긴다.
3. 사용은 수기 지급(`MANUAL`)을 먼저, 그다음 만료가 가까운 순으로 소진하며, 부족하면 한 푼도 쓰지 않고 전체 거절한다.
4. 회원 계정 행을 `PESSIMISTIC_WRITE` 로 잠가 같은 회원의 요청을 직렬화하고, 3초를 넘기면 409로 응답한다.
5. 1회 적립 한도 · 보유 한도 · 만료일수는 하드코딩이 아니라 설정값(`application.properties`)과 개인별 컬럼으로 관리하며, 사용취소·재적립에는 한도 검사를 적용하지 않는다.

**검증 결과**

- 단위 · 슬라이스 · 통합 테스트 233건 전부 GREEN (`@DataJpaTest` 27건, `@WebMvcTest` 39건, `@SpringBootTest` 통합 133건, 순수 단위 33건, 애플리케이션 컨텍스트 로드 1건).
- 빌드된 jar 를 실제로 띄워 curl 로 예시 A~E, 수기 우선 소진, 적립취소 조건, 개인 한도, 오류 케이스, 관리자 필수값까지 왕복 검증 완료(§6 참고).

**제출물 위치**

- ERD: [`src/main/resources/docs/erd.pdf`](src/main/resources/docs/erd.pdf) ([PNG](src/main/resources/docs/erd.png))
- AWS 구성도(옵션): [`src/main/resources/docs/aws-architecture.pdf`](src/main/resources/docs/aws-architecture.pdf) ([PNG](src/main/resources/docs/aws-architecture.png))
- 설계 결정 원문: [`docs/design-decisions.md`](docs/design-decisions.md)

---

## 목차

1. [설계](#1-설계)
2. [API 명세](#2-api-명세)
3. [예시 시나리오 A~E](#3-예시-시나리오-ae)
4. [요구사항에 없는 정책 — 가정](#4-요구사항에-없는-정책--가정)
5. [동시성과 정합성](#5-동시성과-정합성)
6. [테스트 구성](#6-테스트-구성)
7. [한계와 확장](#7-한계와-확장)
8. [AWS 구성 요약](#8-aws-구성-요약)

---

## 1. 설계

### 1.1 테이블을 4개로 나눈 이유

"회원별 총잔액"만 저장하면 어떤 적립이 어떤 주문에서 쓰였는지 복원할 수 없다. 그래서 적립 건마다 잔액을 두고(`point_earning`),
거래가 적립 건별로 얼마를 움직였는지를 상세 행(`point_transaction_detail`)으로 남긴다. 1포인트마다 행을 만들지는 않는다.

| 테이블 | 역할 |
|---|---|
| `point_account` | 회원 포인트 계정. 모든 변경 명령이 이 행을 잠그고 진행하는 직렬화 단위 |
| `point_transaction` | 거래(적립/적립취소/사용/사용취소). 외부에 노출하는 `pointKey` 단위 |
| `point_earning` | 적립 건. 잔액을 가진 최소 단위. 만료는 상태가 아니라 `expires_at` 과 현재 시각의 비교로 판정 |
| `point_transaction_detail` | 거래 상세. 적립 건별 금액 이동(`OUT`=사용으로 차감, `IN`=사용취소로 복원)을 1원 단위로 기록 |

전체 컬럼·제약·인덱스는 [`src/main/resources/schema.sql`](src/main/resources/schema.sql) 과 [`docs/design-decisions.md`](docs/design-decisions.md) §3, ERD([PDF](src/main/resources/docs/erd.pdf))를 참고.

### 1.2 잔액의 정의

> **잔액 = 계정의 `point_earning` 중 `status=ACTIVE` 이고 `expires_at > now` 인 행들의 `remaining_amount` 합**

별도 잔액 컬럼을 두지 않았기 때문에 이 정의 하나로 모든 조회 · 검증 로직이 일관된다.

### 1.3 사용 우선순위

사용 가능한 적립 건을 `kind`(수기가 먼저) → `expires_at` 오름차순(만료가 가까운 순) → `id` 오름차순으로 정렬해 차례로 차감한다.
총액이 부족하면 아무것도 차감하지 않고 `INSUFFICIENT_BALANCE` 로 전체 거절한다 — "일부만 빠져나간" 중간 상태를 남기지 않기 위해서다.

### 1.4 취소 배분과 재적립

- **적립취소**: 해당 적립 건에 `OUT` 상세가 하나도 없어야(한 번도 안 쓰였어야) 가능하다. 만료됐지만 미사용인 적립은 취소할 수 있다.
- **사용취소**: 원 사용이 적립 건별로 가져간 순서(`seq`)대로 되돌린다. 상세별로 아직 안 돌려준 금액(상세 금액 − 그 상세를 가리키는 `IN` 합)만큼 배분한다.
  - 되돌아갈 적립 건이 **아직 살아있고(`ACTIVE`) 미만료**면 그 건의 `remaining_amount` 를 그대로 늘린다(제자리 복원).
  - 그 외(만료됐거나, 정상 흐름에서는 나올 수 없지만 방어적으로 처리하는 `CANCELED`)면 **새 적립 건**을 만들어 돌려준다. 종류(`kind`)는 원 적립 건에서 물려받고, 만료일은 취소 시각 + 기본 일수로 새로 센다.
  - 사용취소와 재적립에는 1회 적립 한도 · 보유 한도를 적용하지 않는다 — 새로 주는 게 아니라 쓴 걸 돌려주는 것이기 때문이다.

### 1.5 정책 값 관리 (하드코딩 금지 요건)

| 값 | 위치 | 기본값 |
|---|---|---|
| 1회 적립 최소 · 최대 | `application.properties` → `point.earn.min-amount` / `max-amount` | 1 / 100,000 |
| 보유 한도 기본값 | `point.balance.default-max` | 1,000,000 |
| 개인별 보유 한도 | `point_account.max_balance` 컬럼(관리자 API `PUT /admin/.../limit` 로 변경, `null`=기본값으로 복귀) | `NULL` |
| 만료 기본 · 최소일수 · 최대년수 | `point.expiry.default-days` / `min-days` / `max-years` | 365 / 1 / 5(5년 **미만**) |

---

## 2. API 명세

경로 접두 `/api/v1`. 요청 · 응답은 JSON. 모든 오류는 `{ "code", "message", "timestamp" }` 형태로 응답한다.

### 2.1 오류 코드

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수값 누락, 형식 오류, 금액 ≤ 0, JSON 역직렬화 실패(거대 숫자 등) |
| 400 | `EARN_AMOUNT_OUT_OF_RANGE` | 1회 적립 범위(1~100,000) 밖 |
| 400 | `EXPIRY_OUT_OF_RANGE` | 만료일수가 범위(1일 이상, 5년 미만) 밖 |
| 404 | `MEMBER_NOT_FOUND` | 회원의 포인트 계정 없음 |
| 404 | `POINT_KEY_NOT_FOUND` | pointKey 없음 또는 타입 불일치 |
| 409 | `BALANCE_LIMIT_EXCEEDED` | 적립 시 보유 한도 초과 |
| 409 | `INSUFFICIENT_BALANCE` | 사용 시 잔액 부족 |
| 409 | `EARN_ALREADY_USED` | 사용 이력 있는 적립 취소 시도 |
| 409 | `EARN_ALREADY_CANCELED` | 이미 취소된 적립 취소 시도 |
| 409 | `CANCEL_AMOUNT_EXCEEDED` | 사용취소 가능액 초과 |
| 409 | `DUPLICATE_ORDER` | 같은 (회원, 주문번호)로 재사용 |
| 409 | `UPDATE_CONFLICT` | 같은 회원의 다른 요청이 계정 행을 3초 넘게 쥐고 있음 |
| 404/405/415 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | 존재하지 않는 경로 / 지원하지 않는 메서드 · 미디어 타입도 같은 오류 본문 |
| 500 | `INTERNAL_ERROR` | 내부 사정은 응답에 노출하지 않음 |

### 2.2 엔드포인트별 요청 · 응답 (실제 curl 왕복 캡처, `e2e_results.log` 실행분)

**적립** — `POST /members/{memberId}/points/earn`

```
요청: {"amount":1000,"expireDays":1}
응답 200: {"pointKey":"lozDCmfI9akuIg4RDnMn0j","amount":1000,"kind":"GENERAL","expiresAt":"2026-09-14T12:57:56.783456Z","balance":1000}
```

**관리자 수기 적립** — `POST /admin/members/{memberId}/points/earn`

```
요청: {"amount":300,"expireDays":365,"adminId":"admin-e2e","reason":"E2E 검증용 수기 지급"}
응답 200: {"pointKey":"WpwswDsflDXIhTQvjLVNvG","amount":300,"kind":"MANUAL","expiresAt":"2027-09-13T12:57:57.066010Z","balance":300}
```

**적립취소** — `POST /members/{memberId}/points/earn/{pointKey}/cancel`

```
응답 200: {"pointKey":"671JG6mKyvn98h4SXOlWlz","canceledAmount":700,"balance":0}
```

**사용** — `POST /members/{memberId}/points/use`

```
요청: {"orderNo":"A1234","amount":1200}
응답 200: {"pointKey":"c4ijAmKrC4XuXvGWzqrU5m","amount":1200,
  "allocations":[{"earningPointKey":"lozDCmfI9akuIg4RDnMn0j","amount":1000},
                 {"earningPointKey":"9WM4LfhpdiwAtqQHujLVfh","amount":200}],
  "balance":300}
```

**사용취소** — `POST /members/{memberId}/points/use/{pointKey}/cancel`

```
요청: {"amount":1100}
응답 200: {"pointKey":"diwDuIuQx1dDtfw6ygOQW2","canceledAmount":1100,
  "restorations":[{"earningPointKey":"lozDCmfI9akuIg4RDnMn0j","amount":1000,"reissued":false},
                   {"earningPointKey":"9WM4LfhpdiwAtqQHujLVfh","amount":100,"reissued":false}],
  "balance":1400,"remainingCancelableAmount":100}
```

만료된 적립 몫을 사용취소하면 `reissued:true` 와 `newEarningPointKey` 가 채워진다(§3, `FullFlowScenarioABCDETest`/`ReissueOnExpiredRestorationTest` 로 검증).

**잔액 조회** — `GET /members/{memberId}/points/balance`

```
응답 200: {"balance":1400,"asOf":"2026-09-13T12:57:56.994616Z"}
```

**적립별 사용 내역 조회** — `GET /members/{memberId}/points/earn/{pointKey}/usages`

```
응답 200: {"earning":{"pointKey":"lozDCmfI9akuIg4RDnMn0j","kind":"GENERAL","status":"ACTIVE",
    "originalAmount":1000,"remainingAmount":1000,"earnedAt":"2026-09-13T12:57:56.783456Z","expiresAt":"2026-09-14T12:57:56.783456Z"},
  "usages":[{"orderNo":"A1234","usePointKey":"c4ijAmKrC4XuXvGWzqrU5m","usedAmount":1000,"canceledAmount":1000,"netUsedAmount":0}]}
```

**개인 보유 한도 변경** — `PUT /admin/members/{memberId}/points/limit`

```
요청: {"maxBalance":1000}          응답 200: {"memberId":"curl-member-4","maxBalance":1000}
요청: {"maxBalance":null}          응답 200: {"memberId":"curl-member-4"}   (기본값으로 복귀, 필드 생략)
```

**오류 응답 예시**

```
409 CANCEL_AMOUNT_EXCEEDED: {"code":"CANCEL_AMOUNT_EXCEEDED","message":"취소할 수 있는 금액은 1 이상 0 이하입니다.","timestamp":"..."}
409 EARN_ALREADY_USED:      {"code":"EARN_ALREADY_USED","message":"이미 사용된 적립은 취소할 수 없습니다.","timestamp":"..."}
409 BALANCE_LIMIT_EXCEEDED: {"code":"BALANCE_LIMIT_EXCEEDED","message":"보유 한도 1000 를 초과합니다. 현재 사용 가능 잔액 0","timestamp":"..."}
404 MEMBER_NOT_FOUND:       {"code":"MEMBER_NOT_FOUND","message":"회원의 포인트 계정을 찾을 수 없습니다.","timestamp":"..."}
```

---

## 3. 예시 시나리오 A~E

과제 원문 예시: A 1,000 적립 → B 500 적립 → 주문 A1234 에서 C 1,200 사용(A 1,000 + B 200) → A 만료 → C 중 1,100 사용취소(D).
A 몫 1,000 은 이미 만료됐으므로 새 적립 E 로 돌려주고, B 몫 100 은 B 에 제자리로 돌려준다.

| 단계 | 동작 | 잔액 | 비고 |
|---|---|---|---|
| 1 | A 1,000 적립 | 1,000 | |
| 2 | B 500 적립 | 1,500 | |
| 3 | 주문 A1234 에서 C 1,200 사용 | 300 | 배분: A 1,000 + B 200 |
| 4 | A 만료 (시간 경과) | 300 | 만료는 상태 변경이 아니라 시각 비교 |
| 5 | C 중 1,100 사용취소 → D | **1,400** | A 몫 1,000 → 만료돼 있어 **신규 적립 E** 로 복원, B 몫 100 → B.remaining 300→400 로 제자리 복원 |
| — | C 의 남은 취소 가능액 | 100 | 1,200 − 1,100 |

이 표의 4~5단계(시계를 실제로 앞당겨 만료를 만드는 경로)는 `FullFlowScenarioABCDETest` 와 `ReissueOnExpiredRestorationTest` 가
`Clock` 을 주입해 검증한다. curl 로는 시계를 조작할 수 없어, `e2e_results.log` 의 curl E2E 는 미만료 상태에서의 배분 · 제자리 복원
수치(3, 5단계와 동일한 합계 1,400 / 잔여 취소 가능액 100)만 실제 HTTP 응답으로 재검증했다 — 계산 로직은 같고 복원 경로(제자리 vs 재적립)만 다르다.

---

## 4. 요구사항에 없는 정책 — 가정

| 미정 사항 | 가정 | 근거 |
|---|---|---|
| 회원 계정 생성 | 첫 적립(또는 관리자 한도 변경) 시 자동 생성. 계정 없는 회원의 사용 · 조회는 404 | 회원 API 는 과제 범위 밖 |
| 같은 주문번호로 두 번 사용 | 거절(409 `DUPLICATE_ORDER`) | 이중 사용 방지, 재전송 안전 |
| 전액 복원된 적립의 적립취소 | 불가 | "일부라도 사용되면 취소 불가"를 이력 기준으로 해석 |
| 만료됐지만 미사용인 적립의 적립취소 | 가능 | 취소 조건은 사용 여부뿐 |
| 재적립(E)의 만료일 | 취소 시각 + 기본 일수(365일) | 원 만료일은 이미 지남 |
| 재적립의 종류 · 우선순위 | 원 적립 건의 `kind` 상속(수기면 수기) | 관리자 지급분의 성격 유지 |
| 사용취소가 보유 한도 · 1회 한도를 넘는 경우 | 검사하지 않음 | 돌려주는 것이지 새로 주는 것이 아님 |
| 사용취소 배분 순서 | 원 사용의 배분 순서(`seq`) | 예시와 일치(A 먼저) |
| pointKey 형식 | 서버 생성, 22자 내외 URL-safe 문자열 | 예측 불가, 유일 |
| 멱등키(Idempotency-Key) | 미구현. 주문번호 유일 제약이 사용의 재전송을 막음 | 요구사항 외, §7 확장 방향 참고 |
| 만료 배치 | 미구현. 잔액 · 사용 대상이 `expires_at` 비교로만 판정되므로 불필요 | 요구사항 외 |
| 동시성 | 회원 계정 행 `PESSIMISTIC_WRITE` 잠금, 3초 초과 시 409 `UPDATE_CONFLICT` | 같은 회원 직렬화, 다른 회원 병렬 |
| 개인 한도를 현재 잔액보다 낮게 설정 | 허용. 기존 잔액은 유지, 이후 적립만 차단 | 한도는 적립 시점에만 검사 |
| 적립 건 불변식 | `remaining = original − Σ OUT(그 건) + Σ IN(그 건, 복원분만)`. 재적립 건 자체의 `original_amount` 를 IN 합에 다시 더하지 않음(이중 집계 방지) | 검증 테스트 기준 |

---

## 5. 동시성과 정합성

- **잠금 단위**: 회원 계정(`point_account`) 행을 `@Lock(PESSIMISTIC_WRITE)` 로 잠근다. 같은 회원의 요청은 직렬화되고, 다른 회원은 서로 막지 않는다.
- **잠금 대기 상한**: H2 접속 URL 의 `LOCK_TIMEOUT=3000`(3초)으로 잡는다. 초과하면 `PessimisticLockingFailureException` / `LockTimeoutException` 을 잡아 409 `UPDATE_CONFLICT` 로 바꿔 응답한다.
- **한 트랜잭션**: 적립 · 사용 · 사용취소는 각각 하나의 트랜잭션이다. 사용취소 중 재적립이 실패하면 취소 자체도 롤백된다(`ReissueFailureRollbackTest`).
- **계정 자동 생성이 별도인 이유**: 첫 적립 시 계정을 만드는 쓰기는 별도 트랜잭션으로 커밋해, 통합 테스트가 매 테스트 시작마다 표를 비울 때 테스트 트랜잭션 롤백에 기대지 않고 명시적으로 `TRUNCATE` 할 수 있게 했다(`AbstractPointIntegrationTest`).
- **왜 낙관적 락이 아니라 비관적 락인가**: 적립 · 사용 · 사용취소가 모두 "현재 잔액을 읽고 그 잔액을 근거로 이동시키는" 연산이라 충돌이 흔하고, 실패 시 재시도보다 짧은 대기 후 명확한 409 가 클라이언트 처리에 더 낫다고 판단했다.
- **검증**: `ConcurrentEarnNoLostUpdateTest`(같은 회원 동시 적립 유실 없음), `ConcurrentUseSerializationTest`(동시 사용 10건이 정확히 맞아떨어지면 전원 성공 · 잔액 0, 초과분은 부분 성공 + `INSUFFICIENT_BALANCE`, 잔액 음수 없음), `LockWaitTimeoutConflictTest`(3초 초과 → 409), `EarningBalanceInvariantAllScenariosTest`(적립 건 불변식)가 각각 `CountDownLatch` + 고정 스레드풀로 실제 동시 출발을 만들어 검증한다.

---

## 6. 테스트 구성

| 슬라이스 | 건수 | 확인 사항 |
|---|---|---|
| 순수 단위 (Spring 컨텍스트 없음) | 33 | 정책 검증(범위 · 만료일수 · 5년 미만 달력 경계 · 윤년), 오류코드 매핑, pointKey 생성기, 엔티티 불변식 |
| `@DataJpaTest` | 27 | 잠금 쿼리, 상세 집계 쿼리, 유일 제약, 스키마-엔티티 매핑(`ddl-auto=validate`) |
| `@WebMvcTest` | 39 | 요청 검증 400, 오류 응답 본문, 404/405/415 |
| `@SpringBootTest` 통합(`AbstractPointIntegrationTest`) | 133 | 예시 A~E 전 과정, 반복 부분 취소 · 전액 취소, 수기 우선 · 만료 순 소진, 적립취소 조건, 개인 한도, 동시성(유실 없음 · 잔액 음수 없음 · 3초 타임아웃), 재적립 실패 롤백, 방어적 분기(GAP-1 회귀) |
| 애플리케이션 컨텍스트 로드 | 1 | `PointApplicationTests` |
| **합계** | **233** | 전부 GREEN |

```bash
./gradlew test
```

---

## 7. 한계와 확장

- **멱등키 없음**: 사용은 (회원, 주문번호) 유일 제약이 재전송을 막지만, 적립 · 취소는 같은 요청이 두 번 오면 두 번 처리된다. 확장한다면 `Idempotency-Key` 헤더 + 요청 해시를 별도 테이블에 기록하는 방식을 고려한다.
- **만료 배치 없음**: 판정을 시각 비교로만 하므로 기능상 문제는 없지만, "곧 만료될 포인트" 를 사전에 알림 발송하려면 조회 전용 배치나 이벤트가 추가로 필요하다.
- **인증 · 인가 없음**: 관리자 API 가 경로로만 분리돼 있다. 운영에 올린다면 관리자 엔드포인트에 별도 인증(예: 내부망 전용 + 서비스 토큰)이 필요하다.
- **영속 DB 아님**: H2 인메모리라 재시작하면 데이터가 사라진다. §8 AWS 구성도는 Aurora MySQL 로 옮기는 경우를 가정한다.
- **다중 인스턴스 확장 시 주의**: 인스턴스를 늘리면 그만큼 커넥션 풀도 늘어난다. **인스턴스 수 × 커넥션 풀 크기 ≤ DB 최대 커넥션** 을 지키지 않으면 잠금 대기가 아니라 커넥션 자체를 얻지 못해 요청이 막힌다.

---

## 8. AWS 구성 요약

옵션 제출물([PDF](src/main/resources/docs/aws-architecture.pdf)/[PNG](src/main/resources/docs/aws-architecture.png)). Route 53 → ALB(퍼블릭, 2 AZ) → ECS Fargate 태스크 2개(프라이빗, Java 21) →
Aurora MySQL(Multi-AZ, writer + 대기 replica, 프라이빗)로 이어지며, CloudWatch(로그 · 메트릭 · 알람)와 Secrets Manager(DB 자격증명)를 곁들인다.
배포는 GitHub Actions → ECR → ECS 로 이어지는 한 줄 파이프라인이다. **만료 배치 · 워커는 두지 않는다** — 만료는 `expires_at` 비교로만 판정하기 때문이다.
제출용 코드는 H2 인메모리 단일 인스턴스이지만, 회원 계정 행 잠금은 Aurora 로 옮겨도 동일하게 동작한다.
