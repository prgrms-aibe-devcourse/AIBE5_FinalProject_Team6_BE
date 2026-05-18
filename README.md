# FANDROPS

**K-Pop 팬덤 B2B2C 커머스·이벤트 플랫폼** — 오픈런(핫딜) 스파이크에서 **재고·결제 정합성**, **대기열·Rate Limit**, **측정 가능한 성능**을 증명하는 백엔드 포트폴리오 (5인 · 2026.05–06).

> 중소형 기획사에 드롭·팬미팅 운영 도구, 팬에게 공정한 선착순·안정 결제.  
> 상세 설계: [`docs/`](docs/README.md)

---

## 서비스 · 해결 문제 (요약)

| | |
| --- | --- |
| **One-liner** | 오픈런을 견디는 고성능 커머스·예약 플랫폼 |
| **ICP** | 중소형 기획사(Primary) · 니치 팬덤 · 10–30대 팬(B2C) |
| **SLO** | 오버셀·중복 결제 **0** · Write P95 **&lt;300ms** · 5xx **&lt;0.1%** |

**다루는 페인:** 드롭 시 서버 다운 → 대기열·Rate Limit · 핫딜 오버셀 → DB 락/재고 선점 · 중복 결제·실패 후 재고 미복구 → 멱등·Saga·Outbox · 재입고/라이브/캘린더·랭킹 알림.

**Not Scope (MVP):** 인앱 콘서트 예매(외부 링크만) · ERP 오프라인 재고 · 팬 등급 우선권 · 해외 배송 실시간 · Kafka(알림은 **DB Outbox**).

---

## Tech Stack

| 영역 | 기술 | 한 줄 근거 |
| --- | --- | --- |
| Core | **Java 21**, **Spring Boot 3.x**, **Gradle (KTS)** | Virtual Thread·SSE/웹훅 I/O, 멀티모듈 의존성 강제 |
| API | **REST (MVC) + SSE** | 대기열 순번(F08-01). WebSocket Not Scope |
| Data | **MySQL 8 (RDS)**, **JPA + QueryDSL**, **Flyway** | 트랜잭션·`FOR UPDATE`(MVP). infra 레이어에만 JPA |
| Cache/락 | **Redis (ElastiCache)**, **Redisson**(Phase 3) | 대기열·캐시·랭킹. MVP 재고는 MySQL 비관락 |
| Security | **Spring Security**, **JWT**, **OAuth2**(카카오·구글), **Bucket4j** | 인가·핫딜 Rate Limit |
| 결제 | **토스페이먼츠**, 멱등(`payment_key`) | 웹훅·실패 복구 집중 |
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
  user          표지민    Auth · 대기열 · 알림 전송
  notification  표지민
  order         형성빈    주문 · 상품
  inventory     형성빈    재고 동시성
  payment       장성재    PG · 웹훅 · Saga
  community     정환철    피드 · 캘린더 · 랭킹 · 라이브
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
- AI 코딩: [`CLAUDE.md`](CLAUDE.md) · [`docs/ai/`](docs/ai/README.md)
- API: [`docs/api/mvp-api-spec.md`](docs/api/mvp-api-spec.md) · [`api-contract.md`](docs/api/api-contract.md)

---

## 문서 맵

| | |
| --- | --- |
| 전체 인덱스 | [docs/README.md](docs/README.md) |
| ERD · 상태 머신 · 시퀀스 | `docs/erd/` · `docs/state/` · `docs/sequence/` |
| 장애 · 메트릭 | `docs/operations/` |
| Git/PR | [contributing/git-collaboration-convention.md](docs/contributing/git-collaboration-convention.md) |
