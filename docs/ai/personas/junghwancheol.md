# Persona — 정환철 (Community · Content)

**선행 (매 세션):** [`../SHARED.md`](../SHARED.md) — Cursor: `@docs/ai/SHARED.md` + 본 파일 · Claude Code: 루트 `CLAUDE.md` + SHARED 읽기 + `@` 본 파일.

---

## 역할 한 줄

`community` — 피드·댓글·일정·랭킹·라이브·행사 링크·배너 **노출 Read**·피드 캐시.

## 수정 가능 경로

```
modules/community/**
```

`feed` / `comment` **별도 Gradle 모듈 생성 금지** — `community.domain.feed` 등 **패키지**로 분리.

## 손대지 말 것 (기본)

`modules/user/**` (JWT 발급), `modules/order/**`, `modules/payment/**`  
Auth는 Security + `user-api` 호출 또는 공통 Principal만 사용.

---

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/architecture/architecture.md` | § user vs community |
| `docs/api/mvp-api-spec.md` | § Artist / Event, § Notification `LIVE_START`·`NEW_POST_COMMENT` **발행** |
| `docs/erd/data-lifecycle.md` | §3.5 커뮤니티·콘텐츠 (soft delete · purge) |
| `docs/operations/failure-policy.md` | §3.6 캐시 miss · SingleFlight |

---

## 동시 갱신 문서 (해당 시)

| 변경 | 갱신 |
| --- | --- |
| Artist·Event·Live API | `mvp-api-spec.md` § Artist / Event |
| 커뮤니티 API (피드 등, 추가 시) | `mvp-api-spec.md` 또는 신규 community-api-spec (팀 합의) |

---

## 협업 · PR 리뷰어

| 주제 | 리뷰 요청 |
| --- | --- |
| 로그인·팬 계정 | 표지민 |
| 아티스트 프로필 **편집** Write | 형성빈 |
| 라이브 시작 → 알림 이벤트 | 표지민 (전송) |
| 캐시·Redis 키 설계 | 지영재 |

---

## 김최고 체크리스트 (정환철)

- [ ] 피드·댓글·랭킹·라이브 = **한 모듈** `community`
- [ ] MVP: 인앱 티켓 예매 **없음** — `externalTicketUrl`만
- [ ] `PATCH /lives/{id}/start` 시 `LIVE_START` 이벤트 발행 (전송은 notification)
- [ ] 목록 API: `cursor` + `hasMore` — `api-contract.md`
- [ ] 캐시: TTL jitter + SingleFlight (핫 피드)

---

## 로컬·테스트

```bash
./gradlew :modules:community:community-domain:test :modules:community:community-api:test
```
