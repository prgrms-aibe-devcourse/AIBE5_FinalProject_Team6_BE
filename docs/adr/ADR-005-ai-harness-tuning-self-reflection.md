# ADR-005: AI 에이전트 하네스 1차 튜닝 및 자동 회고(Self-Reflection) 파이프라인 도입

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-28 |
| **관련 문서** | [ADR-004](./ADR-004-ai-agent-harness-engineering.md) · [SHARED](../ai/SHARED.md) · [auto-pr](../ai/workflows/auto-pr.md) · [jangseongjae persona](../ai/personas/jangseongjae.md) · [jiyoungjae persona](../ai/personas/jiyoungjae.md) |

---

## Title

ADR-004로 구축된 AI 에이전트 하네스의 **중간 회고(Mid-Point Retro)** 를 CTO·수석 AI 프롬프트 엔지니어 관점에서 수행하고, 발견된 문서 불일치·환각 위험·토큰 낭비·페르소나 공백을 제거한다. 동시에 하네스가 스스로 진화할 수 있도록 **자동 회고(Self-Reflection) 파이프라인**을 워크플로우에 내재화한다.

---

## Context — 왜 이 결정이 필요한가

ADR-004 수립 이후 본격적인 도메인 개발(payment · queue · order)이 진행되면서 아래 4가지 구조적 문제가 노출됐다.

1. **문서 불일치로 인한 SSOT 신뢰도 저하**: ADR-004 본문이 "4단계 워크플로우"를 기술한 상태에서 SHARED.md에 Retro(5단계)가 추가되어 두 SSOT가 서로 다른 사실을 기술하게 됐다. AI가 ADR-004를 읽으면 구버전 룰을 공식으로 판단할 위험이 생겼다.

2. **페르소나 라우팅 공백**: `apps/api-server/**/queue/**` · `**/ratelimit/**` 경로 수정 시 CLAUDE.md 라우팅이 지영재 페르소나만 로드했다. 대기열·RateLimit 도메인 계약(멱등, RATE_LIMITED, SSE SLO)을 보유한 장성재 페르소나가 누락되어 환각 구현 리스크가 상존했다. 동일한 구조적 문제가 인증·보안 필터(`security/**`, `auth/**`) 경로에도 잠재했다.

3. **회고 루프 부재**: 코딩·PR 완료 후 AI가 스스로 프롬프트 품질을 평가하고 피드백을 남기는 메커니즘이 없어, 하네스가 세션 간 동일한 실수를 반복할 수 있는 구조였다.

4. **드롭스 오픈런 엣지 케이스 미명시**: 드롭스 오픈런(1만 명 동시 접속) 시나리오에서 jangseongjae 페르소나에 SSE 연결 SLO · Access Ticket TTL 계약 · Outbox 폴러 오너십이 명시되지 않아 엣지 케이스 구현이 추측에 의존하는 상황이었다.

---

## Decision — 결정 및 수행한 작업

### 1. 자동 회고(Self-Reflection) 워크플로우 신규 도입

하네스가 스스로 진화하기 위해 두 위치에 회고 단계를 내재화했다.

#### 1-1. SHARED.md — 코드 작성 전 사고 절차에 '5. Retro' 단계 추가

`SHARED.md`의 `코드 작성 전 사고 절차` 표에 기존 4단계(Brainstorm → Plan → Execute → Debug) 다음에 **5. Retro** 단계를 추가했다.

- **행동 지침**: 코딩·디버깅 완료 후 ① SHARED·페르소나 룰의 명확성 자체 평가 → ② 비효율·모호·충돌 지시 기록 → ③ 아래 형식으로 출력
  ```
  [Retro] 룰 작동: <1줄> | 튜닝 제안: <1줄> | SSOT 동기화 필요: <있음/없음>
  ```
- **목적**: 로컬 코딩 세션에서도 모든 작업 완료 시점마다 하네스 품질 피드백이 생성되도록 강제한다.
- **사전·사후 구분 명시**: 섹션 도입부에 `"1~4단계: 사전·진행 / 5단계(Retro): 완료 후 사후"` 를 명시하여 단계 간 혼동을 방지했다.

#### 1-2. auto-pr.md — PR 워크플로우 마지막에 '9. 하네스 회고 및 보고' 단계 추가

`auto-pr.md` 워크플로우의 8단계(PR 생성) 이후에 **9. 하네스 회고 및 보고** 단계를 추가했다.

- **리포트 형식 (4개 필드)**:
  ```
  [AI 하네스 회고 리포트]
  - 룰 작동 분석: <SHARED·페르소나 지침이 명확하게 동작했는지 1~2줄>
  - 프롬프트 튜닝 제안: <비효율·모호·충돌이 있었던 지시와 개선안 1줄>
  - SSOT 동기화 필요: <있음 — 대상 문서 명시 / 없음>
  - 미명시 Edge Case: <발견된 예외 상황 / 없음>
  ```
- **파일 저장**: 리포트를 `docs/ai/workflows/generated/retro-<PR번호>.md` 에 저장하여 다음 세션에서 참조 가능하게 한다.
- **목적**: 터미널 출력만으로 끝나는 일회성 피드백을 없애고, PR 단위로 회고 이력을 누적하여 하네스 진화 루프를 닫는다.

---

### 우선순위 분류

| 등급 | 기준 | 항목 수 |
| --- | --- | --- |
| P0 | 즉각 수정 — 문서 불일치·환각 유발 | 3 |
| P1 | 룰 모순 제거·드롭스 엣지 케이스 보강 | 2 |
| P2 | 토큰 낭비 제거·회고 루프 완성 | 2 |

---

### P0 — 즉각 수정

#### P0-1. ADR-004 단계 수 동기화

- **파일:** `docs/adr/ADR-004-ai-agent-harness-engineering.md`
- **변경:** `'Brainstorm → Plan → Execute → Debug'의 4단계` → `'Brainstorm → Plan → Execute → Debug → Retro'의 5단계`
- **이유:** SHARED.md(5단계)와 ADR-004(4단계)가 서로 다른 사실을 기술하는 상태를 해소. SSOT 간 충돌은 AI 판단 오류의 직접 원인이 된다.

#### P0-2. CLAUDE.md — 대기열·RateLimit 이중 페르소나 로드

- **파일:** `CLAUDE.md`
- **변경:** 라우팅 표에 아래 행 추가
- **이유:** 기존 `apps/api-server/**` → 지영재 단일 라우팅은 대기열 필터 코드 수정 시 장성재의 도메인 계약(멱등·RATE_LIMITED·SSE SLO)을 통째로 누락시켰다.

| 작업 경로 | 담당 | Persona 로드 |
| --- | --- | --- |
| `apps/api-server/**/queue/**` · `apps/api-server/**/ratelimit/**` | 장성재 + 지영재 | `@jangseongjae.md` `@jiyoungjae.md` (양쪽 로드) |

#### P0-3. CLAUDE.md — 인증·보안 필터 이중 페르소나 로드 _(페르소나 점검에서 신규 발견)_

- **파일:** `CLAUDE.md`
- **변경:** 라우팅 표에 아래 행 추가
- **이유:** `pyojimin.md` 수정 가능 경로에 `apps/api-server/**`(Security·인증 필터)가 명시되어 있으나 CLAUDE.md 라우팅에 해당 경로가 없었다. Security 필터 수정 시 pyojimin의 인증·권한 계약(PII 로그 금지, 토큰 TTL, 권한 클레임)이 누락될 위험이 있었다.

| 작업 경로 | 담당 | Persona 로드 |
| --- | --- | --- |
| `apps/api-server/**/security/**` · `apps/api-server/**/auth/**` | 표지민 + 지영재 | `@pyojimin.md` `@jiyoungjae.md` (양쪽 로드) |

---

### P1 — 룰 모순 제거 및 드롭스 엣지 케이스 보강

#### P1-1. SHARED.md — Retro 단계 재정의

- **파일:** `docs/ai/SHARED.md`
- **기존 문제:** `"보고서 형태로 ... 한 줄로 제시하라"` — 보고서(다중 항목)와 한 줄이 형식 충돌. `"코드를 바로 쓰지 말고 아래 순서를 따른다"` 헤더가 사후 단계인 Retro를 사전 가드로 오인하게 만들었다.
- **변경 내용:**
  - 섹션 도입부: `"1~4단계 사전·진행 / 5단계(Retro) 완료 후 사후"` 구분 명시
  - Retro 행: 모순 제거 → ①자체 평가 ②기록 ③출력 형식의 3단계 행동 지침으로 교체

#### P1-2. jangseongjae.md — 드롭스 오픈런 엣지 케이스 3종 체크리스트 추가

- **파일:** `docs/ai/personas/jangseongjae.md`
- **추가 항목:** 드롭스 오픈런 엣지 케이스 3종 (아래 표)
- **이유:** feat/26 SSE 구현(88b01fa) 이후 연결 한계 SLO가 어느 페르소나에도 없었고, Ticket TTL 레이스 컨디션과 Outbox 폴러 오너십이 양쪽 페르소나 모두 "합의"로만 처리되어 공백 상태였다.

| 항목 | 내용 |
| --- | --- |
| SSE SLO | 드롭스 오픈런 시 SSE emitter 최대 동시 유지 수 SLO 정의. 초과 시 429 + retryable:true (지영재와 Nginx 값 동시 합의) |
| Access Ticket TTL | 기본값 5분 (invariants-and-state-machines.md §4). 드롭스 P95 주문 생성 응답 시간 x3 이상인지 실측 후 application-*.yml 에서 조정 |
| 결제 재시도 Job 구분 | 결제 복구 스케줄러(payment-application)와 알림 Outbox(outbox_events, 표지민)를 혼동하지 않는다. 오너십은 별개 |

---

### P2 — 토큰 낭비 제거 및 회고 루프 완성

#### P2-1. SHARED.md — 포트폴리오 HTML P0 → 보조 참조로 이동

- **파일:** `docs/ai/SHARED.md`
- **변경:**
  - `필수 문서 (항상)` 표에서 `docs/01_service_intro.html` ~ `docs/05_architecture.html` (2행) 제거
  - `작업 유형별 추가 참조` 표 첫 행에 `서비스 개요·기획·IA·화면 흐름` 항목으로 이동
- **이유:** 포트폴리오 HTML 5개를 매 세션 P0로 로드하면 수백~수천 토큰을 소모한다. 실제 코딩 세션의 참조 빈도는 낮으며 `docs/README.md` 목차로 접근 가능하다.

#### P2-2. auto-pr.md — 회고 리포트 필드 확장 및 파일 저장 내재화

- **파일:** `docs/ai/workflows/auto-pr.md`
- **변경:**
  - Step 9 리포트 필드 2개 → 4개 확장 (룰 작동 분석 · 튜닝 제안 · SSOT 동기화 필요 · 미명시 Edge Case)
  - `docs/ai/workflows/generated/retro-<PR번호>.md` 저장 지침 추가
- **이유:** 터미널 출력만으로는 세션 간 피드백 루프가 닫히지 않는다. generated/ 디렉터리에 저장하면 과거 회고를 참조할 수 있어 하네스가 실제로 진화한다.

---

### 페르소나 파일 일관성 점검 (전체 5종)

본 튜닝 과정에서 5개 페르소나 파일 전체를 교차 점검했다.

| 파일 | 점검 결과 | 조치 |
| --- | --- | --- |
| `jangseongjae.md` | SSE SLO · TTL · Outbox 오너십 누락 | P1-2에서 체크리스트 3종 추가 |
| `jiyoungjae.md` | YAML `paths: ".github/workflows/**"` vs 본문·CLAUDE.md `.github/**` 불일치 | YAML 경로를 `.github/**` 로 수정 |
| `pyojimin.md` | `apps/api-server/**`(Security 필터) 경로가 CLAUDE.md 라우팅에 미반영 | P0-3으로 CLAUDE.md 라우팅 행 추가 |
| `hyungseongbin.md` | 이상 없음 — 드롭스 동시성 체크리스트·k6 검증 절차 완비 | 조치 없음 |
| `junghwancheol.md` | 이상 없음 — community 모듈 경계 명확 | 조치 없음 |

---

## Consequences — 트레이드오프

### 긍정적 효과

- **SSOT 일관성 회복**: ADR-004 · SHARED · CLAUDE.md 세 문서가 동일한 5단계 워크플로우를 기술하게 됐다.
- **드롭스 오픈런 안전망 강화**: SSE 연결 SLO · TTL 계약 · Outbox 오너십이 페르소나 레벨에서 강제된다.
- **라우팅 공백 2종 해소**: 대기열·RateLimit(장성재) 및 Security·Auth 필터(표지민) 경로 모두 이중 페르소나 로드 규칙이 적용됐다.
- **토큰 절감**: 포트폴리오 HTML 5개가 선택적 참조로 이동되어 일반 코딩 세션의 컨텍스트 비용이 감소한다.
- **자기 진화 루프 완성**: Retro 단계가 SHARED(로컬 코딩)와 auto-pr.md(PR 워크플로우) 양쪽에 내재화되어, 모든 작업 완료 시점에 하네스 품질 피드백이 생성·저장된다.

### 부정적 효과 및 완화

- **이중 페르소나 로드 오버헤드**: `queue/ratelimit` 및 `security/auth` 경로 수정 시 두 페르소나를 로드하므로 컨텍스트가 증가한다. 완화: 해당 경로는 설정·필터 코드에 한정되므로 발생 빈도가 낮다.
- **retro 파일 누적**: PR마다 `generated/retro-*.md` 가 쌓인다. 완화: `generated/` 디렉터리는 `.gitignore` 또는 월별 정리 대상으로 관리한다.

---

## Compliance — 준수 방법

| # | 규칙 |
| --- | --- |
| 01 | 모든 코딩·디버깅 작업이 끝나면 **반드시 5. Retro 단계를 실행**하고 `[Retro]` 형식으로 출력한다. |
| 02 | PR 생성 후 `docs/ai/workflows/generated/retro-<PR번호>.md` 를 저장한다. 저장 없이 작업을 완료로 보고하지 않는다. |
| 03 | `apps/api-server/**/queue/**` 또는 `**/ratelimit/**` 경로 수정 시 `@jangseongjae.md` 와 `@jiyoungjae.md` 를 양쪽 모두 로드한다. |
| 04 | `apps/api-server/**/security/**` 또는 `**/auth/**` 경로 수정 시 `@pyojimin.md` 와 `@jiyoungjae.md` 를 양쪽 모두 로드한다. |
| 05 | SSOT 문서(SHARED, ADR, 페르소나)를 변경할 때는 관련 ADR에 변경 사실을 동기화한다. 본 ADR이 그 선례다. |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| AI 하네스 최초 구축 | [ADR-004](./ADR-004-ai-agent-harness-engineering.md) |
| 공통 AI 지침 | [SHARED.md](../ai/SHARED.md) |
| PR 자동화 워크플로우 | [auto-pr.md](../ai/workflows/auto-pr.md) |
| 결제·대기열 페르소나 | [jangseongjae.md](../ai/personas/jangseongjae.md) |
| SRE·플랫폼 페르소나 | [jiyoungjae.md](../ai/personas/jiyoungjae.md) |
| 인증·알림 페르소나 | [pyojimin.md](../ai/personas/pyojimin.md) |
| 멀티모듈 모놀리스 | [ADR-001](./ADR-001-multi-module-monolith.md) |