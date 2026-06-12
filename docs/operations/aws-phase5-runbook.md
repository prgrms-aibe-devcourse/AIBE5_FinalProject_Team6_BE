# AWS Phase 5 발표 준비 및 마무리

> **관련:** [aws-phase4-runbook.md](./aws-phase4-runbook.md) · [observability-metrics.md](./observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

Phase 4(부하 테스트·SLO 검증)에서 Phase 5(STAR 리포트·발표·문서 마무리)로 이행하는 작업을 기록한다.

---

## 1. 개요

| 항목 | 내용 |
| --- | --- |
| 기간 | 2026-06-22 ~ 2026-06-25 |
| 목표 | SLO 달성 수치 증명 + 포트폴리오 스토리라인 완성 |
| 핵심 산출물 | STAR 리포트, Grafana 스크린샷, 데모 시나리오 |

---

## 2. 최종 SLO 수치 기록표

Phase 4 튜닝 완료 후 최종 측정값을 기록한다.

| 지표 | 목표 | Baseline | 최종 측정값 | 달성 여부 |
| --- | --- | --- | --- | --- |
| Write P95 (주문) | < 300ms | ms | ms | ✅/❌ |
| Write P95 (결제) | < 300ms | ms | ms | ✅/❌ |
| Read P95 (피드) | < 120ms | ms | ms | ✅/❌ |
| 5xx 에러율 | < 0.1% | % | % | ✅/❌ |
| 오버셀 건수 | 0건 | 0건 | 건 | ✅/❌ |
| 중복 결제 건수 | 0건 | 0건 | 건 | ✅/❌ |

### 2-1. Grafana 스크린샷 보관

```
docs/assets/phase5/
├── grafana-slo-baseline.png      # Baseline 측정 스크린샷
├── grafana-slo-final.png         # 최종 측정 스크린샷
├── grafana-p95-order.png         # 주문 Write P95
├── grafana-p95-payment.png       # 결제 Write P95
├── grafana-p95-feed.png          # 피드 Read P95
└── grafana-d-experiment.png      # D 실험 분산 환경 RPS 분산 확인
```

---

## 3. STAR 리포트 구조

### 3-1. 지영재 (SRE/Platform) STAR

**Situation**

> AWS 예산 90,000원/월 제약으로 ALB를 사용할 수 없는 환경에서, Phase 4 부하 테스트 기간 중 배포 발생 시 기존 `systemctl restart` 방식은 30~60초 다운타임을 유발해 SLO 측정을 오염시켰다.
> 또한 단일 EC2 환경에서 "어느 서버에서 요청을 처리해도 오버셀·중복결제가 발생하지 않는가"를 수치로 검증할 수단이 없었다.

**Task**

> - 추가 비용 없이 배포 다운타임 0건을 달성하는 Blue/Green 무중단 배포 구현
> - k6 부하 테스트 스크립트 설계 및 SLO 기준선 수치 확보
> - EC2-2 단기 실험으로 stateless 분산 설계 검증
> - Grafana 알람이 실제로 Gmail까지 발화하는지 실전 테스트

**Action**

> 1. **Blue/Green 포트 스위칭**: systemd 이중 슬롯(blue :8081 / green :8082), `/etc/nginx/fandrops-active.conf` 교체로 nginx reload 시 0~2초 내 전환. `proxy_next_upstream`으로 reload 순간 클라이언트 오류 최소화
> 2. **t3.small 메모리 관리**: 동시 기동 구간 OOM 방지를 위해 슬롯당 `-Xmx768m` 제한 (합계 1.5GB + OS 300MB = 1.8GB ≤ 2GB)
> 3. **k6 시나리오 6종 설계**: 단순 API 단위 테스트에서 벗어나 JWT 사전 생성(fan_id 1~2100 개별 토큰 CSV) + Redis AccessTicket 사전 적재로 prod 프로파일 유지·Wiremock 결제 모킹·통합 워크로드 모델(피드 60%+대기열 20%+주문 15%+결제 5%)까지 구현
> 4. **D 단기 실험**: EC2-2 t3.micro 2일(약 700원) 기동 → k6 01·04 시나리오 분산 재실행 → 오버셀 0건, SSE 메시지 유실 현상 직접 확인
> 5. **알람 실전 테스트**: status=FAILED 주문 수동 INSERT → Grafana P0 Alert Firing → Gmail 수신 확인

**Result**

> - 배포 중 5xx 0건 달성 (Grafana 5xx 에러율 패널 기준)
> - Write P95 `___ms`, Read P95 `___ms` (Baseline 대비 `___%` 개선)
> - 오버셀 0건, 중복 결제 0건 — 단일/분산 환경 모두 검증
> - `SseEmitterRegistry` in-memory 구조의 분산 한계를 직접 발견 → Redis Pub/Sub 개선 방향 제시
> - Grafana P0 알람 → Gmail SMTP 1분 내 수신 확인

---

## 4. 발표 데모 시나리오

### 4-1. 데모 흐름 (5분 기준)

```
1. Grafana SLO 대시보드 (api.fandrops.site:3000)
   → P95·에러율·오버셀 패널 live 시연

2. k6 부하 테스트 Baseline vs 최종 스크린샷 비교
   → "수치로 증명하는 성능 개선"

3. Blue/Green 배포 live 시연 (CD 워크플로우 트리거)
   → Grafana에서 배포 중 5xx 0건 확인

4. D 실험 결과 (스크린샷)
   → "두 서버에 요청이 분산되었으나 오버셀 0건"
   → "SseEmitterRegistry 한계 → Redis Pub/Sub 개선 방향"

5. P0 Alert firing 스크린샷
   → 알람 발화 → Gmail 수신
```

### 4-2. 한계와 개선 방향 (발표 포인트)

| 한계 | 원인 | 개선 방향 |
| --- | --- | --- |
| EC2-1 SPOF — 실제 HA 아님 | ALB 없음 (예산) | ALB 도입 (~22,600원/월 추가) |
| SSE 메시지 유실 (분산 환경) | `SseEmitterRegistry` in-memory | Redis Pub/Sub 브로드캐스트 |
| Nginx reload 0~2초 불안정 | 파일 교체 방식 | ALB Target Group 교체 방식 |

---

## 5. 문서 마무리 체크리스트

- [ ] SLO 수치 기록표 채우기 (§2)
- [ ] STAR 리포트 Result 수치 채우기 (§3-1)
- [ ] Grafana 스크린샷 `docs/assets/phase5/`에 저장
- [ ] ADR-008 포트폴리오 스토리라인 최종 검토
- [ ] aws-phase4-runbook.md §9 DoD 전체 [x] 완료 처리
- [ ] README 또는 프로젝트 소개 문서에 SLO 달성 수치 반영

---

## 6. Phase 4 DoD → Phase 5 인계 조건

아래 항목이 모두 완료된 후 Phase 5 발표 준비로 이행한다.

- [ ] k6 Baseline 수치 기록 완료 (시나리오 01~06)
- [ ] Wiremock 결제 예외 시나리오 검증 완료
- [ ] D 단기 실험 완료 + EC2-2 terminate 확인
- [ ] SLO 미달 항목 튜닝 완료 (Baseline 대비 개선 수치 보유)
- [ ] Grafana 커스텀 알람 활성화 (#191)
- [ ] P0 Alert firing + 롤백 실전 테스트 완료
- [ ] 최종 SLO 수치 Grafana 스크린샷 보관

---

## 7. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [aws-phase4-runbook.md](./aws-phase4-runbook.md) | 부하 테스트·D 실험·SLO 검증 |
| [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) | Blue/Green 포트폴리오 스토리라인 §10 |
| [ADR-008](../adr/ADR-008-nginx-bluegreen-deployment.md) | 무중단 배포 의사결정 |
| [observability-metrics.md](./observability-metrics.md) | SLO·메트릭·알람 기준 |
| [incident-response.md](./incident-response.md) | P0 알람 실전 테스트 절차 |
