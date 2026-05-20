# 데이터 라이프사이클 (Data Lifecycle)

> **보관·Audit 상세:** [data-retention-and-audit-policy.md](./data-retention-and-audit-policy.md) (§2 데이터 보관 · §3 Audit)  
> **서비스:** 중소형 K-Pop 기획사 대상 **B2B2C 팬덤 커머스·이벤트** — 오픈런 주문/결제 정합성, 드롭 종료 후 데이터 축소, 팬 개인정보 최소화.

본 문서는 **시간이 지난 뒤 서비스 데이터가 어떻게 생성·변경·휴면·보관·삭제되는지**를 단계별로 정의한다.

---

## 1. 라이프사이클 단계

```
[Create] → [Active] → [비노출·삭제] → [Archive] → [Anonymize / Purge]
```

| 단계 | 의미 | 대표 대상 |
| --- | --- | --- |
| **Active** | 읽기·쓰기 정상 | 진행 중 주문, `BANNER.is_active=true`, 대기열 `WAITING` |
| **비노출·삭제** | ERD 컬럼 기준 처리 (`deleted_at` **미사용**) | 배너 `is_active=false`, 콘텐츠 **물리 삭제**, 팬 **PII 마스킹** |
| **Archive** | 운영 DB에서 분리 | Phase 2 좌석, Redis 대기열 스냅샷(선택) |
| **Anonymize** | PII 치환 | 탈퇴 `fans.email` / `nickname` |
| **Purge** | 물리 삭제 | outbox, 웹훅 원본, 만료 알림 |

**거래 핵심(`orders`, `payments`)** 은 상태 전이(`COMPLETED` / `CANCELLED`) + audit + 보관 기간만 사용한다.

---

## 2. 비노출·삭제 정책 (ERD 컬럼 기준)

> MVP ERD에는 `deleted_at` / `anonymized_at` **없음**. 아래는 ERD 필드·물리 삭제로 표현한다.

| 엔티티 | ERD 기준 처리 | 비고 |
| --- | --- | --- |
| `orders`, `order_items`, `payments` | 상태 전이만 | soft delete 없음 |
| `carts`, `cart_items` | 주문 성공 시 삭제 · 탈퇴 cascade | [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) |
| `fans` | 탈퇴 시 `email`·`nickname` **마스킹**(동일 행) | 30일 유예 후 irreversible |
| `products` | Admin **DELETE** 또는 드롭 종료(`drops_end_at` 경과) | 이력은 `order_items` |
| `banners` | `is_active=false` 또는 DELETE | |
| `feeds`, `notices`, `comments` | 작성자·Admin **DELETE** | 90일 후 purge Job |
| `hearts` | 부모 삭제 시 연쇄 DELETE | |
| 드롭스 대기열 (Redis) | TTL · 집계 | [ERD §10](./erd-design.md#10-드롭스-대기열--redis-db-erd-미포함) |
| `payment_webhook_events` | 기간 만료 purge | ERD PNG 외 |

---

## 3. 도메인별 라이프사이클

### 3.1 주문 · 결제 (Commerce)

드롭스 시나리오에서 가장 긴 보관이 필요한 축이다.

| 단계 | `orders` / `order_items` | `payments` |
| --- | --- | --- |
| 생성 | `POST /orders` → `RESERVED` (또는 TX 내 `PENDING`) | 결제 행 `PENDING` 생성 |
| 활성 | 결제·배송(Not Scope)·CS 조회 | confirm · 웹훅 처리 |
| 종료 | `COMPLETED` / `CANCELLED` (**되돌릴 수 없음**) | `SUCCESS` / `FAILED` |
| 보관 | 종료 후 **3년** Active 조회 | 동일 + 웹훅 원본 **90일** |
| 만료 | anonymize → purge 또는 archive | 요약 필드만 3년 |

상태 전이·Saga는 [상태 머신](../state/invariants-and-state-machines.md), audit는 [§3.3 보관 정책](./data-retention-and-audit-policy.md#33-주문-상태-변경-형성빈--장성재).

#### 장바구니 (`carts` / `cart_items`)

| 단계 | 동작 |
| --- | --- |
| Active | 담기·수량 변경·조회 — **MySQL RDB** ([ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md)) |
| 주문 연동 | `POST /orders` 성공 후 해당 `cart_item` 삭제(또는 수량 차감) |
| 종료 | 팬 탈퇴·anonymize 시 `cart` cascade 삭제 |

Redis 장바구니·TTL 자동 만료는 Phase 1 **미사용**.

### 3.2 대기열 (드롭스, Redis)

DB ERD에는 없음 — [erd-design §10](./erd-design.md#10-드롭스-대기열--redis-db-erd-미포함).

| 단계 | Redis 대기열 키 |
| --- | --- |
| Active | `WAITING` → `PROCESSING` |
| 종료 | `DONE` / `EXPIRED` (주문 성공 여부는 `ORDER.status` 참조) |
| 보관 | 종료 후 **180일** (선택 스냅샷·집계만) |
| Purge | TTL 만료 또는 일 단위 집계 |

### 3.3 알림 · Outbox

| 단계 | `notifications` (팬 알림함) | `outbox_events` |
| --- | --- | --- |
| Active | `sent_at` 기록 후 조회 | `PENDING` → 발행 |
| 종료 | — (append 위주) | `published_at` 설정 / `FAILED` |
| Purge | **1년** 후 삭제 (PII 마스킹 선행) | **30일** 후 삭제 또는 cold storage |

### 3.4 이벤트 · 예약 (Phase 2)

MVP: [행사는 외부 티켓 링크](./erd-design.md#8-schedule--artist_schedule)만 — `reservations` / `seats` 테이블 없음.

| 단계 | Phase 2 `reservations`, `seats` |
| --- | --- |
| Active | 이벤트 전·당일 |
| 종료 | 이벤트 `end_at` 경과 |
| Archive | **종료 후 180일** 운영 DB 유지 |
| 이동 | `reservations_archive`, `seats_archive` |
| Purge | archive 보관 기간(팀 합의, 기본 3년) 후 anonymize |

### 3.5 커뮤니티 · 콘텐츠

[ERD §5](./erd-design.md#5-커뮤니티--artist_space-feed-notice-comment-heart-attendance) · `ARTIST_SPACE`, `FEED`, `NOTICE`, `COMMENT`, `HEART`, `ATTENDANCE_EVENT`, `ATTENDANCE_CHECK`

| 데이터 | Active | 비노출·삭제 | Purge |
| --- | --- | --- | --- |
| `feeds`, `notices`, `comments` | 노출 중 | 작성자·Admin **DELETE** | 90일 후 연관 `hearts` 정리 |
| `hearts` | 반응 중 | — | 피드·댓글 purge 시 연쇄 또는 고아 정리 |
| `attendance_events`, `attendance_checks` | 프로모션 기간 | 이벤트 종료 | 1년 후 대상자 집계만 유지 |
| `goods_polls`, `goods_poll_options`, `votes` | 굿즈 투표 기간 | 투표 종료 | 1년 후 원본 purge 또는 집계만 유지 |
| `artist_schedules` (LIVE 등) | 방송·일정 중 | 종료 | 1년 |

---

## 4. 개인정보 · 마스킹 · 최소 저장

FANDROPS는 **10~30대 팬(B2C)** 과 **기획사(B2B)** 를 동시에 다루므로, 팬 PII는 주문·알림에 필요한 범위만 유지한다.

### 4.1 수집 최소화

| 항목 | 정책 |
| --- | --- |
| 회원가입 (팬) | `email`, `nickname`, `terms_agreed_at` 저장. 이메일 가입 시 `password_hash`만 저장하고, 소셜 `providerToken`은 저장하지 않음 ([ERD §4](./erd-design.md#4-partner--artist--artist_member--fan)) |
| B2B·멤버 | `PARTNER` / `ARTIST_MEMBER`의 `password` (해시) — ERD 컬럼 |
| 주문 | `fan_id`, 배송지(도입 시) — 주문 시점 스냅샷을 `order_items` 또는 `order_shipping_snapshot` |
| 결제 | PG 위임 — 카드번호·CVV **미저장** |
| 소셜 로그인 | `providerToken` **일회성 검증만**, DB 미저장 |
| 로그 | `traceId`, `fanId`(해시 가능), `orderId` — 이메일·토큰 **로그 금지** |

### 4.2 마스킹 규칙

| 필드 | 마스킹 예시 | 적용 시점 |
| --- | --- | --- |
| `email` | `a***@domain.com` | 탈퇴 anonymize, audit JSON 저장 시 |
| `nickname` | `fan_***` | 동일 |
| `phone` (Phase 2) | `010-****-1234` | 동일 |
| IP | null | 로그·audit 90일 후 |

### 4.3 탈퇴 · 보관 만료 흐름

```
fan 탈퇴 요청
  → 30일 유예 (행 유지, 로그인 차단)
  → FAN.email / FAN.nickname 마스킹 (동일 PK)
  → orders는 fan_id 유지 (3년 보관)
  → 3년 후 order purge/archive (보관 정책 §2.1)
```

**주문이 있는 팬**은 탈퇴 즉시 행 삭제하지 않는다 — [보관 정책](./data-retention-and-audit-policy.md#21-거래커머스-형성빈--장성재)과 충돌 방지.

---

## 5. 로그 · Audit 데이터 생명주기

| 종류 | 생성 | Active 조회 | 보존 | 종료 |
| --- | --- | --- | --- | --- |
| **application_logs** | 모든 API | Grafana 14~30일 | 동일 | 자동 만료 |
| **audit_logs** | Admin·상태전이·웹훅 요약 | Admin·SRE 1년 | 1년 | Archive 또는 purge |
| **payment_webhook_events** | PG 수신 | 리컨실 90일 | 90일 원본 | 요약만 3년 |

Audit 스키마·이벤트 종류: [data-retention-and-audit-policy §3](./data-retention-and-audit-policy.md#3-audit--무엇을-남길지).

---

## 6. Archive 전략

| 대상 | 트리거 | archive 위치 | 운영 DB |
| --- | --- | --- | --- |
| 대기열 Redis 스냅샷 (선택) | 180일 | Parquet S3 등 | 키 TTL |
| `reservations`, `seats` (P2) | 이벤트+180일 | `*_archive` 테이블 | DELETE |
| `orders` (만료) | 3년 | S3 + 메타 DB 또는 `_archive` | anonymize 후 DELETE |
| `audit_logs` | 1년 | Glacier | DELETE (정책 합의 후) |

Archive 테이블은 **INSERT only**, 애플리케이션 일반 API에서 조회하지 않는다. CS·법무는 별도 read role.

---

## 7. 시간축 요약 (MVP)

```
Day 0     주문·결제·웹훅·audit 생성
Day 1~30  outbox purge / 로그 롤링
Day 90    webhook raw_payload purge
Day 180   대기열 Redis 스냅샷·집계 purge (운영 정책)
Day 365   audit_logs archive 검토 / notifications purge
Year 3    orders·payments 요약 만료 → anonymize / archive
```

Phase 2 이벤트 종료 시점을 T라면: **T+180일** archive, **T+3년** (합의) purge.

---

## 8. 구현 · 운영

| 항목 | 내용 |
| --- | --- |
| 스케줄러 | `RetentionPurgeJob` — [보관 정책 §5](./data-retention-and-audit-policy.md#5-배치--책임) |
| 설정 | `fandrops.retention.*` in `application-prod.yml` |
| 모니터링 | purge 건수, `FAILED`·`PAID` 정체, audit 적재 실패 알람 |
| 문서 동기화 | 보관 기간 변경 → 본 문서 + API·상태머신 영향 검토 |

---

## 9. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [data-retention-and-audit-policy.md](./data-retention-and-audit-policy.md) | 보관 기간·audit 필드 SSOT |
| [erd-design.md](./erd-design.md) | 테이블·Not Scope |
| [invariants-and-state-machines.md](../state/invariants-and-state-machines.md) | 주문 종료 상태 |
| [mvp-api-spec.md](../api/mvp-api-spec.md) | Admin·웹훅 API |
