# 장애 대응 기준 (Incident Response)

> **장애별 기술 정책:** [failure-policy.md](./failure-policy.md) · **메트릭·알람:** [observability-metrics.md](./observability-metrics.md) · **담당:** [architecture § 오너십](../architecture/architecture.md#도메인-오너십--모듈-매핑)

FANDROPS **B2B2C 오픈런** 서비스에서 장애 **등급·대응·커뮤니케이션**을 정의한다.  
핵심 원칙: **결제·재고 정합성** 이 깨질 수 있는 사건은 최우선(P0)으로 취급한다.

---

## 1. 역할

| 역할 | 담당 | 책임 |
| --- | --- | --- |
| **Incident Commander (IC)** | 지영재 (SRE) | 등급 판정, 배포 중지/롤백 결정, 커뮤 조율 |
| **Commerce** | 형성빈 | 주문·재고·핫딜 |
| **Payment** | 장성재 | PG·웹훅·Saga |
| **Platform / Identity** | 표지민 | Redis·대기열·Access Ticket·알림·Outbox |
| **Comms** | 당일 릴리즈 오너 | Slack/Notion 장애 공지 초안 |

---

## 2. 장애 등급

| 등급 | 조건 (예시) | 대응 | 커뮤니케이션 | 목표 복구 |
| --- | --- | --- | --- | --- |
| **P0** | 오버셀·중복 결제·`COMPLETED` without payment 등 **정합성 깨짐 가능** · Primary DB 장애 · 전면 5xx 급증 | **배포 중지** + 롤백 검토 · write 트래픽 차단 · IC 소집 | **팀 전체** Slack + (필요 시) 기획사 공지 검토 | MTTR 목표 **1h** (데이터 복구 별도) |
| **P1** | 결제·웹훅 **지연** · Outbox pending 적체 · Redis 다운 · Queue depth 급증 · Write P95 > 300ms 지속 | 스케일 아웃 / Nginx limit · [failure-policy](./failure-policy.md) fail-fast · 담당 도메인 핫픽스 | **#fandrops-incident** + 담당 오너 | **4h** 내 완화 |
| **P2** | 일부 **조회** 지연 · 캐시 hit 하락 · 비핵심 Admin 지연 | 튜닝 백로그 · 이슈 등록 | GitHub Issue만 | 다음 스프린트 |
| **P3** | 문서·UI 오타, stg only | 일반 백로그 | 없음 | — |

### P0 트리거 체크리스트 (하나라도 해당 시 P0)

- [ ] 판매 가능 재고 < 0 또는 `reserved` 불일치 감지
- [ ] 동일 `tossPaymentKey`로 이중 `COMPLETED`
- [ ] `FAILED` 상태 **5분 초과** 다건
- [ ] prod **5xx > 1%** 5분 지속

### P1 트리거 (예시)

- [ ] `outbox_pending_count` > 1,000 (15분)
- [ ] `http_server_requests` P95 > 500ms (Write, 10분)
- [ ] Redis unavailable (prod)
- [ ] `queue_depth` > 임계 (드롭 오픈 중)

---

## 3. 대응 절차 (요약)

```
감지 (알람 / 제보)
  → IC 지정 + 등급 (P0~P2)
  → Mitigate (limit, rollback, scale)
  → Root cause (traceId, audit, webhook log)
  → Fix + 배포
  → [failure-policy §6 복구 검증](./failure-policy.md#6-복구-검증-체크리스트)
  → Postmortem (P0/P1, 48h 이내)
```

### 배포 · 롤백

| 항목 | 정책 |
| --- | --- |
| P0 진행 중 | **develop → prod 배포 동결** |
| 롤백 | Nginx Blue/Green 이전 슬롯 — [ADR-001](../adr/ADR-001-multi-module-monolith.md) |
| DB 마이그레이션 | 롤백 불가 migration은 P0 기간 **금지** |

### 커뮤니케이션 템플릿 (팬-facing, P0/P1)

> 현재 일부 주문·결제 확인이 지연되고 있습니다.  
> 이미 결제하신 건은 취소되지 않으며, 확인 후 순차 반영됩니다.  
> traceId: 고객센터 문의 시 주문 번호와 함께 전달해 주세요.

---

## 4. 포스트모텀 (P0 / P1)

| 항목 | 내용 |
| --- | --- |
| 시각선 | 감지 ~ 복구 UTC |
| 영향 | 주문 건수, 금액, 정합성 여부 |
| 근본 원인 | 기술·프로세스 |
| 액션 아이템 | owner · due date |

저장: Notion / `docs/postmortems/YYYY-MM-DD-title.md` (팀 합의).

---

## 5. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [failure-policy.md](./failure-policy.md) | Redis, Outbox, timeout |
| [data-retention-and-audit-policy.md](../erd/data-retention-and-audit-policy.md) | 수동 복구 audit |
| [observability-metrics.md](./observability-metrics.md) | 알람 임계값 |
