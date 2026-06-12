## 📝 작업 내용

- [x] `FanJpaEntity` — JPA `@Entity` (FAN 테이블 매핑, `(auth_provider, provider_id)` UNIQUE 제약)
- [x] `AgencyApplicationJpaEntity` — JPA `@Entity` (AGENCY_APPLICATION 테이블 매핑, `business_registration_number` nullable)
- [x] `FanJpaRepository` / `AgencyApplicationJpaRepository` — Spring Data JPA 인터페이스
- [x] `UserRepositoryImpl` — JPA 실 구현 (`DataIntegrityViolationException` 경쟁 조건 처리 포함)
- [x] `AgencyApplicationRepositoryImpl` — JPA 실 구현
- [x] `JwtProviderImpl` — jjwt 0.12.6 HS256 실 구현 (Access 30분 / Refresh 7일)
- [x] `OAuthClientImpl` — Google Authorization Code Flow 실 구현 (`RestClient` 사용)
- [x] `EmailNotificationPortImpl` — JavaMailSender 비밀번호 재설정 메일 발송
- [x] `OAuthProperties` — `@ConfigurationProperties`로 OAuth 설정 중앙화
- [x] `application.yml` — JWT · OAuth · Mail 설정 키 추가 (환경 변수 주입)
- [x] `application-local.yml` — `application-local.secrets.yml` import 추가
- [x] `application-local.example.yml` — 로컬 환경 변수 예시 전면 업데이트

## 🧪 기술적 의사결정 및 검증

- **JWT HS256 선택:** 모놀리스 MVP 구조라 공개키 분배가 불필요. RS256 대비 설정 단순, 단일 Secret Key로 서명·검증 충분.
- **`@ConfigurationProperties` 선택:** OAuth 설정(kakao/google)이 nested 구조라 `@Value` 분산보다 타입 안전한 중앙화가 유지보수에 유리.
- **데이터 기반 검증 결과:** 해당 없음 (동시성·성능 변경 없음)
- **트러블슈팅:** `JwtProvider` 포트 시그니처(`generateAccessToken(Long, UserRole)`)가 스텁 구현체와 불일치한 상태였음. 포트를 UserRole 포함으로 맞추고 `AuthService` 동시 수정.

## 📌 주요 변경사항

- 추가: `FanJpaEntity.java`, `AgencyApplicationJpaEntity.java`, `FanJpaRepository.java`, `AgencyApplicationJpaRepository.java`
- 추가: `OAuthProperties.java`
- 수정: `UserRepositoryImpl.java`, `AgencyApplicationRepositoryImpl.java` (스텁 → JPA 실 구현)
- 수정: `JwtProviderImpl.java`, `OAuthClientImpl.java`, `EmailNotificationPortImpl.java` (스텁 → 실 구현)
- 수정: `build.gradle.kts` — `spring-boot-starter-mail`, `jjwt-api:0.12.6` 의존성 추가
- 수정: `application.yml`, `application-local.yml`, `application-local.example.yml`

## ⚠️ 의도적 미완료 항목 (별도 이슈 예정)

| 항목 | 현재 상태 | 이유 |
| --- | --- | --- |
| `RefreshTokenStoreImpl` | 빈 메서드 스텁 유지 | Redis local/운영 전략 결정 대기 |
| `PasswordResetTokenStoreImpl` | 빈 메서드 스텁 유지 | 동일 |
| Kakao OAuth | `UnsupportedOperationException` | client_id 발급 후 별도 PR |

→ 로그아웃 · 토큰 갱신 · 비밀번호 재설정은 현재 동작하지 않습니다.

## 🔗 연관 이슈

- Closes #57
- Related to #34 (전신 이슈 — 스텁 구현)

## ✅ 셀프 체크리스트

- [x] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가? (AuthServiceTest 전체 통과)
- [x] N+1 문제나 비효율적인 쿼리 실행 계획이 없는가? (단순 findById/findByEmail 조회만 존재)
- [ ] RestDocs 등 API 문서를 업데이트했는가? — 해당 없음 (API Controller 미작성)
- [ ] SSOT 문서를 동시 갱신했는가? — 해당 없음 (스키마 변경 없음)
- [x] 로컬 테스트 환경(Redis/MySQL) 정상 작동을 확인했는가? (H2 + compileJava 통과)
- [x] 소스코드 내 민감한 정보(API Key, 패스워드 등)가 제외되었는가? (secrets.yml gitignore 확인)
