# FANDROPS

**K-Pop 팬덤 B2B2C 커머스·이벤트 플랫폼** — 오픈런(드롭스) 스파이크에서 **재고·결제 정합성**, **대기열·Rate Limit**, **측정 가능한 성능**을 증명하는 백엔드 포트폴리오 (5인 · 2026.05–06).

> 중소형 기획사에 드롭·팬미팅 운영 도구, 팬에게 공정한 선착순·안정 결제.  
> 상세 설계 및 28개의 ADR 문서: [`docs/`](docs/README.md)

---

## 서비스 · 해결 문제 (요약)

| | |
| --- | --- |
| **One-liner** | 오픈런을 견디는 고성능 커머스·예약 플랫폼 |
| **ICP** | 중소형 기획사(Primary) · 니치 팬덤 · 10–30대 팬(B2C) |
| **SLO** | 오버셀·중복 결제 **0** · Write P95 **&lt;300ms** · 5xx **&lt;0.1%** |

**다루는 페인:** 드롭 시 서버 다운 → 대기열(Redis ZSET/SSE)·Rate Limit(Sliding Window) · 드롭스 오버셀 → DB 비관적 락 재고 선점 · 중복 결제·실패 후 재고 미복구 → 멱등 결제 & Choreography Saga · 알림 누락 → 트랜잭션 아웃박스(DB Outbox) · 피드 좋아요 동시성 → Redis Set 캐시 및 백라이팅.

**Not Scope (MVP):** [03_planning §01](docs/03_planning.html) — 인앱 좌석 예매(외부 링크만) · 복수 PG · 실시간 채팅 · 영상 자체 호스팅 · 추천/Elasticsearch · Day-1 MSA · 네이티브 앱 · 오프라인 ERP 실시간 연동 · Kafka(알림은 **DB Outbox**).

---

## 📚 압도적인 설계 및 아키텍처 문서화 (28개의 ADR)
FANDROPS 프로젝트는 시스템의 지속 가능성과 아키텍처 결정을 투명하게 기록하기 위해 **총 28개의 ADR(Architecture Decision Record)**을 작성하고 관리했습니다. 
단순 코드 구현을 넘어 시스템의 고가용성, 인프라, 동시성, 그리고 AI 협업 방식까지 문서화(SSOT, Single Source of Truth)하여 증명했습니다.
- [docs/README.md](docs/README.md)에서 28개의 모든 설계 결정 내역과 다이어그램을 확인하실 수 있습니다.
- [AI 에이전트 하네스 엔지니어링 (ADR-004)](docs/adr/ADR-004-ai-agent-harness-engineering.md)을 구축하여 개발 생산성을 체계화했습니다.

---

## Tech Stack

| 영역 | 기술 | 한 줄 근거 |
| --- | --- | --- |
| Core | **Java 21**, **Spring Boot 3.x**, **Gradle (KTS)** | Virtual Thread·SSE/웹훅 I/O, 멀티모듈 의존성 강제 |
| API | **REST (MVC) + SSE** | 대기열 순번. WebSocket Not Scope |
| Data | **MySQL 8 (RDS)**, **JPA + QueryDSL**, **Flyway** | 트랜잭션·`FOR UPDATE`(MVP). infra 레이어에만 JPA |
| Cache/락 | **Redis (ElastiCache)**, **Redisson**(Phase 3) | 대기열·캐시. MVP 재고는 MySQL 비관락 |
| Security | **Spring Security**, **JWT**, **OAuth2**(카카오·구글), **Bucket4j** | 인가·드롭스 Rate Limit |
| 결제 | **토스페이먼츠**, 멱등(`tossPaymentKey` → `payment_key`) | 웹훅·실패 복구 집중 |
| 알림 | **JavaMail** (+FCM 추후), **DB Outbox** | 발행/전송 분리. Kafka Not Scope |
| Ops | **Prometheus/Grafana**, **k6**, **GitHub Actions**, **Nginx**, **AWS** | SLO·무중단·stg/prod 분리 |
| 문서/테스트 | **SpringDoc**, **JUnit5/Mockito**, **Testcontainers**, **ArchUnit**(선택) | Domain 순수 테스트·E2E |

→ 상세 근거: [ADR-001](docs/adr/ADR-001-multi-module-monolith.md)

---

## 아키텍처

**멀티모듈 모놀리스** — 1 JVM 배포, Gradle로 바운디드 컨텍스트·레이어 분리.

```
apps/api-server          ← Boot 진입 (지영재)
modules/
  common
  user          표지민    Auth · 회원/입점 관리
  notification  표지민    알림 전송
  order         형성빈    주문 · 상품 · 배너 Admin
  inventory     형성빈    재고 동시성
  payment       장성재    PG · 웹훅 · 대기열 · RateLimit · Saga
  community     정환철    피드 · 캘린더 · 출석 · 굿즈 투표 · 라이브
```

레이어: `*-api` → `*-application` → `*-domain` ← `*-infrastructure` (domain에 Spring/JPA 금지)

→ 트리·오너십: [architecture.md](docs/architecture/architecture.md)

---

## 빠른 시작

```bash
./gradlew clean build
# 로컬 (H2, Redis/OAuth off)
set SPRING_PROFILES_ACTIVE=local   # Windows
./gradlew :apps:api-server:bootRun
```

- 시크릿: 레포에 값 없음 → [`application-local.example.yml`](apps/api-server/src/main/resources/application-local.example.yml)
- AI 코딩: [`CLAUDE.md`](CLAUDE.md) · [`docs/ai/SHARED.md`](docs/ai/SHARED.md) + persona
- API: [`docs/api/mvp-api-spec.md`](docs/api/mvp-api-spec.md) · [`api-contract.md`](docs/api/api-contract.md)

---

## 문서 맵

| | |
| --- | --- |
| 전체 인덱스 | [docs/README.md](docs/README.md) |
| ERD · 상태 머신 · 시퀀스 | `docs/erd/` · `docs/state/` · `docs/sequence/` |
| 장애 · 메트릭 | `docs/operations/` |
| Git/PR | [contributing/git-collaboration-convention.md](docs/contributing/git-collaboration-convention.md) |
