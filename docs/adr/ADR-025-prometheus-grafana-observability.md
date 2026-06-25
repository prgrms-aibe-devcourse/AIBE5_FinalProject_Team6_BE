# ADR-025: Spring Actuator + Prometheus + Grafana 기반 MVP 관측 스택 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-05 |
| **구현 완료일** | 2026-06-08 (aws-phase2-runbook.md §3, Docker Compose 기동 확인) |
| **선행 ADR** | [ADR-021 단일 EC2 인프라](./ADR-021-aws-single-ec2-infra-phase1.md) |
| **관련** | [observability-metrics.md](../operations/observability-metrics.md) · [infra/monitoring/prometheus/prometheus.yml](../../infra/monitoring/prometheus/prometheus.yml) · [current-infra-state.md §9](../operations/aws/current-infra-state.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

MVP SLO 관측 스택으로 **Spring Boot Actuator + Micrometer → Prometheus + Grafana**를 채택한다. 관측 컴포넌트는 EC2-1에 Docker로 실행하며, 관리형 모니터링 서비스(CloudWatch 메트릭 알람, 외부 APM)는 MVP 범위에서 제외한다.

---

## Context

### 요구사항

| 항목 | 내용 |
| --- | --- |
| SLO | Write P95 < 300ms, Read P95 < 120ms, 5xx < 0.1%, 오버셀 0건 |
| k6 연동 | k6 실행 중 실시간 SLO 대시보드 확인 필요 |
| 예산 | 월 90,000원 — 이미 ~72,400원 사용 (ADR-021) |
| 커스텀 메트릭 | `fandrops_orders_status`, `fandrops_outbox_pending`, `fandrops_sse_connections` 등 |

### 문제

SLO를 측정하고 k6 부하 테스트 결과를 실시간 확인하려면 HTTP 레이턴시·에러율·커스텀 비즈니스 메트릭을 수집·시각화하는 인프라가 필요하다. 예산 제약 내에서 추가 비용 없이 구성해야 한다.

---

## 방안 비교

### A안 — CloudWatch 메트릭 + CloudWatch Dashboards

```
Spring Boot → CloudWatch Agent → CloudWatch Metrics → CloudWatch Dashboards
```

| 관점 | 평가 |
| --- | --- |
| 추가 비용 | CloudWatch 메트릭 사용량 기반 과금 (커스텀 메트릭 $0.30/메트릭/월) |
| 설정 복잡도 | EMF(Embedded Metrics Format) 코드 변경 필요 또는 Agent 설정 필요 |
| k6 연동 | k6 → CloudWatch 직접 연동 미지원 — 별도 exporter 필요 |
| 커스텀 메트릭 | Micrometer CloudWatch Exporter로 가능하나 설정 복잡 |
| **기각 이유** | Prometheus/Grafana 대비 k6 연동이 복잡하고 커스텀 메트릭당 과금이 발생한다. CloudWatch는 호스트 로그·시스템 메트릭 수집 용도로는 유지하되, SLO 메트릭 시각화는 Prometheus/Grafana가 적합하다. |

### B안 — 관리형 APM (Datadog, New Relic 등)

```
Spring Boot agent → Datadog / New Relic → SaaS 대시보드
```

| 관점 | 평가 |
| --- | --- |
| 기능 | APM trace, 로그 연계, 알람 등 올인원 |
| 비용 | Datadog 최소 $15~31/호스트/월 (~21,000~43,000원) → 예산 초과 위험 |
| **기각 이유** | 예산 제약. 학습·포트폴리오 목적에서 관리형 APM 의존보다 직접 구성이 더 가치 있다. |

### C안 — Spring Actuator + Prometheus + Grafana (Docker, EC2-1 동일 호스트) ✅ 채택

```
Spring Boot (blue :8081 / green :8082)
  /actuator/prometheus  ← Micrometer + prometheus registry
      ↓ scrape (15s)
Prometheus (Docker, EC2-1 :9090)
      ↓ datasource
Grafana (Docker, EC2-1 :3000)
      ↑ remote_write
k6 (EC2-2)  --out experimental-prometheus-rw → Prometheus
```

| 관점 | 평가 |
| --- | --- |
| 추가 비용 | 없음 — EC2-1에 Docker 컨테이너로 실행 |
| k6 연동 | `--out experimental-prometheus-rw` 직접 지원 |
| 커스텀 메트릭 | Micrometer `Counter`/`Gauge`/`Histogram` 그대로 사용 |
| Blue/Green 연동 | 양 슬롯(8081/8082) 동시 스크래핑 — 비활성 슬롯 DOWN은 무해 (아래 참고) |
| **결론** | **채택** |

---

## Decision

**C안 채택 — Prometheus + Grafana on Docker, EC2-1 동일 호스트.**

Spring Boot Actuator의 `/actuator/prometheus` 엔드포인트에서 Prometheus가 15초 간격으로 스크래핑한다. Grafana는 Prometheus를 datasource로 사용한다. k6는 `--out experimental-prometheus-rw`로 Prometheus에 직접 메트릭을 push한다.

**Blue/Green 양 슬롯 동시 스크래핑:**

`prometheus.yml`에 Blue(:8081)와 Green(:8082)을 모두 등록한다. 배포 중이 아닐 때 비활성 슬롯은 DOWN으로 표시되지만 스크래핑 오류는 경보를 울리지 않는다. 이 구조는 다음 이점을 제공한다.

- 배포 후 신규 슬롯이 UP되는 순간부터 메트릭 수집 시작 (별도 설정 변경 불필요)
- Nginx upstream 전환 전후 양 슬롯의 응답 특성을 동시 관찰 가능

비활성 슬롯 DOWN 알람은 `instance` 라벨 필터로 억제해야 한다. **현재 미구현 (확인 필요).**

**CloudWatch 역할 유지:**

CloudWatch Agent 설정(`infra/cloudwatch/`)은 호스트 시스템 메트릭(CPU, 메모리, 디스크)과 journald 로그 수집 목적으로 유지한다. 그러나 FANDROPS 애플리케이션 메트릭 대상의 **CloudWatch 알람은 현재 등록 없음** (AWS CLI 확인 결과, 2026-06-25 기준 `fandrops` 명칭의 CloudWatch 메트릭 알람 없음 — current-infra-state.md §9).

**Slack / PagerDuty 알람 연동:**

- Grafana → Slack 알람 연동: **확인 필요** (구성 여부 미검증)
- PagerDuty 연동: **미구현** (확인 필요)

---

## Consequences

### 긍정

- 추가 비용 없음 — EC2-1 Docker 컨테이너 (Prometheus 300MB, Grafana 200MB RAM 수준)
- k6 Remote Write 직접 지원 — SLO 측정 중 실시간 대시보드 확인
- Micrometer 커스텀 메트릭 그대로 사용 — 코드 변경 최소
- Blue/Green 전환 시 Prometheus 설정 변경 불필요

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| Prometheus/Grafana SPOF | EC2-1에 동거 | SLO 측정 중 관측 인프라 장애 시 k6 결과 손실 위험. 재실행으로 수용 |
| Grafana :3000 / Prometheus :9090 공개 | Security Group에 전체 인터넷 오픈 | 발표·데모 목적 임시 허용. 운영 이후 IP 제한 또는 Basic Auth 적용 권고 (current-infra-state.md §5 주의 사항) |
| CloudWatch 알람 미구현 | 우선순위 | 운영 중단 수준의 P0 알람은 Grafana에서 관리. CloudWatch 알람은 Phase 5 이후 과제 |
| Slack/PagerDuty 연동 미확인 | 확인 필요 | 알람 라우팅이 미구성이면 Grafana 대시보드 수동 확인에 의존 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| 스크래핑 대상 | Blue(:8081)·Green(:8082) 양 슬롯 항상 등록 유지 |
| k6 Remote Write | SLO 측정 시나리오는 `--out experimental-prometheus-rw` 필수 (ADR-027) |
| CloudWatch 메트릭 알람 | FANDROPS 메트릭 기반 CloudWatch 알람 미등록 상태 — 추가 전 팀 합의 필요 |

---

## 검증

- [x] Prometheus Docker 컨테이너 EC2-1 :9090 실행 확인
- [x] Grafana Docker 컨테이너 EC2-1 :3000 실행 확인 (v12.0.1)
- [x] `/actuator/prometheus` 스크래핑 — Blue :8081 UP 확인
- [x] k6 `--out experimental-prometheus-rw` → Prometheus 시계열 수신 확인
- [x] CloudWatch 알람 없음 확인 (AWS CLI 쿼리, 2026-06-25)
- [ ] 비활성 슬롯 DOWN 알람 억제 설정 — **미구현**
- [ ] Grafana → Slack 알람 연동 — **확인 필요**
- [ ] PagerDuty 연동 — **미구현**

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| SLO 메트릭 정의 및 알람 등급 | [observability-metrics.md](../operations/observability-metrics.md) |
| Prometheus 설정 | [infra/monitoring/prometheus/prometheus.yml](../../infra/monitoring/prometheus/prometheus.yml) |
| 현행 인프라 상태 (모니터링 섹션) | [current-infra-state.md §9](../operations/aws/current-infra-state.md) |
| k6 Prometheus Remote Write | [ADR-027](./ADR-027-k6-prometheus-remote-write.md) |
| CloudWatch Agent 설정 | [infra/cloudwatch/](../../infra/cloudwatch/) |
