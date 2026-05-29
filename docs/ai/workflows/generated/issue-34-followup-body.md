## 📝 작업 내용

이슈 #34(PR #40)에서 서버 다운 복구 목적으로 **스텁(Stub)만 올린 상태**. 모든 메서드가 `throw new UnsupportedOperationException("미구현")` 또는 빈 반환이므로 실제 동작하는 구현체로 교체한다.

- `FanJpaEntity` — JPA `@Entity` (FAN 테이블 매핑)
- `AgencyApplicationJpaEntity` — JPA `@Entity` (AGENCY_APPLICATION 테이블 매핑)
- `FanJpaRepository` / `AgencyApplicationJpaRepository` — Spring Data JPA 인터페이스
- `UserRepositoryImpl` → JPA 기반 실 구현 (`(auth_provider, provider_id)` UNIQUE 제약 + 경쟁 조건 처리)
- `AgencyApplicationRepositoryImpl` → JPA 기반 실 구현
- `RefreshTokenStoreImpl` → Redis 기반 실 구현 (TTL: 7일)
- `PasswordResetTokenStoreImpl` → Redis 기반 실 구현 (TTL: 30분, 1회용)
- `JwtProviderImpl` → jjwt 기반 실 구현 (서명·파싱·만료 검증 포함)
- `OAuthClientImpl` → 카카오/구글 Authorization Code → access token 교환 실 구현
- `EmailNotificationPortImpl` → 이메일 전송 실 구현 (비밀번호 재설정 메일)
- Flyway 마이그레이션: `FAN`, `AGENCY_APPLICATION` 테이블 DDL + `(auth_provider, provider_id)` UNIQUE 제약

## Definition of Done (DoD)

- [ ] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가?
- [ ] 동시성 테스트를 통과했는가? → 해당 없음
- [ ] SSOT 문서를 동시 갱신했는가? → 해당 없음
- [ ] RestDocs API 문서를 업데이트했는가? → 해당 없음
- [ ] 로컬 환경에서 엔드투엔드 시나리오를 직접 확인했는가?
- [ ] 소스코드 내 민감 정보(API Key, 패스워드 등)가 없는가?
- [ ] `./gradlew :modules:user:user-infrastructure:compileJava` 통과했는가?
- [ ] `(auth_provider, provider_id)` UNIQUE 제약이 DB 마이그레이션에 포함됐는가?
- [ ] local 프로필에서 Redis 없이 기동 가능한가?

## 📅 마감 기한

- 2026-05-30

## Related

- #34 (user-infrastructure 스텁 구현 — 이 이슈의 전신)
- #18 (user domain + application layer)
- `docs/erd/erd-design.md` §4 — FAN · AGENCY_APPLICATION 테이블 설계
- `docs/adr/ADR-002-per-layer-gradle-modules.md` — infrastructure 레이어 규칙
