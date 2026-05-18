# 데이터 라이프사이클 (Data Lifecycle)

> **보관·Audit 상세:** [data-retention-and-audit-policy.md](./data-retention-and-audit-policy.md) (§5 데이터 보관·audit 정책)  
> **서비스:** 중소형 K-Pop 기획사 대상 **B2B2C 팬덤 커머스·이벤트** — 오픈런 주문/결제 정합성, 드롭 종료 후 데이터 축소, 팬 개인정보 최소화.

본 문서는 **시간이 지난 뒤 서비스 데이터가 어떻게 생성·변경·휴면·보관·삭제되는지**를 단계별로 정의한다.

---

## 1. 라이프사이클 단계

```
[Create] → [Active] → [Soft Delete?] → [Archive] → [Anonymize / Purge]
              ↑              ↑              ↑
         비즈니스 사용   UI 비노출      cold storage
```

| 단계 | 의미 | 대표 대상 |
| --- | --- | --- |
| **Active** | 읽기·쓰기 정상 | 진행 중 주문, 노출 중 배너, 대기열 `WAITING` |
| **Soft Delete** | `deleted_at` 설정, **API·목록에서 숨김**, DB 행 유지 | 팬 탈퇴 요청, 배너 삭제, (선택) 댓글 |
| **Archive** | 운영 DB에서 분리, 조회는 배치·CS·법무만 | 종료 이벤트 좌석(Phase 2), 오래된 `wait_queue` |
| **Anonymize** | PII 제거·치환, 통계·분쟁 키만 유지 | 보관 만료 `orders` 연관 fan 스냅샷 |
| **Purge** | 물리 삭제 | outbox, 웹훅 원본, 만료 로그 |

**거래 핵심(`orders`, `payments`)은 Soft Delete를 사용하지 않는다.** 종료는 `COMPLETED` / `CANCELLED` + audit + 보관 기간으로만 표현한다.

---

## 2. Soft Delete 정책

| 엔티티 | Soft Delete | 필드 | 복구 | 비고 |
| --- | --- | --- | --- | --- |
| `orders`, `order_items` | **❌** | — | — | 상태 전이만 허용 |
| `payments` | **❌** | — | — | |
| `fans` | **✅** | `deleted_at`, `anonymized_at` | 30일 유예 내 CS만 | 이후 이메일·닉네임 마스킹 |
| `products` | **✅** (운영자) | `deleted_at` | Admin | 판매 이력은 `order_items`에 스냅샷 |
| `banners` | **✅** | `deleted_at` | Admin | |
| `posts`, `comments` (community) | **✅** | `deleted_at` | 작성자·Admin | 90일 후 본문 purge |
| `wait_queue` | **❌** | — | — | `EXPIRED`/`DONE` 후 retention 삭제 |
| `payment_webhook_events` | **❌** | — | — | 기간 만료 시 purge |

**쿼리 규칙:** soft delete 대상은 기본 `WHERE deleted_at IS NULL`. Admin·audit 조회는 role 기반으로만 `deleted_at` 포함.

---

## 3. 도메인별 라이프사이클

### 3.1 주문 · 결제 (Commerce)

드롭·핫딜 시나리오에서 가장 긴 보관이 필요한 축이다.

| 단계 | `orders` / `order_items` | `payments` |
| --- | --- | --- |
| 생성 | `POST /orders` → `RESERVED` (또는 TX 내 `PENDING`) | 결제 행 `PENDING` 생성 |
| 활성 | 결제·배송(Not Scope)·CS 조회 | confirm · 웹훅 처리 |
| 종료 | `COMPLETED` / `CANCELLED` (**되돌릴 수 없음**) | `SUCCESS` / `FAILED` |
| 보관 | 종료 후 **3년** Active 조회 | 동일 + 웹훅 원본 **90일** |
| 만료 | anonymize → purge 또는 archive | 요약 필드만 3년 |

상태 전이·Saga는 [상태 머신](../state/invariants-and-state-machines.md), audit는 [§3.3 보관 정책](./data-retention-and-audit-policy.md#33-주문-상태-변경-형성빈--장성재).

### 3.2 대기열 (핫딜 F08-01)

| 단계 | `wait_queue` |
| --- | --- |
| Active | `WAITING` → `PROCESSING` |
| 종료 | `DONE` / `EXPIRED` (주문 성공 여부는 `ORDER.status` 참조) |
| 보관 | 종료 후 **180일** |
| Purge | Job 삭제 또는 일 단위 집계 |

### 3.3 알림 · Outbox

| 단계 | `notification_events` | `outbox_events` |
| --- | --- | --- |
| Active | `PENDING` → 전송 | 미발행 |
| 종료 | `SENT` / `FAILED` | `published_at` 설정 |
| Purge | 1년 후 (payload PII 마스킹 선행) | **30일** 후 삭제 또는 cold storage |

### 3.4 이벤트 · 예약 (Phase 2)

MVP: [행사는 외부 티켓 링크](./erd-design.md#4-wait_queue--product_id-단일-fk-확정)만 — `reservations` / `seats` 테이블 없음.

| 단계 | Phase 2 `reservations`, `seats` |
| --- | --- |
| Active | 이벤트 전·당일 |
| 종료 | 이벤트 `end_at` 경과 |
| Archive | **종료 후 180일** 운영 DB 유지 |
| 이동 | `reservations_archive`, `seats_archive` |
| Purge | archive 보관 기간(팀 합의, 기본 3년) 후 anonymize |

### 3.5 커뮤니티 · 콘텐츠

| 데이터 | Active | Soft Delete | Purge |
| --- | --- | --- | --- |
| 피드·댓글 | 노출 중 | 작성자/Admin 삭제 | 90일 후 |
| 랭킹 집계 | 이벤트 기간 | — | 원본 purge 후 집계만 유지 |
| 라이브 메타 | 방송 중 | 종료 | 1년 |

---

## 4. 개인정보 · 마스킹 · 최소 저장

FANDROPS는 **10~30대 팬(B2C)** 과 **기획사( B2B)** 를 동시에 다루므로, 팬 PII는 주문·알림에 필요한 범위만 유지한다.

### 4.1 수집 최소화

| 항목 | 정책 |
| --- | --- |
| 회원가입 | `email`, `nickname`, 비밀번호(해시) — 실명·주민번호 **수집 안 함** (MVP) |
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
  → soft delete (deleted_at)
  → 30일 유예 (분쟁·재가입)
  → email/nickname anonymize
  → orders는 fan_id 유지하되 PII 스냅샷만 마스킹 (3년 보관 정책까지)
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
| `wait_queue` (선택) | 180일 | 동일 스키마 `_archive` 또는 Parquet S3 | DELETE |
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
Day 180   wait_queue purge
Day 365   audit_logs archive 검토 / notification purge
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
