# ADR-003: 장바구니 저장소 — Phase 1 RDB (`CART` / `CART_ITEM`)

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-19 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) |
| **관련** | [ERD §6 CART](../erd/erd-design.md#6-cart--cart_item) · [관측 메트릭 §2.1](../operations/observability-metrics.md#21-k6--장바구니-phase-2-전환-트리거) |
| **담당** | 형성빈 (`order` · `inventory`) |

---

## Title

MVP **Phase 1**에서 장바구니는 **MySQL RDB** (`CART`, `CART_ITEM`)에 저장한다. Redis-only 장바구니는 채택하지 않는다.

---

## Context

FANDROPS 핵심 KPI는 **오버셀 0 · 중복 주문 0 · 정합성**이다. 장바구니 저장소로 **Redis** vs **RDB** 트레이드오프를 검토했다.

| 관점 | Redis | RDB |
| --- | --- | --- |
| 성능 | 메모리 기반으로 읽기/쓰기 빠름, 드롭스 담기 트래픽을 DB 앞단에서 흡수 가능 | `cart_item` INSERT/UPDATE는 상대적으로 가벼우나, 주문·재고와 **동일 커넥션 풀** 경합 가능 |
| 정합성 | 장바구니 자체는 정합성 주체가 아님 — `POST /orders` 시 `ORDER` + `ORDER_ITEM` + `INVENTORY.reserve()` **단일 RDB TX**가 보장 | `FAN`·`PRODUCT`·`ORDER`와 **동일 영속 계층**, 장애 복구·디버깅 단순 |
| 운영 | Persistence 설정에도 RDB보다 유실 리스크, Hybrid 시 복잡도 증가 | MVP 구현·운영 난이도 낮음 |
| UX | 재시작·장애 시 담기 목록 소실 → 드롭스에서 **재담기 부담** 큼 | 로그인 사용자 **PC/모바일 간 목록 유지** 기대에 부합 |

프로젝트 원칙은 **측정 후 튜닝**이다. 드롭스 순간 실제 병목 후보는 대체로 다음 순이다.

1. 재고 `reserve` (`SELECT … FOR UPDATE` 또는 분산락)
2. 분산락·락 대기
3. `ORDER` INSERT

`cart_item` write는 **아직 측정되지 않은 상태에서** 병목으로 확정하고 Redis를 Phase 1에 도입하는 것은 원칙과 맞지 않다.

---

## Decision

### Phase 1 (MVP) — **RDB**

- ERD의 `CART` / `CART_ITEM` 테이블을 **SSOT**로 사용한다.
- 구현·운영은 `order` / `inventory` 모듈(형성빈) 소유.
- 주문 생성 시: 클라이언트가 넘긴 품목(또는 `cart_item` 조회 결과)으로 `POST /orders` → 재고 예약·주문 행은 **기존과 동일하게 단일 TX**. 장바구니 행 삭제/정리는 **같은 TX 또는 직후 애플리케이션 정책**으로 처리한다.

### Phase 2 — **실측 후 Redis Hybrid 검토**

k6 드롭스 부하 테스트(Phase 4)에서 아래 **하나 이상**이 확인되면 Hybrid(예: Redis = 실시간 조작, RDB = 영속 스냅샷) 전환을 **ADR 개정**으로 검토한다.

| # | 트리거 | 임계치 (합의) |
| --- | --- | --- |
| T1 | `cart_item` write P95 | **주문 생성 트랜잭션 P95의 20% 이상**을 차지 |
| T2 | DB 커넥션 풀 점유율 | 드롭스 구간 **80% 초과**가 **5분 이상** 지속 |
| T3 | HikariCP active connections | `maximum-pool-size`에 **근접**(운영 설정값 기준, 예: ≥90%) |
| T4 | `GET /cart` P95 | **120ms 초과** — 아이템 수(N)만큼 `productPricePort.getPrice()` 호출로 N+1 발생. 상품 API 구현 후 `getPrices(Set<Long>)` 벌크 메서드로 교체 또는 캐시 도입 검토 |

트리거 미충족 시 **RDB 유지**. Redis는 [ADR-001](./ADR-001-multi-module-monolith.md)대로 **대기열·캐시** 등 기존 용도만 사용한다.

---

## Consequences

### 긍정

- MVP 구현 속도·운영 단순성
- 장바구니·주문·회원(`fan_id`) 조인·CS 조회 용이
- 드롭스 중 장바구니 **소실 UX 리스크** 회피

### 부정 · 수용

- 장바구니 다건 쓰기가 늘면 DB write·커넥션 풀 부담 가능 → **k6으로 실측** 후 Phase 2 판단
- TTL 기반 자동 만료는 애플리케이션·배치로 구현 (Redis TTL 미사용)

### 불변 (Redis/RDB 선택과 무관)

| 규칙 | 내용 |
| --- | --- |
| 주문 정합성 | `POST /orders` = 주문 + 재고 예약 **단일 TX**, 성공 시 `RESERVED` — [SHARED](../ai/SHARED.md) |
| 장바구니 역할 | 주문 전 **임시 목록**; 오버셀·중복 주문 방지는 **INVENTORY + ORDER**가 담당 |
| Redis Phase 1 | **드롭스 대기열**, Read 캐시 등 — **장바구니 아님** |

---

## 구현 체크리스트 (형성빈)

- [x] `CART` 1:1 `FAN`, `CART_ITEM` FK·UK(`cart_id`, `product_id`) 적용
- [x] 담기/수량 변경/삭제 API는 `cart_item` RDB CRUD
- [ ] `POST /orders` 시 재고·주문 TX와 장바구니 정리 경계 문서화 (코드·PR)
- [ ] k6 시나리오에 `cart_item` write·pool 메트릭 포함 — [observability-metrics §2.1](../operations/observability-metrics.md#21-k6--장바구니-phase-2-전환-트리거)

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| ERD `CART` / `CART_ITEM` | [erd-design.md §6](../erd/erd-design.md#6-cart--cart_item) |
| 아키텍처 오너십 | [architecture.md § 도메인 오너십](../architecture/architecture.md) |
| ADR-001 (Redis 용도) | [ADR-001](./ADR-001-multi-module-monolith.md) |
| k6 · Phase 2 트리거 | [observability-metrics.md](../operations/observability-metrics.md) |

Phase 2 Hybrid 채택 시 **본 ADR 개정**(상태 Superseded 또는 Amend) + ERD·API·운영 메트릭 동시 갱신.
