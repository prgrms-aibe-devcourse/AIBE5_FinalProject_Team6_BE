# FANDROPS — Claude Code

**진입 문서:** [`docs/ai/README.md`](docs/ai/README.md)

1. 공통: [`docs/ai/SHARED.md`](docs/ai/SHARED.md) — **매 세션 필수** (`@docs/ai/SHARED.md` 또는 프롬프트에 「SHARED 읽고 시작」). `CLAUDE.md`만으로 SHARED가 주입되지 않음.
2. 본인 담당: [`docs/ai/personas/`](docs/ai/personas/) 아래 **본인 파일 1개**만 추가 (`@docs/ai/personas/<본인>.md`).

| 담당 | 파일 |
| --- | --- |
| 표지민 | `docs/ai/personas/pyojimin.md` |
| 정환철 | `docs/ai/personas/junghwancheol.md` |
| 형성빈 | `docs/ai/personas/hyungseongbin.md` |
| 장성재 | `docs/ai/personas/jangseongjae.md` |
| 지영재 | `docs/ai/personas/jiyoungjae.md` |

작업 전 **전체 docs를 읽지 말 것.** SHARED + persona의 **필수 참조 목록**만 `@` 로 로드한다.

---

## 도메인 라우팅 (작업 경로 → Persona)

작업 경로가 아래에 해당하면 **해당 persona 파일을 함께 로드**한다 (`@` 멘션). SHARED만으로는 도메인별 체크리스트·금지 경로가 누락된다.

| 작업 경로 | 담당 | Persona 로드 |
| --- | --- | --- |
| `modules/payment/**` | 장성재 | `@docs/ai/personas/jangseongjae.md` |
| `modules/order/**` · `modules/inventory/**` | 형성빈 | `@docs/ai/personas/hyungseongbin.md` |
| `modules/user/**` · `modules/notification/**` | 표지민 | `@docs/ai/personas/pyojimin.md` |
| `modules/community/**` | 정환철 | `@docs/ai/personas/junghwancheol.md` |
| `apps/api-server/**` · `.github/**` · `compose.yaml` | 지영재 | `@docs/ai/personas/jiyoungjae.md` |

> **주의:** 이 표는 프롬프트 지시다. 자동 로드되지 않으므로 세션 시작 시 직접 `@` 로 로드해야 한다.
