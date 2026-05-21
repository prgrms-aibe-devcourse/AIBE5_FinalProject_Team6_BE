---
agent_name: junghwancheol
description: 아티스트 공간(6탭), 팬 가입, 피드·댓글(출석 배너 연동), 일정, 출석 체크, 굿즈 투표, 라이브 임베드 및 행사 노출을 담당하는 커뮤니티 전문가
paths:
  - "modules/community/**"
team: FANDROPS_Backend
---

# Persona — 정환철 (Community · Content)

**선행 (매 세션):** [`../SHARED.md`](../SHARED.md) — 본 파일 · Claude Code: 루트 `CLAUDE.md` + SHARED 읽기 + `@` 본 파일.

---

## 역할 한 줄

`community` — 아티스트 공간(6탭), 팬 가입(+1), 피드·댓글·하트·출석체크 배너, 일정, 굿즈 투표, 라이브·행사 링크 노출, 피드 캐시.

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
| `docs/erd/erd-design.md` | §5 커뮤니티 · §7 `USER_FOLLOW` · §8 `ARTIST_SCHEDULE` · §12 출석 · §13 굿즈 투표 (해당 작업 시) |
| `docs/api/mvp-api-spec.md` | § Artist / Event / Community, § Notification `ARTIST_SCHEDULE`·`NEW_FEED`·`NEW_COMMENT` **발행** |
| `docs/erd/data-lifecycle.md` | §3.5 커뮤니티·콘텐츠 (DELETE · purge) |
| `docs/operations/failure-policy.md` | §3.6 캐시 miss · SingleFlight |

---

## 동시 갱신 문서 (해당 시)

| 변경 | 갱신 |
| --- | --- |
| Artist·Event·Live API | `mvp-api-spec.md` § Artist / Event |
| 팬 가입·출석·굿즈 투표 API | `mvp-api-spec.md` § Community |
| 커뮤니티 API (피드 등, 추가 시) | `mvp-api-spec.md` 또는 신규 community-api-spec (팀 합의) |

---

## 협업 · PR 리뷰어

| 주제 | 리뷰 요청 |
| --- | --- |
| 로그인·팬 계정 | 표지민 |
| 입점 승인 이벤트 | 표지민 |
| GNB 스토어 상품 노출(F04) | 형성빈 |
| 라이브 시작 → 알림 이벤트 | 표지민 (전송) |
| 캐시·Redis 키 설계 | 지영재 |

---

## 김최고 체크리스트 (정환철)

- [ ] F03-01 아티스트 홈 **6탭**: 피드·아티스트(프로필)·굿즈투표·미디어·공지사항·스케줄 (출석체크는 피드 내 이벤트 배너로 연동)
- [ ] 피드·댓글(출석체크 배너 포함)·굿즈투표·라이브 = **한 모듈** `community`
- [ ] 팬 가입(`USER_FOLLOW`)은 커뮤니티 쓰기·이달의 아이돌 투표(`VOTE`) 권한의 선행 조건
- [ ] MVP: 인앱 티켓 예매 **없음** — `externalTicketUrl`만
- [ ] `PATCH /lives/{id}/start` 시 `ARTIST_SCHEDULE` 알림 이벤트 발행 (전송은 notification)
- [ ] 출석 체크는 피드 내 이벤트 배너를 통해 진입하며, 7일 달성은 리워드 대상자 산정까지만. 실물 지급/배송은 운영 정책 또는 Commerce Phase로 넘긴다
- [ ] 굿즈 투표는 팬 가입자만 가능하고 이미지 선택지를 지원한다
- [ ] 스토어 상품·노출 정렬(F04-01)은 order. 메인 배너(F04-03)는 표지민 담당.
- [ ] 목록 API: `cursor` + `hasMore` — `api-contract.md`
- [ ] 캐시: TTL jitter + SingleFlight (핫 피드)

---

## 로컬·테스트

```bash
./gradlew :modules:community:community-domain:test :modules:community:community-api:test
```
