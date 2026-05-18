# ERD 설계 문서

> **다이어그램:** [`erd.png`](./erd.png)  
> 핫딜·결제·대기열·알림 도메인의 테이블 관계 및 컬럼 설계 근거를 정리한다.

---

## 1. PRODUCT — `reserved_quantity` 분리

### 설계 결정

`stock_quantity`와 `reserved_quantity`를 **별도 컬럼**으로 분리한다.

### 근거

핫딜 오픈 순간 수천 명이 동시에 결제를 시도하는 환경에서, 결제 진행 중인 재고와 실제 판매 가능한 재고를 구분하지 않으면 **오버셀**이 발생한다.

| 컬럼 | 의미 | 변경 시점 |
| --- | --- | --- |
| `stock_quantity` | 실제 판매 완료 후 남은 재고 | 결제 최종 확정(`PAID` → `COMPLETED`) 시 차감 |
| `reserved_quantity` | 결제 진행 중 선점된 재고 | 재고 예약(lock) 시 증가, 결제 완료/취소 시 감소 |

### 오버셀 방지 공식

```
판매 가능 재고 = stock_quantity - reserved_quantity
```

> **주의:** 컬럼 분리만으로 오버셀이 막히지 **않는다**.  
> 유저 A, B가 동시에 `stock=10, reserved=5`를 읽으면 둘 다 예약 가능하다고 판단할 수 있다.  
> 재고 선점 시 **DB 비관락(`SELECT ... FOR UPDATE`)** 또는 **Redis 분산락**으로 동시성 충돌을 제어해야 한다.  
> 컬럼 설계와 락 전략이 **함께** 작동해야 오버셀 0이 보장된다.

---

## 2. ORDER — `status` 허용값 및 상태 성격 명시

### 설계 결정

`status` 컬럼에 가능한 값과 각 상태의 **성격**을 ERD 레벨에서 명시한다.

| 상태 | 성격 | 설명 |
| --- | --- | --- |
| `PENDING` | 일반 | 주문 생성 직후, 재고 예약 전 |
| `RESERVED` | 일반 | 재고 선점 완료, 결제 대기 중 |
| `PAID` | 일반 | PG 결제 승인 확정 시점 기록 |
| `FAILED` | **Transient (경유 상태)** | 결제 실패 직후, Saga 보상 실행 전 잠깐 머무는 중간 상태. **최종 상태가 아님** |
| `COMPLETED` | 최종 | 재고 차감 및 알림 발행까지 모든 후처리 완료 |
| `CANCELLED` | 최종 | 재고 부족 / 결제 실패 / 사용자 취소로 주문 종료 |

### `FAILED`가 존재하는 이유

Saga 보상 트랜잭션의 **트리거 기준**이 되는 상태다. `FAILED`로 저장된 레코드가 있어야 보상 실패 시 재처리 대상을 특정할 수 있다. 보상이 완료되면 반드시 `CANCELLED`로 전이한다.

---

## 3. PAYMENT — `failed_at` 독립 컬럼 및 `payment_key` 멱등성

### 설계 결정 1 — `failed_at` 분리

`paid_at`과 `failed_at`을 **분리된 컬럼**으로 둔다.

#### 근거

결제 실패 시점을 별도로 기록해야 **수동 리컨실리에이션(사후 검증)** 이 가능하다. SLO 문서에서 *"결제 실패 시나리오에서 60초 내 일관 상태 수렴, 수동 리컨실리에이션 로그"* 를 요구하므로 실패 시점 데이터가 DB에 반드시 남아 있어야 한다.

### 설계 결정 2 — `payment_key` Unique Index

`payment_key` 컬럼에 **Unique Index**를 설정하여 중복 웹훅 수신 시 **멱등성**을 보장한다.

#### 근거

토스페이먼츠는 웹훅을 **재전송**할 수 있다. 동일한 결제 건에 대해 웹훅이 두 번 수신됐을 때 `payment_key` Unique Index가 없으면 결제 확정 로직이 중복 실행되어 재고가 이중 차감되거나 알림이 중복 발송될 수 있다.

시퀀스 다이어그램의 `idempotent key` 처리가 ERD 레벨에서 `payment_key` Unique Index로 뒷받침된다.

```
시퀀스: 결제 요청 (with idempotent key)
        ↕ 연결
ERD:    PAYMENT.payment_key (Unique Index)
```

---

## 4. WAIT_QUEUE — `product_id` 단일 FK 확정

### 설계 결정

기존 `drop_target_id (PRODUCT or EVENT)` **다형성 설계를 폐기**하고, `product_id (FK → PRODUCT)` **단일 참조**로 확정한다.

### 근거

| 검토 항목 | 내용 |
| --- | --- |
| 기획서 Not Scope | 콘서트·팬미팅 티켓 예매는 외부 링크 제공만. 인앱 결제 없음 |
| 대기열 용도 | 핫딜 굿즈 드롭 트래픽 흡수 전용 (F08-01) |
| MVP 원칙 | 복잡도 최소화. EVENT 인앱 결제가 생기는 시점에 재설계 |

`target_type` 컬럼을 추가하는 다형성 참조 방식은 **Phase 2 이후** EVENT 인앱 결제가 확정될 때 검토한다.

---

## 5. WAIT_QUEUE — `status` 허용값 명시

```
WAITING | PROCESSING | DONE | EXPIRED
```

| 값 | 의미 |
| --- | --- |
| `WAITING` | 대기열 진입, 순번 대기 중 |
| `PROCESSING` | 진입 토큰 발행, 주문 진행 중 |
| `DONE` | 주문 프로세스 종료. 성공/실패 여부는 `ORDER.status`에서 판단. 대기열은 대기열 역할만 담당 |
| `EXPIRED` | 토큰 만료, 재진입 필요 |

### `DONE`의 범위

`DONE`은 **"대기열 흐름이 끝났음"** 을 의미할 뿐, 주문 성공을 의미하지 않는다. 주문 결과는 `ORDER.status`를 기준으로 판단한다. **대기열과 주문의 관심사를 분리**하는 것이 설계 원칙이다.

---

## 6. NOTIFICATION_EVENT — 재시도 대응 필드 보완

### 현재 설계

```
id / event_type / resource_id / occurred_at
```

### 권고 추가 필드

| 컬럼 | 타입 | 이유 |
| --- | --- | --- |
| `payload` | json | 알림 전송에 필요한 데이터를 이벤트 발행 시점에 스냅샷으로 저장. 재시도 시 원본 데이터 보장 |
| `status` | string | `PENDING` / `SENT` / `FAILED` — 전송 성공 여부 추적 |
| `retry_count` | int | 재시도 횟수 기록. 최대 재시도 초과 시 DLQ 이관 기준 |

### 근거

알림 전송은 외부 채널(이메일, 푸시) 의존성이 있어 실패 가능성이 있다. 재시도 없이 단순 발행만 하면 팬이 결제 완료 알림을 못 받는 상황이 발생한다. `retry_count`와 `status`가 있어야 실패한 이벤트를 추적하고 재처리할 수 있다.

### 예시

| event_type | resource_id | retry_count | status |
| --- | --- | --- | --- |
| `PAYMENT_SUCCESS` | `order_123` | 2 | `SENT` |
| `RESTOCK_ALERT` | `product_456` | 3 | `FAILED` → DLQ 이관 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| ERD 다이어그램 | [`erd.png`](./erd.png) |
| 데이터 보관 · Audit | [`data-retention-and-audit-policy.md`](./data-retention-and-audit-policy.md) |
| 데이터 라이프사이클 | [`data-lifecycle.md`](./data-lifecycle.md) |
| 멀티모듈 모놀리스 (ADR) | [`../adr/ADR-001-multi-module-monolith.md`](../adr/ADR-001-multi-module-monolith.md) |
| 레이어별 Gradle (ADR) | [`../adr/ADR-002-per-layer-gradle-modules.md`](../adr/ADR-002-per-layer-gradle-modules.md) |
| 불변조건 · 상태 머신 | [`../state/invariants-and-state-machines.md`](../state/invariants-and-state-machines.md) |
| 결제·주문 시퀀스 | [`../sequence/payment-flow-reason.md`](../sequence/payment-flow-reason.md) |
