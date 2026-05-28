# ERD 설계 문서

> **다이어그램:** `[erd.md](./erd.md)` · `[erd.png](./erd.png)`  
> K-Pop 팬덤 **B2B2C** 플랫폼의 테이블 관계·컬럼 설계 근거. **DB 29테이블** (`erd.md`). 드롭스·결제·커뮤니티·아티스트 운영 도메인을 포함한다.

---

## 0. 엔티티 개요


| 도메인          | 테이블                                                                                                                 | 비고                                                                                      |
| ------------ | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| **사용자·아티스트** | `FAN`, `AGENCY_APPLICATION`, `AGENCY_ACCOUNT`, `ARTIST_PROFILE`, `ARTIST_MEMBER`, `USER_FOLLOW`                     | 팬(B2C) · 입점신청(B2B) · **운영 주체(B2B)** · 아티스트 프로필·멤버 · 팔로우(팬 가입)                           |
| **커뮤니티**     | `ARTIST_FEED`, `FEED_IMAGE`, `ARTIST_NOTICE`, `COMMENT`, `FEED_LIKE`, `COMMENT_LIKE`                                | 피드 · 피드 다중 이미지 · 공지 · 댓글(대댓글) · 피드/댓글 좋아요                                               |
| **커머스**      | `PRODUCT`, `INVENTORY`, `INVENTORY_HISTORY`, `CART`, `CART_ITEM`, `ORDER`, `ORDER_ITEM`, `PAYMENT`, `RESTOCK_ALERT` | 상품·재고·재고 이력·**장바구니(RDB)** ·주문·결제 — [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) |
| **일정**       | `ARTIST_SCHEDULE`                                                                                                   | 아티스트 스케줄(일정) 및 공지 연동 캘린더                                                                |
| **출석 체크**    | `ATTENDANCE_EVENT`, `ATTENDANCE_LOG`                                                                                | 출석 체크 이벤트 관리 및 팬 출석 기록                                                                  |
| **굿즈 투표**    | `GOODS_VOTE`, `GOODS_VOTE_OPTION`, `GOODS_VOTE_RECORD`                                                              | 굿즈 디자인/콘셉트 이미지 선택지 투표 및 기록                                                              |
| **운영·알림**    | `BANNER`, `NOTIFICATION`                                                                                            | 메인/스토어 배너 · 팬 알림함                                                                       |


---

## 1. INVENTORY — 재고 테이블 분리 및 이력(HISTORY) 기록

### 설계 결정

재고 데이터를 `PRODUCT`에서 분리하여 `INVENTORY` 테이블로 관리하고, `INVENTORY_HISTORY`를 추가해 변경 이력을 감사 로그로 남긴다. `INVENTORY`에는 `total_qty`, `reserved_qty`, `available_qty`를 둔다. `PRODUCT`에는 `status`(`ON_SALE` | `SOLD_OUT`) 컬럼을 추가한다.

### 근거

드롭스 오픈 순간 수천 명이 동시에 결제를 시도하는 환경에서, 결제 진행 중인 재고와 실제 판매 가능한 재고를 구분하지 않으면 **오버셀**이 발생한다. 또한 Phase 4 부하 테스트 등에서 오버셀이 발생하지 않았음을 증명하기 위해 모든 재고 변동 내역을 기록하는 테이블이 필요하다.


| 테이블                 | 컬럼              | 의미                                    | 변경 시점                                                                      |
| ------------------- | --------------- | ------------------------------------- | -------------------------------------------------------------------------- |
| `INVENTORY`         | `total_qty`     | 전체 재고 (판매 완료 전까지 유지)                  | 결제 최종 확정(`PAID` → `COMPLETED`) 시 차감                                        |
| `INVENTORY`         | `reserved_qty`  | 결제 진행 중 선점된 재고                        | 재고 예약 시 증가, 결제 완료/취소 시 감소                                                  |
| `INVENTORY`         | `available_qty` | 판매 가능 재고 (`total_qty - reserved_qty`) | 재고 예약, 결제 완료, 취소, 재입고 시 동기화                                                |
| `PRODUCT`           | `status`        | 판매 상태 (`ON_SALE`, `SOLD_OUT`)         | `available_qty`가 0이면 `SOLD_OUT`, 재입고로 1 이상이면 `ON_SALE`                     |
| `INVENTORY_HISTORY` | `change_type`   | 재고 변동 유형                              | 재고 변동(`RESERVE`, `RELEASE`, `DECREASE`, `INCREASE`, `COMPENSATE`) 시 INSERT |


### 오버셀 방지 공식

```
available_qty = total_qty - reserved_qty
```

`available_qty`를 저장 컬럼으로 둘 경우 위 공식은 **DB CHECK 또는 도메인 invariant**로 검증한다. 구현에서 generated column/read model을 선택하면 저장 대신 계산값으로 노출할 수 있다.

> **주의:** 컬럼 분리만으로 오버셀이 막히지 **않는다**.  
> 재고 선점 시 낙관적 락(`version`)이나 **DB 비관락(`SELECT ... FOR UPDATE`)** 또는 **Redis 분산락**으로 동시성 충돌을 제어해야 한다.

---

## 2. ORDER — `status` 허용값 및 상태 성격 명시

### 설계 결정

`fan_id`로 팬과 연결한다. `status` 컬럼에 가능한 값과 각 상태의 **성격**을 ERD 레벨에서 명시한다.


| 상태          | 성격                    | 설명                                    |
| ----------- | --------------------- | ------------------------------------- |
| `PENDING`   | 일반                    | 주문 생성 직후, 재고 예약 전                     |
| `RESERVED`  | 일반                    | 재고 선점 완료, 결제 대기 중                     |
| `PAID`      | 일반                    | PG 결제 승인 확정 시점 기록                     |
| `FAILED`    | **Transient (경유 상태)** | 결제 실패 직후, Saga 보상 실행 전. **최종 상태가 아님** |
| `COMPLETED` | 최종                    | 재고 차감 및 알림 발행까지 모든 후처리 완료             |
| `CANCELLED` | 최종                    | 재고 부족 / 결제 실패 / 사용자 취소로 주문 종료         |


`idempotency_key`로 주문 생성 멱등을 보장한다.

### `FAILED`가 존재하는 이유

Saga 보상 트랜잭션의 **트리거 기준**이 되는 상태다. 보상이 완료되면 반드시 `CANCELLED`로 전이한다.

---

## 3. PAYMENT — `failed_at` 독립 컬럼 및 `payment_key` 멱등성

### 설계 결정 1 — `failed_at` 분리

`paid_at`과 `failed_at`을 **분리된 컬럼**으로 둔다. `ORDER`와 1:1.

### 설계 결정 2 — `payment_key` Unique Index

`payment_key` 컬럼에 **Unique Index**를 설정하여 중복 웹훅 수신 시 **멱등성**을 보장한다. API·DTO에서는 `tossPaymentKey`로 매핑한다.

```
시퀀스: 결제 요청 (with idempotent key)
        ↕ 연결
ERD:    PAYMENT.payment_key (Unique Index)
```


### 테이블 스키마

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 내부 식별자 |
| `order_id` | BIGINT | NOT NULL, FK → ORDER.id | 주문 참조 (1:1) |
| `payment_key` | VARCHAR | UNIQUE | `tossPaymentKey` 저장 — 멱등성 보장 (P-1) |
| `amount` | BIGINT | NOT NULL | 결제 금액 — confirm 시 PG 금액 대조용 |
| `method` | VARCHAR | | 결제수단 (토스 PG 반환 문자열, e.g. 카드) |
| `status` | VARCHAR | NOT NULL | `PENDING` / `SUCCESS` / `FAILED` |
| `paid_at` | TIMESTAMP | | SUCCESS 시 기록 (P-2) |
| `failed_at` | TIMESTAMP | | FAILED 시 기록 (P-3) |

| `status`  | 의미                          |
| --------- | --------------------------- |
| `PENDING` | 결제 세션 생성                    |
| `SUCCESS` | PG 승인 확정 (`paid_at` 기록)     |
| `FAILED`  | PG 거절·타임아웃 (`failed_at` 기록) |


---

## 4. AGENCY_ACCOUNT · ARTIST_PROFILE · ARTIST_MEMBER · FAN

> **용어 (`AGENCY_*`)**  
> DB·API 식별자(`AGENCY_ACCOUNT`, `agency_id`, `ROLE_AGENCY` 등)는 **그대로 유지**한다.  
> `**AGENCY_*`는 법인 기획사만이 아니라, 아티스트 공간을 운영하는 B2B 주체(기획사·1인 크리에이터·매니저)** 를 가리킨다.  
> 화면·IA·기능 명세 등 사용자에게 보이는 문서에서는 **아티스트 스튜디오**, **운영 주체**, **운영자** 등 상위 개념을 쓴다.

### 설계 결정

- `**FAN`**: `email`, `nickname`, `auth_provider`(`LOCAL`  `KAKAO`  `GOOGLE`), `provider_id`(소셜 시), `password_hash`(로컬 가입 시), `created_at`. 소셜 `providerToken`은 저장하지 않는다 ([mvp-api § Auth](../api/mvp-api-spec.md#auth--fan-계정)).
  - `**is_allow_notification` (신규 컬럼)**: 마이페이지 글로벌 알림(푸시 온/오프) 수신 설정을 저장하는 BOOLEAN 타입 컬럼.
- `**AGENCY_APPLICATION` (신규 테이블)**: 운영 주체의 플랫폼 입점 신청 정보를 영속화하기 위한 심사용 테이블.
  - `company_name`, `business_registration_number`, `representative_name`, `contact_email`, `contact_phone`, `introduction`, `target_artist_name`을 저장.
  - `applied_at`(신청 일시), `reviewed_at`(심사 완료 일시)로 심사 SLA·보관 기간([data-retention §2.6](./data-retention-and-audit-policy.md#26-커뮤니티--계정-정환철--표지민)) 산정.
  - `status` 기본값 `PENDING` 이며, 최종 승인(`APPROVED`) 시점에 `AGENCY_ACCOUNT` 로그인 계정과 `ARTIST_PROFILE`이 자동으로 개설됨. 반려 시 `reject_reason`을 필수로 기록.

### 입점 신청 (`AGENCY_APPLICATION`) — 운영 주체 유형별 필드 매핑

입점은 `**POST /b2b/apply` · `AGENCY_APPLICATION` · 승인 시 `AGENCY_ACCOUNT`** 한 경로로 처리한다. DB/API 식별자가 `Agency`/`AGENCY_*`여도 **법인 기획사·1인 크리에이터·매니저** 모두 동일 테이블·동일 API를 쓴다. 유형별 차이는 **화면 라벨·검증 규칙** 수준이며, MVP에서 `operator_type` 컬럼은 두지 않는다(Admin 심사·`introduction`·`target_artist_name`으로 구분).


| 필드 (API)                          | 법인 기획사           | 1인 크리에이터                            | 매니저               |
| --------------------------------- | ---------------- | ----------------------------------- | ----------------- |
| `company_name`                    | 법인·레이블명          | **활동명**(본인 브랜드)                     | **매니지먼트·대행사명**    |
| `representative_name`             | 대표자명             | **본인 실명**                           | 담당자·대표명           |
| `business_registration_number`    | 법인 사업자등록번호       | **개인사업자 번호** 권장(없으면 정책·Admin 예외 심사) | 소속 법인·매니지먼트 사업자번호 |
| `contact_email` / `contact_phone` | 담당자 연락처          | 본인 연락처                              | 담당자 연락처           |
| `introduction`                    | 회사·소속 아티스트 소개    | 활동·채널 소개                            | 대행 범위·소속 아티스트 소개  |
| `target_artist_name`              | 신규·기존 **그룹/유닛명** | 본인 활동명과 **동일해도 됨**                  | **소속 아티스트명**      |


**승인 후 매핑**


| 신청 필드               | 생성 엔티티                        | 비고                             |
| ------------------- | ----------------------------- | ------------------------------ |
| `company_name`      | `AGENCY_ACCOUNT.company_name` | 운영 주체 표시명(회사·활동명·매니지먼트명)       |
| (승인 시 Admin/시스템 입력) | `ARTIST_PROFILE.name`         | 보통 `target_artist_name`과 동일·유사 |
| —                   | `ARTIST_PROFILE.agency_id`    | 항상 승인된 `AGENCY_ACCOUNT.id` FK  |


`**business_registration_number` (MVP 정책)**

- **권장:** 입점 폼에서 **필수** — 정산·세무·분쟁 대비([data-lifecycle §3.6](./data-lifecycle.md#36-운영-주체-입점-신청-b2b)).
- **1인 크리에이터:** 안내 문구에 **「개인사업자 등록 번호 입력 가능」** 명시. 미등록 신청자는 `PENDING` 유지 후 Admin이 서류 보완 요청·반려·예외 승인.
- **구현:** ERD 컬럼 추가 없이 nullable 허용 + API/프론트 validation·Admin 심사 UI만 조정 가능. 유형별 분기가 필요해지면 이후 `operator_type` enum 추가를 검토한다.
- `**AGENCY_ACCOUNT`**: 운영 주체(B2B) 로그인 계정. `login_id`, `password_hash`, `company_name`(회사명·활동명·매니지먼트 명칭), `contact_email`, `status`, `invitation_token`, `token_expired_at`, `role` 기본 `ROLE_AGENCY`. 입점 심사 완료 후 생성되는 로그인/권한 계정.
- `**ARTIST_PROFILE`**: 아티스트 공간을 구성하는 앵커 엔티티 (`artist_id`).
  - `agency_id` FK → `AGENCY_ACCOUNT`.
  - `name`, `joined_at`.
  - `**fan_count` (성능 최적화 집계 필드)**: 스토어 아티스트 정렬 및 배너 노출 기준이 팬 수이기 때문에, 트래픽 집중 시 매번 COUNT 쿼리를 실행하는 성능 이슈를 예방하고자 추가. `USER_FOLLOW`의 INSERT/DELETE 트랜잭션 시점에 애플리케이션 레벨에서 값을 원자적으로 업데이트(+1 / -1)한다.
  - `**homepage_url`, `youtube_url`, `instagram_url`**: 프로필 탭의 외부 아웃링크를 저장하기 위한 컬럼.
  - `**profile_image_url`, `cover_image_url`, `bio`**: 프로필 탭에 노출할 소개 이미지와 소개글을 저장하기 위한 컬럼.
- `**ARTIST_MEMBER**`: `artist_id` FK, `login_id`, `password_hash`, `member_name`, `role` 기본 `ROLE_ARTIST`. **피드 작성 주체** (`artist_member_id`).
  - `**profile_image_url` (신규 컬럼)**: 개별 멤버(하니, 민지 등)의 프로필 이미지를 렌더링하기 위한 VARCHAR 타입 컬럼.

### 근거

B2B2C에서 운영 주체–아티스트–멤버 계층을 DB에 명시해야 커뮤니티(`ARTIST_FEED`)·커머스(`PRODUCT`)·굿즈 투표(`GOODS_VOTE`)가 동일한 `artist_id`로 묶인다.

---

## 5. 커뮤니티 — 피드, 이미지, 공지, 댓글, 좋아요

### 설계 결정


| 테이블               | 역할                                                                      |
| ----------------- | ----------------------------------------------------------------------- |
| `ARTIST_FEED`     | 멤버(`artist_member_id`)가 올리는 피드. `artist_id` 소속. `content`               |
| `FEED_IMAGE` (신규) | 피드에 첨부되는 다중 이미지 테이블 (1:N). `feed_id` FK, `image_url`                    |
| `ARTIST_NOTICE`   | 아티스트 공식 공지 및 일정 연동용. `title`, `content`, `image_urls`, `type` ("GENERAL |
| `COMMENT`         | 피드(`feed_id`)에 작성하는 댓글/대댓글. 아티스트/팬 공용. `parent_id` self FK              |
| `FEED_LIKE`       | 피드(`feed_id`) 좋아요. 아티스트/팬 공용                                            |
| `COMMENT_LIKE`    | 댓글(`comment_id`) 좋아요. 팬 전용                                              |


### 5.1. 피드 다중 이미지화 (`FEED_IMAGE` 신규)

- 기존 `ARTIST_FEED` 내 `image_urls VARCHAR` 단일 컬럼을 제거하고 `FEED_IMAGE` 테이블로 1:N 분리하였다.
- 단일 컬럼에 콤마(,) 등으로 여러 URL을 저장할 경우, 이미지의 순서 관리 및 개별 수정/삭제가 어렵기 때문이다.
- **표시 순서 제어:** 등록 순서가 노출 순서이므로 `created_at` 컬럼으로 정렬하여 표시 순서를 제어한다.

### 5.2. N+1 문제 해결을 위한 집계 컬럼 도입

- `ARTIST_FEED` 테이블에 `like_count`, `comment_count` 집계 컬럼을 추가했다.
- 목록 조회 시 피드마다 댓글 수와 좋아요 수를 세기 위해 COUNT 쿼리를 날릴 경우 발생하는 N+1 쿼리 성능 문제를 방지한다.
- 댓글/좋아요 등록 및 삭제 시점에 해당 피드의 카운트를 애플리케이션 레벨에서 업데이트 트랜잭션 처리한다.

### 5.3. COMMENT (댓글) 구조 변경 및 제약조건

- **작성자 다형성 지원 (`fan_id` Nullable, `artist_member_id` FK Nullable 추가):** 아티스트도 답글을 달 수 있도록 변경되었다.
- `**artist_id` FK 추가:** 마이페이지 `내가 남긴 댓글 히스토리` 조회 시 복잡한 Join(`COMMENT -> ARTIST_FEED -> ARTIST_PROFILE`)을 타지 않고 직접 아티스트 프로필 조회가 가능하도록 비효율을 개선했다.
- **제약조건 (DB 레벨 & 애플리케이션 검증):**
  - **작성자 필수 제약 (DB CHECK 제약 권장):** `fan_id IS NOT NULL OR artist_member_id IS NOT NULL` (팬과 아티스트 멤버 중 최소 하나는 값이 있어야 하고, 두 필드가 동시에 값을 가질 수 없다).
  - **아티스트 답글 제약 (DB CHECK 제약 권장):** 아티스트는 최상위 댓글을 쓸 수 없고 오직 답글만 가능하므로 `artist_member_id IS NOT NULL` 일 경우 반드시 `parent_id IS NOT NULL` 이어야 한다.

### 5.4. FEED_LIKE (좋아요) 구조 변경 및 중복 방지 제약조건

- **작성자 다형성 및 최적화:** 아티스트도 좋아요를 누를 수 있도록 `fan_id` Nullable 변경 및 `artist_member_id` FK, `artist_id` FK를 추가하였다.
- **동시성 및 중복 요청 방지 제약조건 (DB UNIQUE Index 필수):**
  - 하나의 피드에 팬/아티스트가 중복으로 좋아요를 insert 하는 것을 DB 단에서 확실히 차단하기 위해 유니크 제약을 지정한다.
  - `**UNIQUE(fan_id, feed_id)`**
  - `**UNIQUE(artist_member_id, feed_id)`**

### 5.5. ARTIST_NOTICE (공지)와 일정 자동 연동

- `type` 컬럼(`GENERAL | LIVE | EVENT | DROP`)이 추가되었다.
- 운영자(아티스트 스튜디오)가 공지 등록 시 `type`을 `LIVE`, `EVENT`, `DROP` 중 하나로 설정할 경우, 서버 비즈니스 로직에 의해 아래 `ARTIST_SCHEDULE`에 데이터가 자동으로 INSERT되어 일정 탭에도 노출되도록 구현한다.

---

## 6. CART · CART_ITEM

### 설계 결정

- **저장소: MySQL RDB (Phase 1)** — Redis-only 장바구니는 채택하지 않음. 근거·Phase 2 전환 조건: [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md)
- `FAN` ↔ `CART` **1:1** — `CART`: `fan_id`, `created_at`, `updated_at`
- `CART_ITEM`: `cart_id`, `product_id`, `quantity`, `added_at`. UK 권장: `(cart_id, product_id)`
- 주문 생성(`POST /orders`) 시 `ORDER` + `ORDER_ITEM` + `INVENTORY.reserve()`는 **단일 TX**로 정합성 보장. 장바구니는 주문 **전** 임시 목록이며, 주문 성공 후 `cart_item` 삭제·수량 반영은 애플리케이션 정책(동일 TX 또는 직후)으로 처리한다.

### Redis와의 구분


| 저장                           | 용도                                                                         |
| ---------------------------- | -------------------------------------------------------------------------- |
| **RDB `CART` / `CART_ITEM`** | 로그인 팬 장바구니 영속 (담기·수량 변경·조회)                                                |
| **Redis**                    | 드롭스 **대기열**·Read 캐시 등 — [§10](#10-드롭스-대기열--redis-db-erd-미포함) · **장바구니 아님** |


---

## 7. USER_FOLLOW


| 테이블           | 설계 포인트                                                                                                                                        |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `USER_FOLLOW` | 팬(`fan_id`)이 아티스트(`artist_id`)를 팔로우. `followed_at`. **팬 가입(F01-04)** 의 DB 표현 — 커뮤니티 쓰기(댓글·좋아요)·굿즈 투표·구매 전 선행 조건. UK 권장: `(fan_id, artist_id)` |


---

## 8. ARTIST_SCHEDULE (일정 단일화 및 공지 연동)

- 기존에 이중 설계되었던 `SCHEDULE`과 `ARTIST_SCHEDULE`을 통합하여 `**ARTIST_SCHEDULE` 단일 테이블**로 일정을 관리한다.
- `notice_id` FK를 추가해 `ARTIST_NOTICE`에서 자동 발행된 일정임을 표시한다.


| 테이블               | `type` 허용값 | 용도     |
| ----------------- | ---------- | ------ |
| `ARTIST_SCHEDULE` | `DROP`     | `LIVE` |


### 알림 트리거 연동

- 일정이 등록되면 팬 알림 트리거(`NOTIFICATION.notification_type = ARTIST_SCHEDULE`)가 작동하여 팬 알림함으로 알림이 발송된다.

---

## 9. BANNER · RESTOCK_ALERT · NOTIFICATION (팬 알림함)

### `BANNER`

`banner_type`: `MAIN`  `STORE`. `product_id` FK(스토어 배너), `title`, `image_url`, `landing_url`, `exposure_order`, `is_active`, `start_at` / `end_at`.


| 종류        | 담당 (Admin CRUD · Read)                            |
| --------- | ------------------------------------------------- |
| **MAIN**  | `user` (표지민) — GNB 홈 메인 배너·아티스트·이벤트 홍보 (F04-03)   |
| **STORE** | `order` (형성빈) — 상품·드롭스 프로모션 (`banner_type=STORE`) |


비노출은 `**is_active=false`** (ERD에 `deleted_at` 없음).

### `RESTOCK_ALERT`

`fan_id` + `product_id` 구독. ERD `status`: `PENDING`  `SENT`  `CANCELLED` — [상태 머신 §7.2](../state/invariants-and-state-machines.md#72-restock_alert).

### `NOTIFICATION`

팬 **알림함**. `notification_id` PK, `fan_id` FK(수신 팬 ID), `notification_type`(`NEW_FEED`  `NEW_COMMENT`  `RESTOCK`  `ARTIST_SCHEDULE`), `target_id`, `message`, `is_read`(기본 `false`), `sent_at`.

- 전송 파이프라인 `status`는 **Outbox** 책임([§11](#11-알림--notification-vs-outbox)).
- API 목록은 `is_read`·`sentAt` 반환 — [mvp-api § 알림](../api/mvp-api-spec.md#notification-알림).

---

## 10. 드롭스 대기열 — Redis (DB ERD 미포함)

### 설계 결정

대기열 **행은 RDB ERD에 두지 않는다**. 드롭스 상품 오픈 트래픽 흡수·Access Ticket은 **Redis**에 `product_id` 단일 키 기준으로 저장한다.

### 근거 (기존 §4 WAIT_QUEUE DB안 폐기)


| 검토 항목 | 내용                                |
| ----- | --------------------------------- |
| 트래픽   | 대기열은 고빈도·단기 TTL 데이터               |
| MVP   | `product_id`만 참조 (EVENT 인앱 결제 없음) |
| 정합    | 주문 성공 여부는 `**ORDER.status`만** 본다  |


### 허용 상태 (Redis / API)

```
WAITING | PROCESSING | DONE | EXPIRED
```


| 값            | 의미                        |
| ------------ | ------------------------- |
| `WAITING`    | 대기열 진입                    |
| `PROCESSING` | Access Ticket 발급, 주문 진행 중 |
| `DONE`       | 대기열 흐름 종료 (주문 성공 여부와 무관)  |
| `EXPIRED`    | 토큰 만료                     |


상세 전이·불변조건: [invariants §6](../state/invariants-and-state-machines.md#6-wait_queue-상태-머신).

---

## 11. 알림 — `NOTIFICATION` vs Outbox

### 설계 결정


| 계층         | 저장소                                                                       | 역할                                                       |
| ---------- | ------------------------------------------------------------------------- | -------------------------------------------------------- |
| **발행·재시도** | `outbox_events` (인프라, [ADR-001](../adr/ADR-001-multi-module-monolith.md)) | `PENDING` → 발행 → `published_at`. 실패 시 `retry_count`, DLQ |
| **팬 조회**   | `NOTIFICATION` (본 ERD)                                                    | 전송 완료 후 팬 알림함에 남는 **최종 기록** (`is_read` 갱신)               |


`NOTIFICATION` 행에는 Outbox 파이프라인 `status`를 두지 않는다. 재시도·`FAILED` 추적은 **Outbox** 책임.

### 권고 Outbox 필드 (ERD PNG 외)


| 컬럼               | 이유                                 |
| ---------------- | ---------------------------------- |
| `payload` (json) | 재시도 시 스냅샷                          |
| `status`         | `PENDING` / `PUBLISHED` / `FAILED` |
| `retry_count`    | DLQ 기준                             |


---

## 12. 출석 체크 — `ATTENDANCE_EVENT` · `ATTENDANCE_LOG` (신규)

### 설계 결정

- `**ATTENDANCE_EVENT`**: 아티스트별 출석체크 이벤트를 정의하는 테이블.
  - `artist_id` FK, `start_date`, `end_date`, `reward_desc`, `is_active`를 포함한다.
  - **애플리케이션 검증:** 현재 날짜가 `start_date`와 `end_date` 사이에 있는지 확인하고, `is_active=false`인 경우 출석 체크 요청을 차단한다.
- `**ATTENDANCE_LOG`**: 특정 출석체크 이벤트에 대한 팬의 일자별 출석 기록을 저장한다.
  - `event_id` FK, `fan_id` FK, `checked_date`.
  - **중복 출석 방지 제약조건 (DB UNIQUE Index 필수):** 하루에 중복 출석 처리가 되는 동시성 이슈를 물리적으로 완전히 차단하기 위해 복합 유니크 제약을 설정한다.
    - `**UNIQUE(event_id, fan_id, checked_date)`**
  - **7일 달성 여부 산정:** 로그에 쌓인 데이터 중 동일 `event_id`와 `fan_id`를 조건으로 COUNT 쿼리를 실행하여 7일 이상 출석 여부를 판단한다.

---

## 13. 굿즈 투표 — `GOODS_VOTE` · `GOODS_VOTE_OPTION` · `GOODS_VOTE_RECORD` (신규)

### 설계 결정

- `**GOODS_VOTE`**: 굿즈 디자인/콘셉트 이미지 선택 투표를 생성하고 마감 기한을 관리한다.
  - **개설 권한:** `POST /artists/{id}/goods-votes`는 `**ROLE_AGENCY` 운영 계정**(아티스트 스튜디오)만. `ARTIST_MEMBER`는 피드·댓글 등 콘텐츠 작성용.
  - `artist_id` FK, `title`, `ends_at`, `is_active`.
  - **애플리케이션 검증:** 투표 요청 시 `ends_at`이 지나지 않았는지, `is_active=true` 상태인지를 체크하여 비정상 투표를 제한한다.
- `**GOODS_VOTE_OPTION`**: 투표에 등록된 이미지 및 텍스트 선택지 정보와 득표 현황을 관리한다.
  - `vote_id` FK, `label`, `image_url`.
  - `**vote_count` (성능 최적화 집계 필드):** 투표 마감 직전에 유저가 동시에 몰려와 COUNT 쿼리로 실시간 득표수를 계산하면 DB 부하가 치명적이므로 집계 컬럼을 도입한다. 투표 기록 생성 트랜잭션 시점에 해당 옵션 행의 `vote_count`를 원자적으로 증가(`UPDATE GOODS_VOTE_OPTION SET vote_count = vote_count + 1 WHERE id = :option_id`) 시킨다.
- `**GOODS_VOTE_RECORD`**: 1인 1투표 검증 및 투표 이력을 영속화한다.
  - `vote_id` FK, `option_id` FK, `fan_id` FK.
  - **1인 1투표 보장 제약조건 (DB UNIQUE Index 필수):** 한 팬이 한 투표에 여러 번 중복 투표하는 것을 DB 수준에서 방어하기 위해 유니크 제약을 지정한다.
    - `**UNIQUE(vote_id, fan_id)`**

---

## 관련 문서


| 문서                  | 경로                                                                                       |
| ------------------- | ---------------------------------------------------------------------------------------- |
| ERD 다이어그램 (Mermaid) | `[erd.md](./erd.md)`                                                                     |
| ERD 이미지             | `[erd.png](./erd.png)`                                                                   |
| 데이터 보관 · Audit      | `[data-retention-and-audit-policy.md](./data-retention-and-audit-policy.md)`             |
| 데이터 라이프사이클          | `[data-lifecycle.md](./data-lifecycle.md)`                                               |
| 멀티모듈 모놀리스 (ADR)     | `[../adr/ADR-001-multi-module-monolith.md](../adr/ADR-001-multi-module-monolith.md)`     |
| 장바구니 저장소 (ADR)      | `[../adr/ADR-003-cart-storage-rdb-phase1.md](../adr/ADR-003-cart-storage-rdb-phase1.md)` |
| 불변조건 · 상태 머신        | `[../state/invariants-and-state-machines.md](../state/invariants-and-state-machines.md)` |
| 결제·주문 시퀀스           | `[../sequence/payment-flow-reason.md](../sequence/payment-flow-reason.md)`               |


