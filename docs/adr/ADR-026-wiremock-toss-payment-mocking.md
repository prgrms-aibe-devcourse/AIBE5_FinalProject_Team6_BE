# ADR-026: Toss 결제 API 부하 테스트를 WireMock으로 결정론적으로 모킹

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-13 |
| **구현 완료일** | 2026-06-14 (PR #243, #250 — WireMock stub 설정 및 k6 s03 연동) |
| **선행 ADR** | [ADR-023 k6 전용 EC2 실행](./ADR-023-k6-dedicated-ec2-runner.md) |
| **관련** | [infra/k6/wiremock/](../../infra/k6/wiremock/) · [run-k6.yml WireMock 사전 확인 스텝](../../.github/workflows/run-k6.yml) · [current-infra-state.md §6](../operations/aws/current-infra-state.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

k6 결제 부하 테스트(s03, s06)에서 실제 Toss API 대신 **EC2-1에서 WireMock Docker 컨테이너**를 기동해 결제 응답을 결정론적으로 제어한다. Spring Boot 앱은 `TOSS_API_BASE_URL=http://localhost:8090`으로 WireMock에 접근한다.

---

## Context

### Toss 결제 흐름

```
k6 VU → POST /api/v1/payments/toss/confirm
           → Spring Boot → TOSS_API_BASE_URL/v1/payments/confirm
                              ↓ (실제 Toss API 또는 WireMock)
                         응답 → DB 상태 변경 (RESERVED → PAID / FAILED)
```

Spring Boot는 환경변수 `TOSS_API_BASE_URL`을 통해 Toss API 엔드포인트를 주입받는다.

### 문제

k6로 결제 흐름(s03: 결제 확인, s06: 통합 워크로드)을 고부하로 테스트할 때 실제 Toss API를 사용하면 다음 문제가 발생한다.

1. **비결정론적 응답:** Toss sandbox의 응답 시간과 상태가 일정하지 않아 P95 SLO 측정값이 외부 API 성능에 의존
2. **요청 속도 제한:** Toss sandbox는 API rate limit이 존재 — 고 VU(수백 VU) 시나리오에서 429 응답 발생
3. **비용 위험:** 실제 결제 API 오류 시 운영 계정에 영향 가능성
4. **시나리오 재현 불가:** timeout·잔액 부족·서버 오류 시나리오를 의도적으로 주입할 수 없음

---

## 방안 비교

### A안 — 실제 Toss Sandbox API 사용

```
Spring Boot → https://api.tosspayments.com (sandbox)
```

| 관점 | 평가 |
| --- | --- |
| 구현 노력 | 없음 — 기존 설정 그대로 |
| 결정론적 재현 | ❌ 외부 API 응답 시간 비결정적 |
| Rate limit | ❌ 고 VU에서 429 발생 → SLO 측정 오염 |
| 시나리오 주입 | ❌ 오류 시나리오 제어 불가 |
| **기각 이유** | SLO 측정에서 Toss sandbox의 네트워크 지연과 rate limit이 간섭한다. |

### B안 — 앱 내부 Mock 서비스 (`@MockBean`, 인터페이스 stub)

```java
// @Profile("test") 또는 별도 profile
@Bean TossPaymentClient mockTossClient() { return (req) -> successResponse(); }
```

| 관점 | 평가 |
| --- | --- |
| 독립성 | 외부 의존 없음 |
| 시나리오 주입 | 코드 변경 필요 — 오류 시나리오마다 별도 프로필/설정 필요 |
| prod 코드 변경 | 테스트용 분기가 prod 코드에 침투 위험 |
| **기각 이유** | 코드 변경 없이 외부 HTTP 레벨에서 stub을 교체하는 WireMock이 더 깔끔하다. 여러 시나리오를 JSON 파일만 교체해 제어할 수 있다. |

### C안 — WireMock Docker on EC2-1 (:8090) ✅ 채택

```
Spring Boot (EC2-1 :8081)
  TOSS_API_BASE_URL=http://localhost:8090
      ↓
WireMock (Docker, EC2-1 :8090 → container :8080)
  매핑: toss-confirm-success.json / timeout.json / balance-error.json / server-error.json
```

| 관점 | 평가 |
| --- | --- |
| 결정론적 재현 | ✅ JSON 응답 고정 |
| 시나리오 주입 | ✅ JSON 파일 교체 또는 `paymentKey` 패턴 매칭으로 시나리오 선택 |
| 비용 | 없음 — 기존 EC2-1 Docker 환경 재활용 |
| 코드 변경 | 없음 — `TOSS_API_BASE_URL` 환경변수 변경만으로 전환 |
| **결론** | **채택** |

---

## Decision

**C안 채택 — WireMock Docker on EC2-1, port 8090.**

WireMock은 `wiremock/wiremock:latest` 이미지로 EC2-1에서 Docker로 실행된다. Spring Boot는 `TOSS_API_BASE_URL=http://localhost:8090`으로 WireMock에 접근한다.

**4개 stub 시나리오:**

| 파일 | `paymentKey` 패턴 | 응답 | 용도 |
| --- | --- | --- | --- |
| `toss-confirm-success.json` | `/^success-.*/` | HTTP 200, `status: "DONE"` | 정상 결제 확인 |
| `toss-confirm-timeout.json` | `/^timeout-.*/` | 응답 지연 (`fixedDelayMilliseconds`) | 타임아웃 처리 검증 |
| `toss-confirm-balance-error.json` | `/^balance-.*/` | HTTP 200, `code: "NOT_ENOUGH_BALANCE"` | 잔액 부족 처리 검증 |
| `toss-confirm-server-error.json` | `/^server-error-.*/` | HTTP 500 | 서버 오류 처리 검증 |

k6는 WireMock에 직접 접근하지 않는다. k6 → Spring Boot → WireMock 경로로만 동작한다.

**사전 확인 연동 (ADR-023 Operational Safeguards):**

`run-k6.yml`에서 s03/s06 실행 전 SSM RunCommand로 WireMock 컨테이너 상태를 확인한다. 미기동 시 즉시 실패 처리(`exit 1`)하여 측정 결과 오염을 방지한다.

---

## Consequences

### 긍정

- 결제 시나리오 완전 결정론적 재현 — s03/s06 P95 측정값이 외부 API에 의존하지 않음
- 4개 오류 시나리오 재현 가능 — timeout/잔액 부족/서버 오류 처리 로직 검증
- 코드 변경 없이 환경변수 전환만으로 실제 Toss API↔WireMock 전환

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| WireMock 장애 시 s03/s06 결과 오염 | 단일 Docker 컨테이너 | `run-k6.yml` WireMock 사전 확인으로 장애 조기 차단 |
| EC2 재기동 시 수동 재기동 필요 | Docker 자동 시작 미설정 | `docker start wiremock` 1회 실행으로 복구. `--restart always` 옵션 추가로 해소 가능 |
| 실제 Toss API 응답 특성 미검증 | WireMock이 stub 응답만 반환 | 실제 Toss API 연동은 통합 테스트 환경에서 별도 검증 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| WireMock 포트 | EC2-1 host :8090 → container :8080 고정 |
| 앱 환경변수 | k6 s03/s06 실행 시 `TOSS_API_BASE_URL=http://localhost:8090` 확인 필수 |
| s03/s06 실행 전 | WireMock 컨테이너 기동 상태 반드시 확인 후 k6 실행 |

---

## 검증

- [x] WireMock Docker 컨테이너 EC2-1 :8090 실행 확인 (`docker ps --filter name=wiremock`)
- [x] `toss-confirm-success.json` — `paymentKey` 패턴 매칭 → DONE 응답 확인
- [x] s03 실행 시 결제 확인 P95 측정 완료 (k6-realfinal-result.md)
- [x] `run-k6.yml` WireMock 사전 확인 스텝 — 미기동 시 `exit 1` 동작 확인

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| WireMock stub 파일 | [infra/k6/wiremock/](../../infra/k6/wiremock/) |
| k6 실행 워크플로 (WireMock 확인 스텝) | [run-k6.yml](../../.github/workflows/run-k6.yml) |
| 현행 인프라 (WireMock 실행 상태) | [current-infra-state.md §6](../operations/aws/current-infra-state.md) |
| 결제 결과 측정 | [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
| k6 전용 EC2 실행 | [ADR-023](./ADR-023-k6-dedicated-ec2-runner.md) |
