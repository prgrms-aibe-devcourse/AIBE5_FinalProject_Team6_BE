# 운영 메트릭 최소 세트 (Observability)

> **스택:** Prometheus + Grafana · Actuator — [ADR-001](../adr/ADR-001-multi-module-monolith.md)  
> **장애 대응:** [incident-response.md](./incident-response.md) · **SLO 근거:** 서비스 소개 — Write P95 &lt; 300ms, 5xx &lt; 0.1%

MVP에서 **반드시 수집·대시보드·알람**할 메트릭.  
핫딜(오픈런) 전 **Grafana 패널 + PagerDuty/Slack 연동** 완료를 DoD로 한다.

---

## 1. 메트릭 목록

| 메트릭 | Prometheus (예시) | 왜 필요한가 | 알람 등급 |
| --- | --- | --- | --- |
| HTTP **P95 / P99** | `http_server_requests_seconds` | 팬 **체감 성능** (주문·대기열·결제) | P1 if P95 > 300ms (Write, 10m) |
| **5xx 비율** | `rate(http_server_requests_total{status=~"5.."})` | **안정성 SLO** 0.1% | P0 if > 1% (5m) |
| **4xx 비율** (선택) | `status=~"4.."` | Rate limit·품절 UX 트래픽 | P2 |
| **DB active connections** | Hikari `hikaricp_connections_active` | **커넥션 고갈** 조기 경보 | P1 if > 80% pool (5m) |
| **DB slow query** | RDS Performance Insights / log | 락·풀스캔 | P2 |
| **Redis memory** | `redis_memory_used_bytes` | OOM·eviction | P1 |
| **Redis evicted keys** | `redis_evicted_keys_total` | 캐시·대기열 키 퇴출 | P1 if rate > 0 (드롭 중) |
| **Queue depth** | `fandrops_queue_waiting_total` (custom) | 대기열 **적체** | P1 (드롭 오픈) |
| **SSE active connections** | `fandrops_sse_connections` (custom) | 프록시·메모리 | P1 |
| **Outbox pending count** | `fandrops_outbox_pending` | **유실·지연** 신호 | P1 if > 1000 (15m) |
| **Outbox publish failures** | `fandrops_outbox_publish_failures_total` | 알림·후처리 실패 | P1 |
| **Cache hit ratio** | hits / (hits + misses) | DB 보호·피드 Read | P2 if < 80% (10m) |
| **Lock wait time** (가능 시) | InnoDB `innodb_row_lock_time` | **동시성 병목** | P1 |
| **Order state anomalies** | `fandrops_orders_status{status="FAILED"}` age | Saga **정체** | P0 if > 0 for 5m |
| **PG webhook latency** | `fandrops_webhook_process_seconds` | 결제 확정 지연 | P1 |
| **JVM heap / GC** | `jvm_memory_used_bytes` | 메모리 누수 | P2 |

### MVP에서 제외·Phase 2

| 메트릭 | 비고 |
| --- | --- |
| Kafka consumer lag | Kafka Not Scope — Outbox pending으로 대체 |
| k8s pod (EC2 MVP) | node_exporter 기본만 |

---

## 2. SLO 목표 (MVP)

| SLO | 목표 | 측정 창 |
| --- | --- | --- |
| Write API P95 | **&lt; 300ms** | 5m rolling |
| 5xx rate | **&lt; 0.1%** | 5m rolling |
| 결제 실패 후 일관 상태 | **&lt; 60s** | [상태 머신 SLO](../state/invariants-and-state-machines.md#43-paid-정체-재처리) |
| 오버셀 | **0건** | 비즈니스 리포트 |

부하 검증: **k6** 핫딜 시나리오 — [ADR-001](../adr/ADR-001-multi-module-monolith.md).

### 2.1 k6 · 장바구니 Phase 2 전환 트리거

[ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) — Phase 1은 RDB 장바구니. 아래를 k6·Prometheus에서 **반드시 수집**하고, 임계치 초과 시 Hybrid(Redis+RDB) 검토.

| 메트릭 (custom 권장) | 용도 | Phase 2 트리거 |
| --- | --- | --- |
| `fandrops_cart_item_write_seconds` (histogram) | 담기·수량 변경 P95 | P95 ≥ **주문 생성 TX P95 × 0.2** (20% 이상) |
| `hikaricp_connections_active` / `max` | 풀 점유율 | 핫딜 구간 **> 80%** 가 **5m** 지속 |
| `hikaricp_connections_active` | 절대값 | `maximum-pool-size` **≥ 90%** 근접 |

시나리오에 **장바구니 담기 burst** + **주문 생성**을 함께 넣어 `cart_item`이 실제 병목인지 검증한다. 미충족 시 RDB 유지.

---

## 3. 대시보드 구성 (Grafana)

| Row | 패널 |
| --- | --- |
| **Overview** | RPS, 5xx%, P95/P99, active users (선택) |
| **Commerce** | orders/min by status, reserve fail rate, `RESERVE_FAILED` count, `cart_item` write P95 vs order TX P95 |
| **Payment** | webhook QPS, duplicate skip rate, `PAID` stuck count |
| **Queue** | depth, join rate, SSE connections, Redis health |
| **Data** | DB pool, slow queries, outbox pending |
| **Infra** | CPU, memory, disk (EC2/RDS) |

---

## 4. 로그 · trace 상관

| 항목 | 정책 |
| --- | --- |
| 구조화 로그 | JSON — `traceId`, `orderId`, `fanId`(해시 가능) |
| 보관 | prod **30일** — [data-lifecycle](../erd/data-lifecycle.md#5-로그--audit-데이터-생명주기) |
| 금지 | 이메일, 토큰, PG PAN |

`traceId` = [API 계약](../api/api-contract.md) 응답 필드와 동일.

---

## 5. 알람 → 장애 등급 매핑

| 알람 | 등급 |
| --- | --- |
| 5xx > 1% | P0 |
| FAILED order > 5m | P0 |
| Outbox pending > 1000 | P1 |
| Write P95 > 300ms | P1 |
| Redis down | P1 |
| Cache hit < 80% | P2 |

상세 대응: [incident-response.md](./incident-response.md).

---

## 6. 담당

| 영역 | 담당 |
| --- | --- |
| Prometheus·Grafana·알람 라우팅 | 지영재 |
| 커스텀 메트릭 (order, outbox, queue) | 도메인 오너 PR + 지영재 리뷰 |
| k6 · SLO 리포트 | 지영재 + 형성빈 (핫딜 시나리오) |

---

## 7. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [failure-policy.md](./failure-policy.md) | 장애 시 동작 |
| [api-contract.md](../api/api-contract.md) | traceId |
| [invariants-and-state-machines.md](../state/invariants-and-state-machines.md) | FAILED·PAID 모니터링 |
