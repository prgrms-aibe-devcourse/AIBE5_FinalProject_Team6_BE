# ADR-002: 바운디드 컨텍스트·레이어마다 `build.gradle.kts`를 둔다

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-19 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) |

---

## Title

`user-api`, `user-domain` 등 **레이어 단위 Gradle 서브모듈**마다 `build.gradle.kts`를 두고, 루트 `settings.gradle.kts`에 전 경로를 등록한다.

---

## Context — 왜 이 질문이 나오는가

멀티모듈 레포를 처음 열면 다음 구조가 보인다.

```
modules/user/
├── user-domain/build.gradle.kts
├── user-application/build.gradle.kts
├── user-api/build.gradle.kts
└── user-infrastructure/build.gradle.kts
```

자연스러운 의문:

> “도메인 폴더 하나에 `build.gradle.kts` 하나면 되지 않나?”  
> “하위마다 Gradle 파일이 있는 게 과한 거 아닌가?”

ADR-001에서 **멀티모듈 모놀리스**와 **레이어 분리**를 채택했기 때문에, Gradle 모듈 경계를 **레이어와 1:1**로 맞추는 것이 정석이다.  
파일 수가 늘어나는 비용은 **컴파일 타임 경계 강제**라는 이득으로 정당화한다.

---

## Options — 검토한 대안

| # | 방식 | `build.gradle.kts` 수 | 판정 |
| --- | --- | --- | --- |
| ① | **레이어당 모듈** (`user-domain`, `user-api`, …) | 도메인 × 4 (notification은 3) | ✔ 채택 |
| ② | **도메인당 모듈 1개** (`user/` 아래 패키지만 분리) | 도메인 수만큼 | ✕ 기각 |
| ③ | **루트 단일 모듈** (패키지로만 레이어 구분) | 1개 | ✕ 기각 (ADR-001과 충돌) |

### ②를 기각하는 이유

`modules/user/build.gradle.kts` 하나에 JPA·Web·Domain 코드가 같이 있으면:

- `user-domain` 패키지가 실수로 `jakarta.persistence`를 import해도 **빌드는 통과**한다.
- “domain은 JPA 금지”가 **코드 리뷰·문서**에만 남는다.
- ADR-001의 **ArchUnit·Gradle 이중 방어**가 무력화된다.

### ③을 기각하는 이유

단일 `build.gradle.kts`는 ADR-001의 **멀티모듈 모놀리스** 선택과 동일하게 “단일 패키지 모놀리스”에 가깝다.

---

## Decision — 왜 레이어마다 `build.gradle.kts`가 **정상**인가

멀티모듈에서 **각 폴더 = 독립된 작은 Java 프로젝트**다.  
Gradle은 모듈 단위로 **의존성 그래프·컴파일 classpath·테스트 classpath**를 만든다.

### 1. 역할에 맞는 의존성만 허용 (쏙쏙 원칙)

| 모듈 | 필요한 것 | 넣으면 안 되는 것 |
| --- | --- | --- |
| `user-domain` | (최소) `common`, Lombok | Spring Web, JPA, Redis |
| `user-application` | `user-domain`, 포트 interface | JPA 구현체 |
| `user-api` | `user-application`, `spring-boot-starter-web` | `EntityManager`, Repository 구현 |
| `user-infrastructure` | `user-domain`, JPA, Mapper | HTTP Controller |

`user-infrastructure/build.gradle.kts` 예:

```kotlin
dependencies {
    implementation(project(":modules:user:user-domain"))
    implementation(project(":modules:user:user-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
```

`user-domain/build.gradle.kts`에는 JPA가 **없다**. 넣는 순간 그 모듈의 classpath에만 올라가고, domain 코드에서 JPA를 쓰려 하면 그 모듈 안에서만 컴파일되지만 — **의도치 않은 라이브러리 유입**을 팀 전체가 눈으로 확인하기 어렵다.  
모듈을 쪼개면 **파일 위치 = 허용 의존성**이 된다.

### 2. 잘못된 참조는 컴파일이 막는다

```kotlin
// order-application/build.gradle.kts — 잘못된 예
implementation(project(":modules:payment:payment-infrastructure"))
```

타 도메인 **infrastructure 구현체**에 직접 붙이면 빌드는 되지만, ADR-001 위반이다.  
올바른 방법은 `payment-application`이 정의한 **포트 interface**만 참조하는 것이다.

반대로 `order-domain`에 JPA를 추가하면:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
```

`order-domain`을 쓰는 모든 상위 모듈의 그래프가 무거워지고, **“domain이 JPA를 알게 됐다”** 는 사실이 `dependencies` 태스크 출력으로 드러난다.

### 3. 레이어 의존 방향이 빌드 그래프가 된다

허용 방향 (ADR-001):

```
api → application → domain
infrastructure → domain (+ application 포트 구현)
```

`user-api`가 `user-domain`만 직접 보면 되는지? — **api는 application을 통해야** 하므로 `user-api`의 `build.gradle.kts`에 `user-domain`을 넣지 않고 `user-application`만 둔다.  
이 제약이 없으면 Controller가 Domain을 직접 호출하는 코드가 쉽게 생긴다.

### 4. IDE·CI가 모듈 단위로 동작한다

- IntelliJ: `settings.gradle.kts`에 등록된 경로마다 **소스 루트·classpath**가 생긴다. 등록이 빠지면 “빨간 줄”·실행 구성 오류가 난다.
- CI: 변경된 모듈만 빌드·테스트하는 **선택적 태스크** (`:modules:user:user-domain:test`)가 가능하다.
- 병렬 빌드: Gradle이 모듈 단위로 작업을 나눈다.

### 5. 루트 설정과의 역할 분담 (중복이 아님)

| 파일 | 역할 |
| --- | --- |
| `settings.gradle.kts` | **어떤 모듈이 존재하는지** Gradle에 등록 |
| 루트 `build.gradle.kts` `subprojects {}` | **공통** — Java 21, BOM, Lombok, JUnit |
| 각 `*/build.gradle.kts` | **차이** — 이 모듈만의 `project()` 의존·Spring 스타터 |

공통은 루트에 한 번, **모듈별 차이만** 하위 파일에 쓴다.  
“Gradle 파일이 많다” ≠ “설정이 N번 복붙된다”.

`settings.gradle.kts` 등록 예 (경로 깊이에 맞게 `:` 사용):

```kotlin
rootProject.name = "FANDROPS"

include(":apps:api-server")
include(":modules:common")

fun includeBoundedContext(name: String, includeApi: Boolean = true) {
    include(":modules:$name:$name-domain")
    include(":modules:$name:$name-application")
    if (includeApi) include(":modules:$name:$name-api")
    include(":modules:$name:$name-infrastructure")
}

includeBoundedContext("user")
includeBoundedContext("payment")
// …
```

중간 폴더 `modules/user/`만 include하고 `user-domain`을 빼면 **Gradle은 해당 모듈을 모른다.**  
반드시 **리프(실제 `build.gradle.kts`가 있는 디렉터리)** 까지 경로를 적는다.

---

## 우리가 추가로 채택한 운영 원칙

1. **Boot 플러그인은 `apps/api-server`만** — 실행 가능 JAR는 조립 모듈 하나. 나머지는 `java-library`.

2. **더 쪼개지 않는다** — `user-domain/model`, `user-domain/service`처럼 패키지 이하로 Gradle 모듈을 나누지 않는다. 경계는 **레이어 + 바운디드 컨텍스트**가 끝.

3. **공통 로직은 `common`에 최소화** — 모든 모듈이 `common`에 의존하게 만들지 않는다. 비대해지면 “또 하나의 모놀리스”가 된다.

4. **의존성 추가 시 질문 두 가지**
   - 이 라이브러리가 **이 레이어 책임**에 맞는가?
   - 더 가벼운 모듈(예: `domain`)에 넣을 이유가 있는가?

5. **검증 습관**
   ```bash
   ./gradlew :modules:user:user-domain:dependencies --configuration compileClasspath
   ```
   Spring/JPA 누수 여부를 PR 전에 확인한다.

---

## Consequences — 트레이드오프

| 이점 | 비용 |
| --- | --- |
| 레이어·도메인 경계가 컴파일로 강제됨 | `build.gradle.kts`·`settings` 항목 증가 |
| classpath가 가벼워져 domain 테스트가 빠름 | 신규 모듈 추가 시 보일러플레이트 (템플릿·복제) |
| 팀원별 모듈 오너십이 명확해짐 | IntelliJ Gradle sync 필수 (등록 누락 시 IDE 오류) |
| MSA 후보 단위가 Gradle 모듈과 일치 | 초기 학습 곡선 (implementation(project(...)) 이해) |

**완화:** 루트 `subprojects` convention, `includeBoundedContext()` 같은 `settings` 헬퍼, 이후 `buildSrc` convention plugin 검토(모듈 수가 더 늘 때).

---

## Compliance — 준수 방법

| # | 규칙 |
| --- | --- |
| 01 | 새 레이어·도메인 추가 시 **리프 경로**를 `settings.gradle.kts`에 `include` 한다. |
| 02 | 각 리프 디렉터리에 `build.gradle.kts`가 있어야 한다. 부모 `user/`만 있고 자식에 없으면 안 된다. |
| 03 | `*-domain`에는 Spring·JPA·Redis 의존성을 선언하지 않는다. |
| 04 | 모듈 간 `implementation(project(...))`는 **허용된 방향**만 (ADR-001). infrastructure → 타 도메인 api 금지. |
| 05 | “이 모듈에만 필요한 의존성”은 **그 모듈의** `build.gradle.kts`에만 추가한다. 루트 `build.gradle.kts`에 도메인별 의존성을 몰아넣지 않는다. |

---

## FAQ

**Q. `user` 폴더에 `build.gradle.kts`가 없어도 되나요?**  
A. 됩니다. Gradle은 **include된 리프 모듈**만 인식합니다. `modules/user/`는 논리적 그룹 폴더일 뿐입니다.

**Q. 파일이 너무 많아요.**  
A. 모듈 수 = 도메인 수 × 레이어 수입니다. FANDROPS MVP 기준 약 20개 전후는 **의도된 규모**입니다. 패키지 단일 모듈보다 파일은 많지만, **경계 위반 비용**이 더 큽니다.

**Q. ADR-001과 뭐가 다른가요?**  
A. ADR-001은 **왜 멀티모듈 모놀리스인가**(배포·팀·MSA). ADR-002는 **왜 Gradle 모듈을 레이어까지 쪼개는가**(빌드·classpath·IDE).

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 멀티모듈 모놀리스 | [ADR-001](./ADR-001-multi-module-monolith.md) |
| 장바구니 RDB (Phase 1) | [ADR-003](./ADR-003-cart-storage-rdb-phase1.md) |
| Git 협업 (브랜치·PR) | [../contributing/git-collaboration-convention.md](../contributing/git-collaboration-convention.md) |
| 실제 `settings.gradle.kts` | [../../settings.gradle.kts](../../settings.gradle.kts) |
