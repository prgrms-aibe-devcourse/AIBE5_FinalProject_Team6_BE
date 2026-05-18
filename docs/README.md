# FANDROPS 문서

> 프로젝트 개요·기술스택·빠른 시작: [루트 README.md](../README.md)

## 설계 (Design)

| 영역 | 문서 | 다이어그램 |
| --- | --- | --- |
| ERD | [erd/erd-design.md](./erd/erd-design.md) | [erd/erd.png](./erd/erd.png) |
| Data · Retention / Audit | [erd/data-retention-and-audit-policy.md](./erd/data-retention-and-audit-policy.md) | — |
| Data · Lifecycle | [erd/data-lifecycle.md](./erd/data-lifecycle.md) | — |
| Sequence | [sequence/payment-flow-reason.md](./sequence/payment-flow-reason.md) | [sequence/payment-order-flow.png](./sequence/payment-order-flow.png) |
| ADR | [ADR-001](./adr/ADR-001-multi-module-monolith.md) · [ADR-002](./adr/ADR-002-per-layer-gradle-modules.md) | — |
| Architecture | [architecture/architecture.md](./architecture/architecture.md) | — |
| API (MVP) | [api/mvp-api-spec.md](./api/mvp-api-spec.md) | — |
| API · Contract | [api/api-contract.md](./api/api-contract.md) | — |
| State · Invariants | [state/invariants-and-state-machines.md](./state/invariants-and-state-machines.md) | [state/order-state-machine.png](./state/order-state-machine.png) |
| Operations · Failure | [operations/failure-policy.md](./operations/failure-policy.md) | — |
| Operations · Incident | [operations/incident-response.md](./operations/incident-response.md) | — |
| Operations · Metrics | [operations/observability-metrics.md](./operations/observability-metrics.md) | — |

> community 피드 API 명세는 순차 추가 예정.

## AI 코딩 (Claude Code · Cursor)

| 문서 | 설명 |
| --- | --- |
| [**AI 가이드 (시작)**](./ai/README.md) | SHARED + 담당 persona |
| [공통 지침](./ai/SHARED.md) | 페르소나 김최고 · 필수 docs · 금지 사항 |
| [루트 CLAUDE.md](../CLAUDE.md) | Claude Code 자동 로드 |

담당별: `docs/ai/personas/{pyojimin,junghwancheol,hyungseongbin,jangseongjae,jiyoungjae}.md`

## 협업 (Contributing)

| 문서 | 설명 |
| --- | --- |
| [**Git & 코드 협업 컨벤션**](./contributing/git-collaboration-convention.md) | 브랜치 전략, 네이밍, 커밋, PR, 코드 리뷰 |
| [이슈 템플릿 (GitHub)](../.github/ISSUE_TEMPLATE/task.md) | New issue 시 자동 적용 |
| [PR 템플릿 (GitHub)](../.github/pull_request_template.md) | New pull request 시 자동 적용 |

### 빠른 링크

- 이슈 만들기: 저장소 **Issues → New issue → 「작업 이슈」**
- PR 만들기: `develop` 대상으로 PR 생성 시 템플릿 자동 삽입
- 브랜치 예: `feat/23`, `fix/41`, `refactor/15`
