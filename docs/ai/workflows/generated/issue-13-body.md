## 📝 작업 내용

드롭스(F04-02) 상품 오픈 시 팬 대기열 **등록·순번 조회** API와 Redis 저장 로직을 구현한다. (Small PR — SSE·이탈·Access Ticket은 다음 이슈)

- `POST /api/v1/queue/join/{productId}` — 대기열 등록 (`WAITING`)
- `GET /api/v1/queue/status?productId=` — 현재 순번·상태·예상 대기 시간 조회
- `payment` 모듈: domain 포트 → application → Redis 구현체 + 로컬 In-Memory fallback

**Out-of-scope (별도 이슈/PR)**

- `GET /queue/stream/{productId}` (SSE)
- `DELETE /queue/exit/{productId}`
- Access Ticket 발급 (`WAITING` → `PROCESSING`, invariants §6)

## Definition of Done (DoD)

- [ ] `WaitQueueService` join/status 및 W-1(Terminal 재활성화 금지) 단위 테스트
- [ ] Redis Sorted Set 순위 조회·로컬 `LocalWaitQueueRepository` fallback 동작 확인
- [ ] [mvp-api-spec § Wait Queue](docs/api/mvp-api-spec.md) · [invariants §6](docs/state/invariants-and-state-machines.md) · [erd-design §10](docs/erd/erd-design.md) 정합
- [ ] RestDocs(또는 API 문서) join/status 스펙 반영
- [ ] 로컬에서 `X-Fan-Id`(또는 인증 헤더)로 join → status E2E 확인
- [ ] 민감 정보·하드코딩 시크릿 없음

## 📅 마감 기한

- 2026-06-18

## Related

- F04-02 / F06 연동: [mvp-api-spec.md](docs/api/mvp-api-spec.md) · [invariants §6 WAIT_QUEUE](docs/state/invariants-and-state-machines.md) · [erd-design §10](docs/erd/erd-design.md)
- 담당: 장성재 (`modules/payment`)
