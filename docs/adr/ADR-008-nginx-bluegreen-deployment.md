# ADR-008: ALB 없는 환경의 무중단 배포 전략 — 단일 EC2 Blue/Green 포트 스위칭

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-10 |
| **구현 완료일** | 2026-06-11 (PR #221, #222) |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) |
| **관련** | [nginx-bluegreen-strategy.md](../operations/nginx-bluegreen-strategy.md) · [aws-phase3-runbook.md §8](../operations/aws-phase3-runbook.md) · [aws-phase4-runbook.md](../operations/aws-phase4-runbook.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

예산 90,000원/월 제약으로 ALB를 도입할 수 없는 환경에서, **단일 EC2 Blue/Green 포트 스위칭(B안)** 으로 무중단 배포를 구현한다. EC2-2 단기 실험(D안)은 Phase 4에서 분산 설계 검증 목적으로만 한시적으로 수행한다.

---

## Context

### 인프라 현황

| 항목 | 값 |
| --- | --- |
| EC2 | t3.small (2 vCPU, 2GB RAM), Amazon Linux 2023 |
| 배포 방식 | GitHub Actions → S3 → SSM RunCommand → Blue/Green 포트 스위칭 (2026-06-11, PR #221·#222) |
| 배포 다운타임 | 0~2초 (nginx reload + `proxy_next_upstream` 재시도) |
| SLO | Write P95 < 300ms, 5xx rate < 0.1%, 오버셀 0건 |
| ALB | **없음** |
| 월 예산 | **90,000원** |
| 현재 월 비용 | ~72,400원 (EC2 + RDS + ElastiCache + CloudWatch/S3) |

### 문제

Phase 4 부하 테스트 기간 중 배포가 발생하면 기존 `systemctl restart` 방식은 30~60초 다운타임이 생긴다. k6 실행 중 다운타임이 발생하면 5xx가 측정되어 SLO 수치가 오염된다. 또한 운영 중 다운타임 자체가 SLO 위반이다.

### 예산 제약

ALB 고정 요금: $0.0225/hr × 720h = **$16.4/월(~22,600원)**

| 구성 | 월 비용 | 예산 대비 |
| --- | --- | --- |
| 현재 (EC2 1대) | ~72,400원 | ✅ +17,600원 여유 |
| + EC2-2 t3.micro | ~82,900원 | ✅ +7,100원 여유 |
| + ALB만 추가 | ~95,000원 | ❌ 5,000원 초과 |
| + ALB + EC2-2 | ~105,400원 | ❌ 15,400원 초과 |

ALB 단독 추가만으로도 예산을 초과하므로 ALB 없이 Nginx로 배포 안정성을 구현해야 한다.

---

## 방안 비교

### A안 — 현행 유지 (단일 EC2 + `systemctl restart`)

```
Internet → Nginx :80 → Spring Boot :8080 → RDS / Redis
배포: systemctl restart → 30~60초 다운타임
```

| 관점 | 평가 |
| --- | --- |
| 비용 추가 | 없음 |
| 다운타임 | 30~60초 |
| SLO 영향 | 배포 시 5xx 발생 → SLO 측정 오염 |
| 포트폴리오 | 설명할 설계 스토리 없음 |
| **결론** | **기각 — SLO 목표 달성 불가** |

### B안 — 단일 EC2 Blue/Green 포트 스위칭 ✅ **채택**

```
EC2 1대
├── Nginx :80 → upstream (fandrops-active.conf)
├── Spring Boot blue  :8081 ← active (평상시)
└── Spring Boot green :8082 ← 배포 시만 기동
```

배포 흐름: 비활성 슬롯 기동 → 헬스체크 UP → `active.conf` 교체 → `nginx reload` → 구 슬롯 Graceful Shutdown

| 관점 | 평가 |
| --- | --- |
| 비용 추가 | 없음 |
| 다운타임 | 0~2초 (nginx reload 순간, `proxy_next_upstream`으로 재시도) |
| 구현 복잡도 | 중 (systemd 유닛 2개, active.conf 구조, 배포 스크립트) |
| 포트폴리오 | "ALB 없이 Nginx로 무중단 배포 직접 구현" |
| **결론** | **채택 — Phase 4 전 구현** |

### C안 — 단일 EC2 Nginx 상시 로드밸런싱 (:8081 + :8082)

```
EC2 1대
├── Nginx :80 → upstream { :8081; :8082; }
├── Spring Boot :8081 (상시 운영)
└── Spring Boot :8082 (상시 운영)
```

| 관점 | 평가 |
| --- | --- |
| 비용 추가 | 없음 |
| 실질 분산 효과 | CPU·RAM 공유 → 진짜 분산 아님, GC pressure 상시 발생 |
| 배포 다운타임 | 해소 안 됨 (한 프로세스 재시작 시 해당 슬롯 오류 발생) |
| **기각 이유** | 같은 서버에서 두 프로세스가 상시 경쟁 → 2GB에서 GC pressure 증가. 분산도 아니고 무중단도 아님. |

### D안 — EC2 2대 + Nginx 로드밸런싱 (단기 실험) ✅ **Phase 4 한정 채택**

```
Internet → EC2-1 (Nginx LB + App1 :8081)
               upstream { 127.0.0.1:8081; EC2-2-Private:8080; }
           EC2-2 t3.micro (App2 :8080)  ← 실험 후 terminate
           ↓
           RDS / ElastiCache (공유)
```

| 관점 | 평가 |
| --- | --- |
| 추가 비용 | t3.micro 2일 ~700원 |
| 고가용성 | EC2-1(Nginx) SPOF → 실제 HA 아님 |
| 목적 | stateless 설계 검증 — "어느 서버에서 처리해도 오버셀 0건" |
| **결론** | **Phase 4 단기 기동 후 terminate. 영구 전환 불가.** |

**영구 D 전환을 하지 않는 이유:** ALB 없이 EC2 2대를 상시 운영하면 EC2-1(Nginx)이 SPOF로 남아 운영 안정성은 단일 EC2와 동일하면서 운영 복잡도만 증가한다. 실제 HA를 위해서는 ALB가 필수이며, 이는 예산 제약으로 현재 불가하다.

---

## Decision

**B안 채택 + D안 Phase 4 단기 실험.**

B안(단일 EC2 Blue/Green)은 추가 비용 없이 배포 다운타임을 0~2초로 줄여 SLO 측정을 오염시키지 않는다. D안은 오버셀·중복결제 방지가 DB 레벨에서 보장되어 어느 서버에 가도 정합성이 유지됨을 수치로 검증하는 목적으로만 한시적으로 실행한다.

**B안 핵심 설계 결정:**

| 결정 | 이유 |
| --- | --- |
| `-Xmx768m` (기존 `-Xmx1024m`에서 축소) | 전환 중 두 프로세스 동시 기동 시 heap 합계 1.5GB + OS 300MB = 1.8GB → t3.small 2GB 내 수용 |
| `proxy_next_upstream error timeout http_502` | nginx reload 순간 일시적 502를 Nginx가 자동 재시도, 클라이언트 오류 노출 최소화 |
| `server.shutdown: graceful` + 30s timeout | 구 슬롯 stop 시 처리 중인 주문·결제 요청을 최대 30초간 안전 완료 후 종료 |
| `active-slot` 파일 기록 | 재기동·장애 후 현재 active 슬롯을 스크립트가 자동 인식 |

---

## Consequences

### 긍정

- 배포 중 다운타임 제거 → SLO 5xx < 0.1% 유지 가능
- 추가 비용 없음 (예산 범위 내 유지)
- 헬스체크 실패 시 자동 롤백 — 신 버전 문제가 운영에 미치는 영향 최소화
- D 실험으로 stateless 설계를 수치로 검증, 포트폴리오 근거 확보

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| EC2-1 SPOF — 실제 HA 아님 | ALB 없음 (예산 제약) | D 실험 목적이 HA가 아닌 stateless 검증임을 발표에서 명시 |
| Nginx reload 순간 0~2초 불안정 | 파일 교체 방식 | `proxy_next_upstream`으로 클라이언트 오류 노출 최소화 |
| 전환 중 메모리 압박 | t3.small 2GB | `-Xmx768m` 제한으로 동시 기동 시 1.8GB 이내 유지 |
| **SseEmitterRegistry 분산 한계** | in-memory `ConcurrentHashMap` | 오버셀·중복결제 정합성에는 영향 없음, SSE UX 한계로 한정 (아래 불변 참고) |

### 불변

| 규칙 | 내용 |
| --- | --- |
| D 실험 목적 | stateless 설계 검증 전용 — HA 확보 목적 아님, 실험 완료 즉시 EC2-2 terminate |
| SseEmitterRegistry 한계 인지 | D 실험 중 SSE 메시지 간헐적 유실 예상 — 정합성(오버셀·중복결제)과 무관, UX 한계로 발표에서 명시, 개선 방향: Redis Pub/Sub 브로드캐스트 |
| Blue/Green 슬롯 heap | 슬롯별 `-Xmx768m` 고정 — 동시 기동 구간 OOM 방지 |
| 롤백 트리거 | 헬스체크 60초 타임아웃 또는 전환 후 Grafana P0 알람 발화 시 이전 슬롯 즉시 복구 |

---

## 포트폴리오 스토리라인

> AWS 예산 90,000원 제약으로 ALB를 사용할 수 없는 환경에서, Nginx upstream과 systemd 이중 슬롯 구조를 직접 구현해 무중단 Blue/Green 배포를 달성했습니다.

> 배포 중 t3.small(2GB) 메모리에서 두 Spring Boot 프로세스가 동시 기동되는 구간의 OOM 위험을 `-Xmx768m` heap 제한과 Graceful Shutdown 30초 유예로 해소했으며, `proxy_next_upstream`으로 Nginx reload 순간 클라이언트 오류 노출을 최소화했습니다.

> Phase 4에서 EC2-2를 단기 기동해 k6 부하 테스트를 분산 환경에서 실행했습니다. 재고는 DB 단일 UPDATE(`WHERE availableQty >= qty`) + 낙관락, 결제는 `PESSIMISTIC_WRITE` + unique index 조합으로 어느 서버에서 요청을 처리해도 오버셀·중복결제가 발생하지 않음을 수치로 검증했습니다.

> 코드 분석 과정에서 `SseEmitterRegistry`가 in-memory `ConcurrentHashMap`으로 SSE 연결을 관리해 분산 환경에서 대기열 상태 메시지가 유실될 수 있음을 직접 발견했습니다. 정합성(오버셀·중복결제)은 DB 레벨에서 보장되므로 비즈니스 무결성에는 영향이 없으나, Redis Pub/Sub 브로드캐스트로 해소할 수 있는 UX 한계로 명시했습니다.

> ALB 없는 구조의 SPOF 한계와 SSE 분산 문제를 직접 발견하고 개선 방향까지 제시한 것이 단순 구현을 넘어 운영 관점의 설계 사고를 보여주는 포인트입니다.

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| Blue/Green 기술 상세 및 배포 스크립트 | [nginx-bluegreen-strategy.md](../operations/nginx-bluegreen-strategy.md) |
| Phase 3 Blue/Green EC2 적용 절차 | [aws-phase3-runbook.md §8](../operations/aws-phase3-runbook.md) |
| Phase 4 D 실험 및 SLO 검증 계획 | [aws-phase4-runbook.md](../operations/aws-phase4-runbook.md) |
| SLO 메트릭 정의 | [observability-metrics.md](../operations/observability-metrics.md) |
