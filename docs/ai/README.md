# AI 코딩 어시스턴트 가이드 (Claude Code)

팀원·AI가 **동일한 SSOT**만 참조해 토큰을 아끼고, 설계와 충돌하지 않게 한다.

## 첫 메시지에 무엇을 넣는가

| 도구              | 자동 | 첫 메시지에 **반드시 `@`** | 사용법                               |
|-----------------| --- | --- |-----------------------------------|
| **Claude Code** | 루트 [`CLAUDE.md`](../../CLAUDE.md) | **본인** `personas/<본인>.md` **1개** | **매 세션 필수:** `@docs/ai/SHARED.md` |

### persona 파일 (본인 것만)

| 담당 | `@` 경로 |
| --- | --- |
| 표지민 | `docs/ai/personas/pyojimin.md` |
| 정환철 | `docs/ai/personas/junghwancheol.md` |
| 형성빈 | `docs/ai/personas/hyungseongbin.md` |
| 장성재 | `docs/ai/personas/jangseongjae.md` |
| 지영재 | `docs/ai/personas/jiyoungjae.md` |

---

## 작업 순서 (Git — 프롬프트와 별개)

1. GitHub **이슈** 생성 (Feature / Bug) → 번호 확정 (예: `#23`)
2. (코드 작업 시) `develop`에서 브랜치 생성 — **이슈 만든 뒤에만**  
   - Feature → `feat/<번호>` · Bug → `fix/<번호>` (예: `feat/23` — 브랜치명에 `#` 없음)
3. AI 세션 시작 → 아래 **도구별** 첫 메시지

프롬프트에는 **이슈 `#번호`는 써도 되고**, **`feat/23`은 브랜치를 실제로 만든 경우에만** 적는다.

---

## 예시

```
**[적용 예시]**<br>상황에 따라 아래 두 가지 방식 중 하나를 선택하여 컨텍스트를 명시적으로 주입:<br><br>**1. 일반적인 작업 시작 시:**<br>`> @docs/ai/SHARED.md @docs/ai/personas/jangseongjae.md 결제 도메인 작업 시작할게.`<br><br>**2. github 워크플로우 기반 작업 시:**<br>`> @docs/ai/SHARED.md @docs/ai/personas/hyungseongbin.md @docs/ai/workflows/auto-pr.md 파일의 워크플로우에 따라 '장바구니 담기 API 및 RDB 저장 로직' 구현 시작해 줘.`
```

---

## 역할 분리

- **SHARED** = 김최고 + 공통 금지 + SSOT 목차
- **persona** = 내 모듈 경계 + 추가 docs + 체크리스트
- **설계 md** = 프롬프트에 적은 섹션만

## 파일 구조

```
docs/ai/
├── README.md
├── SHARED.md
└── personas/
    ├── pyojimin.md
    ├── junghwancheol.md
    ├── hyungseongbin.md
    ├── jangseongjae.md
    └── jiyoungjae.md
```

전체 설계 목차: [`docs/README.md`](../README.md)

## PR 전 자가 점검

- [ ] SHARED 금지 사항 위반 없음
- [ ] persona **수정 가능 모듈** 밖 코드 없음
- [ ] API/상태/ERD 변경 시 persona **동시 갱신** 문서 반영
- [ ] [`git-collaboration-convention.md`](../contributing/git-collaboration-convention.md) (이슈 → 브랜치 → PR `develop`)
