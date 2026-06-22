    # ADR-010: 대기열 실시간 알림 전략 — SSE 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-22 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) |
| **관련** | [invariants §6 WAIT_QUEUE](../state/invariants-and-state-machines.md) · [mvp-api-spec § Wait Queue](../api/mvp-api-spec.md) |
| **담당** | 장성재 (`payment` — 대기열·Access Ticket) |

---

## Title

드롭스 오픈런 대기열의 실시간 순번·Access Ticket 알림은 **SSE(Server-Sent Events)** 로 구현한다. Short Polling, Long Polling, WebSocket은 채택하지 않는다.

---

## Context

드롭스 상품 오픈 시 수천 명의 팬이 동시에 대기열에 진입한다. 각 팬은 자신의 현재 순위와 Access Ticket 발급 여부를 실시간으로 알아야 한다.

요구사항을 정리하면 다음과 같다.

- **통신 방향**: 서버 → 클라이언트 단방향 (순번·상태 push). 클라이언트가 서버로 보낼 메시지 없음.
- **동시 연결 규모**: 드롭스 오픈런 최대 2,000 동시 연결 (`fandrops.ratelimit.sse-max-emitters: 2000`).
- **인프라 제약**: ADR-001에서 Redis는 대기열·캐시 용도로 한정. 추가 인프라(메시지 브로커 등) 도입은 MVP 스코프 밖.
- **구현 비용**: Spring MVC 내장 기능 범위에서 해결. 별도 라이브러리·프로토콜 전환 최소화.

실시간 알림 전략으로 네 가지를 검토했다.

---

## 방안 비교

### A안 — Short Polling (클라이언트 주기적 GET)

클라이언트가 N초마다 `GET /api/v1/queue/status`를 반복 호출한다.

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 낮음 — 표준 REST, 추가 인프라 없음 |
| 서버 부하 | 높음 — 1,000 VU × 1 req/3s = **333 RPS 추가 트래픽** (대기 중 아무 변화 없어도 동일) |
| 레이턴시 | 폴링 주기(= N초) 만큼 지연 |
| 서버 메모리 | 커넥션 상시 유지 없음 — 유리 |
| **기각 이유** | 대기열 상태 변화 없는 구간에서도 동일 트래픽이 발생한다. 오픈런 집중 구간에 불필요한 RPS가 추가되어 DB·Redis 부하를 가중시킨다. 레이턴시가 폴링 주기에 종속되어 순번 업데이트를 즉시 전달할 수 없다. |

### B안 — Long Polling (서버 응답 보류)

클라이언트가 요청하면 서버는 상태 변화가 생길 때까지 응답을 보류하다가 변화 발생 시 반환한다.

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 높음 — 응답 보류·타임아웃·재연결 처리 직접 구현 필요 |
| 서버 부하 | Short Polling보다 효율적이나 커넥션 점유 동일 |
| 레이턴시 | 변화 즉시 전달 가능 |
| 서버 메모리 | Short Polling 대비 커넥션 유지 시간 길어짐 |
| **기각 이유** | SSE 대비 구현 복잡도가 높으면서 얻는 이점이 없다. Spring에서 Long Polling은 `DeferredResult`/`Callable` 기반으로 별도 구현이 필요하고, 재연결 로직도 직접 처리해야 한다. SSE(`SseEmitter`)가 동일한 효과를 더 단순하게 제공한다. |

### C안 — WebSocket (양방향 full-duplex)

WebSocket 프로토콜로 서버-클라이언트 간 양방향 채널을 연다.

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 높음 — STOMP or 순수 WebSocket, Nginx `proxy_pass upgrade` 설정 추가 필요 |
| 서버 부하 | SSE와 유사한 커넥션 유지 비용 |
| 레이턴시 | 변화 즉시 전달 가능 |
| 통신 방향 | 양방향 — **대기열 use-case에서 클라이언트→서버 방향은 불필요** |
| **기각 이유** | 대기열 알림은 서버→클라이언트 단방향 push가 전부다. WebSocket의 양방향 복잡도(handshake 업그레이드, 프레이밍, Nginx 설정 변경)는 이 use-case에 과잉이다. ADR-001 §단순성 원칙에 따라 채택하지 않는다. |

### D안 — SSE (Server-Sent Events) ← **채택**

HTTP 기반 서버→클라이언트 단방향 이벤트 스트림. Spring MVC `SseEmitter` 내장 지원.

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 낮음 — `SseEmitter` 내장, 추가 라이브러리 없음 |
| 서버 부하 | 커넥션 유지 → 서버 메모리 점유 (수용 가능, 상한으로 제어) |
| 레이턴시 | 변화 즉시 전달 가능 |
| 통신 방향 | 단방향 — 대기열 use-case에 정확히 부합 |
| 재연결 | 브라우저 `EventSource` API 자동 재연결 내장 |
| 단점 | stale 커넥션이 누적되면 상한에 조기 도달 → heartbeat로 제거 필요 |

---

## Decision

**D안(SSE) 채택.**

대기열 통신은 서버→클라이언트 **단방향 push만 필요**하므로 WebSocket 양방향 복잡도는 불필요하다. Short Polling은 변화 없는 구간에서도 트래픽을 생성해 오픈런 집중 부하를 가중시킨다. Spring MVC `SseEmitter`로 추가 라이브러리 없이 구현하고, `SseEmitterRegistry`에서 연결 상한(2,000)을 명시적으로 제어한다.

---

## 인프라 부하 포인트와 대응

SSE 채택 후 세 가지 컴포넌트에서 부하가 집중된다.

### Redis (대기열 상태 저장소)

```
역할: 팬의 대기 순번·Access Ticket을 ZSET/String으로 저장 (영속)
부하 성격: 오픈런 진입 순간 ZADD·ZRANK·SETEX 집중
```

SSE 연결이 없는 팬(네트워크 재연결 중)도 대기열 순번을 유지해야 한다. 이를 위해 **Redis = 대기열 상태의 영속 저장소**, **SSE = 실시간 push 채널(휘발)** 로 역할을 분리했다. `QueueAdvanceScheduler.tick()`이 SSE 연결 목록과 Redis WAITING 목록의 합집합을 순회(`registry.getActiveProductIds() ∪ waitQueueService.getActiveProductIds()`)하므로 SSE 단절 팬도 처리된다.

Redis 자체는 인메모리 DB로 단순 read/write는 빠르지만, 대용량 데이터를 장기간 보관하기에 적합하지 않다. 대기열 데이터는 TTL을 설정해 자동 만료시키고(`data-retention-and-audit-policy.md` 대기열 TTL 참고), 누적 방지 정책을 유지한다.

### SSE 커넥션 (서버 메모리)

```
역할: 팬에게 순번·DONE 이벤트 push
부하 성격: 커넥션당 서버 스레드·메모리 점유, stale 커넥션 누적 위험
```

k6 s05(SSE 대기열) 측정에서 stale emitter가 누적되어 2,000 상한에 조기 도달, 정상 구간(1,000 VU)에서 `SseCapacityExceededException` → 429가 발생했다. 브라우저는 disconnect를 서버에 알리지 않고, Spring은 `onTimeout(60s)` 전까지 stale을 감지하지 못하기 때문이다.

**해결:** `SseEmitterRegistry.sendHeartbeat()`를 5초 주기로 실행한다. 이미 끊어진 연결에 write 시 발생하는 `IOException`·`IllegalStateException`을 catch해 즉시 `emitters.remove()`한다. 이로써 `onTimeout(60s)` 만료 대기 없이 stale을 5초 이내에 정리한다.

```java
// SseEmitterRegistry
public void sendHeartbeat() {
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
        try {
            entry.getValue().send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(entry.getKey());
        }
    }
}

// QueueAdvanceScheduler
@Scheduled(fixedDelayString = "${fandrops.queue.scheduler.heartbeat-ms:5000}")
public void heartbeat() {
    registry.sendHeartbeat();
}
```

### Scheduler 스레드 (tick·heartbeat 경합)

```
역할: tick(3s) — 대기열 팬 승급 처리 / heartbeat(5s) — stale emitter 제거
부하 성격: Spring @EnableScheduling 기본 스케줄러는 단일 스레드 — heartbeat가 tick을 블로킹 가능
```

1,000 VU 환경에서 `sendHeartbeat()`는 최대 2,000개 emitter에 동기 I/O를 수행한다. 단일 스레드라면 heartbeat 실행 중 tick이 대기하여 3s 주기를 지키지 못할 수 있다.

**해결:** `spring.task.scheduling.pool.size: 2`로 스케줄러 스레드 풀을 2개로 분리한다. heartbeat와 tick이 별도 스레드에서 독립 실행된다.

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2
```

---

## 부하 포인트 요약

| 컴포넌트 | 부하 성격 | 대응 |
| --- | --- | --- |
| Redis ZSET | 오픈런 순간 read/write 집중 | SSE(휘발)와 역할 분리, TTL로 누적 방지 |
| SseEmitter | 커넥션당 메모리 점유, stale 누적 | 상한 2,000 + heartbeat 5초 주기 즉시 제거 |
| Scheduler | tick/heartbeat 단일 스레드 경합 | `pool.size: 2`로 스레드 분리 |

---

## Consequences

### 긍정

- 단방향 push use-case에 최적화된 프로토콜 — WebSocket 양방향 복잡도 제거
- Short Polling 대비 오픈런 집중 구간 불필요 트래픽 제거
- Spring MVC `SseEmitter` 내장 — 추가 라이브러리·인프라 없음
- `SseEmitterRegistry`에서 상한(2,000)·등록·해제를 단일 지점에서 제어
- 브라우저 `EventSource` 자동 재연결 — 클라이언트 구현 단순화

### 부정 · 수용

- 커넥션 유지로 서버 메모리 점유 → 상한(2,000) + heartbeat로 수용 범위 내 관리
- HTTP/1.1 브라우저당 도메인 연결 수 제한(6개) — 대기열은 탭 1개 사용 전제, 실운영 영향 없음
- stale emitter 제거가 heartbeat 주기(5s)에 종속 → 순간 상한 초과 후 정리까지 최대 5s 지연

### 불변

| 규칙 | 내용 |
| --- | --- |
| SSE 상한 초과 응답 | `429 + retryable: true` — api-contract.md 계약 유지 |
| 대기열 상태 저장소 | Redis 단독 — SSE 단절 팬도 순번 보장 |
| 순번 최종 판정 | `ORDER.status` 기준 — 대기열 DONE ≠ 주문 성공 (invariants W-2) |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 대기열 불변식 | [invariants-and-state-machines.md §6](../state/invariants-and-state-machines.md) |
| Wait Queue API 스펙 | [mvp-api-spec.md § Wait Queue](../api/mvp-api-spec.md) |
| SSE 상한·RateLimit 계약 | [api-contract.md](../api/api-contract.md) |
| Redis 장애 정책 | [failure-policy.md §3.1](../operations/failure-policy.md) |
| k6 SSE 부하 측정 결과 | [k6-tuned-results.md § s05](../operations/k6-tuned-results.md) |
