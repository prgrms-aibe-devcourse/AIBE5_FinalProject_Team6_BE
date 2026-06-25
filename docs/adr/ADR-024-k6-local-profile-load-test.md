# ADR-024: k6 부하 테스트 인증 전략 — local profile X-Fan-Id 우회 및 tokens.csv 사전 발급

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-05 |
| **구현 완료일** | 2026-06-10 (LocalSecurityConfig, tokens.csv seed 준비) |
| **선행 ADR** | [ADR-023 k6 전용 EC2 실행](./ADR-023-k6-dedicated-ec2-runner.md) |
| **관련** | [infra/k6/lib/auth.js](../../infra/k6/lib/auth.js) · [infra/k6/seed/tokens.csv](../../infra/k6/seed/) · [apps/api-server/src/main/java/com/fandrops/config/LocalSecurityConfig.java](../../apps/api-server/src/main/java/com/fandrops/config/LocalSecurityConfig.java) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

k6 부하 테스트에서 운영 JWT 보안 설정을 변경하지 않고, 로컬 개발 환경에서는 `local` 프로필 + `X-Fan-Id` 헤더 직접 주입으로, 실제 SLO 측정에서는 **사전 발급 JWT tokens.csv**로 각각 인증 문제를 해결한다. 테스트 목적은 인증 흐름 검증이 아니라 **주문·재고·SSE·대기열의 동시성 검증**에 집중한다.

---

## Context

### 문제

k6로 고부하 시나리오(s01: 200 VU, s04: 1,000 VU, s05: 2,000 연결)를 실행하려면 수백~수천 개의 인증된 팬 계정이 필요하다. 다음 두 가지 방식은 적합하지 않다.

1. **테스트 시작 시 로그인 반복:** VU마다 `POST /api/v1/auth/login`을 호출하면 로그인 부하가 DB를 먼저 포화시켜 실제 측정 대상(주문·재고·대기열)의 SLO를 측정하기 전에 결과가 오염된다
2. **prod 프로필 보안 설정 변경:** 운영 JWT 검증을 약화하거나 우회하면 운영 환경의 보안 원칙이 훼손된다

### 테스트 목적 명시

k6 시나리오가 검증하는 핵심 속성:

| 시나리오 | 검증 목적 |
| --- | --- |
| s01: 주문 동시성 | 200 VU 동시 주문 → 재고 100개 → 오버셀 0건, P95 < 300ms |
| s04: 드롭 스파이크 | 0 → 1,000 VU 급증 → 재고 낙관락 충돌 처리, 5xx < 0.1% |
| s05: SSE 대기열 | 2,000 동시 SSE 연결 → 연결 유지 안정성 |
| s06: 통합 워크로드 | 대기열 진입 → 주문 → 결제 전체 흐름 P95 측정 |

인증 흐름(로그인, JWT 발급) 자체는 검증 대상이 아니다.

---

## 방안 비교

### A안 — 테스트 시작 시 `POST /auth/login` 반복 호출

```
k6 setup():
  for i in 1..2100:
    POST /api/v1/auth/login → JWT 획득
  
default():
  Authorization: Bearer <jwt>
```

| 관점 | 평가 |
| --- | --- |
| 구현 단순도 | 중간 |
| SLO 측정 품질 | 낮음 — 로그인 DB 쿼리 폭발이 실제 측정 시작 전 시스템을 예열 이상으로 부하 |
| 확장성 | VU 2,100개 × 로그인 DB 쿼리 = DB 커넥션 포화 위험 |
| **기각 이유** | 인증 흐름이 실제 측정 대상(주문·재고·대기열)의 성능에 간섭한다 |

### B안 — prod 프로필에 테스트 인증 우회 코드 추가

```java
// ApiSecurityConfig — prod 프로필에 테스트 헤더 추가
if (request.getHeader("X-Test-Fan-Id") != null) {
    // prod에서도 JWT 검증 건너뜀
}
```

| 관점 | 평가 |
| --- | --- |
| 구현 단순도 | 쉬움 |
| 보안 | **prod 보안 약화** — 운영 서버에 인증 우회 코드 상주 |
| **기각 이유** | prod 프로필 보안 설정 변경은 원칙 위반이다 |

### C안 — local 프로필 X-Fan-Id 우회 + tokens.csv 사전 발급 ✅ 채택

**로컬 개발 환경:**

```java
@Profile("local")
@Configuration
public class LocalSecurityConfig {
    // anyRequest().permitAll() + JWT 필터 등록은 유지
    // X-Fan-Id 헤더로 fanId 직접 주입
}
```

```js
// compare_strategies.js — 로컬 테스트용
headers: { 'X-Fan-Id': String(fanId) }
```

**SLO 측정 환경 (EC2-2 → EC2-1 prod):**

```csv
# tokens.csv — 팬 1~2100 JWT 사전 발급
fan_id,token
1,eyJhbGciOiJIUzI1NiJ9...
2,eyJhbGciOiJIUzI1NiJ9...
```

```js
// 모든 SLO 시나리오 — tokens.csv 로드
const userTokens = new SharedArray('users', () =>
  papaparse.parse(open('../seed/tokens.csv'), { header: true }).data
);
// 이후 Authorization: Bearer <token> 사용
```

| 관점 | 평가 |
| --- | --- |
| prod 보안 | **변경 없음** — prod 프로필은 항상 JWT 검증 |
| local 보안 | `@Profile("local")` 격리 — prod 빌드에 포함되지 않음 |
| SLO 측정 품질 | JWT Bearer 인증 경로를 통과하므로 실제 운영 경로와 동일한 필터 체인 실행 |
| 구현 복잡도 | tokens.csv 사전 생성 스크립트 필요 (1회성 작업) |
| **결론** | **채택** |

---

## Decision

**C안 채택 — local 프로필 X-Fan-Id 우회(로컬 개발용) + tokens.csv 사전 발급(SLO 측정용).**

두 인증 방식의 역할 분리:

| 환경 | 인증 방식 | 목적 |
| --- | --- | --- |
| 로컬 개발 (`SPRING_PROFILES_ACTIVE=local`) | `X-Fan-Id` 헤더 직접 주입 | 빠른 로컬 시나리오 개발·디버깅. 동시성 로직 검증에 집중 |
| SLO 측정 (EC2-2 → EC2-1 prod) | `tokens.csv` JWT Bearer 토큰 | prod JWT 필터 체인 통과. 인증 오버헤드 포함된 실제 SLO 측정 |

`@Profile("local")` 어노테이션으로 `LocalSecurityConfig`는 prod 빌드에서 완전히 제외된다. prod 프로필의 `ApiSecurityConfig`는 변경 없다.

tokens.csv는 팬 ID 1~2,100에 대해 사전에 JWT를 일괄 발급 후 S3에 업로드하고, `run-k6.yml`에서 시나리오 실행 전에 다운로드한다.

---

## Consequences

### 긍정

- prod 보안 설정 변경 없음 — 운영 JWT 검증 로직 그대로 유지
- 로그인 DB 쿼리 없음 → 테스트 시작 시 DB 예열 오염 없음
- `@Profile("local")` 격리로 코드 분기 오염 없음
- SLO 측정이 실제 운영 인증 경로(JWT 필터 체인)를 통과하므로 측정값 신뢰성 유지

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| tokens.csv 만료 관리 | JWT 만료 시간 존재 | 장기 만료 토큰 발급 또는 테스트 전 재발급 스크립트로 관리 |
| tokens.csv S3 수동 업로드 필요 | 자동화 미구현 | 1회성 준비 작업. 재발급 시 `aws s3 cp` 1회 실행 |
| local profile로 인증 흐름 미검증 | 설계 의도 | 인증 흐름은 별도 통합 테스트에서 커버. k6는 동시성 검증에 집중 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| prod 인증 설정 | `ApiSecurityConfig`(prod)에 테스트용 인증 우회 코드 추가 금지 |
| SLO 측정 인증 | SLO 근거가 되는 측정은 반드시 tokens.csv JWT로 실행 — X-Fan-Id 측정값은 SLO 근거로 사용 금지 |
| local 프로필 격리 | `@Profile("local")` 빈은 prod 프로필 활성화 시 로드되지 않음 확인 필수 |

---

## 검증

- [x] `LocalSecurityConfig` — `@Profile("local")` 적용, prod 빌드에서 미로드 확인
- [x] `tokens.csv` — fan_id 1~2100 JWT 포함, `SharedArray`로 로드 확인
- [x] s01~s06 — tokens.csv JWT + `Authorization: Bearer` 헤더로 SLO 측정 완료
- [x] prod 프로필 서버에서 X-Fan-Id 헤더 처리 코드 없음 확인

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| LocalSecurityConfig 구현 | [LocalSecurityConfig.java](../../apps/api-server/src/main/java/com/fandrops/config/LocalSecurityConfig.java) |
| k6 인증 헬퍼 | [infra/k6/lib/auth.js](../../infra/k6/lib/auth.js) |
| k6 시나리오 사전 준비 | [infra/k6/README.md](../../infra/k6/README.md) |
| k6 전용 EC2 실행 결정 | [ADR-023](./ADR-023-k6-dedicated-ec2-runner.md) |
