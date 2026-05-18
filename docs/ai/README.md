# AI 코딩 어시스턴트 가이드 (Claude Code · Cursor)

팀원·AI가 **동일한 문서만** 참조해 토큰을 아끼고, 설계와 충돌하지 않게 한다.

## 사용법

| 도구 | 주입할 것 | 하지 말 것 |
| --- | --- | --- |
| **Claude Code** | [`CLAUDE.md`](../../CLAUDE.md) 자동 + **`@docs/ai/SHARED.md`** + persona | `docs/` 통째로 · SHARED 생략 |
| **Cursor** | [`AGENTS.md`](../../AGENTS.md) 또는 `@docs/ai/SHARED.md` + persona | `CLAUDE.md`만 (라우터만 있음) |
| **기타** | SHARED + persona 경로를 첫 메시지에 붙여넣기 | ADR 전부·PNG |

### 첫 메시지 예시 (형성빈)

```
@docs/ai/SHARED.md
@docs/ai/personas/hyungseongbin.md

feat/12 — POST /orders accessTicket 검증 + reserve 단일 TX.
docs/state/invariants-and-state-machines.md §2, mvp-api-spec POST /orders만 참고.
```

### 역할 분리

- **SHARED** = 김최고 + 전원 공통 금지·SSOT 목록 (한 번만)
- **persona** = 내 모듈 경계 + 내가 추가로 읽을 docs + 내 체크리스트
- **설계 docs** = SHARED/persona가 가리킨 파일만 (작업별)

## 파일 구조

```
docs/ai/
├── README.md          ← 이 파일
├── SHARED.md          ← 페르소나 + 공통 규칙 + 문서 인덱스 (모두 필수)
└── personas/
    ├── pyojimin.md      표지민 — user, notification
    ├── junghwancheol.md 정환철 — community
    ├── hyungseongbin.md 형성빈 — order, inventory
    ├── jangseongjae.md  장성재 — payment
    └── jiyoungjae.md    지영재 — platform, api-server
```

## 문서 인덱스 (전체)

[`docs/README.md`](../README.md) — 설계·운영 문서 목록.

## PR 전 AI 자가 점검

- [ ] SHARED **금지 사항** 위반 없음
- [ ] persona **수정 가능 모듈** 밖 코드 없음
- [ ] API/상태/ERD 변경 시 persona **동시 갱신 문서** 반영
- [ ] [`contributing/git-collaboration-convention.md`](../contributing/git-collaboration-convention.md) 브랜치·커밋·PR
