# ADR-023: k6 부하 테스트를 전용 EC2(EC2-2)에서 실행

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-15 |
| **구현 완료일** | 2026-06-17 (EC2-2 `team06-fandrops-2` 기동, k6 v2.0.0 설치) |
| **선행 ADR** | [ADR-021 단일 EC2 인프라](./ADR-021-aws-single-ec2-infra-phase1.md) · [ADR-022 CD 파이프라인](./ADR-022-cd-pipeline-gha-s3-ssm.md) |
| **관련** | [current-infra-state.md §7](../operations/aws/current-infra-state.md) · [run-k6.yml](../../.github/workflows/run-k6.yml) · [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

SLO 측정 목적의 k6 부하 테스트는 **동일 VPC 내 전용 EC2-2(t3.small)**에서 실행한다. GitHub Actions runner는 SLO 측정에 사용하지 않는다.

---

## Context

### 인프라 현황

| 항목 | 값 |
| --- | --- |
| EC2-1 (App) | `i-07d1c60d175cdb8ca`, t3.medium, `10.0.1.114`, 서울 리전 `ap-northeast-2a` |
| EC2-2 (k6) | `i-067eea702d856fa05`, t3.small, `10.0.1.47`, 동일 VPC `ap-northeast-2a` |
| k6 버전 | v2.0.0 (`team06-fandrops-2`) |
| SLO | Write P95 < 300ms, Read P95 < 120ms |

### 문제

k6를 GitHub Actions runner에서 실행하면 SLO 측정에 다음 문제가 발생한다.

1. **왕복 지연 오염:** GitHub Actions runner는 미국 동부(us-east-1 등) 위치. AWS 서울(ap-northeast-2) EC2-1까지 Trans-Pacific 왕복 지연 약 180~220ms가 k6 측정 레이턴시에 포함되어 애플리케이션 실제 처리 성능을 반영하지 못한다
2. **EC2-1 CPU 경합:** k6를 EC2-1(App 서버)과 동일 인스턴스에서 실행하면 k6의 CPU 사용이 App 서버 성능을 저하시켜 측정 결과가 왜곡된다

---

## 방안 비교

### A안 — GitHub Actions hosted runner에서 k6 실행

```
GHA Runner (us-east) → HTTPS → EC2-1 api.fandrops.site (ap-northeast-2)
측정 레이턴시 = 애플리케이션 처리 시간 + 미국-서울 왕복 지연(~200ms)
```

| 관점 | 평가 |
| --- | --- |
| 추가 비용 | 없음 |
| 레이턴시 오염 | Trans-Pacific ~180~220ms 포함 → Write P95 < 300ms SLO 측정 불가 |
| 격리성 | App 서버와 분리됨 |
| **기각 이유** | P95 SLO 목표(Write 300ms, Read 120ms)가 Trans-Pacific 지연 하나로 초과된다. SLO 측정 목적에서 네트워크 지연이 비즈니스 로직 처리 성능을 가려서는 안 된다. |

### B안 — EC2-1과 동일 인스턴스에서 k6 실행

```
EC2-1:  [Spring Boot] + [k6] → localhost:8081
         (CPU 공유, 측정 중 App CPU 감소)
```

| 관점 | 평가 |
| --- | --- |
| 추가 비용 | 없음 |
| 레이턴시 | loopback 접근 — 네트워크 지연 없음 |
| CPU 경합 | k6 고부하(1,000+ VU) 시 App 서버 CPU 감소 → 측정값 실제 성능보다 낮게 나옴 |
| **기각 이유** | k6가 App 서버와 CPU를 공유하면 부하 생성 자체가 측정 대상에 영향을 주는 측정 오염이 발생한다. |

### C안 — 전용 EC2-2 (동일 VPC, 별도 인스턴스) ✅ 채택

```
EC2-2 (k6, t3.small, 10.0.1.47)
  → VPC 내부 경로 → EC2-1 (App, 10.0.1.114:8081)
  → 또는 HTTPS → api.fandrops.site (동일 리전)
```

| 관점 | 평가 |
| --- | --- |
| 추가 비용 | t3.small ~10,500원/월 → 총 ~82,900원 (예산 90,000원 이내) |
| 레이턴시 | 동일 AZ VPC 내부 경로 → 왕복 지연 1~3ms |
| CPU 격리 | App 서버와 완전 분리 — 부하 생성이 측정 대상에 영향 없음 |
| Prometheus 연동 | EC2-2에서 `--out experimental-prometheus-rw` → EC2-1:9090 직접 전송 가능 |
| **결론** | **채택** |

---

## Decision

**C안 채택 — 전용 EC2-2(t3.small)에서 SLO 측정.**

동일 VPC(`ap-northeast-2a`) 내에 k6 전용 EC2-2를 기동하고 App 서버(EC2-1)에 VPC 내부 경로(`10.0.1.114:8081`)로 직접 접근한다. SLO 측정 결과는 Prometheus Remote Write로 EC2-1:9090에 전송한다 (ADR-027).

**GHA runner 예외 — s07 시나리오:**

s07(300 RPS, `constant-arrival-rate`)은 GHA runner에서 실행했다. s07의 목적은 애플리케이션 처리 성능 측정이 아닌 특정 RPS에서의 에러율·포화 여부 확인이며, 네트워크 지연이 RPS 달성 여부 판단에 영향을 주지 않는다. 따라서 GHA runner 사용이 허용된다.

**수동 실행 안전 장치 (Operational Safeguards):**

k6 부하 테스트는 의도치 않은 실행이 DB 데이터를 오염시킬 수 있다. 다음 안전 장치를 `run-k6.yml`에 구현했다.

| 장치 | 구현 | 이유 |
| --- | --- | --- |
| `workflow_dispatch` 전용 | `push`/`schedule` 트리거 없음 | 자동 트리거 시 운영 시간대 EC2 CPU 스파이크 + 데이터 오염 위험 |
| `confirm: "yes"` 입력 필수 | `if: ${{ github.event.inputs.confirm == 'yes' }}` | 시나리오 선택 실수로 인한 DB 데이터 오염 이력 존재 (s03 WireMock 미기동 상태에서 실행 → 결제 실패율 100%) |
| WireMock 사전 확인 | s03/s06 실행 전 SSM RunCommand로 WireMock 컨테이너 상태 확인 | WireMock 미기동 상태에서 s03/s06 실행 시 결과 전체 재실행 필요 |

---

## Consequences

### 긍정

- Trans-Pacific 지연 제거 → VPC 내부 왕복 1~3ms
- App 서버 CPU 격리 → k6 부하가 측정 대상에 영향 없음
- Prometheus Remote Write 직접 연결 가능
- s01~s07 전 시나리오 SLO 달성 확인

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| 월 ~10,500원 추가 비용 | t3.small 상시 기동 | 예산 90,000원 범위 내 (~82,900원). SLO 수치 신뢰성이 비용보다 중요 |
| EC2-2 수동 관리 | CD 파이프라인 범위 외 | k6는 수동 트리거(`workflow_dispatch`) — 자동화 불필요 |
| s07 GHA runner 레이턴시 포함 | Trans-Pacific 경유 | s07 목적이 처리 성능 측정이 아닌 포화점 확인이므로 허용 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| SLO 측정 | s01~s06 결과는 EC2-2에서만 측정 — GHA runner 측정값을 SLO 근거로 사용 금지 |
| 수동 실행 | k6 실행은 `workflow_dispatch` + `confirm: "yes"` 필수 — 자동 트리거 추가 금지 |
| WireMock 확인 | s03/s06 실행 전 WireMock 기동 상태 반드시 확인 |

---

## 검증

- [x] EC2-2 `i-067eea702d856fa05`, t3.small, `10.0.1.47` — 동일 VPC 확인
- [x] k6 v2.0.0 설치 확인 (`k6 version`)
- [x] `http://10.0.1.114:8081/actuator/health` = `UP` 확인 (VPC 내부 경로)
- [x] s01~s06 SLO 전 항목 달성 (k6-realfinal-result.md)
- [x] `run-k6.yml` — `confirm: "yes"` 미입력 시 job 전체 skip 확인
- [x] s03/s06 WireMock 사전 확인 스텝 동작 확인

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| k6 실행 워크플로 | [run-k6.yml](../../.github/workflows/run-k6.yml) |
| 현행 인프라 상태 (EC2-2 정보) | [current-infra-state.md §7](../operations/aws/current-infra-state.md) |
| k6 최종 측정 결과 | [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
| Prometheus Remote Write 수집 | [ADR-027](./ADR-027-k6-prometheus-remote-write.md) |
| WireMock Toss 모킹 | [ADR-026](./ADR-026-wiremock-toss-payment-mocking.md) |
