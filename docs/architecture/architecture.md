# FANDROPS 아키텍처 개요

> **포트폴리오:** [01](../01_service_intro.html) · [02](../02_research.html) · [03](../03_planning.html) · [04 IA](../04_IA.html)  
> **기능 SSOT:** [mvp-functional-requirements-v2.md](../requirements/mvp-functional-requirements-v2.md) (§1 F-ID)  
> 보조: [03_planning.html](../03_planning.html) (Not Scope · KPI · 로드맵) · [04_IA.html](../04_IA.html) (화면 흐름)
> 상세 결정·기술 스택·호출 흐름: [ADR-001](../adr/ADR-001-multi-module-monolith.md) · 레이어별 Gradle: [ADR-002](../adr/ADR-002-per-layer-gradle-modules.md) · 장바구니 RDB: [ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md) · HTTP: [API 명세](../api/mvp-api-spec.md) · [계약](../api/api-contract.md) · 상태: [상태 머신](../state/invariants-and-state-machines.md) · 운영: [장애](../operations/failure-policy.md) · [메트릭](../operations/observability-metrics.md)

**멀티모듈 모놀리스** — 하나의 Spring Boot 프로세스로 배포하되, 코드 경계는 Gradle 서브프로젝트로 분리하여 **의존성 방향을 컴파일 단계에서 강제**한다.

---

## 레포지토리 디렉터리 구조

```
fandrops/
├── settings.gradle.kts
├── build.gradle.kts
├── apps/
│   └── api-server/                 # Boot 진입점 — 조립·설정만. 비즈니스 로직 최소화
│       ├── src/main/java/com/fandrops/
│       │   └── FandropsApplication.java
│       └── build.gradle.kts
├── modules/
│   ├── common/                     # 공통 유틸·에러코드·응답 포맷 (최소화)
│   ├── user/                       # Auth·회원·인가·입점 (표지민)
│   │   ├── user-domain/
│   │   ├── user-application/
│   │   ├── user-api/
│   │   └── user-infrastructure/
│   ├── order/                      # Commerce·주문 (형성빈)
│   │   ├── order-domain/
│   │   ├── order-application/
│   │   ├── order-api/
│   │   └── order-infrastructure/
│   ├── payment/                    # PG·웹훅·E2E (장성재)
│   │   ├── payment-domain/
│   │   ├── payment-application/
│   │   ├── payment-api/
│   │   └── payment-infrastructure/
│   ├── inventory/                  # 재고·드롭스 동시성 (형성빈)
│   │   ├── inventory-domain/
│   │   ├── inventory-application/
│   │   ├── inventory-api/
│   │   └── inventory-infrastructure/
│   ├── notification/               # 알림 (표지민) — api 레이어 없음
│   │   ├── notification-domain/
│   │   ├── notification-application/
│   │   └── notification-infrastructure/
│   └── community/                  # 피드·공지사항·댓글·스케줄·출석(배너)·투표·라이브·행사 (정환철)
│       ├── community-domain/
│       ├── community-application/
│       ├── community-api/
│       └── community-infrastructure/
└── docs/                           # ADR, 시퀀스, ERD 등
```

---

## 레이어 역할 정의

| 레이어 | 역할 | 규칙 |
| --- | --- | --- |
| `*-api` | HTTP 요청 수신, DTO 변환, Application 호출 | 비즈니스 로직 없음. Controller만. |
| `*-application` | 유스케이스 실행, 트랜잭션 경계, Domain 조합 | 포트(interface) 정의. 구현체 직접 참조 금지. |
| `*-domain` | 비즈니스 규칙, Domain Model / Aggregate / Value Object | Spring · JPA · Redis 의존 금지. 빌드로 강제. |
| `*-infrastructure` | JPA Entity·Repository, Redis, 외부 API 어댑터 | Application 포트 구현. Domain ↔ persistence 매핑. |

---

## 의존성 방향 규칙

### 허용

- `api` → `application` → `domain`
- `infrastructure` → `domain`
- `application` → (port interface)
- `common` ← 모든 모듈 (common은 최소 유지)

### 금지

- `domain` → Spring / JPA / Redis
- `user-*` → `order-infrastructure`
- `order-api` → `payment-infrastructure`
- `domain` → domain (타 모듈 직접 참조)

### MVP 한시적 예외 — 크로스 모듈 native SQL JOIN

아래 native SQL은 `artist_profile`(`user` 모듈 소유 테이블)을 `order`·`inventory` 인프라에서 직접 조인한다.  
단일 JVM 모놀리스이므로 런타임 오류는 없지만, `user` 모듈 스키마 변경 시 무음으로 깨질 수 있다.

| 쿼리 위치 | 참조 테이블 | 허용 조건 |
| --- | --- | --- |
| `OrderJpaRepository.findAgencyOrderSummaryRows` | `artist_profile.agency_id` | `idx_artist_profile_agency` 인덱스 존재 (V11) |
| `InventoryHistoryJpaRepository.findAgencySummaryRows` | `artist_profile.agency_id` | 동일 |

**해소 조건**: `product.agency_id` 역정규화 또는 포트 호출로 교체 시 이 예외 삭제.  
추적 이슈: GitHub Issue #348 (order·inventory → artist_profile 크로스 조인 해소)

### 검증

```bash
./gradlew :modules:order:order-domain:dependencies
```

ArchUnit(선택): `domain` 패키지가 `org.springframework`, `jakarta.persistence`를 import하면 테스트 실패.

---

## 도메인 오너십 → 모듈 매핑

> F-scope: [architecture § 도메인 오너십](#도메인-오너십--모듈-매핑) (Gradle 모듈·담당자)

| 담당자 | Gradle 모듈 | 도메인 영역 | F-scope (요약) |
| --- | --- | --- | --- |
| 지영재 | `apps/api-server` + 플랫폼 | Platform / SRE | F08-02, 인프라·k6·Grafana |
| 표지민 | `user` · `notification` | Identity / Platform Admin | F01-01~03, F02-01~02, F04-03, 알림 **전송**, F03-04(전송) |
| 정환철 | `community` | Community / Content | F01-04, F02-03, F03-01~08(발행), F05-01~03, F07-01 |
| **형성빈** | **`order` · `inventory`** | **Commerce** | **F04-01~02**, F04-04~05, F07-02(주문) |
| **장성재** | **`payment`** | **Payment / Traffic Gate** | F04-02(대기열), **F06-01~03**, F07-02(결제) |

### order / payment 경계 (협업)

| 구분 | 오너 | 비고 |
| --- | --- | --- |
| 주문 생성·상태·장바구니·재고 선점 | **형성빈** (`order`, `inventory`) | 장바구니 = **RDB** `CART`/`CART_ITEM` ([ADR-003](../adr/ADR-003-cart-storage-rdb-phase1.md)). PG 승인 **이후** 상태 수렴은 장성재와 스키마·시퀀스 합의 |
| 결제 승인·웹훅·멱등·실패 복구 | **장성재** (`payment`) | [상태 머신·Saga](../state/invariants-and-state-machines.md) · [시퀀스](../sequence/payment-flow-reason.md) |
| 대기열·RateLimit 정책·애플리케이션 제한 | **장성재** (`payment`) | 드롭스 상품 주문/결제 진입 보호 목적의 정책·키·에러 계약. Nginx/ALB 제한값은 지영재와 동시 리뷰 |
| 알림 전송 | **표지민** (`notification`) | 결제 완료·재입고 등 **이벤트 발행**은 형성빈·장성재·정환철이 각자 담당 |

PR 머지 전 **해당 도메인 오너 리뷰** · API·이벤트 페이로드 변경 시 문서 동시 수정.

---

## `user` vs `community` — 왜 나뉘는가

[mvp-functional-requirements-v2.md](../requirements/mvp-functional-requirements-v2.md) 기준 — **Gradle 모듈 = 바운디드 컨텍스트**이지, 화면 탭 1:1이 아니다.

| Gradle 모듈 | 담당 | 들어가는 기능 (예시) | 들어가지 **않는** 것 |
| --- | --- | --- | --- |
| **`user`** | 표지민 | F01-01~03 Auth, F02-01~02 입점 Admin, F04-03 메인 배너 | 피드·댓글, F01-04 팬 가입 구현, 대기열, F04 상품(스토어배너 제외) |
| **`community`** | 정환철 | F03 전부, F05, F07-01 | Auth 발급, 주문·결제·상시/드롭스 상품 CRUD |
| **`notification`** | 표지민 (전송) | 이메일/푸시 **발송** 어댑터 | 이벤트 **발행**(페이로드) — 발행은 각 도메인 오너 |

### 피드·댓글·출석·굿즈투표·라이브가 전부 `community`인 이유

명세상 이 기능들은 **F03(커뮤니티)·F05(행사/콘텐츠)** 묶음이고, 오너는 **정환철(Community / Content)** 한 명이다.  
별도 `feed` / `comment` Gradle 모듈로 쪼개지 **않는다** — ADR-002 원칙(레이어×바운디드 컨텍스트까지만 분리).  

패키지 예: `com.fandrops.community.api` · `…application.feed` · `…domain.goodsvote` · `…domain.attendance` 처럼 **하위 패키지**로 나누고, 빌드 모듈 is `community-*` 하나로 유지한다.

### 경계가 헷갈리는 협업 (명세 기준)

| 기능 | 오너 | 모듈 |
| --- | --- | --- |
| 팬 가입 (+1) | 정환철 | `community`의 `USER_FOLLOW` 관계. 커뮤니티 쓰기 권한의 선행 조건 |
| 아티스트 프로필 **편집/공개** | 정환철 | 승인된 입점 건을 받아 프로필·외부 링크·공간을 공개 |
| 팬·SNS 프로필 **Read**, 마이페이지 활동 **집계/BFF** | 정환철 | `community` (Read·BFF) |
| 대기열·Access Ticket | 장성재 | `payment`의 Traffic Gate 책임. 주문 검증은 형성빈, Redis/Nginx 운영값은 지영재 리뷰 |
| RateLimit | 장성재 | `payment` 중심 정책. `apps/api-server` 필터·Nginx 값은 지영재 리뷰 |

헷갈리면 **F코드·명세 표**를 보고, PR은 **해당 오너**에게 리뷰 요청한다.

---

## 운영 인프라 / 배포 구조 요약

FANDROPS 백엔드는 단일 Spring Boot 멀티모듈 모놀리스로 배포되며, 운영 인프라는 EC2-1 앱 서버와 EC2-2 k6 부하 생성 서버로 역할을 분리한다.

### Runtime

| 구성 요소 | 내용 |
| --- | --- |
| FE | Vercel (`fandrops.site`) |
| BE | EC2-1 (`t3.medium`, `api.fandrops.site`) |
| Reverse Proxy | Nginx |
| App Runtime | Spring Boot Blue/Green slot (blue: `8081` / green: `8082`) |
| DB | RDS MySQL |
| Cache / Queue | ElastiCache Redis |
| Storage | S3 |
| Monitoring | Prometheus / Grafana (EC2-1 공존) |
| Load Test | EC2-2 (`t3.small`, k6 전용 runner — 운영 트래픽 미수신) |

### Blue/Green 배포 요약

단일 EC2-1 내부에서 두 개의 Spring Boot 슬롯을 사용한다.

1. 현재 active slot은 `/etc/fandrops/active-slot`으로 관리한다.
2. 배포 시 inactive slot에 새 JAR를 기동한다.
3. `/actuator/health`가 UP인지 확인한다.
4. Nginx upstream을 새 slot 포트로 전환한다.
5. `nginx -t` 후 `systemctl reload nginx`를 수행한다.
6. 기존 slot은 graceful shutdown한다.

상세 명령어와 운영 절차는 [`docs/operations/nginx-bluegreen-strategy.md`](../operations/nginx-bluegreen-strategy.md)를 SSOT로 둔다.

### k6 실행 구조

```text
ec2-2 (t3.small, k6 runner)
   |
   | k6 traffic
   v
Nginx on ec2-1
   |
active Spring Boot slot on ec2-1 (t3.medium)
```

- ec2-1은 운영 앱 서버이자 서버 사이드 지표 기준점이다.
- ec2-2는 운영 트래픽을 받지 않는 k6 전용 부하 생성 서버다.
- k6를 앱 서버와 분리한 이유: 앱 서버 리소스와 부하 생성기 리소스 경합 방지, 서버 성능과 k6 runner 성능을 분리해서 해석.

상세 실행 방식은 [`docs/operations/k6-tuned-results.md`](../operations/k6-tuned-results.md) 및 [`docs/operations/k6-actions-runner.md`](../operations/k6-actions-runner.md)를 참고한다.
