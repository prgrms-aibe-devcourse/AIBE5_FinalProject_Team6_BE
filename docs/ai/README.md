# AI 코딩 어시스턴트 가이드 (Claude Code · Cursor)

팀원·AI가 **동일한 SSOT**만 참조해 토큰을 아끼고, 설계와 충돌하지 않게 한다.

## 도구별 — 첫 메시지에 무엇을 넣는가

| 도구 | 자동 | 첫 메시지에 **반드시 `@`** | SHARED 처리 |
| --- | --- | --- | --- |
| **Claude Code** | 루트 [`CLAUDE.md`](../../CLAUDE.md) | **본인** `personas/<본인>.md` **1개** | 자동 ❌ — **매 세션 필수:** `@docs/ai/SHARED.md` 또는 「SHARED 읽고 시작」 |
| **Cursor** | [`AGENTS.md`](../../AGENTS.md) (경로 안내만) | **`SHARED.md`** + **본인 persona** | `@docs/ai/SHARED.md` 필수 (`AGENTS.md` 본문·금지·SSOT 미포함) |

> **Claude Code와 Cursor는 첫 메시지 템플릿이 다릅니다.** 아래 § 기본 패턴 참고.

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

## 기본 패턴 — Claude Code

```
@docs/ai/personas/<본인>.md

이슈 #<번호>: <한 줄 목표>
docs/ai/SHARED.md 먼저 읽고, <읽을 설계 doc·섹션>만 참고해서 구현해줘.
```

- **`@docs/ai/SHARED.md`는 매 세션 필수** — `@` 하거나, 본문에 **「SHARED.md 읽고 시작」**을 반드시 넣는다.
- `CLAUDE.md`는 이미 로드됨 — 다시 `@` 할 필요 없음.

---

## 기본 패턴 — Cursor

```
@docs/ai/SHARED.md
@docs/ai/personas/<본인>.md

이슈 #<번호>: <한 줄 목표>
<읽을 설계 doc·섹션>만 참고해서 구현해줘.
```

- Cursor는 **`@SHARED` + `@persona` 둘 다** 필수.

---

## 본문에 넣는 3줄 (도구 공통 · 복붙용)

`@` 블록 **아래**에 이 형식만 맞추면 됨 (담당·이슈마다 내용만 바꿈):

```
이슈 #<번호>: <모듈/엔드포인트> — <한 줄 목표>
<path/to/doc.md §섹션 또는 "POST /foo만"> 만 참고.
(브랜치 feat/<번호> 에서 작업 중)   ← 브랜치 만든 경우에만 이 줄
```

---

## 담당별 예시 (Claude / Cursor 구분)

### 형성빈 — Claude Code

```
@docs/ai/personas/hyungseongbin.md

이슈 #23: POST /orders — accessTicket 검증 + 재고 예약 단일 TX.
SHARED.md 읽고, invariants-and-state-machines.md §2, mvp-api-spec POST /orders만 참고.
```

### 형성빈 — Cursor

```
@docs/ai/SHARED.md
@docs/ai/personas/hyungseongbin.md

이슈 #23: POST /orders — accessTicket 검증 + 재고 예약 단일 TX.
invariants-and-state-machines.md §2, mvp-api-spec POST /orders만 참고.
```

### 장성재 — Claude Code

```
@docs/ai/personas/jangseongjae.md

이슈 #15: 토스 웹훅 tossPaymentKey 멱등, RESERVED→PAID.
SHARED.md 읽고, payment-flow-reason.md·mvp-api-spec 결제 식별자만. order/inventory 구현 수정 금지.
```

### 표지민 — Cursor

```
@docs/ai/SHARED.md
@docs/ai/personas/pyojimin.md

이슈 #8: 대기열 join + PROCESSING 시 Access Ticket 발급.
invariants §6·§4.2(Q-1~Q-3), mvp-api-spec § Wait Queue만. 검증은 order(형성빈), user는 발급만.
```

(정환철·지영재도 동일 규칙: Claude = persona + SHARED 읽으라고 명시 / Cursor = SHARED + persona)

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
