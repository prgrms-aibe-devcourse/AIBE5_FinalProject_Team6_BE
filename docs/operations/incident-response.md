# 장애 대응 기준 (Incident Response)

> **장애별 기술 정책:** [failure-policy.md](./failure-policy.md) · **메트릭·알람:** [observability-metrics.md](./observability-metrics.md) · **담당:** [architecture § 오너십](../architecture/architecture.md#도메인-오너십--모듈-매핑)

FANDROPS **B2B2C 오픈런** 서비스에서 장애 **등급·대응·커뮤니케이션**을 정의한다.  
핵심 원칙: **결제·재고 정합성** 이 깨질 수 있는 사건은 최우선(P0)으로 취급한다.

---

## 1. 역할

| 역할 | 담당 | 책임 |
| --- | --- | --- |
| **Incident Commander (IC)** | 지영재 (SRE) | 등급 판정, 배포 중지/롤백 결정, 커뮤 조율 |
| **Commerce** | 형성빈 | 주문·재고·드롭스 |
| **Payment** | 장성재 | PG·웹훅·Saga |
| **Identity / Notification** | 표지민 | Auth·알림·Outbox |
| **Traffic Policy** | 장성재 | 대기열·Access Ticket·RateLimit 정책, 결제/주문 진입 제한 기준 |
| **Comms** | 당일 릴리즈 오너 | Slack/Notion 장애 공지 초안 |

---

## 2. 장애 등급

| 등급 | 조건 (예시) | 대응 | 커뮤니케이션 | 목표 복구 |
| --- | --- | --- | --- | --- |
| **P0** | 오버셀·중복 결제·`COMPLETED` without payment 등 **정합성 깨짐 가능** · Primary DB 장애 · 전면 5xx 급증 | **배포 중지** + 롤백 검토 · write 트래픽 차단 · IC 소집 | **팀 전체** Slack + (필요 시) 운영 주체 공지 검토 | MTTR 목표 **1h** (데이터 복구 별도) |
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
> traceId: 운영 문의 시 주문 번호와 함께 전달해 주세요.

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

## 5. 실전 테스트 절차 (Phase 4 검증)

> Phase 4에서 알람 발화·롤백 프로세스가 실제로 작동하는지 사전 검증한다.
> 테스트 후 반드시 삽입 데이터를 정리한다.

### 5-1. P0 Alert firing 테스트

```sql
-- 1. status=FAILED 주문 1건 수동 INSERT
INSERT INTO orders (fan_id, product_id, status, total_amount, idempotency_key, created_at, updated_at)
VALUES (9999, 1, 'FAILED', 0, 'test-alert-firing-001', NOW(), NOW());

-- 2. Grafana(api.fandrops.site:3000) → Alerting → Alert Rules
--    fandrops-failed-order-p0: Pending → Firing 전환 확인 (평가 주기 1분)
-- 3. Gmail 수신 확인 (팀 공유 메일)

-- 4. 테스트 데이터 정리
DELETE FROM orders WHERE idempotency_key = 'test-alert-firing-001';
```

### 5-2. Blue/Green 롤백 실전 테스트

**헬스체크 실패 시나리오 (자동 롤백 검증):**

```bash
# 현재 active 슬롯 확인
cat /etc/fandrops/active-slot   # 예: blue

# 비활성 슬롯에 고의로 broken JAR 배포 → 헬스체크 실패 유도
# → bluegreen-deploy.sh 가 자동으로 새 슬롯 stop, 구 슬롯 계속 서비스
sudo journalctl -u fandrops-green -n 50   # 실패 로그 확인
```

**수동 롤백 절차 (Grafana P0 발화 후):**

```bash
PREV_SLOT="blue"   # 이전 정상 슬롯
PREV_PORT=8081

# 1. Nginx를 이전 슬롯으로 즉시 전환
sudo tee /etc/nginx/fandrops-active.conf <<EOF
upstream fandrops_backend {
    server 127.0.0.1:${PREV_PORT};
    keepalive 32;
}
EOF
sudo nginx -t && sudo systemctl reload nginx

# 2. active-slot 파일 갱신
echo "$PREV_SLOT" | sudo tee /etc/fandrops/active-slot

# 3. 문제 슬롯 종료
sudo systemctl stop fandrops-green

# 4. 헬스체크 확인
curl -s https://api.fandrops.site/actuator/health
```

### 5-3. 검증 체크리스트

| 항목 | 확인 방법 | 결과 |
| --- | --- | --- |
| P0 Alert Gmail 수신 | 알람 발화 후 1~2분 내 Gmail 확인 | ✅/❌ |
| 수동 INSERT 후 Alert Firing | Grafana Alert Rules 화면 | ✅/❌ |
| 자동 롤백 (헬스체크 실패) | journalctl로 롤백 로그 확인 | ✅/❌ |
| 수동 롤백 후 서비스 정상 | curl actuator/health → UP | ✅/❌ |
| 테스트 데이터 정리 | orders 테이블 확인 | ✅/❌ |

---

## 6. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [failure-policy.md](./failure-policy.md) | Redis, Outbox, timeout |
| [data-retention-and-audit-policy.md](../erd/data-retention-and-audit-policy.md) | 수동 복구 audit |
| [observability-metrics.md](./observability-metrics.md) | 알람 임계값 |
