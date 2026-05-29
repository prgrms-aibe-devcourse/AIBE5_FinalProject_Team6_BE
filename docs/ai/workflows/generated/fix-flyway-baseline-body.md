## 📝 작업 내용

- [x] `application-prod.yml`에 `flyway.baseline-on-migrate: true` 설정 추가
- [x] Flyway 마이그레이션 전략 문서 작성 (`docs/operations/flyway-migration-strategy.md`)

## 🧪 기술적 의사결정 및 검증

- **선택한 기술 및 배경:**
  PR #51(community 피드 도메인)에서 `V1__create_community_feed_tables.sql`이 처음 추가됐고, PR #63(order 도메인) 머지 후 CD 배포 시 prod RDS에 `flyway_schema_history` 테이블이 없는 상태에서 Flyway가 `FlywayException: Found non-empty schema(s) fandrops but no schema history table`을 던지며 앱 기동 거부.

  세 가지 방안 검토:
  1. **Flyway 비활성화** — 단순하나 스키마 이력 관리 포기
  2. **baseline-on-migrate + ddl-auto: update 유지 (채택)** — 즉시 복구, 팀 작업 방식 변경 없음. MVP 기간 중 엔티티 추가가 잦아 `ddl-auto: update`를 유지하는 것이 현실적
  3. **ddl-auto: validate + Flyway 전환** — 올바른 장기 방향이나, 팀원이 마이그레이션 파일 누락 시 즉시 prod 크래시 위험 → MVP 이후로 미룸

- **트러블슈팅:**
  CD 헬스체크 step 실패 → `journalctl -u fandrops`로 원인 확인 → Flyway BeanCreationException 특정

## 📌 주요 변경사항

- 수정: `apps/api-server/src/main/resources/application-prod.yml` — flyway baseline 설정 2줄 추가
- 추가: `docs/operations/flyway-migration-strategy.md` — 현재 Phase B 설명 + Phase C 전환 시점·절차 문서화

## 🔗 연관 이슈

- Related to #30

## ✅ 셀프 체크리스트

- [ ] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가?
- [x] N+1 문제나 비효율적인 쿼리 실행 계획이 없는가?
- [ ] RestDocs 등 API 문서를 업데이트했는가? (API 변경 시)
- [x] SSOT 문서(api-spec, invariants, erd-design 등)를 동시 갱신했는가?
- [x] 로컬 테스트 환경(Redis/MySQL) 정상 작동을 확인했는가?
- [x] 소스코드 내 민감한 정보(API Key, 패스워드 등)가 제거되었는가?