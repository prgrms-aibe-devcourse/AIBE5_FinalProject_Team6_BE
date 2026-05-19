# ERD 설계 문서

> **다이어그램:** [`erd.png`](./erd.png)  
> K-Pop 팬덤 **B2B2C** 플랫폼의 테이블 관계·컬럼 설계 근거. 핫딜·결제·커뮤니티·아티스트 운영 도메인을 포함한다.

---

## 0. 엔티티 개요

| 도메인 | 테이블 | 비고 |
| --- | --- | --- |
| **사용자·아티스트** | `FAN`, `PARTNER`, `ARTIST`, `ARTIST_MEMBER`, `FAN_ARTIST` | 팬(B2C) · 기획사(B2B) · 아티스트·멤버 |
| **커뮤니티** | `ARTIST_SPACE`, `FEED`, `NOTICE`, `COMMENT`, `HEART` | 아티스트 공간 · 피드·공지 · 댓글·하트 |
| **커머스** | `PRODUCT`, `INVENTORY`, `CART`, `CART_ITEM`, `ORDER`, `ORDER_ITEM`, `PAYMENT`, `RESTOCK_ALERT` | 상품·재고·**장바구니(RDB)** ·주문·결제 — [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) |
| **랭킹·일정** | `VOTE`, `IDOL_RANKING`, `SCHEDULE`, `ARTIST_SCHEDULE` | 월간 투표 · 드롭·라이브·행사 |
| **운영·알림** | `BANNER`, `NOTIFICATION` | 홈 배너 · 팬 알림함 |

**DB ERD에 없고 별도 저장하는 것**

| 기능 | 저장 | 문서 |
| --- | --- | --- |
| 핫딜 대기열 (F08-01) | **Redis** (상태·토큰) | [§10](#10-핫딜-대기열--redis-db-erd-미포함) · [상태 머신 §6](../state/invariants-and-state-machines.md#6-wait_queue-상태-머신) |
| 알림 발행·재시도 | **`outbox_events`** (ADR Outbox) | [§11](#11-알림--notification-vs-outbox) · [ADR-001](../adr/ADR-001-multi-module-monolith.md) |
| PG 웹훅 원본 | `payment_webhook_events` (보관 정책) | [data-retention §2.2](./data-retention-and-audit-policy.md#22-결제웹훅-장성재) |
| Audit | `audit_logs` | [data-retention §3](./data-retention-and-audit-policy.md#3-audit--무엇을-남길지) |

---

## 1. PRODUCT — `reserved_quantity` 분리

### 설계 결정

`stock_quantity`와 `reserved_quantity`를 **별도 컬럼**으로 분리한다. `PRODUCT`는 `artist_id`로 아티스트에 소속된다.

### 근거

핫딜 오픈 순간 수천 명이 동시에 결제를 시도하는 환경에서, 결제 진행 중인 재고와 실제 판매 가능한 재고를 구분하지 않으면 **오버셀**이 발생한다.

| 컬럼 | 의미 | 변경 시점 |
| --- | --- | --- |
| `stock_quantity` | 실제 판매 완료 후 남은 재고 | 결제 최종 확정(`PAID` → `COMPLETED`) 시 차감 |
| `reserved_quantity` | 결제 진행 중 선점된 재고 | 재고 예약(lock) 시 증가, 결제 완료/취소 시 감소 |
| `hotdeal_start_at` / `hotdeal_end_at` | 핫딜 노출·가격 적용 구간 | Admin·아티스트 상품 등록 시 설정 |

### 오버셀 방지 공식

```
판매 가능 재고 = stock_quantity - reserved_quantity
```

> **주의:** 컬럼 분리만으로 오버셀이 막히지 **않는다**.  
> 재고 선점 시 **DB 비관락(`SELECT ... FOR UPDATE`)** 또는 **Redis 분산락**으로 동시성 충돌을 제어해야 한다.

---

## 2. ORDER — `status` 허용값 및 상태 성격 명시

### 설계 결정

`fan_id`로 팬과 연결한다. `status` 컬럼에 가능한 값과 각 상태의 **성격**을 ERD 레벨에서 명시한다.

| 상태 | 성격 | 설명 |
| --- | --- | --- |
| `PENDING` | 일반 | 주문 생성 직후, 재고 예약 전 |
| `RESERVED` | 일반 | 재고 선점 완료, 결제 대기 중 |
| `PAID` | 일반 | PG 결제 승인 확정 시점 기록 |
| `FAILED` | **Transient (경유 상태)** | 결제 실패 직후, Saga 보상 실행 전. **최종 상태가 아님** |
| `COMPLETED` | 최종 | 재고 차감 및 알림 발행까지 모든 후처리 완료 |
| `CANCELLED` | 최종 | 재고 부족 / 결제 실패 / 사용자 취소로 주문 종료 |

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

| `status` | 의미 |
| --- | --- |
| `PENDING` | 결제 세션 생성 |
| `SUCCESS` | PG 승인 확정 (`paid_at` 기록) |
| `FAILED` | PG 거절·타임아웃 (`failed_at` 기록) |

---

## 4. PARTNER · ARTIST · ARTIST_MEMBER · FAN

### 설계 결정

- **`FAN`**: `email`, `nickname`, `created_at`만 저장. **비밀번호 컬럼 없음** — MVP 인증은 소셜 OAuth ([mvp-api § Auth](../api/mvp-api-spec.md#auth--fan-계정)).
- **`PARTNER`**: 기획사(B2B). `login_id`, `password`, `company_name`, `contact_email`, `status`, `invitation_token`, `token_expired_at`. `status` 예: `PENDING` \| `APPROVED` \| `REJECTED` (Admin 입점 API는 `PARTNER` 행 대상).
- **`ARTIST`**: `partner_id` FK, `name`, `joined_at`. 굿즈·일정·랭킹의 **앵커 엔티티**.
- **`ARTIST_MEMBER`**: `login_id`, `password`, `member_name`, `role` 기본값 `ROLE_ARTIST`. **피드·공지 작성 주체**.

### 근거

B2B2C에서 기획사-아티스트-멤버 계층을 DB에 명시해야 커뮤니티(`ARTIST_SPACE`)·커머스(`PRODUCT`)·투표(`VOTE`)가 동일한 `artist_id`로 묶인다.

---

## 5. 커뮤니티 — `ARTIST_SPACE`, `FEED`, `NOTICE`, `COMMENT`, `HEART`

### 설계 결정

| 테이블 | 역할 |
| --- | --- |
| `ARTIST_SPACE` | 아티스트당 커뮤니티 허브 (`status`로 개설·운영 상태) |
| `FEED` | 멤버(`artist_member_id`)가 올리는 피드. `artist_space_id` 소속 |
| `NOTICE` | 공식 공지 (제목·본문·`image_urls`) |
| `COMMENT` | 팬(`fan_id`)이 피드에 작성. `parent_id`로 **대댓글** (self FK) |
| `HEART` | `target_type` = `FEED` \| `COMMENT`, `target_id` — **다형 좋아요** |

### 근거

피드·댓글·반응을 `community` 모듈 단일 바운디드 컨텍스트로 구현한다. [architecture § user vs community](../architecture/architecture.md#user-vs-community--왜-나뉘는가)

---

## 6. CART · CART_ITEM

### 설계 결정

- **저장소: MySQL RDB (Phase 1)** — Redis-only 장바구니는 채택하지 않음. 근거·Phase 2 전환 조건: [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md)
- `FAN` ↔ `CART` **1:1** — `CART`: `fan_id`, `created_at`, `updated_at`
- `CART_ITEM`: `cart_id`, `product_id`, `quantity`, `added_at`. UK 권장: `(cart_id, product_id)`
- 주문 생성(`POST /orders`) 시 `ORDER` + `ORDER_ITEM` + `INVENTORY.reserve()`는 **단일 TX**로 정합성 보장. 장바구니는 주문 **전** 임시 목록이며, 주문 성공 후 `cart_item` 삭제·수량 반영은 애플리케이션 정책(동일 TX 또는 직후)으로 처리한다.

### Redis와의 구분

| 저장 | 용도 |
| --- | --- |
| **RDB `CART` / `CART_ITEM`** | 로그인 팬 장바구니 영속 (담기·수량 변경·조회) |
| **Redis** | 핫딜 **대기열**·Read 캐시·랭킹 등 — [§10](#10-핫딜-대기열--redis-db-erd-미포함) · **장바구니 아님** |

---

## 7. FAN_ARTIST · VOTE · IDOL_RANKING

| 테이블 | 설계 포인트 |
| --- | --- |
| `FAN_ARTIST` | 팬의 아티스트 팔로우 (`followed_at`) |
| `VOTE` | 팬·아티스트·`round`·`month` 단위 투표 기록 (중복 방지는 앱·UK로 보장) |
| `IDOL_RANKING` | 아티스트별 `vote_count` 집계 (`round`, `month`) |

---

## 8. SCHEDULE · ARTIST_SCHEDULE

| 테이블 | `type` 예시 | 용도 |
| --- | --- | --- |
| `SCHEDULE` | `DROP` \| `EVENT` \| `LIVE` | 팬 알림 트리거 (`NOTIFICATION` 연계) |
| `ARTIST_SCHEDULE` | `DROP` \| `LIVE` \| `EVENT` \| `NOTICE` | 아티스트·운영 캘린더 등록 |

### MVP — 인앱 좌석 예약 Not Scope

콘서트·팬미팅 **인앱 결제·좌석 DB는 없음**. `EVENT` 타입 일정은 **외부 티켓 URL** 노출만 한다. Phase 2에서 `reservations` / `seats` 검토 — [data-retention §2.4](./data-retention-and-audit-policy.md#24-예약--좌석--phase-2-not-scope-mvp).

---

## 9. BANNER · RESTOCK_ALERT · NOTIFICATION (팬 알림함)

### `BANNER`

홈 노출용. `exposure_order`, `is_active`, `start_at` / `end_at`로 기간·순서 제어. 비노출은 **`is_active=false`** (ERD에 `deleted_at` 없음). Admin CRUD는 `user` 모듈.

### `RESTOCK_ALERT`

`fan_id` + `product_id` 구독. ERD `status`는 `string` — 앱 허용값: `ACTIVE` \| `SENT` ([상태 머신 §7.2](../state/invariants-and-state-machines.md#72-restock_alert)).

### `NOTIFICATION`

팬 **알림함** (`fan_id`, `type`, `title`, `message`, `sent_at`). `SCHEDULE` 등 이벤트 처리 후 INSERT. **읽음(`is_read` / `read_at`) 컬럼 없음** — 목록 API도 `sentAt`만 반환.

---

## 10. 핫딜 대기열 — Redis (DB ERD 미포함)

### 설계 결정

대기열 **행은 RDB ERD에 두지 않는다**. F08-01 핫딜 트래픽 흡수·Access Ticket은 **Redis**에 `product_id` 단일 키 기준으로 저장한다.

### 근거 (기존 §4 WAIT_QUEUE DB안 폐기)

| 검토 항목 | 내용 |
| --- | --- |
| 트래픽 | 대기열은 고빈도·단기 TTL 데이터 |
| MVP | `product_id`만 참조 (EVENT 인앱 결제 없음) |
| 정합 | 주문 성공 여부는 **`ORDER.status`만** 본다 |

### 허용 상태 (Redis / API)

```
WAITING | PROCESSING | DONE | EXPIRED
```

| 값 | 의미 |
| --- | --- |
| `WAITING` | 대기열 진입 |
| `PROCESSING` | Access Ticket 발급, 주문 진행 중 |
| `DONE` | 대기열 흐름 종료 (주문 성공 여부와 무관) |
| `EXPIRED` | 토큰 만료 |

상세 전이·불변조건: [invariants §6](../state/invariants-and-state-machines.md#6-wait_queue-상태-머신).

---

## 11. 알림 — `NOTIFICATION` vs Outbox

### 설계 결정

| 계층 | 저장소 | 역할 |
| --- | --- | --- |
| **발행·재시도** | `outbox_events` (인프라, [ADR-001](../adr/ADR-001-multi-module-monolith.md)) | `PENDING` → 발행 → `published_at`. 실패 시 `retry_count`, DLQ |
| **팬 조회** | `NOTIFICATION` (본 ERD) | 전송 완료 후 팬 알림함에 남는 **최종 기록** |

`NOTIFICATION` 행에는 전송 파이프라인 `status`를 두지 않는다. 재시도·`FAILED` 추적은 **Outbox** 책임.

### 권고 Outbox 필드 (ERD PNG 외)

| 컬럼 | 이유 |
| --- | --- |
| `payload` (json) | 재시도 시 스냅샷 |
| `status` | `PENDING` / `PUBLISHED` / `FAILED` |
| `retry_count` | DLQ 기준 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| ERD 다이어그램 | [`erd.png`](./erd.png) |
| 데이터 보관 · Audit | [`data-retention-and-audit-policy.md`](./data-retention-and-audit-policy.md) |
| 데이터 라이프사이클 | [`data-lifecycle.md`](./data-lifecycle.md) |
| 멀티모듈 모놀리스 (ADR) | [`../adr/ADR-001-multi-module-monolith.md`](../adr/ADR-001-multi-module-monolith.md) |
| 장바구니 저장소 (ADR) | [`../adr/ADR-003-cart-storage-rdb-phase1.md`](../adr/ADR-003-cart-storage-rdb-phase1.md) |
| 불변조건 · 상태 머신 | [`../state/invariants-and-state-machines.md`](../state/invariants-and-state-machines.md) |
| 결제·주문 시퀀스 | [`../sequence/payment-flow-reason.md`](../sequence/payment-flow-reason.md) |
