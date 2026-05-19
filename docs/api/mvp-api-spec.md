# FANDROPS MVP 백엔드 API 명세서

K-Pop 팬덤 커머스 / 예약 플랫폼 — **MVP 핵심 도메인** HTTP API

> **관련 문서:** [API 계약](./api-contract.md) · [ERD](../erd/erd-design.md) · [결제·주문 시퀀스](../sequence/payment-flow-reason.md) · [불변조건·상태 머신](../state/invariants-and-state-machines.md) · [장애 정책](../operations/failure-policy.md) · [아키텍처](../architecture/architecture.md)

---

## 문서 범위

| 포함 (본 명세) | MVP 이후·별도 명세 예정 |
| --- | --- |
| Auth, Fan, 대기열, 상품·주문·결제, 재고 Internal, 알림, Admin(입점·배너·모니터링) | **피드·댓글·랭킹·하트** (`community` — 정환철) |
| Artist·Event·Calendar·Live (콘텐츠·행사 축) | 장바구니 다건, 쿠폰, 정산 등 Commerce 확장 |

Gradle 모듈·담당자 매핑은 [architecture.md § 도메인 오너십](../architecture/architecture.md#도메인-오너십--모듈-매핑)을 따른다.

---

## 공통 규칙

| 항목 | 값 |
| --- | --- |
| Base URL | `/api/v1` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 응답 envelope · 페이지네이션 · `retryable` | **[api-contract.md](./api-contract.md)** (SSOT) |

- `traceId`: 요청 단위 추적 ID — 로그·[audit](../erd/data-retention-and-audit-policy.md)·[장애 대응](../operations/incident-response.md)와 동일.
- Internal 경로(`/internal/**`): **동일 JVM 포트 호출**. MSA 전환 시 gRPC/REST Internal로 교체.

---

## API → Gradle 모듈 (구현 위치)

| 접두 경로 / 영역 | 모듈 | 담당 |
| --- | --- | --- |
| `/auth/*`, `/fans/me` (계정), `/queue/*`, `/admin/artist-applications`, `/admin/banners` | `user` | 표지민 |
| `/artists/*`, `/lives/*`, 행사·일정 | `community` | 정환철 |
| `/products/*`, `/orders/*`, `/fans/me/orders` | `order` · `inventory` | 형성빈 |
| `/payments/*`, `/fans/me/payments/*` | `payment` | 장성재 |
| `/internal/inventory/*` | `inventory` (포트) | 형성빈 |
| `/internal/notifications/publish`, `/fans/me/notifications`, `/notifications/*` | `notification` | 표지민 (전송) |
| `/admin/monitoring` | `api-server` + 관측 스택 | 지영재 |

---

## Auth / Fan 계정

`user-api` · 담당: **표지민**

> `FAN` ERD 컬럼: `email`, `nickname`, `created_at`만 — **`password` 없음** ([ERD §4](../erd/erd-design.md#4-partner--artist--artist_member)). MVP 팬 가입·로그인은 **소셜 OAuth**만.

| Method | Endpoint | 설명 | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/auth/social/{provider}` | 소셜 로그인·가입 (`kakao` · `google`) | `providerToken` | `{ accessToken, refreshToken }` — `FAN` 행 upsert |
| POST | `/auth/logout` | 로그아웃 (토큰 무효화) | — | `204 No Content` |
| POST | `/auth/token/refresh` | Access Token 재발급 | `refreshToken` | `{ accessToken, expiresIn }` |
| GET | `/fans/me` | 내 정보 조회 | — | `{ fanId, email, nickname, createdAt }` |
| PUT | `/fans/me` | 내 정보 수정 | `nickname` (optional) | `{ fanId, nickname }` |

---

## Artist / Event

`community-api` · 담당: **정환철**

| Method | Endpoint | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- |
| GET | `/artists` | 아티스트 목록 조회 | `?cursor`, `size` | `{ items: [...], nextCursor }` |
| GET | `/artists/{id}` | 아티스트 상세 | — | `{ id, partnerId, name, joinedAt }` |
| POST | `/artists` | 아티스트 등록 (Admin) | `partnerId`, `name` | `201` `{ artistId }` |
| GET | `/artists/{id}/calendar` | 드롭·팬미팅·라이브 통합 일정 | `?from`, `to` | `{ events: [{ type, title, startTime }] }` |
| POST | `/artists/{id}/events` | 행사 등록 (운영자) | `title`, `type`, `startTime`, `externalTicketUrl` | `201` `{ eventId }` |
| PATCH | `/lives/{id}/start` | 라이브 시작 (상태 갱신 + 알림 이벤트 발행) | — | `{ liveId, isLive: true }` |

`PATCH /lives/{id}/start` 성공 시 `notification`에 `LIVE_START` 이벤트 발행 → 표지민 모듈이 전송.

---

## Product / Hotdeal / Restock

`order-api` · `inventory-*` · 담당: **형성빈**

| Method | Endpoint | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- |
| GET | `/products` | 상품 목록 조회 | `?type=hotdeal`, `cursor`, `size` | `{ items: [{ id, artistId, name, price, hotdealStartAt, hotdealEndAt, stockQuantity, reservedQuantity, updatedAt }], nextCursor }` |
| GET | `/products/{id}` | 상품 상세 | — | `{ id, artistId, name, price, hotdealStartAt, hotdealEndAt, stockQuantity, reservedQuantity, updatedAt }` |
| POST | `/products` | 상품 등록 (운영자) | `artistId`, `name`, `price`, `stockQuantity`, `hotdealStartAt`, `hotdealEndAt` (optional) | `201` `{ productId }` |
| POST | `/products/{id}/restock-subscribe` | 재입고 알림 구독 | — | `201` `{ alertId }` |
| DELETE | `/products/{id}/restock-subscribe` | 구독 취소 | — | `204 No Content` |
| POST | `/products/{id}/restock` | 재입고 처리 + 이벤트 발행 (운영자) | `quantity` | `{ productId, stockQuantity }` |

재입고 시 `RESTOCK_ALERT` 이벤트 발행 → `notification` 전송 (표지민).

- `?type=hotdeal`: `hotdeal_start_at ≤ now ≤ hotdeal_end_at` 인 행만 필터 ([ERD `PRODUCT`](../erd/erd-design.md#1-product--reserved_quantity-분리)).
- `stockQuantity` / `reservedQuantity`: `INVENTORY` 조인.

---

## Wait Queue (대기열)

`user-api` · 담당: **표지민**

| Method | Endpoint | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- |
| POST | `/queue/join/{productId}` | 대기열 등록 | — | `{ queueId, position, status: WAITING }` |
| GET | `/queue/status` | 현재 순번 조회 (Polling) | `?productId` | `{ position, status, estimatedWaitSec }` |
| GET | `/queue/stream/{productId}` | 순번 실시간 안내 (SSE) | — | `text/event-stream` `{ position, status }` |
| DELETE | `/queue/exit/{productId}` | 대기열 이탈 | — | `204 No Content` |

### Access Ticket (진입 토큰)

- `WaitQueueService`가 `PROCESSING` 진입 시 **일회용 Access Ticket** 발급.
- 이후 `POST /orders` 요청 body에 `accessTicket` 포함 **필수**.
- **검증 위치:** `OrderService` (우회 호출 차단). [시퀀스 문서 §1](../sequence/payment-flow-reason.md#1-대기열-토큰-검증을-orderservice에서-수행) 참고.
- 토큰 없음·만료 → `403` + `ERR_4003` (`INVALID_QUEUE_TICKET`).

---

## Order / Payment

### 결제 식별자 (`orderPaymentKey` / `tossPaymentKey`)

API·DTO에서는 아래 이름을 **고정**한다. (`paymentKey` 단일 필드명 사용 금지)

| 필드명 | 출처 | 용도 | 저장 |
| --- | --- | --- | --- |
| **`orderPaymentKey`** | `POST /orders` 응답 (서버 발급) | 주문·결제 세션 상관관계, 클라이언트가 결제창 진입 전 보관 | `ORDER` 또는 `PAYMENT` 행의 서버 측 키 (구현 시 `order_payment_key` 등) |
| **`tossPaymentKey`** | 토스 결제위젯/SDK (PG 발급) | `POST /payments/toss/confirm` 요청, 웹훅 멱등 | DB `PAYMENT.payment_key` (Unique) — [ERD §3](../erd/erd-design.md#3-payment--failed_at-독립-컬럼-및-payment_key-멱등성) |

```
Fan → POST /orders          → orderPaymentKey 수신
Fan → 토스 결제창            → tossPaymentKey 수신 (PG)
Fan → POST .../confirm      → tossPaymentKey + orderId + amount
PG  → POST .../webhook      → payload 내 키 → tossPaymentKey로 매핑 후 멱등 처리
```

---

| Method | Endpoint | 모듈 | 담당 | 설명 |
| --- | --- | --- | --- | --- |
| POST | `/orders` | `order` | 형성빈 | 주문 생성 + 재고 예약 (단일 트랜잭션) |
| GET | `/orders/{id}` | `order` | 형성빈 | 주문 상세 |
| GET | `/fans/me/orders` | `order` | 형성빈 | 내 주문 목록 |
| DELETE | `/orders/{id}` | `order` | 형성빈 | 주문 취소 (사용자) |
| POST | `/payments/toss/confirm` | `payment` | 장성재 | 결제창 승인 (클라이언트 → 서버 → 토스) |
| POST | `/payments/webhook` | `payment` | 장성재 | 토스 웹훅 (PG → 서버, **외부 비노출**) |
| GET | `/fans/me/payments/{id}` | `payment` | 장성재 | 결제 상세 |

### POST `/orders`

| 필드 | 설명 |
| --- | --- |
| Request | `accessTicket`, `items: [{ productId, quantity }]` |
| Response `201` | `{ orderId, status: RESERVED, orderPaymentKey }` |

**설계 근거**

- 주문 생성과 재고 예약을 **한 트랜잭션**으로 처리. 분리 시 reserve 미호출 **PENDING 좀비 주문** 가능.
- 성공 시 `status=RESERVED` + **`orderPaymentKey`** 반환 → 클라이언트가 주문·결제 세션을 식별한 뒤 토스 결제창 진입.
- PG에서 받은 키는 **`tossPaymentKey`** 로만 다룬다 ([결제 식별자](#결제-식별자-orderpaymentkey--tosspaymentkey) 참고).

### confirm / fail API 비노출

`PAID→COMPLETED`, `RESERVED→FAILED→CANCELLED` 전이는 **웹훅 수신 후 서버 내부** 처리.  
클라이언트가 직접 호출하면 결제 상태 조작 취약점 → **외부 API로 노출하지 않음**.

### POST `/payments/toss/confirm`

| Request | `tossPaymentKey`, `orderId`, `amount` |
| Response | `{ paymentId, status: PENDING }` |

- `tossPaymentKey`: 토스 SDK·결제 성공 콜백의 `paymentKey` 값을 **본 API 필드명으로 매핑**해 전달한다.
- `orderId`: `POST /orders` 응답의 `orderId`. 금액·상태 검증에 사용.

### POST `/payments/webhook`

토스 payload 수신 → payload의 PG `paymentKey`를 **`tossPaymentKey`로 매핑** → `PAYMENT.payment_key` 멱등(Unique) → 주문·재고 후처리. 상세: [payment-flow-reason.md](../sequence/payment-flow-reason.md).

---

## Inventory (Internal — 외부 비노출)

`inventory-application` 포트 · 담당: **형성빈**

| Method | Endpoint | 설명 | Request Body | 호출 주체 |
| --- | --- | --- | --- | --- |
| POST | `/internal/inventory/reserve` | 재고 예약 (`reserved_quantity` ↑) | `productId`, `quantity` | `OrderService` |
| POST | `/internal/inventory/confirm` | 결제 확정 (`reserved` ↓, `stock` ↓) | `productId`, `quantity` | `PaymentService` (웹훅 후) |
| POST | `/internal/inventory/restore` | 결제 실패 복구 (`reserved` ↓ rollback) | `productId`, `quantity` | Saga 보상 |

> 멀티모듈 모놀리스에서는 위 표는 **계약(포트) 문서화**용이다. 실제 구현은 HTTP가 아닌 `InventoryReservePort` 등 **interface 직접 호출**.

---

## Notification

`notification-*` · 전송: **표지민** · 발행: 도메인 오너

| Method | Endpoint | 설명 | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/internal/notifications/publish` | 알림 이벤트 발행 | `eventType`, `resourceId`, `payload` | `201` `{ eventId }` |
| GET | `/fans/me/notifications` | 내 알림 목록 | `?cursor`, `size` | `{ items: [{ id, type, title, message, sentAt }] }` — [ERD `NOTIFICATION`](../erd/erd-design.md#9-banner--restock_alert--notification-팬-알림함) (`read` 컬럼 없음) |

### 이벤트 타입별 발행 오너

| 이벤트 | 발행 오너 | 전송 담당 |
| --- | --- | --- |
| `PAYMENT_SUCCESS` | 장성재 (`payment`) | 표지민 (`notification`) |
| `RESTOCK_ALERT` | 형성빈 (`inventory`) | 표지민 |
| `LIVE_START` | 정환철 (`community`) | 표지민 |
| `NEW_POST_COMMENT` | 정환철 (`community`) | 표지민 |

`POST /internal/notifications/publish`도 런타임에서는 **포트 호출**; HTTP 경로는 계약·테스트 더블용으로만 사용 가능.

---

## Admin

| Method | Endpoint | 모듈 | 담당 | 설명 |
| --- | --- | --- | --- | --- |
| GET | `/admin/artist-applications` | `user` | 표지민 | 입점 신청 목록 `?status=PENDING` — DB `PARTNER` ([ERD §4](../erd/erd-design.md#4-partner--artist--artist_member)) |
| PATCH | `/admin/artist-applications/{id}` | `user` | 표지민 | 승인·반려 `status`, `reason` |
| GET | `/admin/monitoring` | platform | 지영재 | 주문·결제·재고 모니터링 `?from`, `to` |
| GET | `/admin/banners` | `user` | 표지민 | 배너 목록 |
| POST | `/admin/banners` | `user` | 표지민 | 배너 등록 |
| PATCH | `/admin/banners/{id}` | `user` | 표지민 | 배너 수정 |
| DELETE | `/admin/banners/{id}` | `user` | 표지민 | 배너 삭제 |

배너 **노출 Read** (팬 화면)는 `community` 쪽 Public API로 추가 예정.

---

## 공통 에러 코드

`error.code` · HTTP · `retryable` 전체 목록은 **[api-contract.md §4](./api-contract.md#4-에러-코드-목록)**.

### OUT_OF_STOCK vs RESERVE_FAILED (요약)

| `error.code` | 의미 | `retryable` |
| --- | --- | --- |
| `OUT_OF_STOCK` | `stock_quantity` = 0 (전체 품절) | false |
| `RESERVE_FAILED` | 동시 경쟁 패배 | true |

[ERD §1](../erd/erd-design.md#1-product--reserved_quantity-분리) · [failure-policy](../operations/failure-policy.md#5-장애--api-계약-매핑).

---

## 상태값 정의

전이·타임아웃·보상·종료 상태·불변조건은 **[불변조건 · 상태 머신](../state/invariants-and-state-machines.md)** 이 SSOT이다.  
아래는 API 독자용 요약이다.

| 엔티티 | 컬럼 | 허용값 (요약) |
| --- | --- | --- |
| ORDER | `status` | 정상: `PENDING` → `RESERVED` → `PAID` → `COMPLETED` · 실패: `RESERVED` → `FAILED` → `CANCELLED` · 취소: `PENDING` → `CANCELLED` |
| PAYMENT | `status` | `PENDING` → `SUCCESS` / `FAILED` (종료) |
| WAIT_QUEUE (Redis) | `status` | `WAITING` → `PROCESSING` → `DONE` / `EXPIRED` — [ERD §10](../erd/erd-design.md#10-핫딜-대기열--redis-db-erd-미포함) |
| PRODUCT | — | `stock_quantity`, `reserved_quantity`, `hotdeal_start_at`, `hotdeal_end_at`, `artist_id` |
| CART / CART_ITEM | — | RDB `carts`·`cart_items` — [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) (Phase 1 Redis 미사용) |
| RESTOCK_ALERT | `status` | `ACTIVE` / `SENT` (구현 시 문자열 — [§7.2](../state/invariants-and-state-machines.md#72-restock_alert)) |
| `outbox_events` | `status` | `PENDING` → `PUBLISHED` / `FAILED` (DLQ) — [ERD §11](../erd/erd-design.md#11-알림--notification-vs-outbox) |
| NOTIFICATION | — | 팬 알림함: `type`, `title`, `message`, `sent_at` (전송 완료 후 기록) |

> `POST /orders` 성공 응답은 `RESERVED`만 노출. `PENDING`은 TX 내부용 — [상태 머신 §2.3](../state/invariants-and-state-machines.md#23-공개-api-vs-내부-tx).

---

## 설계 검토 요약 (명세 품질)

| 항목 | 평가 |
| --- | --- |
| ERD·시퀀스와의 정합 | ✅ ERD 24테이블·`hotdeal_*`·`NOTIFICATION`(read 없음)·팬 OAuth-only·`CART` RDB — [erd-design](../erd/erd-design.md) |
| 보안·멱등 | ✅ confirm/fail 비노출, 웹훅 내부 처리, `DUPLICATE_PAYMENT` |
| UX 에러 구분 | ✅ 4004/4005 분리 — 문서화 우수 |
| 제목 vs 범위 | ⚠️ "Full Domain"이나 **community 피드/댓글/랭킹 API는 미포함** — 본 문서는 **MVP Commerce + 콘텐츠 일정·라이브** 축으로 범위 명시함 |
| 용어 | ✅ `orderPaymentKey` / `tossPaymentKey` 분리 ([결제 식별자](#결제-식별자-orderpaymentkey--tosspaymentkey)) |
| 시퀀스 vs API | ℹ️ 시퀀스에 `PENDING` 후 reserve 표현이 있으나, 공개 API는 **원자적 `RESERVED`** 응답으로 단순화 — 내부 구현은 동일 TX 안에서 처리 가능 |

변경 시 **해당 도메인 오너 PR 리뷰** + ERD/시퀀스/본 문서 동시 갱신.
