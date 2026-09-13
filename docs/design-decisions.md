# 설계 결정 기록 — 무료 포인트 시스템 API

과제: 무신사페이먼츠 Backend Engineer 과제 전형 (무료 포인트 시스템). 요구사항 원문은 §1.
이 문서는 구현의 단일 진실 원천이다. 요구사항에 없는 정책은 §7에 "가정"으로 분리했다.

## 1. 요구사항 요약 (원문 기준)

- 환경: Java 21, Spring Boot 3.x, H2. 제출물: 코드(GitHub), ERD(PDF/이미지, `resource` 하위, 필수), AWS 아키텍처(옵션), README(빌드 방법·과제 설명, 필수).
- 기능: 적립, 적립취소, 사용, 사용취소.
- 적립: 1회 1 이상 100,000 이하. 1회 최대치는 하드코딩이 아닌 방법으로 제어. 개인별 보유 최대금액 제한이 있고 하드코딩이 아닌 방법으로 변경 가능. 특정 시점에 적립된 포인트가 어떤 주문에서 1원 단위까지 사용됐는지 추적 가능. 관리자 수기 지급은 다른 적립과 구분 식별. 모든 포인트에 만료일: 최소 1일 이상, 최대 5년 미만, 기본 365일.
- 적립취소: 특정 적립 건의 적립 금액만큼 취소. 일부라도 사용됐으면 취소 불가.
- 사용: 주문 시에만. 주문번호 기록. 수기 지급 포인트 우선, 그 다음 만료일이 짧게 남은 순.
- 사용취소: 전체 또는 일부. 취소 시점에 이미 만료된 포인트를 되돌려야 하면 그 금액만큼 신규 적립.
- 예시: A 1,000 적립 → B 500 적립 → 주문 A1234 에서 C 1,200 사용(A 1,000 + B 200) → A 만료 → C 중 1,100 사용취소 D: A 몫 1,000 은 만료됐으므로 E 로 신규 적립, B 몫 100 은 B 잔액 300→400. 총잔액 300→1,400. C 의 남은 취소 가능액 100.

## 2. 확정된 아키텍처

| 항목 | 결정 | 이유 |
|---|---|---|
| 배포 구조 | 단일 Spring Boot 애플리케이션 | 과제 규모, 실행·검토 단순 |
| 스택 | Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.5 | 과제 제약 |
| 데이터 접근 | Spring Data JPA (Hibernate) | 공고 필수 스택. 회원 계정 잠금은 `@Lock(PESSIMISTIC_WRITE)` |
| DB | H2 인메모리, `schema.sql` 명시 DDL (`ddl-auto=validate`) | 사전 작업 없이 실행. ERD 와 스키마를 한 곳(schema.sql)에서 관리 |
| 금액 | `long` 정수 포인트 | 소수 없음 |
| 시각 | `Clock` 주입 | 만료 시나리오(예시 4단계)를 테스트에서 재현 |
| 코드 스타일 | Lombok 미사용. 엔티티는 클래스, DTO 는 record, 생성자 주입 | 의도가 코드에 보이게 |
| 루트 패키지 | `com.musinsa.point` | |
| 인증·인가 | 없음. 관리자 기능은 `/api/v1/admin/**` 경로로만 분리 | 과제 범위 밖. README 에 명시 |

## 3. 데이터 모델 — 테이블 4개

"회원별 총잔액"만 저장하면 어떤 적립이 어떤 주문에 쓰였는지 복원할 수 없다. 적립 건마다 잔액을 두고, 거래가 적립 건별로 얼마를 움직였는지를 상세 행으로 남긴다. 1포인트마다 행을 만들지는 않는다.

### 3.1 `point_account` — 회원 포인트 계정 (잠금 단위)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | |
| `member_id` | VARCHAR(64) NOT NULL UNIQUE | 회원 식별자 (외부 시스템 값 그대로) |
| `max_balance` | BIGINT NULL | 개인별 보유 한도. NULL 이면 설정 기본값 적용 |
| `created_at` | TIMESTAMP NOT NULL | |

첫 적립 시 자동 생성한다. 모든 변경 명령은 이 행을 `FOR UPDATE` 로 잠근 뒤 진행한다.

### 3.2 `point_earning` — 적립 건 (잔액을 가진 단위)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | |
| `account_id` | BIGINT NOT NULL FK | |
| `transaction_id` | BIGINT NOT NULL UNIQUE FK | 이 적립 건을 만든 거래 (pointKey 는 거래에 있음) |
| `kind` | VARCHAR(16) NOT NULL | `MANUAL`(관리자 수기) / `GENERAL`(일반) |
| `original_amount` | BIGINT NOT NULL, > 0 | 최초 적립액 |
| `remaining_amount` | BIGINT NOT NULL, 0 ≤ x ≤ original | 남은 금액 |
| `earned_at` | TIMESTAMP NOT NULL | |
| `expires_at` | TIMESTAMP NOT NULL | 만료 시각(이 시각부터 사용 불가) |
| `status` | VARCHAR(16) NOT NULL | `ACTIVE` / `CANCELED`(적립취소됨) |
| `admin_id` | VARCHAR(64) NULL | 수기 지급 관리자 |
| `reason` | VARCHAR(200) NULL | 수기 지급 사유 |
| `reissued_from_transaction_id` | BIGINT NULL FK | 만료분 재적립이면 원인 사용취소 거래 |

만료는 상태 컬럼이 아니라 `expires_at` 비교로 판정한다. 만료 배치는 두지 않는다.

### 3.3 `point_transaction` — 거래 (pointKey 단위)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | |
| `point_key` | VARCHAR(32) NOT NULL UNIQUE | 외부 노출 식별자. 서버 생성 |
| `account_id` | BIGINT NOT NULL FK | |
| `type` | VARCHAR(16) NOT NULL | `EARN` / `EARN_CANCEL` / `USE` / `USE_CANCEL` |
| `amount` | BIGINT NOT NULL, > 0 | 요청 금액 |
| `order_no` | VARCHAR(64) NULL | `USE` 에 필수. `USE_CANCEL` 은 원 사용의 주문번호 복사 |
| `use_order_no` | VARCHAR(64) NULL | `USE` 거래에만 `order_no` 와 같은 값. 주문 이중 사용 방지용 유일 제약 컬럼 (H2 에 부분 유일 인덱스가 없어 분리) |
| `related_transaction_id` | BIGINT NULL FK | `EARN_CANCEL`→원 적립 거래, `USE_CANCEL`→원 사용 거래, 재적립 `EARN`→원인 사용취소 거래 |
| `created_at` | TIMESTAMP NOT NULL | |

제약: `UNIQUE(account_id, use_order_no)` (NULL 은 중복 허용) — 같은 주문의 이중 사용 방지(§7 가정).

### 3.4 `point_transaction_detail` — 거래 상세 (적립 건별 금액 이동)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | |
| `transaction_id` | BIGINT NOT NULL FK | |
| `earning_id` | BIGINT NOT NULL FK | 금액이 이동한 적립 건 |
| `amount` | BIGINT NOT NULL, > 0 | 이동 금액 |
| `direction` | VARCHAR(8) NOT NULL | `OUT`(적립 건에서 차감: USE) / `IN`(적립 건으로 복원: USE_CANCEL) |
| `reversed_detail_id` | BIGINT NULL FK | `USE_CANCEL` 상세가 되돌리는 원 `USE` 상세 |
| `seq` | INT NOT NULL | 거래 안의 배분 순서 |

예시 C 는 상세 2행(A 1,000 OUT, B 200 OUT). D 는 상세 2행(E 1,000 IN·reversed=C→A 상세, B 100 IN·reversed=C→B 상세). "적립 A 가 어떤 주문에서 얼마 쓰였나" = A 의 OUT 상세 → 거래 → 주문번호. "C 의 남은 취소 가능액" = C 의 각 OUT 상세 금액 − 그 상세를 reversed 로 가리키는 IN 상세 합.

인덱스: PK·UNIQUE 외에 `point_earning(account_id, status, expires_at)`, `point_transaction(account_id, created_at)`, `point_transaction_detail(earning_id)`, `point_transaction_detail(reversed_detail_id)`.

## 4. 정책 값 관리 (하드코딩 금지 요건)

| 값 | 위치 | 기본 |
|---|---|---|
| 1회 적립 최소·최대 | `application.properties` `point.earn.min-amount` / `point.earn.max-amount` → `@ConfigurationProperties` | 1 / 100,000 |
| 보유 한도 기본값 | `point.balance.default-max` | 1,000,000 (가정) |
| 개인별 보유 한도 | `point_account.max_balance` (관리자 API 로 변경) | NULL → 기본값 |
| 만료 기본·범위 | `point.expiry.default-days` / `min-days` / `max-years` | 365 / 1 / 5 (5년 **미만**) |

## 5. 기능별 규칙

### 5.1 적립 (일반 / 관리자 수기)
1. 계정 조회(없으면 생성) → 행 잠금.
2. 검증: `min ≤ amount ≤ max`; 만료일수 `1 ≤ days` 이고 `expires_at < earned_at + 5년`(달력 기준); 사용 가능 잔액 + amount ≤ 보유 한도.
3. `EARN` 거래 + 적립 건 생성(수기면 `kind=MANUAL`, `admin_id`, `reason` 필수).
4. 응답: pointKey, 적립액, 만료 시각, 잔액.

### 5.2 적립취소
1. pointKey 로 `EARN` 거래·적립 건 조회 → 계정 잠금.
2. 검증: 상태 `ACTIVE`; 해당 적립 건에 `OUT` 상세가 하나도 없음(한 번이라도 사용됐으면 불가). 만료된 적립 건도 미사용이면 취소 가능(§7).
3. 적립 건 `status=CANCELED`, `remaining_amount=0`, `EARN_CANCEL` 거래(related=원 적립 거래) 생성.

### 5.3 사용
1. 계정 잠금. 검증: `amount ≥ 1`, `order_no` 필수, 같은 계정·주문번호의 `USE` 없음.
2. 대상 적립 건: `status=ACTIVE`, `remaining_amount > 0`, `expires_at > now`. 정렬: `kind`(MANUAL 먼저) → `expires_at ASC` → `id ASC`.
3. 순서대로 차감. 총액이 부족하면 전체 실패(`INSUFFICIENT_BALANCE`).
4. `USE` 거래 + 적립 건별 `OUT` 상세 생성.

### 5.4 사용취소
1. pointKey 로 `USE` 거래 조회 → 계정 잠금. 검증: `1 ≤ amount ≤ 사용액 − 기취소액`.
2. 원 사용의 `OUT` 상세를 `seq` 순으로 돌며, 상세별 남은 취소 가능액(상세 금액 − 그 상세의 IN 합) 만큼 취소액을 배분.
3. 상세별 복원: 원 적립 건이 `ACTIVE` 이고 `expires_at > now` 면 `remaining_amount += x`, `IN` 상세(earning=원 적립 건). 그 외(만료)면 **신규 적립**(적립취소된 건은 사용 이력이 없어 이 경로에 올 수 없지만, 방어적으로 같은 분기로 처리): `EARN` 거래(related=이 사용취소 거래) + 적립 건(`kind` 는 원 적립 건 상속, `expires_at = now + 기본 일수`, `reissued_from_transaction_id` = 이 사용취소 거래) 생성 후 `IN` 상세(earning=새 적립 건).
4. 전부 한 트랜잭션. 신규 적립 실패 시 취소도 롤백.
5. **한도 검사 없음**: 사용취소·재적립은 1회 적립 한도·보유 한도를 적용하지 않는다(쓴 것을 돌려주는 것이지 새로 주는 것이 아님).
6. 응답: pointKey(D), 취소액, 복원 상세(적립 건별 금액, 재적립이면 새 pointKey), 잔액, 남은 취소 가능액.

### 5.5 조회
- 잔액: `ACTIVE` 이고 `expires_at > now` 인 적립 건의 `remaining_amount` 합.
- 적립별 사용 내역: 적립 pointKey → `OUT` 상세들을 주문번호별로 묶어 `usedAmount`, `canceledAmount`, `netUsedAmount`.

## 6. API

경로 접두 `/api/v1`. 요청·응답은 JSON. 모든 오류는 `{ "code", "message", "timestamp" }`.

| 기능 | 메서드 · 경로 | 요청 | 응답 핵심 |
|---|---|---|---|
| 적립 | `POST /members/{memberId}/points/earn` | `amount`, `expireDays?` | `pointKey`, `amount`, `expiresAt`, `balance` |
| 관리자 수기 적립 | `POST /admin/members/{memberId}/points/earn` | `amount`, `expireDays?`, `adminId`, `reason` | 위와 같음 + `kind=MANUAL` |
| 적립취소 | `POST /members/{memberId}/points/earn/{pointKey}/cancel` | 없음 | `pointKey`(취소 거래), `canceledAmount`, `balance` |
| 사용 | `POST /members/{memberId}/points/use` | `orderNo`, `amount` | `pointKey`, `amount`, `allocations[{earningPointKey, amount}]`, `balance` |
| 사용취소 | `POST /members/{memberId}/points/use/{pointKey}/cancel` | `amount` | `pointKey`, `canceledAmount`, `restorations[{earningPointKey, amount, reissued, newEarningPointKey?}]`, `balance`, `remainingCancelableAmount` |
| 잔액 | `GET /members/{memberId}/points/balance` | | `balance`, `asOf` |
| 적립별 사용 내역 | `GET /members/{memberId}/points/earn/{pointKey}/usages` | | `earning{...}`, `usages[{orderNo, usePointKey, usedAmount, canceledAmount, netUsedAmount}]` |
| 개인 보유 한도 변경 | `PUT /admin/members/{memberId}/points/limit` | `maxBalance` (null 이면 기본값으로, 1 이상) | `memberId`, `maxBalance` |

오류코드

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수값 누락, 형식 오류, 금액 ≤ 0 |
| 400 | `EARN_AMOUNT_OUT_OF_RANGE` | 1회 적립 범위 밖 (`Long.MAX` 등 거대 값 포함). 범위 검증은 한도 검사보다 먼저 수행해 덧셈 오버플로를 막는다 |
| 400 | `EXPIRY_OUT_OF_RANGE` | 만료일수 범위 밖 (0 포함) |
| 400 | `INVALID_REQUEST` | JSON 숫자가 long 범위를 넘는 등 역직렬화 불가 |
| 404 | `MEMBER_NOT_FOUND` | 계정 없음 (조회·사용·취소) |
| 404 | `POINT_KEY_NOT_FOUND` | pointKey 없음 또는 타입 불일치 |
| 409 | `BALANCE_LIMIT_EXCEEDED` | 적립 시 보유 한도 초과 |
| 409 | `INSUFFICIENT_BALANCE` | 사용 시 잔액 부족 |
| 409 | `EARN_ALREADY_USED` | 사용 이력 있는 적립 취소 |
| 409 | `EARN_ALREADY_CANCELED` | 이미 취소된 적립 |
| 409 | `CANCEL_AMOUNT_EXCEEDED` | 취소 가능액 초과 |
| 409 | `DUPLICATE_ORDER` | 같은 주문번호 재사용 |
| 404/405/415 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | 프레임워크 오류도 같은 본문 |
| 500 | `INTERNAL_ERROR` | 내부 메시지 미노출 |

## 7. 요구사항에 없는 정책 — 가정

| 미정 사항 | 가정 | 근거 |
|---|---|---|
| 회원 계정 생성 | 첫 적립 시 자동 생성. 관리자 한도 변경도 계정이 없으면 생성 후 설정. 계정 없는 회원의 사용·조회는 404 | 회원 API 가 과제 범위 밖 |
| 같은 주문번호로 두 번 사용 | 거절(409) | 이중 사용 방지, 재전송 안전 |
| 사용됐다가 전액 복원된 적립의 적립취소 | 불가 | "일부가 사용된 경우 취소 불가"를 이력 기준으로 해석 |
| 만료됐지만 미사용인 적립의 적립취소 | 가능 | 취소 조건은 사용 여부뿐 |
| 재적립(E)의 만료일 | 취소 시각 + 기본 일수(365) | 원 만료일은 이미 지남 |
| 재적립의 종류·우선순위 | 원 적립 건의 `kind` 상속(수기면 수기) | 관리자 지급분의 성격 유지 |
| 사용취소가 보유 한도·1회 한도를 넘는 경우 | 검사하지 않음 | 돌려주는 것이지 새로 주는 것이 아님 |
| 사용취소 배분 순서 | 원 사용의 배분 순서(seq) | 예시와 일치(A 먼저) |
| pointKey 형식 | 서버 생성 22자 내외 URL-safe 문자열 | 예측 불가, 유일 |
| 멱등키(Idempotency-Key) | 미구현. 주문번호 유일 제약이 사용의 재전송을 막음. 확장 방향으로 README 에 기술 | 요구사항 외 |
| 만료 배치 | 미구현. 잔액·사용 대상이 `expires_at` 로 판정되므로 불필요 | 요구사항 외 |
| 동시성 | 회원 계정 행 `PESSIMISTIC_WRITE` 잠금, 잠금 대기 3초 초과 → 409 `UPDATE_CONFLICT` | 같은 회원 직렬화, 다른 회원 병렬 |
| 개인 한도를 현재 잔액보다 낮게 설정 | 허용. 기존 잔액은 유지하고 이후 적립만 차단 | 한도는 적립 시점 검사 |
| 적립 건 불변식 | `remaining_amount = original_amount − Σ OUT(그 건) + Σ IN(그 건, 복원분만)`. 재적립 건을 만든 IN 상세는 그 건의 `original_amount` 자체이므로 Σ IN 에 다시 더하지 않는다(이중 집계 방지) | 검증 테스트 기준 |

## 8. 패키지 구성

| 패키지 | 구성 |
|---|---|
| `account` | `PointAccount`, `PointAccountRepository`, 한도 변경 |
| `point` | `PointEarning`, `PointTransaction`, `PointTransactionDetail`, 리포지토리 |
| `point.command` | `EarnService`, `UseService`, `CancelService` (트랜잭션 경계) |
| `point.query` | `BalanceQueryService`, `EarningUsageQueryService` |
| `point.policy` | `PointPolicyProperties`(설정), 검증 |
| `api` | 컨트롤러, 요청·응답 DTO |
| `common` | `ErrorCode`, `ApiException`, `GlobalExceptionHandler`, `PointKeyGenerator`, `Clock` 설정 |

## 9. 검증 계획

| 범위 | 확인 사항 |
|---|---|
| 단위 | 정책 검증(범위·만료일수·5년 미만 달력 경계·윤년), 사용 우선순위 정렬, 취소 배분 계산, pointKey 유일성 |
| `@DataJpaTest` | 잠금 쿼리, 상세 집계 쿼리, 유일 제약 |
| `@WebMvcTest` | 요청 검증 400, 오류 본문, 404/405/415 |
| `@SpringBootTest` | **예시 A~E 전 과정**(잔액 1,400, C 잔여 취소액 100, E 생성, B 400), 반복 부분 취소, 전액 취소, 수기 우선·만료 순, 적립취소 조건, 한도(개인별 컬럼 우선), 같은 회원 동시 사용·취소(증가분 유실 없음, 잔액 음수 없음), 재적립 실패 시 롤백, 불변식(적립 건 잔액 = 최초 − OUT 합 + IN 합) |

## 10. 제출물 생성

- ERD: 구현 후 `schema.sql` 기준으로 작성해 `src/main/resources/docs/erd.pdf`(+`.png`). 테이블·컬럼·키·관계만.
- AWS 구성도(옵션): `src/main/resources/docs/aws-architecture.pdf`. ALB → ECS Fargate(2 AZ) → Aurora MySQL Multi-AZ, CloudWatch, Secrets Manager. 만료 배치가 없으므로 워커 없음.
- README: 요약(실행·테스트) → 설계 핵심 → API → 가정 표 → ERD·AWS 링크.
