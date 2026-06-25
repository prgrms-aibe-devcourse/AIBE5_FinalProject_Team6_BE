# ADR-027: k6 결과를 SSM stdout이 아닌 Prometheus Remote Write로 수집

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-17 |
| **구현 완료일** | 2026-06-17 (PR #263, #264, #266) |
| **선행 ADR** | [ADR-023 k6 전용 EC2 실행](./ADR-023-k6-dedicated-ec2-runner.md) · [ADR-025 Prometheus/Grafana 스택](./ADR-025-prometheus-grafana-observability.md) |
| **관련** | [infra/k6/lib/thresholds.js](../../infra/k6/lib/thresholds.js) · [run-k6.yml](../../.github/workflows/run-k6.yml) · [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

k6 실행 결과를 SSM RunCommand stdout 캡처나 JUnit XML 파일 대신 **Prometheus Remote Write (`--out experimental-prometheus-rw`)** 로 EC2-1 Prometheus에 직접 push하여 Grafana에서 실시간 확인한다.

---

## Context

### 인프라 현황

| 항목 | 값 |
| --- | --- |
| k6 실행 위치 | EC2-2 (`10.0.1.47`) |
| Prometheus | EC2-1 (`10.0.1.114:9090`) |
| k6 버전 | v2.0.0 (`experimental-prometheus-rw` 지원) |

### 문제

EC2-2에서 k6를 SSM RunCommand로 실행하면 결과를 가져오는 방식에 다음 한계가 있다.

1. **SSM RunCommand stdout 24 KB 잘림:** SSM은 단일 명령의 stdout을 24 KB까지만 반환한다. s05(2,100 VU, ~6분) 시나리오에서 k6 요약 출력이 24 KB를 초과하여 결과 후반부가 소실되었다
2. **사후 분석만 가능:** stdout/파일을 사후에 확인하는 방식은 k6 실행 중 실시간 SLO 위반 여부를 알 수 없다
3. **InfluxDB 추가 비용:** k6가 공식 지원하는 InfluxDB는 추가 인프라 비용이 발생한다

---

## 방안 비교

### A안 — SSM RunCommand stdout 캡처

```
SSM RunCommand → k6 실행 → stdout → get-command-invocation (StandardOutputContent)
```

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 낮음 |
| 실시간 확인 | ❌ — 실행 완료 후에만 확인 가능 |
| stdout 한계 | ❌ **24 KB 잘림** — s05(2,100 VU) 결과 소실 확인됨 |
| **기각 이유** | 24 KB 한계로 고부하 시나리오 결과를 완전히 수신할 수 없다. |

### B안 — JUnit XML → S3 업로드 후 수동 확인

```
k6 --out json=result.json → 완료 후 aws s3 cp result.json s3://.../k6/result.json
```

| 관점 | 평가 |
| --- | --- |
| 실시간 확인 | ❌ — 실행 완료 후에만 확인 가능 |
| 파일 크기 한계 | 없음 |
| 운영 복잡도 | S3 업로드 + 다운로드 후 파싱 필요 |
| **기각 이유** | 실시간 SLO 모니터링 불가. 별도 분석 도구가 없으면 파일만으로 인사이트를 도출하기 어렵다. |

### C안 — k6 → Prometheus Remote Write → Grafana ✅ 채택

```
EC2-2: k6 --out experimental-prometheus-rw
               ↓ HTTP POST (push)
EC2-1: Prometheus :9090 /api/v1/write
               ↓ datasource
EC2-1: Grafana :3000 (실시간 대시보드)
```

| 관점 | 평가 |
| --- | --- |
| 실시간 확인 | ✅ — k6 실행 중 Grafana에서 VU 수·P95·오류율 즉시 확인 |
| stdout 한계 | 없음 — 메트릭을 Prometheus에 push |
| 추가 인프라 | 없음 — ADR-025에서 Prometheus 이미 구동 중 |
| **결론** | **채택** |

---

## Decision

**C안 채택 — k6 `--out experimental-prometheus-rw` → Prometheus EC2-1:9090.**

k6 실행 시 `--out experimental-prometheus-rw=http://10.0.1.114:9090/api/v1/write`를 추가한다. Prometheus는 Grafana의 datasource로 연결되어 있으므로 k6 메트릭을 실시간으로 확인할 수 있다.

**stale series 문제 및 해결:**

k6가 종료된 후에도 Prometheus는 마지막으로 받은 메트릭 값을 약 5분간 유지한다 (stale series). `k6_http_req_duration_p95` 등의 메트릭을 단순 쿼리하면 테스트 종료 후에도 마지막 값이 계속 표시된다.

해결: Grafana 패널 쿼리에서 `max_over_time(metric[2m])` 구간 함수를 사용해 테스트 활성 구간의 최고점을 표시하고, 종료 후 stale 기간을 자연스럽게 처리한다.

**테스트 완료 후 SLO 수치 확정 방법:**

k6 종료 시 stdout에 출력되는 Summary 테이블(P50/P95/P99, 오류율, 요청 수)을 최종 SLO 수치의 근거로 사용한다. Prometheus 시계열은 k6 실행 중 모니터링 목적으로 사용하고, 공식 SLO 기록은 k6-realfinal-result.md에 Summary 텍스트 기반으로 작성한다.

---

## Consequences

### 긍정

- SSM 24 KB 잘림 문제 완전 해소 — s05(2,100 VU, ~6분) 결과 전체 수신
- k6 실행 중 Grafana 실시간 대시보드로 SLO 위반 즉시 인지
- 추가 인프라 비용 없음 — Prometheus(ADR-025) 재활용

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| stale series (종료 후 ~5분) | Prometheus remote_write 특성 | `max_over_time([2m])` 쿼리로 활성 구간 수치 추출 |
| EC2-2 → EC2-1:9090 방화벽 | Security Group | `fandrops-prod-sg-ec2`에서 동일 SG 내 9090 포트 허용 설정 확인 필요 |
| `experimental` 플래그 | k6 v2.0.0 기준 실험적 기능 | k6 v2.0.0에서 정상 동작 확인 완료. k6 업그레이드 시 API 변경 여부 재확인 필요 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| SLO 측정 실행 | 모든 SLO 시나리오(s01~s07)는 `--out experimental-prometheus-rw` 포함 실행 |
| SLO 공식 수치 | k6 Summary stdout 기반 — Prometheus 시계열은 보조 확인 용도 |

---

## 검증

- [x] k6 `--out experimental-prometheus-rw=http://10.0.1.114:9090/api/v1/write` 동작 확인
- [x] Grafana에서 k6 메트릭(`k6_http_req_duration_p95`, `k6_vus`) 실시간 시각화 확인
- [x] s05(2,100 VU) 결과 전체 수신 — 24 KB 잘림 없음
- [x] stale series — `max_over_time([2m])` 쿼리로 종료 후 노이즈 제거 확인

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| k6 thresholds / 시나리오 | [infra/k6/lib/thresholds.js](../../infra/k6/lib/thresholds.js) |
| k6 실행 워크플로 | [run-k6.yml](../../.github/workflows/run-k6.yml) |
| k6 최종 측정 결과 | [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
| Prometheus 스택 | [ADR-025](./ADR-025-prometheus-grafana-observability.md) |
| k6 전용 EC2 실행 | [ADR-023](./ADR-023-k6-dedicated-ec2-runner.md) |
