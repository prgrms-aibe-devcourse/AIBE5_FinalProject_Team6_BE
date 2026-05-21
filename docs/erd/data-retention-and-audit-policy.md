# 데이터 보관 · Audit 정책

> **서비스 맥락:** K-Pop 팬덤 **B2B2C 커머스·이벤트 예약** 플랫폼. 오픈런(드롭스) 주문·결제 정합성과 분쟁·세무 대응을 전제로 보관 기간을 둔다.  
> **연관:** [ERD 설계](./erd-design.md) · [상태 머신](../state/invariants-and-state-machines.md) · [데이터 라이프사이클](./data-lifecycle.md)

본 문서는 **DB·로그·감사(audit) 레코드**를 얼마나, 어떤 형태로 남길지 정의한다.  
법무·세무 최종 확정 전까지 표의 기간은 **팀 합의용 기본값(MVP)** 이다.

---

## 1. 원칙

| 원칙 | 설명 |
| --- | --- |
| **최소 수집** | 주문·결제·분쟁에 필요한 필드만 DB에 저장. 비밀번호는 해시만 저장하고, PG·소셜 원문 토큰은 저장하지 않음. |
| **불변 거래 기록** | `orders` / `payments`는 **soft delete 하지 않음**. 취소·환불은 상태·audit으로만 표현. |
| **감사 가능성** | 관리자 조작·상태 전이·웹훅 처리는 **append-only audit** + `traceId`로 상관. |
| **계층적 삭제** | 원본(payload) → 요약(summary) → archive → purge 순으로 단계적 축소. |
| **MVP vs Phase 2** | 인앱 좌석 예약(`reservations`, `seats`)은 Not Scope. 정책은 **Phase 2 선반영**. |

---

## 2. 데이터 보관 (Retention)

### 2.1 거래·커머스 (형성빈 · 장성재)

| 데이터 | 보관 기간 | 시작 시점 | 만료 후 처리 | 비고 |
| --- | --- | --- | --- | --- |
| **`orders`**, **`order_items`** | **3년** | `status` = `CANCELLED` 또는 `COMPLETED` **이후** (최종 상태 시각) | anonymize 또는 cold storage 이관 후 운영 DB에서 제거 | 세법·분쟁 대비는 **법무·세무 합의로 5년까지 연장 가능** |
| **`payments`** | **3년** (요약 필드) | `SUCCESS` / `FAILED` 확정 후 | PG 식별자·금액·상태·`paid_at`/`failed_at`만 유지 가능 | 원본 연동 상세는 §2.2 |
| **`payment_webhook_events`** | **90일** (원본 payload) | 수신 시각 | 이후 **요약 행**만 `payments` 또는 `payment_webhook_summaries`에 유지 | 멱등·리컨실용 |
| **`products`** (판매 종료) | **1년** | `drops_end_at` 경과 또는 Admin 삭제 후 | archive 또는 비식별 통계만 | `is_active` 컬럼 없음 — [ERD §1](./erd-design.md#1-inventory--재고-테이블-분리-및-이력history-기록) |
| **`carts`**, **`cart_items`** | **활성 사용 중** | 주문 완료·취소 후 해당 행 삭제 | 팬 탈퇴 시 cascade | RDB only — [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) |
| **`restock_alerts`** | **1년** | `SENT` 또는 구독 해지 후 | 삭제 | fan_id는 §4 마스킹 |

### 2.2 결제·웹훅 (장성재)

| 데이터 | 보관 | 만료 후 |
| --- | --- | --- |
| 웹훅 **원본 JSON** (`payment_webhook_events.raw_payload`) | **90일** | 삭제. 아래 요약 필드만 유지 |
| 웹훅 **요약** | `event_id`, `tossPaymentKey`, `order_id`, `processing_result`, `retry_count`, `received_at`, `trace_id` | **3년** (`payments`와 동일) |

토스 재전송·[멱등](../state/invariants-and-state-machines.md#3-payment-상태-머신) 대응 기간(통상 7~30일)을 넘기는 원본 보관은 **운영 비용 대비 이득이 적다**는 전제.

### 2.3 대기열 · 알림 (장성재 · 표지민)

| 데이터 | 보관 | 만료 후 |
| --- | --- | --- |
| **드롭스 대기열 (Redis)** | **180일** (선택 스냅샷·집계) | `DONE` / `EXPIRED` 후 TTL·집계만. 담당: 장성재 ([ERD §10](./erd-design.md#10-드롭스-대기열--redis-db-erd-미포함)) |
| **`notifications`** | **1년** (`sent_at` 기준) | PII 마스킹 후 삭제 |
| **`outbox_events`** | **30일** | `published_at` 이후 삭제 또는 S3 cold storage ([ADR-001](../adr/ADR-001-multi-module-monolith.md) Outbox) |

### 2.4 예약 · 좌석 — Phase 2 (Not Scope MVP)

MVP는 행사 **외부 티켓 링크**만 제공([ERD §8](./erd-design.md#8-schedule--artist_schedule)). 인앱 예약 도입 시 아래를 적용한다.

| 데이터 | 보관 | 만료 후 |
| --- | --- | --- |
| **`reservations`**, **`seats`** | **이벤트 종료 후 180일** | `_archive` 테이블로 이동 후 운영 DB purge |
| 이벤트 메타 (`events` 등) | 종료 후 **3년** (커머스와 동일 정책 검토) | archive |

### 2.5 로그 · 인프라 (지영재)

| 데이터 | 보관 | 비고 |
| --- | --- | --- |
| **`application_logs`** (구조화 JSON) | **14~30일** (stg 14일 · prod 30일 권장) | CloudWatch / Loki 등. `traceId`, `orderId` 포함 |
| **Access / Nginx 로그** | **30일** | IP는 §4 참고 |
| **DB 슬로우 쿼리 로그** | **7일** | |

### 2.6 커뮤니티 · 계정 (정환철 · 표지민)

| 데이터 | 보관 | 비고 |
| --- | --- | --- |
| **`fans`** (탈퇴 후) | **30일** 유예 → `email`·`nickname` 마스킹 (행 유지) | ERD에 `deleted_at` 없음 |
| **`feeds`**, **`notices`**, **`comments`** | 탈퇴·운영 삭제 시 **DELETE** → **90일** 후 purge Job | `community` 모듈 |
| **`attendance_events`**, **`attendance_checks`** | 이벤트 종료 후 **1년** | 리워드 대상자 산정 근거. 배송/지급 자동화는 별도 정책 |
| **`goods_polls`**, **`goods_poll_options`**, **`votes`** | 투표 종료 후 **1년** | 굿즈 투표 집계·중복 투표 검증 |
| **`banners`** | `is_active=false` 또는 DELETE 후 **1년** | ERD `is_active` 사용 |
| **`partners`** (입점) | `REJECTED`·만료 초대 **1년**, `APPROVED`는 영구 메타만 | `invitation_token` 만료 후 정리 · Admin audit |
| **`fan_artist`** | 탈퇴 시 관계 **DELETE** | 아티스트별 가입 팬 수 집계만 유지 가능 |
| **`notifications`** | `sent_at` 기준 **1년** 후 DELETE | 읽음 상태 없음 — [ERD §9](./erd-design.md#9-banner--restock_alert--notification-팬-알림함) |

---

## 3. Audit — 무엇을 남길지

Audit 레코드는 **`audit_logs`** (append-only, 수정·삭제 API 없음). 보관 **1년**.  
목적: 관리자 수동 복구·환불 조작 추적·결제 리컨실·보안 사고 조사.

### 3.1 공통 필드

| 필드 | 설명 |
| --- | --- |
| `id` | UUID |
| `occurred_at` | UTC |
| `actor_type` | `FAN` / `ADMIN` / `SYSTEM` / `WEBHOOK` |
| `actor_id` | fan_id, admin_id, `system`, PG 식별자 |
| `action` | 도메인 동사 (`ORDER_STATE_CHANGE`, `ADMIN_BANNER_UPDATE`, …) |
| `resource_type` | `ORDER`, `PAYMENT`, `PRODUCT`, … |
| `resource_id` | 대상 PK |
| `trace_id` | [공통 응답 traceId](../api/mvp-api-spec.md#공통-규칙) |
| `before_json` / `after_json` | 변경 전·후 스냅샷 (PII 마스킹 후 저장) |
| `reason` | 선택 (취소 사유, Admin 메모) |
| `client_ip` | Admin·의심 요청만 (90일 후 IP 필드 null) |

### 3.2 Admin API (도메인 오너 · 지영재)

**대상:** `/admin/**` 전 엔드포인트 ([MVP API](../api/mvp-api-spec.md#admin))

| 기록 항목 | 예시 |
| --- | --- |
| who | `admin_id`, role |
| when | `occurred_at` |
| what | `PATCH /admin/main-banners/{id}` 또는 `PATCH /admin/artist-applications/{id}` |
| before / after | `{ "title": "…", "imageUrl": "…" }` JSON diff |

오너 예: 입점 심사 승인·반려는 표지민, F04-03 메인 배너 Admin은 표지민 (`user`), 모니터링/운영성 조회는 지영재.

### 3.3 주문 상태 변경 (형성빈 · 장성재)

[상태 머신](../state/invariants-and-state-machines.md) 전이마다 SYSTEM/서비스 계정으로 기록.

```json
{
  "action": "ORDER_STATE_CHANGE",
  "resource_type": "ORDER",
  "resource_id": "ord_abc",
  "before_json": { "status": "RESERVED" },
  "after_json": { "status": "PAID" },
  "reason": "WEBHOOK_PAYMENT_SUCCESS",
  "trace_id": "tr-xyz"
}
```

| 전이 | `reason` 예시 |
| --- | --- |
| `RESERVED` → `PAID` | `WEBHOOK_PAYMENT_SUCCESS` |
| `RESERVED` → `FAILED` | `PAYMENT_DECLINED` |
| `FAILED` → `CANCELLED` | `SAGA_COMPENSATION_COMPLETE` |
| `RESERVED` → `CANCELLED` | `USER_CANCEL` / `PAYMENT_TIMEOUT` |
| `PAID` → `COMPLETED` | `INVENTORY_CONFIRMED` |

`FAILED` 정체·보상 실패는 **알람** + audit `SAGA_COMPENSATION_FAILED`.

### 3.4 결제 웹훅 수신 (장성재)

`payment_webhook_events`와 **별도**로 audit에 **처리 결과 요약**을 남긴다 (원본 90일, audit 1년).

| 필드 | 설명 |
| --- | --- |
| `event_id` | PG 이벤트 ID |
| `idempotency_key` | `tossPaymentKey` / `payment_key` |
| `processing_result` | `SUCCESS` / `DUPLICATE_SKIPPED` / `FAILED` |
| `retry_count` | 수신·처리 재시도 횟수 |
| `order_id` | 연관 주문 |

### 3.5 Audit 보관

| 항목 | 기간 |
| --- | --- |
| **`audit_logs` 전체** | **1년** |
| 만료 후 | cold storage(S3 Glacier) **선택 이관** 또는 삭제 (팀·법무 합의) |

---

## 4. 개인정보 — 마스킹 · 최소 저장 (요약)

상세 라이프사이클은 [data-lifecycle.md §4](./data-lifecycle.md#4-개인정보--마스킹--최소-저장).

| 데이터 | 최소 저장 | 마스킹 시점 |
| --- | --- | --- |
| 이메일 | 가입·주문·영수증 발송에 필요한 기간 | 탈퇴 유예 종료 · 주문 보관 만료 |
| 비밀번호 (팬) | 이메일 가입 시 **해시만 저장**. 재설정 토큰은 TTL·1회성으로 저장 | 비밀번호 재설정 완료 또는 토큰 만료 |
| 비밀번호 (AGENCY_ACCOUNT·ARTIST_MEMBER) | `password_hash`만 저장 | — |
| 소셜 `providerToken` | **저장 금지** (검증 직후 폐기) | — |
| 카드·계좌 | **PG 토큰만**, PAN 저장 금지 | — |
| IP (로그·audit) | 90일 원문 → 이후 null | 배치 |

---

## 5. 배치 · 책임

| Job | 주기 | 담당 | 동작 |
| --- | --- | --- | --- |
| `RetentionPurgeJob` | 일 1회 | 지영재 (플랫폼) | §2 만료 데이터 삭제·archive |
| `WebhookPayloadTruncateJob` | 일 1회 | 장성재 | 90일 초과 payload null |
| `OutboxCleanupJob` | 일 1회 | 표지민 | 30일 초과 outbox 삭제 |
| `AuditArchiveJob` | 월 1회 | 지영재 | 1년 초과 audit cold storage |

실행 전 **dry-run 카운트** 로그, prod는 **승인된 배치 윈도우**만.

---

## 6. MVP 적용 범위 체크리스트

| 항목 | MVP | Phase 2 |
| --- | --- | --- |
| orders / order_items 3년 | ✅ | |
| payments / webhook 90일→요약 | ✅ | |
| outbox 30일 | ✅ | |
| 대기열 Redis 180일 (스냅샷) | ✅ | |
| notifications 1년 | ✅ | |
| audit_logs 1년 | ✅ | |
| application_logs 14~30일 | ✅ | |
| reservations / seats archive | — | ✅ |
| 수동 환불 audit | — | ✅ |

---

## 7. 관련 문서

| 문서 | 경로 |
| --- | --- |
| 데이터 라이프사이클 | [data-lifecycle.md](./data-lifecycle.md) |
| ERD | [erd-design.md](./erd-design.md) |
| 상태 머신 | [../state/invariants-and-state-machines.md](../state/invariants-and-state-machines.md) |
| API | [../api/mvp-api-spec.md](../api/mvp-api-spec.md) |

정책 변경 시 **법무·세무·도메인 오너** 합의 후 본 문서·라이프사이클·ERD를 동시에 갱신한다.
