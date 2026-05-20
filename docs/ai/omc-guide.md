# FANDROPS — Claude Code + OMC 사용 가이드

---

## OMC란?

**oh-my-claudecode (OMC)** 는 Claude Code 위에 얹는 멀티 에이전트 오케스트레이션 레이어입니다. 쉽게 말하면 Claude Code를 더 잘 쓰게 해주는 플러그인 모음입니다.

---

## 1. 설치

Claude Code가 설치된 상태에서 채팅창에 입력:

```
setup omc
```

또는

```
/oh-my-claudecode:omc-setup
```

---

## 2. 세션 시작 (매번 필수)

새 대화를 시작할 때마다 아래를 첫 줄에 붙여넣으세요.

```
@docs/ai/SHARED.md @docs/ai/personas/<본인파일>.md 읽고 시작해줘
```

| 담당 | 본인 파일 |
|------|-----------|
| 표지민 | `docs/ai/personas/pyojimin.md` |
| 정환철 | `docs/ai/personas/junghwancheol.md` |
| 형성빈 | `docs/ai/personas/hyungseongbin.md` |
| 장성재 | `docs/ai/personas/jangseongjae.md` |
| 지영재 | `docs/ai/personas/jiyoungjae.md` |

이렇게 하면 Claude가 프로젝트 규칙, 담당 모듈, 금지사항을 모두 인지한 상태로 시작합니다.

---

## 3. 주의사항

- **매 세션마다** SHARED + persona 로드 필수 — 안 하면 팀 규칙을 모름
- Claude가 커밋·push를 제안해도 **직접 요청 전엔 하지 않음**
- 타 담당자 모듈 수정 시 해당 오너에게 리뷰 요청

---

## 4. 키워드 트리거 (슬래시 없이 채팅에 입력)

| 키워드 | 동작 |
|--------|------|
| `autopilot` | 아이디어 → 완성 코드까지 자율 실행 |
| `ralph` | 완료될 때까지 반복 실행 |
| `ulw` | ultrawork — 대형 작업 고속 병렬 실행 |
| `ccg` | Claude + Codex + Gemini 3모델 비교 |
| `ralplan` | 합의 기반 계획 수립 |
| `deep interview` | 요구사항 심층 인터뷰 |
| `tdd` | TDD 모드 |
| `deslop` / `anti-slop` | AI 생성 불필요 코드 정리 |
| `ultrathink` | 깊은 추론 모드 |
| `cancelomc` | 실행 중단 |

---

## 5. 워크플로우 스킬 (`/` 로 호출)

| 명령어 | 용도 |
|--------|------|
| `/autopilot` | 전체 자율 실행 |
| `/ralph` | 완료까지 지속 실행 |
| `/ultrawork` | 고속 병렬 실행 |
| `/ultraqa` | 테스트 → 검증 → 수정 반복 |
| `/team` | 여러 에이전트 병렬 오케스트레이션 |
| `/omc-teams` | 팀 설정 관리 |
| `/ccg` | 3모델 합성 |
| `/omc-plan` | 계획 수립 |
| `/ralplan` | 합의 기반 계획 |
| `/tdd` | TDD 구현 |
| `/review` | 코드 리뷰 |
| `/security-review` | 보안 검토 |
| `/comprehensive-review:full-review` | 종합 리뷰 |
| `/investigate` | 버그 원인 분석 |
| `/diagnose` | 에러 진단 |
| `/trace` | 실행 흐름 추적 |
| `/deep-dive` | 심층 분석 |
| `/prototype` | 빠른 프로토타입 |
| `/simplify` | 코드 단순화 |
| `/ai-slop-cleaner` | AI 코드 슬롭 정리 |
| `/improve-codebase-architecture` | 아키텍처 개선 제안 |
| `/deepinit` | 프로젝트 전체 AGENTS.md 생성 |
| `/deep-interview` | 요구사항 심층 인터뷰 |

---

## 6. 문서·이슈

| 명령어 | 용도 |
|--------|------|
| `/to-issues` | 작업 내용 → GitHub 이슈 변환 |
| `/to-prd` | PRD 문서 생성 |
| `/document-generate` | 문서 자동 생성 |
| `/handoff` | 작업 인수인계 문서 |
| `/retro` | 회고 정리 |
| `/release` | 릴리스 정리 |

---

## 7. 백엔드 전문

| 명령어 | 용도 |
|--------|------|
| `/backend-development:feature-development` | 백엔드 기능 개발 |
| `/backend-development:api-design-principles` | API 설계 원칙 |
| `/backend-development:saga-orchestration` | Saga 패턴 구현 |
| `/backend-development:architecture-patterns` | 아키텍처 패턴 |
| `/backend-development:cqrs-implementation` | CQRS 구현 |
| `/backend-development:microservices-patterns` | 마이크로서비스 패턴 |
| `/security-scanning:security-sast` | 정적 보안 분석 |
| `/security-scanning:security-hardening` | 보안 강화 |

---

## 8. 유틸

| 명령어 | 용도 |
|--------|------|
| `/health` | 프로젝트 상태 점검 |
| `/hud` | 현재 상태 대시보드 |
| `/omc-setup` | OMC 초기 설치 |
| `/omc-doctor` | OMC 설치 문제 진단 |
| `/update-config` | Claude Code 설정 변경 |
| `/fewer-permission-prompts` | 권한 프롬프트 줄이기 |
| `/loop` | 반복 실행 설정 |
| `/schedule` | 예약 실행 설정 |
| `/cancel` | 실행 중인 모드 중단 |
| `/external-context` | 외부 문서·리서치 로드 |
| `/learn` | 특정 주제 학습 |

---

## 9. 에이전트 종류 (자동 선택됨)

| 에이전트 | 모델 | 역할 |
|----------|------|------|
| `explore` | haiku | 코드 탐색·파일 검색 |
| `executor` | sonnet | 구현·리팩터 |
| `debugger` | sonnet | 버그 원인 분석 |
| `verifier` | sonnet | 완료 검증 |
| `tracer` | sonnet | 흐름 추적 |
| `test-engineer` | sonnet | 테스트 전략 |
| `security-reviewer` | sonnet | 보안 검토 |
| `code-reviewer` | opus | 코드 리뷰 |
| `architect` | opus | 시스템 설계 |
| `planner` | opus | 실행 계획 |
| `analyst` | opus | 요구사항 분석 |
| `critic` | opus | 계획·설계 비판적 검토 |
| `writer` | haiku | 문서 작성 |
| `designer` | sonnet | UX·인터랙션 설계 |
| `qa-tester` | sonnet | 런타임 테스트 |
| `git-master` | sonnet | 커밋·히스토리 관리 |
| `code-simplifier` | opus | 코드 단순화 |
| `document-specialist` | sonnet | SDK·API 문서 조회 |
| `scientist` | sonnet | 데이터 분석 |
