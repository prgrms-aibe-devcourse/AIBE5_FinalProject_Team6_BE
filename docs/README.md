# FANDROPS 문서

> 프로젝트 개요·기술스택·빠른 시작: [루트 README.md](../README.md)

## 설계 (Design)

| 영역 | 문서 | 다이어그램 |
| --- | --- | --- |
| ERD | [erd/erd-design.md](./erd/erd-design.md) | [erd/erd.png](./erd/erd.png) |
| Data · Retention / Audit | [erd/data-retention-and-audit-policy.md](./erd/data-retention-and-audit-policy.md) | — |
| Data · Lifecycle | [erd/data-lifecycle.md](./erd/data-lifecycle.md) | — |
| Sequence | [sequence/payment-flow-reason.md](./sequence/payment-flow-reason.md) | [sequence/payment-order-flow.png](./sequence/payment-order-flow.png) |
| ADR | [ADR-001](./adr/ADR-001-multi-module-monolith.md) · [ADR-002](./adr/ADR-002-per-layer-gradle-modules.md) · [ADR-003 장바구니 RDB](./adr/ADR-003-cart-storage-rdb-phase1.md) | — |
| **기능 요구사항 §3** | [requirements/mvp-functional-requirements.md](./requirements/mvp-functional-requirements.md) | **F-ID SSOT** |
| **포트폴리오 01–05** | [01](./01_service_intro.html) · [02](./02_research.html) · [03](./03_planning.html) · [04](./04_IA.html) · [05](./05_architecture.html) | 서비스·리서치·기획·IA·아키텍처 |
| Architecture | [architecture/architecture.md](./architecture/architecture.md) | — |
| API (MVP) | [api/mvp-api-spec.md](./api/mvp-api-spec.md) | — |
| API · Contract | [api/api-contract.md](./api/api-contract.md) | — |
| State · Invariants | [state/invariants-and-state-machines.md](./state/invariants-and-state-machines.md) | [state/order-state-machine.png](./state/order-state-machine.png) |
| Operations · Failure | [operations/failure-policy.md](./operations/failure-policy.md) | — |
| Operations · Incident | [operations/incident-response.md](./operations/incident-response.md) | — |
| Operations · Metrics | [operations/observability-metrics.md](./operations/observability-metrics.md) | — |

> **다이어그램 PNG** (`erd.png`, `order-state-machine.png`, `payment-order-flow.png`): 문서에서 링크하지만 **레포에 없으면** 해당 경로에 파일을 커밋한다. 없을 때는 각 `.md`의 표·Mermaid가 SSOT이다.

> F-ID·기능명: [requirements §3](./requirements/mvp-functional-requirements.md) · HTTP: [mvp-api-spec](./api/mvp-api-spec.md) · DB: [ERD](./erd/erd-design.md).

## AI 코딩 (Claude Code)

| 문서 | 설명 |
| --- | --- |
| [**AI 가이드 (시작)**](./ai/README.md) | SHARED + 담당 persona |
| [공통 지침](./ai/SHARED.md) | 페르소나 김최고 · 필수 docs · 금지 사항 |
| [루트 CLAUDE.md](../CLAUDE.md) · [AGENTS.md](../AGENTS.md) | Claude 진입 (경로만 — 본문·금지·SSOT는 [SHARED](./ai/SHARED.md) **매 세션 필수**) |

담당별: `docs/ai/personas/{pyojimin,junghwancheol,hyungseongbin,jangseongjae,jiyoungjae}.md`  
Claude Code: [`CLAUDE.md`](../CLAUDE.md) 자동 + `@SHARED` + `@persona`

## 협업 (Contributing)

| 문서 | 설명 |
| --- | --- |
| [**Git & 코드 협업 컨벤션**](./contributing/git-collaboration-convention.md) | 브랜치 전략, 네이밍, 커밋, PR, 코드 리뷰 |
| [이슈 · Feature](../.github/ISSUE_TEMPLATE/feature.md) · [Bug](../.github/ISSUE_TEMPLATE/bug.md) | New issue 시 템플릿 선택 |
| [PR 템플릿 (GitHub)](../.github/pull_request_template.md) | New pull request 시 자동 적용 |

### 빠른 링크

- 이슈 만들기: **Issues → New issue → Feature** (기능) 또는 **Bug** (버그)
- PR 만들기: `develop` 대상으로 PR 생성 시 템플릿 자동 삽입
- 브랜치 예: `feat/23` (Feature 이슈), `fix/41` (Bug 이슈), `refactor/15`
