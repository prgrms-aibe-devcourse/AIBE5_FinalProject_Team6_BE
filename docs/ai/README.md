# AI 코딩 어시스턴트 가이드 (Claude Code · Cursor)

팀원·AI가 **동일한 문서만** 참조해 토큰을 아끼고, 설계와 충돌하지 않게 한다.

## 사용법

| 도구 | 설정 |
| --- | --- |
| **Claude Code** | 루트 [`CLAUDE.md`](../../CLAUDE.md) 자동 로드 → 본인 persona 추가 |
| **Cursor** | 채팅에 `@docs/ai/SHARED.md` + `@docs/ai/personas/<본인>.md` |
| **기타** | 세션 시작 프롬프트에 SHARED + persona 경로 붙여넣기 |

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
