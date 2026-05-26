## 📝 작업 내용

- [x] `POST /api/v1/queue/join/{productId}` — 대기열 등록 (`WAITING`)
- [x] `GET /api/v1/queue/status?productId=` — 순번·상태·`estimatedWaitSec` 조회
- [x] `payment-domain` — `WaitQueueStatus`, `WaitQueueEntry`, `WaitQueueRepository`(포트)
- [x] `payment-application` — `WaitQueueService`, `QueueJoinCommand`, `QueueStatusResult`
- [x] `payment-infrastructure` — `RedisWaitQueueRepository`, `LocalWaitQueueRepository`(fallback)
- [x] `payment-api` — `WaitQueueController`, 응답 DTO
- [x] `payment-infrastructure` Redis 의존성 추가

## 🧪 기술적 의사결정 및 검증

- **선택한 기술 및 배경:**
  - **저장소:** 드롭스 대기열은 RDB가 아닌 **Redis** ([erd-design §10](docs/erd/erd-design.md) — 고빈도·단기 TTL, `product_id` 단일 키 기준).
  - **키 설계 후보 비교**

    | 방안 | 구조 | 장점 | 단점 |
    | --- | --- | --- | --- |
    | **A. Sorted Set** | `queue:{productId}:entries` (ZSet, score=입장 시각) + `queue:{productId}:fan:{fanId}` (Hash, 상태·메타) | `ZRANK`/`ZCARD`로 순위·총 대기수 **O(log N)** | join/status 원자성을 위해 **Lua 스크립트** 필요 |
    | B. Hash 단일키 | `queue:{productId}` (Hash, fanId→JSON) | 구현 단순 | 순위 계산 **O(N)** 선형 스캔 — 오픈런 구간 폴링·SSE 부하 |
    | C. Redisson `RQueue` | 분산 큐 추상화 | API 간편 | MVP Not Scope ([architecture](docs/architecture/architecture.md) Phase 3 Redisson 비교 실험) |

  - **→ A(Sorted Set) 채택 이유**
    1. `GET /queue/status` 폴링·이후 `GET /queue/stream`(SSE)에서 **순번·총 대기수**를 로그 시간에 가깝게 조회해야 함.
    2. Hash 단일키는 팬 수 증가 시 순위 산출이 O(N)이라, 드롭스 피크에서 status API가 병목이 될 수 있음.
    3. ZSet은 [invariants §6 WAIT_QUEUE](docs/state/invariants-and-state-machines.md)의 `WAITING`/`PROCESSING` 전이·순번 노출 요구와 맞고, Access Ticket·처리량 알림 등 **후속 PR 확장**에도 동일 키 공간을 재사용하기 쉬움.
    4. Redisson은 추상화 이점이 있으나, MVP 단계에서는 `StringRedisTemplate` + 명시적 키 스키마로 **동작·디버깅 가시성**을 우선.
  - **구현 메모:** 멀티키(ZSet + Hash) 갱신은 Lua로 원자화. 로컬/CI Redis 미기동 시 `@ConditionalOnMissingBean` **`LocalWaitQueueRepository`**(In-Memory, 동일 포트 계약) fallback.
- **데이터 기반 검증 결과:** (k6·부하는 후속 PR/지영재와 합의 후 첨부)
- **트러블슈팅:**
  - **W-1:** `DONE`/`EXPIRED` Terminal 상태에서 `join` 재요청 시 기존 entry 반환·재활성화 금지 (`isTerminal()`).
  - **W-3:** Access Ticket 1개 원칙 — 본 PR Out-of-scope, 다음 PR에서 `PROCESSING` 전이 구현 예정.

## 📌 주요 변경사항

- 추가: `WaitQueueStatus`, `WaitQueueEntry`, `WaitQueueRepository`, `WaitQueueService`, `RedisWaitQueueRepository`, `LocalWaitQueueRepository`, `WaitQueueController`, Queue 응답 DTO
- 수정: `payment-infrastructure/build.gradle.kts` (Redis)

## 🔗 연관 이슈

- Closes #13

## ✅ 셀프 체크리스트

- [x] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가?
- [x] N+1 문제나 비효율적인 쿼리 실행 계획이 없는가? (Redis ZSET, DB 미사용)
- [ ] RestDocs 등 API 문서를 업데이트했는가? (API 변경 시)
- [ ] SSOT 문서(api-spec, invariants, erd-design 등)를 동시 갱신했는가?
- [x] 로컬 테스트 환경(Redis/MySQL) 정상 작동을 확인했는가? (Redis 없을 때 fallback 확인)
- [x] 소스코드 내 민감한 정보(API Key, 패스워드 등)가 제외되었는가?
