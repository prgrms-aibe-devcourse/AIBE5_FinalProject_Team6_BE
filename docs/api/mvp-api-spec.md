# FANDROPS MVP 백엔드 API 명세서

K-Pop 팬덤 커머스 / 예약 플랫폼 — **MVP 핵심 도메인** HTTP API

> **포트폴리오:** [01](../01_service_intro.html) · [02](../02_research.html) · [03](../03_planning.html) · [04 IA](../04_IA.html)  
> **관련 문서:** [MVP 기능 요구사항 §1](../requirements/mvp-functional-requirements-v2.md#1-mvp-기능-요구사항-인벤토리) · [API 계약](./api-contract.md) · [ERD](../erd/erd-design.md) · [결제·주문 시퀀스](../sequence/payment-flow-reason.md) · [불변조건·상태 머신](../state/invariants-and-state-machines.md) · [장애 정책](../operations/failure-policy.md) · [아키텍처](../architecture/architecture.md)

---

## 문서 범위

본 명세는 [mvp-functional-requirements-v2.md](../requirements/mvp-functional-requirements-v2.md) **§1 기능 목록에 등재된 F-ID만** 다룬다.

| F 그룹 | 포함 |
| --- | --- |
| F01~F08 (표 등재분) | Auth, 입점, 커뮤니티, 상시·드롭스 상품, 장바구니·주문, 행사·외부 티켓, 결제 E2E, 마이페이지, Admin 모니터링 |
| 미등재 | 고객센터, 신고·제재 Admin, 쿠폰·정산, 인앱 좌석 예매, 자체 라이브·영상 업로드 |

Gradle 모듈·담당자: [architecture.md § 도메인 오너십](../architecture/architecture.md#도메인-오너십--모듈-매핑) · F-scope: [architecture § 도메인 오너십](../architecture/architecture.md#도메인-오너십--모듈-매핑).

### F-ID → API 색인

| F-ID | Endpoint (요약) | 모듈 |
| --- | --- | --- |
| F01-01~03 | `/auth/*` | `user` |
| F01-04 | `POST /artists/{id}/follow`, `DELETE /artists/{id}/follow` | `community` |
| F02-01 | `POST /b2b/apply` (입점 신청) | `user` |
| F02-02 | `/admin/artist-applications` (Admin 심사) | `user` |
| F02-03 | `GET /artists/{id}` (프로필·SNS) | `community` |
| F03-01 | `POST /artists/{id}/spaces` | `community` |
| F03-02~03 | `/feeds`, `/comments`, `.../likes` (`FEED_LIKE`/`COMMENT_LIKE`) | `community` |
| F03-04 | `/fans/me/notifications`, 이벤트 발행 | `notification` / 각 도메인 |
| F03-05~06 | `/calendar`, `/lives`, `PATCH .../start` | `community` |
| F03-07~08 | `/attendance-events/.../check-in` (피드 배너), `/goods-votes` | `community` |
| F04-01 | `POST /products` (상시), `?type=regular` | `order` |
| F04-02 | `POST /products` (드롭스 기간), `?type=drops`, `/queue/*` | `order` · `payment` |
| F04-03 | `/banners/main`, `/admin/main-banners` | `user` |
| F04-03 (STORE) | `/store-banners`, `/admin/store-banners` | `order` |
| F04-04~05 | `/cart`, `/orders`, `.../restock-subscribe` | `order` |
| F05-01~03 | `POST /artists/{id}/events` (+ 외부 URL) | `community` |
| F06-01~03 | `/payments/*`, webhook | `payment` |
| F07-01~02 | `/fans/me/artists`, `/activities`, `/orders`, `/payments` | `community` · `order` · `payment` |
| F08-02 | `/admin/monitoring` | platform |

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
| `/auth/*`, `/fans/me` (계정), `/b2b/apply`, `/admin/artist-applications` | `user` | 표지민 |
| `/queue/*` | `payment` | 장성재 |
| `/artists/*`, `/spaces/*`, `/feeds/*`, `/comments/*`, `/lives/*`, 행사·스케줄·출석·투표 | `community` | 정환철 |
| `/products/*`, `/cart/*`, `/orders/*`, `/fans/me/orders` | `order` · `inventory` | 형성빈 |
| `/banners/main`, `/admin/main-banners` | `user` | 표지민 (F04-03) |
| `/payments/*`, `/fans/me/payments/*` | `payment` | 장성재 |
| `/internal/inventory/*` | `inventory` (포트) | 형성빈 |
| `/internal/notifications/publish`, `/fans/me/notifications`, `/notifications/*` | `notification` | 표지민 (전송) |
| `/admin/monitoring` | `api-server` + 관측 스택 | 지영재 |

---

## Auth / Fan 계정

`user-api` · 담당: **표지민**

> `FAN`은 이메일 가입과 소셜 가입을 모두 지원한다. 비밀번호는 `password_hash`로만 저장하고, 소셜 provider access token은 저장하지 않는다 ([ERD §4](../erd/erd-design.md#4-agency_account--artist_profile--artist_member--fan)).

| Method | Endpoint | 설명 | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/auth/signup` | 이메일 회원가입 + 약관 동의 | `email`, `password`, `nickname`, `termsAgreed: true` | `201` `{ fanId, accessToken, refreshToken }` |
| POST | `/auth/login` | 이메일/loginId 로그인 (Fan · Agency · ArtistMember 공용) — Fan 우선, 동일 loginId 충돌 시 Fan → Agency → ArtistMember 순 | `email`, `password` | `{ accessToken, refreshToken }` — `role=FAN\|AGENCY\|ARTIST` JWT 발급 |
| POST | `/auth/social/{provider}` | 소셜 로그인·가입 (`kakao` · `google`) | `code` | `{ accessToken, refreshToken }` — `FAN` 행 upsert |
| POST | `/auth/password-reset/request` | 비밀번호 재설정 메일 발송 | `email` | `204 No Content` |
| POST | `/auth/password-reset/confirm` | 재설정 토큰 검증 후 비밀번호 변경 | `token`, `newPassword` | `204 No Content` |
| POST | `/auth/logout` | 로그아웃 (토큰 무효화) | — | `204 No Content` |
| POST | `/auth/token/refresh` | Access Token 재발급 (RTR — 매 호출마다 새 refreshToken 발급) | `refreshToken` | `{ accessToken, refreshToken, expiresIn }` |
| GET | `/fans/me` | 내 정보 조회 | — | `{ fanId, email, nickname, allowNotification, createdAt }` |
| PUT | `/fans/me` | 내 정보 수정 | `nickname` (optional), `allowNotification` (optional) | `{ fanId, nickname, allowNotification }` |
| POST | `/b2b/apply` | 운영 입점 신청 (F02-01) | `companyName`, `businessRegistrationNumber`, `representativeName`, `contactEmail`, `contactPhone`, `introduction`, `targetArtistName` | `201` `{ applicationId, status: "PENDING" }` |

> **[프론트 연동 주의 — RTR]** `/auth/token/refresh` 응답에 `refreshToken`이 포함됩니다.
> 클라이언트는 매 refresh 응답마다 저장된 refreshToken을 새 값으로 교체해야 합니다.
> 로그아웃 시에는 현재 보유 중인 최신 refreshToken을 전송합니다.

---

## Artist / Event

`community-api` · 담당: **정환철**

| Method | Endpoint | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- |
| GET | `/artists` | 아티스트 목록 (스토어 탐색) | `?cursor`, `size`, `sort=fanCount` (F04-01) | `{ items: [{ id, name, fanCount, ... }], nextCursor }` |
| GET | `/artists/{id}` | 아티스트 상세 · 프로필·외부 링크 (F02-03) | — | `{ id, agencyAccountId, name, joinedAt, profileImageUrl, snsLinks[], scheduleSummary[] }` |
| POST | `/artists/{id}/follow` | 팬 가입(팔로우, `USER_FOLLOW`) + 팬 수 증가 | — | `201` `{ artistId, fanId, followedAt }` |
| DELETE | `/artists/{id}/follow` | 팔로우 해지 | — | `204 No Content` |
| POST | `/artists` | 아티스트 등록 (Admin) | `agencyId`, `name` | `201` `{ artistId }` |
| GET | `/artists/{id}/calendar` | 드롭·팬미팅·라이브 통합 스케줄 | `?from`, `to` | `{ events: [{ type, title, startTime }] }` |
| POST | `/artists/{id}/events` | 행사 안내·외부 예매 링크 (F05-01~03) | `title`, `type`, `venue`, `startTime`, `ticketOpenAt` (참고), `externalTicketUrls[]` (F05-02), `externalTicketUrlExpiresAt` (optional) | `201` `{ eventId }` |
| PATCH | `/lives/{id}/start` | 라이브 시작 (상태 갱신 + 알림 이벤트 발행) | — | `{ liveId, isLive: true }` |

`PATCH /lives/{id}/start` 성공 시 `notification`에 `ARTIST_SCHEDULE` 타입 이벤트 발행 → 표지민 모듈이 전송.

---

## Community

`community-api` · 담당: **정환철**

| Method | Endpoint | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- |
| GET | `/artists/{id}/notices` | 공지사항 목록 (④ 공지사항 탭) | `?cursor`, `size` | `{ items: [...], nextCursor }` |
| GET | `/artists/{id}/notices/{noticeId}` | 공지 상세 | — | `{ id, title, content, imageUrls[], createdAt }` |
| POST | `/artists/{id}/notices` | 공지 작성 (아티스트 멤버) | `title`, `content`, `imageUrls[]` | `201` `{ noticeId }` |
| POST | `/artists/{id}/feeds` | 아티스트 게시글 작성(텍스트+이미지) | `content`, `imageUrls[]` | `201` `{ feedId }` |
| GET | `/artists/{id}/feeds` | 피드 목록 | `?cursor`, `size` | `{ items: [...], nextCursor }` |
| DELETE | `/artists/{id}/feeds/{feedId}` | 피드 삭제 (작성자 아티스트 멤버만) | — | `204 No Content` |
| POST | `/feeds/{id}/comments` | 댓글/답글 작성 | `content`, `parentId` (optional) | `201` `{ commentId }` |
| POST | `/feeds/{id}/likes` | 피드 좋아요 (`FEED_LIKE`) | — | `201` |
| DELETE | `/feeds/{id}/likes` | 피드 좋아요 취소 | — | `204 No Content` |
| POST | `/comments/{id}/likes` | 댓글 좋아요 (`COMMENT_LIKE`) | — | `201` |
| DELETE | `/comments/{id}/likes` | 댓글 좋아요 취소 | — | `204 No Content` |
| GET | `/artists/{id}/attendance-events` | 진행 중 출석 이벤트 (피드 배너 연동, F03-07) | — | `{ items: [{ id, startDate, endDate, rewardDesc }] }` |
| POST | `/attendance-events/{id}/check-in` | 출석 체크 (`ATTENDANCE_LOG`) | — | `201` `{ eventId, checkedDate, streakDays }` |
| GET | `/artists/{id}/goods-votes` | 굿즈 투표 목록 (F03-08) | `?cursor`, `size` | `{ items: [...], nextCursor }` |
| POST | `/artists/{id}/goods-votes` | 굿즈 투표 생성 (운영 계정 · `ROLE_AGENCY`) | `title`, `endsAt`, `options: [{ label, imageUrl }]` | `201` `{ voteId }` |
| POST | `/goods-votes/{id}/ballots` | 굿즈 투표 참여 (`GOODS_VOTE_RECORD`, 1인 1표) | `optionId` | `201` `{ recordId }` |
| GET | `/fans/me/activities` | 내가 남긴 댓글/좋아요 히스토리 | `?cursor`, `size` | `{ items: [...], nextCursor }` |
| GET | `/fans/me/artists` | 가입 아티스트 목록 | `?cursor`, `size` | `{ items: [...], nextCursor }` |

- 피드 작성은 이미지 업로드 URL만 받는다. 동영상 업로드는 MVP 제외.
- 댓글/좋아요/굿즈 투표는 해당 아티스트 **팬 가입(F01-04 → `USER_FOLLOW`)** 후 write 가능. 미가입 시 일부 읽기만 허용.
- F03-01 아티스트 공간: `ARTIST_PROFILE` 승인 시 **앱 6탭(피드·아티스트·굿즈투표·미디어·공지사항·스케줄)** 으로 구성(출석은 피드 내 배너). 별도 `ARTIST_SPACE` 테이블 없음.
- 출석 체크(`ATTENDANCE_EVENT`/`ATTENDANCE_LOG`)·굿즈 투표(`GOODS_VOTE`/`GOODS_VOTE_OPTION`/`GOODS_VOTE_RECORD`) API·ERD 반영. 출석은 피드 내 이벤트 배너 진입.
- 굿즈 투표 **개설**(`POST /artists/{id}/goods-votes`)·강제 종료는 **`ROLE_AGENCY` 운영 계정**만. 팬 투표(`POST /goods-votes/{id}/ballots`)는 팬 가입(`USER_FOLLOW`) 후.
- 외부 티켓(F05-02~03): `http`/`https`만 허용, 만료 후 비노출.
- **상점(Store)은 GNB 스토어 탭(F04)** — 아티스트 홈 탭 아님.

---

## Product (F04)

`order-api` · `inventory-*` · 담당: **형성빈**

| Method | Endpoint | F-ID | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- | --- |
| GET | `/products` | F04-01·02 | 상품 목록 | `?type=regular` \| `drops`, `artistId`(선택), `cursor`, `size` | `{ items: [{ id, artistId, name, price, status, totalQty, availableQty }], nextCursor }` |
| GET | `/products/{id}` | F04-01·02 | 상품 상세 | — | `{ id, artistId, name, price, status, dropsStartAt, dropsEndAt, totalQty, reservedQty, availableQty, updatedAt }` |
| POST | `/products` | F04-01 | **상시** 상품 등록 | `artistId`, `name`, `price`, `totalQty` | `201` `{ productId }` |
| POST | `/products` | F04-02 | **드롭스** 상품 등록 | 위 + `dropsStartAt`, `dropsEndAt`, `totalQty` | `201` `{ productId }` |
| PATCH | `/products/{id}` | F04-01·02 | 수정·품절 | `name`, `price`, `status`, 기간(드롭스) | `{ productId, status }` |
| POST | `/products/{id}/restock-subscribe` | F04-05 | 재입고 알림 구독 | — | `201` `{ alertId }` |
| DELETE | `/products/{id}/restock-subscribe` | F04-05 | 구독 취소 | — | `204` |
| POST | `/products/{id}/restock` | F04-05 | 재입고 + 이벤트 발행 | `quantity` | `{ productId, totalQty }` |

- **F04-01** `?type=regular`: `drops_start_at`·`drops_end_at` 모두 NULL.
- **F04-02** `?type=drops`: `drops_start_at ≤ now ≤ drops_end_at`. 카운트다운·대기열([§ Wait Queue](#wait-queue-대기열)) 적용.
- `?artistId={id}`: 특정 아티스트 상품만 반환. 미전달 시 전체 반환 (하위 호환).
- 재입고 시 `RESTOCK_ALERT` 발행 → 표지민 전송.
- `totalQty` / `reservedQty` / `availableQty`: `INVENTORY` 조인 ([ERD §1](../erd/erd-design.md#1-inventory--재고-테이블-분리-및-이력history-기록)).

---

## Agency Artists

`user-api` · 담당: **표지민**  
Agency 계정이 자신의 소속 아티스트 목록 조회. `artist_profile.agency_id = 로그인 Agency ID` 로 소유권 scoping.

| Method | Endpoint | Auth | 설명 | Query Params | Response |
| --- | --- | --- | --- | --- | --- |
| GET | `/agency/artists` | Agency | 내 소속 아티스트 목록 (팬 수 내림차순, cursor 페이지네이션) | `cursor`(optional), `size`(default 20, max 100) | `{ data: { items: [{ id, name, profileImageUrl, fanCount }], nextCursor, hasMore }, traceId }` |

---

## Agency Banner (F04-03)

`user-api` · 담당: **표지민**  
Agency 계정이 자신의 메인 배너를 직접 관리. `banner.agency_id = 로그인 Agency ID` 로 소유권 scoping.

| Method | Endpoint | Auth | 설명 | Request Body | Response |
| --- | --- | --- | --- | --- | --- |
| GET | `/agency/banners` | Agency | 내 배너 목록 | — | `{ data: [BannerResponse], traceId }` |
| POST | `/agency/banners` | Agency | 배너 등록 | `title`, `imageUrl`, `landingUrl`, `exposureOrder`, `startAt`?, `endAt`? | `201` `{ data: BannerResponse, traceId }` |
| PATCH | `/agency/banners/{id}` | Agency | 배너 수정 (부분) | 위 필드 모두 선택, `isActive` | `{ data: BannerResponse, traceId }` |
| DELETE | `/agency/banners/{id}` | Agency | 배너 비활성화 (soft delete) | — | `204` |
| POST | `/agency/uploads` | Agency | 배너 이미지 S3 Presigned PUT URL 발급 | `contentType`, `contentLength` | `{ presignedUrl, imageUrl, expiresAt }` |

**소유권 규칙**: `PATCH`·`DELETE` 시 `agency_id` 불일치 → `404 BANNER_NOT_FOUND` (ID 열거 방지).

**이미지 업로드 플로우**:
1. `POST /agency/uploads` → `presignedUrl`, `imageUrl`
2. 클라이언트가 `presignedUrl`로 직접 S3 PUT
3. `POST /agency/banners { imageUrl: ... }` ← PUT 완료 후에만 유효

---

## Store Banner (F04-03 STORE)

`order-api` · 담당: **형성빈**  
`banner` 테이블 `banner_type='STORE'` 레코드만 접근. MAIN 배너는 user 모듈(표지민) 담당.

| Method | Endpoint | Auth | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- | --- |
| GET | `/store-banners` | 없음 | 활성 스토어 배너 목록 (노출 시간 범위 내) | — | `{ data: [StoreBannerResponse], traceId }` |
| POST | `/admin/store-banners` | Admin | 스토어 배너 등록 | `title`, `imageUrl`, `landingUrl`, `exposureOrder`, `startAt`?, `endAt`?, `productId`? | `201` `{ bannerId }` |
| PATCH | `/admin/store-banners/{id}` | Admin | 스토어 배너 수정 (부분) | 위 필드 모두 선택 | `{ bannerId }` |
| DELETE | `/admin/store-banners/{id}` | Admin | 스토어 배너 비활성화 (soft delete) | — | `204` |

**`StoreBannerResponse` 필드**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 배너 ID |
| `title` | String | 배너 제목 |
| `imageUrl` | String | 이미지 URL |
| `landingUrl` | String | 클릭 시 이동 URL |
| `exposureOrder` | int | 노출 순서 (오름차순) |
| `status` | String | `ACTIVE` / `WAITING` / `INACTIVE` |
| `startAt` | ISO8601? | 노출 시작 시각 (null = 즉시) |
| `endAt` | ISO8601? | 노출 종료 시각 (null = 무기한) |
| `productId` | Long? | 연결 상품 ID (ERD §9) |

**status 계산 규칙**  
`INACTIVE` ← `isActive=false` 또는 `endAt < now`  
`WAITING` ← `isActive=true` AND `startAt > now`  
`ACTIVE` ← 그 외

**에러 코드**

| 상황 | HTTP | errorCode |
| --- | --- | --- |
| 존재하지 않는 배너 수정·삭제 | 404 | `STORE_BANNER_NOT_FOUND` |
| `startAt >= endAt` | 400 | `INVALID_REQUEST` |
| 필수 필드 누락 | 400 | `INVALID_REQUEST` |

---

## Cart

`order-api` · 담당: **형성빈**

| Method | Endpoint | 설명 | Request Body / Param | Response |
| --- | --- | --- | --- | --- |
| GET | `/cart` | 내 장바구니 조회 | — | `{ items: [{ cartItemId, productId, quantity, price }] }` |
| POST | `/cart/items` | 장바구니 담기 | `productId`, `quantity` | `201` `{ cartItemId }` |
| PATCH | `/cart/items/{id}` | 수량 변경 | `quantity` | `{ cartItemId, quantity }` |
| DELETE | `/cart/items/{id}` | 장바구니 항목 삭제 | — | `204 No Content` |

장바구니 저장소는 RDB `CART`/`CART_ITEM`만 사용한다. Redis 장바구니는 MVP 금지([ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md)).

---

## Wait Queue (대기열)

`payment-api` · 담당: **장성재**

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
- 대기열·RateLimit 정책은 장성재가 관리하고, Redis/Nginx/ALB 운영값은 지영재 리뷰를 받는다.

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
| POST | `/payments/toss/webhook` | `payment` | 장성재 | 토스 웹훅 (PG → 서버, **외부 비노출**) |
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

### GET `/orders/{id}`

| 필드 | 설명 |
| --- | --- |
| Path | `id` — 주문 ID |
| Header | `Authorization: Bearer <accessToken>` |
| Response `200` | `{ orderId, status, totalAmount, orderPaymentKey, items: [{ productId, quantity, unitPrice, subtotal }], createdAt }` |
| Response `404` | 주문 없음 또는 본인 소유가 아닌 경우 (소유자 노출 방지) |

**설계 근거**

- fanId는 JWT에서 추출하여 주문 소유자와 비교. 불일치 시 존재 자체를 숨기기 위해 `404` 반환.
- `orderPaymentKey`는 결제창 진입·취소 시 클라이언트 식별자로 사용.

### GET `/fans/me/orders`

| 필드 | 설명 |
| --- | --- |
| Header | `Authorization: Bearer <accessToken>` |
| Query | `cursor` (선택, Long) — 이전 페이지 마지막 orderId / `size` (선택, 기본 20, 최대 100) |
| Response `200` | `{ items: [{ orderId, status, totalAmount, createdAt }], nextCursor }` |

**설계 근거**

- 최신순(id DESC) cursor-based pagination. `nextCursor`가 `null`이면 마지막 페이지.
- 다음 페이지 요청: `?cursor={nextCursor}&size={size}`.
- `idx_orders_fan_id` 인덱스(V25)로 fan_id 범위 스캔 최적화.

### DELETE `/orders/{id}`

| 항목 | 내용 |
| --- | --- |
| **인증** | Bearer JWT (또는 로컬 `X-Fan-Id` 헤더) |
| **Response `204`** | 취소 성공, Body 없음 |
| **Response `404`** | `ORDER_NOT_FOUND` — 존재하지 않거나 본인 소유가 아닌 주문 |
| **Response `409`** | `ORDER_CANCELLATION_NOT_ALLOWED` — `RESERVED` 가 아닌 상태 (PAID·COMPLETED·CANCELLED·FAILED) |

**취소 가능 상태**

- `RESERVED` 만 취소 가능. `PAID` 이후 단계는 결제가 확정된 상태이므로 API 취소 불가 (환불은 별도 CS 프로세스).
- 취소 처리 순서: 재고 복구(`inventoryRestorePort.restore`) → 상태 `CANCELLED` 업데이트 (원자적 트랜잭션).

### confirm / fail API 비노출

`PAID→COMPLETED`, `RESERVED→FAILED→CANCELLED` 전이는 **웹훅 수신 후 서버 내부** 처리.  
클라이언트가 직접 호출하면 결제 상태 조작 취약점 → **외부 API로 노출하지 않음**.

### POST `/payments/toss/confirm`

| Request | `tossPaymentKey`, `orderId`, `amount` |
| Response | `{ paymentId, status: PENDING }` |

- `tossPaymentKey`: 토스 SDK·결제 성공 콜백의 `paymentKey` 값을 **본 API 필드명으로 매핑**해 전달한다.
- `orderId`: `POST /orders` 응답의 `orderId`. 금액·상태 검증에 사용.

### POST `/payments/toss/webhook`

Toss PG → 서버 비동기 결제 상태 수신. 상세: [payment-flow-reason.md](../sequence/payment-flow-reason.md).

| 항목 | 내용 |
| --- | --- |
| **서명 검증** | `X-Signature-256: sha256=<hex>` 헤더 HMAC-SHA256 검증 — 불일치 시 `401` |
| **멱등** | `tossPaymentKey` = `PAYMENT.payment_key` Unique — 동일 키 재수신 시 `200` 즉시 반환, 이벤트 재발행 없음 (P-1) |
| **성공 처리** | `data.status = DONE` → `PAYMENT PENDING→SUCCESS`, `paid_at` 기록 → `PaymentApprovedEvent` (AFTER_COMMIT) |
| **실패 처리** | `data.status = ABORTED \| EXPIRED` → `PAYMENT PENDING→FAILED`, `failed_at` 기록 → `PaymentFailedEvent` (AFTER_COMMIT) |
| **기타 status** | `CANCELED` 등 → 무시 (로그만) |
| **Response** | `200 OK` (성공·멱등 모두) / `401` (서명 실패) / `500` (파싱·처리 오류) |

**요청 페이로드 (Toss 표준):**
```json
{
  "eventType": "PAYMENT_STATUS_CHANGED",
  "createdAt": "2024-01-01T09:00:00+09:00",
  "data": {
    "paymentKey": "<tossPaymentKey>",
    "orderId": "<orderId>",
    "status": "DONE | ABORTED | EXPIRED",
    "method": "카드",
    "totalAmount": 15000,
    "approvedAt": "2024-01-01T09:00:00+09:00"
  }
}
```

---

## Inventory (Internal — 외부 비노출)

`inventory-application` 포트 · 담당: **형성빈**

| Method | Endpoint | 설명 | Request Body | 호출 주체 |
| --- | --- | --- | --- | --- |
| POST | `/internal/inventory/reserve` | 재고 예약 (`reserved_qty` ↑, `available_qty` ↓, 이력 기록) | `productId`, `quantity` | `OrderService` |
| POST | `/internal/inventory/confirm` | 결제 확정 (`reserved_qty` ↓, `total_qty` ↓, 이력 기록) | `productId`, `quantity` | `PaymentService` (웹훅 후) |
| POST | `/internal/inventory/restore` | 결제 실패 복구 (`reserved_qty` ↓ rollback, `available_qty` ↑, 이력 기록) | `productId`, `quantity` | Saga 보상 |
| POST | `/internal/inventory/increase` | 재입고 (`total_qty` ↑, `available_qty` ↑, 이력 기록) | `productId`, `quantity` | `RestockAlertService` (F04-05) |

> 멀티모듈 모놀리스에서는 위 표는 **계약(포트) 문서화**용이다. 실제 구현은 HTTP가 아닌 `InventoryReservePort` 등 **interface 직접 호출**.

---

## Notification

`notification-*` · 전송: **표지민** · 발행: 도메인 오너

| Method | Endpoint | 설명 | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/internal/notifications/publish` | 알림 이벤트 발행 | `eventType`, `resourceId`, `payload` | `201` `{ eventId }` |
| GET | `/fans/me/notifications` | 내 알림 목록 | `?cursor`, `size` | `{ items: [{ id, type, message, isRead, sentAt, targetId }] }` — [ERD `NOTIFICATION`](../erd/erd-design.md#9-banner--restock_alert--notification-팬-알림함) |
| PATCH | `/fans/me/notifications/{id}/read` | 알림 읽음 처리 | — | `200` |

### 이벤트 타입별 발행 오너

| 이벤트 | 발행 오너 | 전송 담당 |
| --- | --- | --- |
| `PAYMENT_SUCCESS` | 장성재 (`payment`) | 표지민 (`notification`) |
| `RESTOCK_ALERT` | 형성빈 (`inventory`) | 표지민 |
| `NEW_FEED` | 정환철 (`community`) | 표지민 |
| `NEW_COMMENT` | 정환철 (`community`) | 표지민 |
| `ARTIST_SCHEDULE` | 정환철 (`community`) — 라이브·일정 (F03-05~06) | 표지민 |
| `ARTIST_APPLICATION_APPROVED` | 표지민 (`user`) | 표지민 |

`POST /internal/notifications/publish`도 런타임에서는 **포트 호출**; HTTP 경로는 계약·테스트 더블용으로만 사용 가능.

---

## Admin

| Method | Endpoint | 모듈 | 담당 | 설명 |
| --- | --- | --- | --- | --- |
| POST | `/admin/auth/login` | `user` | 표지민 | Admin 전용 로그인 — `email`, `password` → `role=ADMIN` JWT 발급 (seed: `admin@fandrops.com`) |
| GET | `/admin/artist-applications` | `user` | 표지민 | 입점 신청 목록 `?status=PENDING` — DB `AGENCY_APPLICATION` ([ERD §4](../erd/erd-design.md#4-agency_account--artist_profile--artist_member--fan)) |
| PATCH | `/admin/artist-applications/{id}` | `user` | 표지민 | 승인·반려 `status` ("APPROVED | REJECTED"), `rejectReason` (반려 시 필수) |
| GET | `/admin/monitoring` | platform | 지영재 | 주문·결제·재고 모니터링 `?from`, `to` |
| GET | `/admin/main-banners` | `user` | 표지민 | 메인 배너 목록 (F04-03) |
| POST | `/admin/main-banners` | `user` | 표지민 | 메인 배너 등록 |
| PATCH | `/admin/main-banners/{id}` | `user` | 표지민 | 메인 배너 수정 |
| DELETE | `/admin/main-banners/{id}` | `user` | 표지민 | 메인 배너 삭제 |
| POST | `/admin/uploads` | `user` | 표지민 | 배너 이미지 S3 Presigned PUT URL 발급 — `{ contentType, contentLength }` → `{ presignedUrl, imageUrl, expiresAt }` · 클라이언트가 presignedUrl로 직접 S3 PUT 후 imageUrl을 배너 등록에 사용 (#180) |
| GET | `/banners/main` | `user` | 표지민 | GNB 홈 메인 배너 노출 (F04-03) |
| GET | `/admin/store-banners` | `order` | 형성빈 | 스토어 배너 목록 관리 (F04-03 STORE) |
| POST | `/admin/store-banners` | `order` | 형성빈 | 스토어 배너 등록 |
| PATCH | `/admin/store-banners/{id}` | `order` | 형성빈 | 스토어 배너 수정 |
| DELETE | `/admin/store-banners/{id}` | `order` | 형성빈 | 스토어 배너 비활성화 (soft delete) |

#### POST `/admin/uploads` — S3 PUT 시 주의사항

```
요청 필드:
  contentType   string  허용값: image/jpeg · image/png · image/webp
  contentLength number  파일 바이트 크기. 1 이상, 5,242,880(5MB) 이하.

응답 필드:
  presignedUrl  string  S3 직접 PUT 전용 URL (expiresAt까지 유효)
  imageUrl      string  배너 등록 API(POST /admin/main-banners)에 전달할 실제 이미지 URL
  expiresAt     string  presignedUrl 만료 시각 (ISO 8601)
```

**⚠️ S3 PUT 시 Content-Length 헤더 필수**
`presignedUrl`에는 요청 시 선언한 `contentLength` 값이 서명에 바인딩됩니다.
S3 PUT 요청의 `Content-Length` 헤더가 선언한 값과 **다를 경우 S3가 403을 반환**합니다.

```
올바른 흐름:
1. 파일 크기를 미리 확인 (예: file.size === 102400)
2. POST /admin/uploads { contentType: "image/jpeg", contentLength: 102400 }
3. PUT presignedUrl, headers: { "Content-Type": "image/jpeg", "Content-Length": 102400 }
   body: <파일 바이너리>
4. POST /admin/main-banners { imageUrl: ... }  ← PUT 완료 후에만 유효
```

F04-03은 **메인 배너(user)** + **스토어 배너(order)** 분리 관리. 스토어 아티스트 노출 순서는 F04-01 (`GET /artists?sort=fanCount`).

---

## 공통 에러 코드

`error.code` · HTTP · `retryable` 전체 목록은 **[api-contract.md §4](./api-contract.md#4-에러-코드-목록)**.

### OUT_OF_STOCK vs RESERVE_FAILED (요약)

| `error.code` | 의미 | `retryable` |
| --- | --- | --- |
| `OUT_OF_STOCK` | `available_qty` = 0 (전체 품절) | false |
| `RESERVE_FAILED` | 동시 경쟁 패배 | true |

[ERD §1](../erd/erd-design.md#1-inventory--재고-테이블-분리-및-이력history-기록) · [failure-policy](../operations/failure-policy.md#5-장애--api-계약-매핑).

---

## 상태값 정의

전이·타임아웃·보상·종료 상태·불변조건은 **[불변조건 · 상태 머신](../state/invariants-and-state-machines.md)** 이 SSOT이다.  
아래는 API 독자용 요약이다.

| 엔티티 | 컬럼 | 허용값 (요약) |
| --- | --- | --- |
| ORDER | `status` | 정상: `PENDING` → `RESERVED` → `PAID` → `COMPLETED` · 실패: `RESERVED` → `FAILED` → `CANCELLED` · 취소: `PENDING` → `CANCELLED` |
| PAYMENT | `status` | `PENDING` → `SUCCESS` / `FAILED` (종료) |
| WAIT_QUEUE (Redis) | `status` | `WAITING` → `PROCESSING` → `DONE` / `EXPIRED` — [ERD §10](../erd/erd-design.md#10-드롭스-대기열--redis-db-erd-미포함) |
| PRODUCT | `status` | `ON_SALE` / `SOLD_OUT` |
| INVENTORY | — | `total_qty`, `reserved_qty`, `available_qty` (`available_qty = total_qty - reserved_qty`) |
| INVENTORY_HISTORY | `change_type` | `RESERVE` / `RELEASE` / `DECREASE` / `INCREASE` / `COMPENSATE` |
| CART / CART_ITEM | — | RDB `carts`·`cart_items` — [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) (Phase 1 Redis 미사용) |
| RESTOCK_ALERT | `status` | `PENDING` / `SENT` / `CANCELLED` — [§7.2](../state/invariants-and-state-machines.md#72-restock_alert) |
| `outbox_events` | `status` | `PENDING` → `PUBLISHED` / `FAILED` (DLQ) — [ERD §11](../erd/erd-design.md#11-알림--notification-vs-outbox) |
| NOTIFICATION | `notification_type` | `NEW_FEED` / `NEW_COMMENT` / `RESTOCK` / `ARTIST_SCHEDULE` · `is_read`, `sent_at` |

> `POST /orders` 성공 응답은 `RESERVED`만 노출. `PENDING`은 TX 내부용 — [상태 머신 §2.3](../state/invariants-and-state-machines.md#23-공개-api-vs-내부-tx).

---

## 설계 검토 요약 (명세 품질)

| 항목 | 평가 |
| --- | --- |
| ERD·시퀀스와의 정합 | ✅ ERD 29테이블·`FEED_IMAGE`·`GOODS_VOTE_*`·`ATTENDANCE_*`·`drops_*`·`NOTIFICATION`·`AGENCY_ACCOUNT`·`FEED_LIKE`/`COMMENT_LIKE`·`USER_FOLLOW`·`CART` RDB — [erd-design](../erd/erd-design.md) |
| 보안·멱등 | ✅ confirm/fail 비노출, 웹훅 내부 처리, `DUPLICATE_PAYMENT` |
| UX 에러 구분 | ✅ 4004/4005 분리 — 문서화 우수 |
| 제목 vs 범위 | ✅ F01~F08 MVP 핵심 기능을 본 문서에 반영. 좌석 예매·자체 라이브·리워드 배송 자동화는 Not Scope로 분리 |
| 용어 | ✅ `orderPaymentKey` / `tossPaymentKey` 분리 ([결제 식별자](#결제-식별자-orderpaymentkey--tosspaymentkey)) |
| 시퀀스 vs API | ℹ️ 시퀀스에 `PENDING` 후 reserve 표현이 있으나, 공개 API는 **원자적 `RESERVED`** 응답으로 단순화 — 내부 구현은 동일 TX 안에서 처리 가능 |

변경 시 **해당 도메인 오너 PR 리뷰** + ERD/시퀀스/본 문서 동시 갱신.
