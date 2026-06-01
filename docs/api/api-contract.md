# API 계약 (공통 응답 · 페이지네이션)

> **엔드포인트 목록:** [mvp-api-spec.md](./mvp-api-spec.md)  
> **장애·재시도:** [../operations/failure-policy.md](../operations/failure-policy.md) · **에러 코드 ↔ HTTP:** 본 문서 §4

FANDROPS 공개 REST API(`/api/v1/**`)의 **응답 envelope·에러 코드·페이지네이션** SSOT.  
`modules/common` DTO·`api-server` 예외 핸들러는 본 계약을 따른다.

---

## 1. 공통 Envelope

모든 JSON 응답은 아래 구조를 따른다. HTTP status는 의미에 맞게 별도 설정한다.

### 1.1 성공

```json
{
  "success": true,
  "data": {
    "orderId": "ord_01HXYZ",
    "status": "RESERVED",
    "orderPaymentKey": "opk_abc"
  },
  "error": null,
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `success` | boolean | ✅ | 항상 `true` |
| `data` | object \| array \| null | ✅ | 리소스 본문. 목록 API도 `data` 안에 `items` 등을 둠 |
| `error` | null | ✅ | 성공 시 항상 `null` |
| `traceId` | string (UUID) | ✅ | 요청 상관 ID. 로그·[audit](../erd/data-retention-and-audit-policy.md)와 동일 값 |

### 1.2 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "OUT_OF_STOCK",
    "message": "재고가 부족합니다.",
    "retryable": false
  },
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `success` | boolean | ✅ | 항상 `false` |
| `data` | null | ✅ | 실패 시 항상 `null` |
| `error.code` | string | ✅ | **고정 enum** — `SCREAMING_SNAKE_CASE` ([§4](#4-에러-코드-목록)) |
| `error.message` | string | ✅ | 팬-facing 문구 (한국어). 동일 `code`라도 상황별 문구 가능 |
| `error.retryable` | boolean | ✅ | 클라이언트 **재시도 권장 여부** ([§5](#5-retryable-가이드)) |
| `traceId` | string | ✅ | 성공과 동일 |

> **구현 메모:** 과거 초안의 `error: { "code": "ERR_4004" }` 형태는 **`code` = 상수명(`OUT_OF_STOCK`)** 으로 통일한다. 내부 로그·메트릭에는 `ERR_4004` 라벨을 병행해도 된다.

### 1.3 204 No Content

`POST /auth/logout`, `DELETE` 등 본문 없는 성공은 **body 없음**. `traceId`는 응답 헤더 `X-Trace-Id`로 반환한다.

---

## 2. 페이지네이션 (커서)

목록 API (`GET /products`, `/orders`, `/fans/me/orders` 등) 공통.

### 2.1 요청

| 파라미터 | 타입 | 기본 | 설명 |
| --- | --- | --- | --- |
| `cursor` | string | null | 이전 응답의 `nextCursor`. 첫 페이지는 생략 |
| `size` | int | 20 | 1~50 (초과 시 400 `INVALID_REQUEST`) |

### 2.2 응답 (`data` 내부)

```json
{
  "success": true,
  "data": {
    "items": [ { "id": "prod_1", "name": "…" } ],
    "nextCursor": "eyJpZCI6InByb2RfMSJ9",
    "hasMore": true
  },
  "error": null,
  "traceId": "…"
}
```

| 필드 | 설명 |
| --- | --- |
| `items` | 현재 페이지 결과 (빈 배열 가능) |
| `nextCursor` | 다음 페이지 토큰. `hasMore: false`이면 `null` |
| `hasMore` | 추가 페이지 존재 여부 |

---

## 3. 시간 · 식별자

| 항목 | 규칙 |
| --- | --- |
| **시각** | ISO-8601 **UTC** — `2026-05-12T08:00:00Z` (`OffsetDateTime` 직렬화) |
| **리소스 ID** | 문자열 prefix 권장 — `ord_`, `pay_`, `fan_`, `prod_` |
| **금액** | 정수 **원(KRW)** — `amount: 15000` (소수 통화 Not Scope) |

---

## 4. 에러 코드 목록

[mvp-api-spec § 공통 에러](../api/mvp-api-spec.md#공통-에러-코드)와 1:1 대응.

| `error.code` | HTTP | `retryable` | 팬-facing `message` (예시) |
| --- | --- | --- | --- |
| `INVALID_TOKEN` | 401 | false | 로그인이 필요합니다. |
| `FORBIDDEN` | 403 | false | 접근 권한이 없습니다. |
| `INVALID_QUEUE_TICKET` | 403 | false | 대기열이 만료되었습니다. 다시 입장해 주세요. |
| `OUT_OF_STOCK` | 409 | false | 재고가 부족합니다. |
| `RESERVE_FAILED` | 409 | **true** | 잠시 후 다시 시도해 주세요. |
| `PAYMENT_FAILED` | 402 | false | 결제에 실패했습니다. |
| `DUPLICATE_PAYMENT` | 409 | false | 이미 처리된 결제입니다. |
| `ORDER_NOT_FOUND` | 404 | false | 주문을 찾을 수 없습니다. |
| `INVENTORY_NOT_FOUND` | 404 | false | 재고 정보를 찾을 수 없습니다. |
| `RATE_LIMITED` | 429 | **true** | 요청이 많습니다. 잠시 후 다시 시도해 주세요. |
| `INVALID_REQUEST` | 400 | false | 요청 형식이 올바르지 않습니다. |
| `INTERNAL_ERROR` | 500 | **true** | 일시적인 오류입니다. |
| `DB_LOCK_TIMEOUT` | 500 | **true** | 일시적인 오류입니다. |

내부 상수 매핑 예: `ERR_4004` → `OUT_OF_STOCK`.

---

## 5. `retryable` 가이드

| `retryable` | 클라이언트 동작 | 대표 `code` |
| --- | --- | --- |
| `true` | 지수 백오프 재시도 (1s → 2s → 4s, 최대 3회) | `RESERVE_FAILED`, `RATE_LIMITED`, `INTERNAL_ERROR`, `DB_LOCK_TIMEOUT` |
| `false` | 재시도 없이 UI 안내·다른 플로우 | `OUT_OF_STOCK`, `INVALID_QUEUE_TICKET`, `PAYMENT_FAILED` |

드롭스 주문(`POST /orders`)에서 `RESERVE_FAILED` 재시도 시 **동일 `accessTicket`** 으로만 재호출 ([상태 머신](../state/invariants-and-state-machines.md)).

---

## 6. 인증 헤더

```
Authorization: Bearer <accessToken>
X-Trace-Id: <optional client-propagated, 서버가 없으면 생성>
```

---

## 7. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [mvp-api-spec.md](./mvp-api-spec.md) | Method · Path · 도메인별 body |
| [failure-policy.md](../operations/failure-policy.md) | 장애 시 API·상태 동작 |
| [observability-metrics.md](../operations/observability-metrics.md) | 5xx·P95 SLO |

계약 변경 시 **OpenAPI(SpringDoc) + common 모듈 + 본 문서** 동시 PR.
