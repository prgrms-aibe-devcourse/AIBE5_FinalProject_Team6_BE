# ADR-016: ArtistProfilePort — 크로스 도메인 참조 격리

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-30 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) · [ADR-002 레이어 Gradle 모듈](./ADR-002-per-layer-gradle-modules.md) |
| **관련** | [architecture.md § 도메인 오너십](../architecture/architecture.md) |
| **담당** | 정환철 (`community`) |
| **관련 PR** | [#171](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/pull/171) · [#173](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/pull/173) |

---

## Title

`community` 모듈이 아티스트 프로필(이름·이미지) 데이터를 조회할 때 `user` 모듈 인프라를 직접 참조하지 않고, `ArtistProfilePort` 인터페이스를 통해서만 접근한다. 로컬 환경에서는 `@Profile("local")` 스텁 어댑터로 대체한다.

---

## Context

피드·댓글 등 community 콘텐츠를 API로 응답할 때 아티스트 이름·프로필 이미지가 필요하다. 이 데이터는 `user` 모듈의 `artist_member` 테이블에 존재한다.

ADR-002는 도메인 간 `*-infrastructure` 직접 참조를 금지하고, **포트(interface)** 를 통해서만 교신하도록 규정한다. 즉, `community-infrastructure`에서 `user-infrastructure`의 JPA Repository를 직접 주입해 쓰는 것은 아키텍처 하드 룰 위반이다.

추가 제약:
- 로컬 개발 환경은 `user` 모듈 DB 시드 없이도 `community` 기능을 단독으로 실행해야 한다.
- CI 환경에서 `community-domain:test`는 `user` 모듈 의존 없이 통과해야 한다.

---

## 방안 비교

### A안 — community-infrastructure에서 user-infrastructure 직접 참조

`CommunityConfig`에 `UserArtistMemberJpaRepository`를 직접 `@Autowired`해 프로필을 조회한다.

| 관점 | 평가 |
| --- | --- |
| 구현 속도 | 빠름 — 추가 인터페이스 없음 |
| 아키텍처 | **ADR-002 위반** — 도메인 간 infra 직접 참조 |
| 테스트 격리 | community 테스트가 user JPA 빈을 필요로 해 독립 실행 불가 |
| **기각 이유** | 아키텍처 하드 룰 위반. user 스키마 변경 시 community 빌드가 깨지는 강결합을 유발한다. |

### B안 — Internal HTTP 호출 (`/internal/artists/{id}`)

`community-infrastructure`에서 `user-api`의 `/internal/artists/{artistId}/profile` 엔드포인트를 RestClient로 호출한다.

| 관점 | 평가 |
| --- | --- |
| 아키텍처 | ADR-001 준수 (같은 JVM, Internal HTTP 허용) |
| 성능 | 피드 목록 조회 시 아티스트별 HTTP 왕복 발생 — N+1 가능 |
| 로컬 환경 | `user-api` 포트가 떠 있어야 함 — 개발 편의 저하 |
| **기각 이유** | 피드 목록 응답에 아티스트 정보가 항상 포함되는 구조에서 HTTP 왕복은 P95에 직접 영향을 준다. 로컬 환경 부담도 크다. |

### C안 — ArtistProfilePort 인터페이스 + @Profile 분리 ← **채택**

`community-application`에 `ArtistProfilePort` 인터페이스를 선언하고, 프로덕션용 어댑터와 로컬용 스텁 어댑터를 분리한다.

```
community-application/port/ArtistProfilePort.java        ← 인터페이스
community-infrastructure/.../ArtistProfileAdapter.java   ← prod (@Component)
community-infrastructure/.../ArtistProfileStubAdapter.java ← local (@Profile("local"))
```

| 관점 | 평가 |
| --- | --- |
| 아키텍처 | ADR-002 준수 — 포트를 통한 단방향 의존 |
| 로컬 환경 | 스텁이 고정 더미 데이터 반환 → user 모듈 없이 community 단독 실행 가능 |
| 테스트 격리 | 테스트에서 `@Mock ArtistProfilePort` — user JPA 의존 없음 |
| 단점 | 인터페이스·어댑터 클래스 추가 |

---

## Decision

**C안(ArtistProfilePort 인터페이스 + @Profile 분리) 채택.**

ADR-002의 도메인 간 포트 원칙을 지키면서, `@Profile("local")` 스텁으로 로컬·테스트 환경의 `user` 모듈 의존성을 완전히 제거한다.

---

## 핵심 구현 결정 2가지

### ① Bean 충돌 해소 — @Profile("local") 스텁 격리

초기 구현(PR #171)에서 `ArtistProfileAdapter`(prod)와 `ArtistProfileStubAdapter`(local) 두 빈이 프로덕션 컨텍스트에 동시에 등록되어 `NoUniqueBeanDefinitionException`이 발생했다.

PR #173에서 스텁 어댑터에 `@Profile("local")`을 추가해 해소했다.

```java
// ArtistProfileStubAdapter.java
@Component
@Profile("local")
public class ArtistProfileStubAdapter implements ArtistProfilePort {
    @Override
    public ArtistProfileResult findByArtistMemberId(Long artistMemberId) {
        return new ArtistProfileResult(artistMemberId, "테스트 아티스트", null);
    }
}
```

프로덕션(`prod`, `default` 프로파일)에서는 `ArtistProfileAdapter`만 활성화되어 충돌이 없다.

### ② 포트 위치 — community-application/port

`ArtistProfilePort`는 `community-application` 레이어에 선언한다. `domain` 레이어에 두면 Spring 어노테이션이 없는 순수 도메인 객체 제약과 충돌하고, `infrastructure` 레이어에 두면 서비스가 인프라에 의존하는 역방향이 된다. `application` 레이어가 포트의 적절한 위치다.

---

## Consequences

### 긍정

- ADR-002 도메인 간 포트 원칙 준수 — `community-infrastructure`가 `user-*` 모듈을 직접 참조하지 않음
- 로컬 환경: `user` 모듈 DB 시드 없이 `community` 단독 실행 가능
- 테스트: `@Mock ArtistProfilePort`로 `user` JPA 의존 없이 단위 테스트 독립 실행

### 부정 · 수용

- `ArtistProfileAdapter`(prod) 구현은 같은 JVM의 `user-application` 서비스를 Spring 빈으로 직접 호출 — MVP 단계에서 Internal HTTP 대신 동일 JVM 내 직접 호출로 네트워크 왕복 없음
- 스텁 반환값은 하드코딩 더미 데이터 → 로컬에서 실제 아티스트명 미노출 (허용 범위)

### 불변

| 규칙 | 내용 |
| --- | --- |
| 레이어 방향 | `community-infrastructure`는 `user-infrastructure` 직접 참조 금지 — `ArtistProfilePort`만 |
| 프로파일 | 스텁 어댑터에 `@Profile("local")` 필수 — prod 컨텍스트 활성화 금지 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 레이어 의존 규칙 | [ADR-002](./ADR-002-per-layer-gradle-modules.md) |
| 도메인 오너십 | [architecture.md](../architecture/architecture.md) |