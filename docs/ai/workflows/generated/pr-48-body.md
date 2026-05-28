## 📝 작업 내용

- [x] `SHARED.md` — 코드 작성 전 사고 절차에 5. Retro(회고) 단계 추가 및 사전·사후 단계 구분 명시
- [x] `auto-pr.md` — PR 워크플로우에 9. 하네스 회고 및 보고 단계 추가 (4개 필드 + retro 파일 저장)
- [x] `CLAUDE.md` — `queue/ratelimit`(장성재+지영재), `security/auth`(표지민+지영재) 이중 페르소나 라우팅 행 추가
- [x] `ADR-004` — 4단계 → 5단계 수정, ADR-005 forward reference 추가
- [x] `ADR-005` — AI 하네스 1차 튜닝 기록 신규 작성 (P0·P1·P2 우선순위·페르소나 5종 점검 포함)
- [x] `jangseongjae.md` — 드롭스 오픈런 엣지 케이스 체크리스트 3종 추가 (SSE SLO, Access Ticket TTL 검증, 결제 재시도 Job 구분)
- [x] `jiyoungjae.md` — YAML `paths` 필드 `.github/workflows/**` → `.github/**` 수정

## 🧪 기술적 의사결정 및 검증

- **선택한 기술 및 배경:** 단순 프롬프트 추가 대신 Brainstorm→Plan→Execute→Debug→**Retro** 5단계 루프를 통해 하네스가 세션 간 자기 진화하도록 설계. 회고 결과를 `generated/retro-<PR번호>.md`에 저장하여 피드백 루프를 닫음.

- **데이터 기반 검증 결과:** `invariants-and-state-machines.md` §4·§6 교차 검증으로 할루시네이션 2건(Access Ticket TTL 섹션 번호 오류, Outbox 오너십 혼동) 발견 및 수정. ERD·API 명세·상태 머신·장애 정책 4개 문서 정합성 이상 없음 확인.

- **트러블슈팅:** 마크다운 linter가 테이블 셀 내 `**` glob 패턴을 bold marker로 오인하는 quirk 발견 — 볼드 마커 제거로 해결.

## 📌 주요 변경사항

- 자동 회고(Self-Reflection) 파이프라인 2곳(SHARED 로컬 코딩 / auto-pr.md PR 워크플로우) 내재화
- CLAUDE.md 라우팅 공백 2종 해소 — 대기열·RateLimit 및 Security·Auth 필터 경로의 이중 페르소나 로드
- ADR-005 신규 작성으로 하네스 변경 이력 체계화 (P0·P1·P2 우선순위 + 페르소나 5종 일관성 점검 기록)

## 🔗 연관 이슈

- Closes #48

## ✅ 셀프 체크리스트

- [x] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가? — 해당 없음 (AI 하네스 문서 변경)
- [x] N+1 문제나 비효율적인 쿼리 실행 계획이 없는가? — 해당 없음
- [x] RestDocs 등 API 문서를 업데이트했는가? — 해당 없음 (API 변경 없음)
- [x] SSOT 문서(api-spec, invariants, erd-design 등)를 동시 갱신했는가? — ADR-004·ADR-005·페르소나 5종 동기화 완료
- [x] 로컬 테스트 환경(Redis/MySQL) 정상 작동을 확인했는가? — 해당 없음
- [x] 소스코드 내 민감한 정보(API Key, 패스워드 등)가 제외되었는가? — 확인 완료