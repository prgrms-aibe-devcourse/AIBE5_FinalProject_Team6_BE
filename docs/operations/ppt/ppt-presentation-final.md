# FANDROPS 발표 — AWS 인프라 · k6 부하테스트 최종 정리본

> 작성일: 2026-06-25 | 담당: 지영재 (SRE / CI/CD)  
> 용도: 발표 PPT 슬라이드 원고 기반 자료  
> 수치 미확인 항목: `[수치 확인 필요]` / 불확실한 내용: `[확인 필요]`

---

## 1. AWS 인프라 설계 요약

**발표 코멘트:**  
FANDROPS 백엔드는 단일 EC2 인스턴스에 앱 서버·Nginx·모니터링 스택을 통합하고, 별도 EC2에서 k6 부하테스트를 실행하는 구조로 운영했습니다. 월 72,000원 예산 제약 속에서도 Blue/Green 무중단 배포와 SSM 기반 보안 운영을 실현했습니다.

| 역할 | 구성요소 | 사양 |
|---|---|---|
| 앱 서버 | EC2-1 `team06-fandrops` | t3.medium (2vCPU, 4GB RAM) |
| 부하테스트 전용 | EC2-2 `team06-fandrops-2` | t3.small |
| 데이터베이스 | RDS MySQL 8.0.46 | db.t3.micro, 20GB |
| 캐시 | ElastiCache Redis 7.1.0 | cache.t3.micro |
| 오브젝트 스토리지 | S3 | `ap-northeast-2` |
| 리버스 프록시 + 배포 | Nginx | EC2-1 내 systemd |
| 모니터링 | Prometheus + Grafana | EC2-1 내 Docker |

**예산**: 월 ~72,000원 (한도 90,000원 이내)  
**도메인**: `api.fandrops.site` (EC2-1) / `fandrops.site` (Vercel FE)

---

## 2. 최종 아키텍처 설계 표

```
[클라이언트] ──HTTPS──▶ [Nginx / EC2-1 :80/:443]
                                   │
                       ┌───────────┴───────────┐
                       │                       │
            [fandrops-blue :8081]  [fandrops-green :8082]
            (현재 Active)           (배포 시 임시 기동)
                       │
             ┌─────────┼──────────┐
             │         │          │
       [RDS MySQL]  [Redis]     [S3]
       (별도 인스턴스) (별도 인스턴스) (오브젝트)

[EC2-2 k6] ──HTTPS──▶ [api.fandrops.site / Nginx :443] ──▶ [EC2-1 active slot]
             └─ s01·s03·s07: active slot 직접 접근 (`http://10.0.1.114:{active_port}`, Nginx 우회)
                ──Remote Write──▶ [Prometheus :9090]
                                         ▼
                                  [Grafana :3000]
```

| 구성요소 | 인스턴스 / 엔드포인트 | 비고 |
|---|---|---|
| EC2-1 | `i-07d1c60d175cdb8ca`, IP `43.203.3.196` | t3.medium, Active slot: blue:8081 |
| EC2-2 | `i-067eea702d856fa05`, IP `3.34.42.43` | t3.small, k6 전용 |
| RDS | `fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com:3306` | 프라이빗 전용 |
| Redis | `fandrops-prod-redis-001.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com:6379` | 프라이빗 전용 |
| S3 | `fandrops-prod-storage-495264909330-ap-northeast-2-an` | Public access block 전체 활성화 |
| VPC | `vpc-0fef7cb616a5fd333`, CIDR `10.0.0.0/16` | 기본 VPC 아님, 전용 구성 |

---

## 3. AWS 인프라 설계 결정 근거

### 3-1. 단일 EC2 t3.medium

**결정**: 앱·Nginx·모니터링을 EC2-1 단일 인스턴스에 통합

**경위**: 초기 t3.small로 시작 → 부하테스트 중 OOM 발생 및 Spring Boot 응답 불안정 → t3.medium으로 스케일업 후 안정화

| 인스턴스 | RAM | 결과 |
|---|---|---|
| t3.small | 2GB | JVM + Prometheus + Grafana + WireMock 동시 기동 시 OOM |
| **t3.medium** | **4GB** | 모든 프로세스 안정 기동, 300~600 RPS 처리 가능 |
| t3.large 이상 | 8GB+ | 예산 한도 초과 |

---

### 3-2. Blue/Green 배포 전략 (ALB 없는 Nginx 포트 스위칭)

**결정**: ALB 없이 Nginx upstream hot-reload 방식의 Blue/Green 배포

**왜 ALB를 쓰지 않았나**: ALB 추가 시 월 비용이 90,000원 한도를 초과. Nginx upstream 파일을 hot-reload하면 0~2초 다운타임으로 동일 효과 구현 가능.

**동작 방식**:
1. inactive slot(green :8082) 기동
2. `/actuator/health` 체크 통과 시 Nginx upstream을 `:8082`로 전환 (`nginx -s reload`)
3. old slot(blue :8081) 종료
4. `/etc/fandrops/active-slot` 파일에 현재 active 슬롯 기록

**배포 트리거**: GitHub Actions CI/CD (`bluegreen-deploy.sh`)

---

### 3-3. SSM Session Manager (SSH 키페어 없는 운영)

**결정**: EC2 접근을 SSH 키페어 없이 SSM Session Manager로 대체

**이유**:
- 키페어 분실·유출 위험 제거
- IAM 역할 기반 접근 제어 → 팀원별 권한 관리 용이
- 보안그룹 `sg-0fbc632fa599e1e64` — SSH 22번 인바운드 규칙 없음 (AWS CLI 확인)

**SSM 운영 상태**: EC2-1·EC2-2 모두 SSM PingStatus=Online (AWS CLI 확인)

**운영 중 발견한 한계**: Windows 환경에서 SSM CLI stdout 인코딩 이슈 발생. `PYTHONUTF8=1` + `--cli-input-json file://` + `LC_ALL=C` 조합으로 해결.

---

### 3-4. HikariCP maximumPoolSize 증설 (기본값 10 → 30)

**결정**: Spring Boot 기본 DB 커넥션 풀 크기를 30으로 증설

**이유**: 부하테스트 중 커넥션 풀 고갈로 `Connection is not available, request timed out` 발생. db.t3.micro MySQL `max_connections` 제약 내에서 30 설정 시 안정화. 30 이상은 RDS max_connections 초과 위험.

---

### 3-5. Nginx Rate Limit 설계 (4개 Zone)

| Zone | 설정 | 적용 경로 | 설계 이유 |
|---|---|---|---|
| `fandrops_order` | 5r/s, burst 10 | `/api/v1/orders` | DB Write 과부하 방지 |
| `fandrops_queue` | 10r/s, burst 1000 | `/api/v1/queue/join` | 대기열 진입 burst 허용 |
| `fandrops_payment` | 5r/s, burst 10 | `/api/v1/payments/toss/confirm` | 결제 과다 요청 차단 |
| `fandrops_sse` | connection zone | `/api/v1/queue/stream` | SSE 동시 연결 수 제어 |

---

### 3-6. k6 전용 EC2-2 분리

**결정**: 부하테스트 실행기를 EC2-1 앱 서버와 분리

**이유**:
- 동일 인스턴스에서 k6 실행 시 CPU 경합 → 측정값 오염
- EC2-2 (서울 리전, 동일 VPC) → 동일 리전 내부 통신, 대륙 간 왕복 지연 없음
- GitHub Actions runner 대비: 국내↔서울 왕복 없이 일관된 측정값 확보

---

## 4. k6 테스트 환경 요약

| 항목 | 값 |
|---|---|
| k6 버전 | v2.0.0 (SSM RunCommand로 EC2-2 실측 확인) |
| 실행 위치 | EC2-2 t3.small (`3.34.42.43`) |
| 측정 대상 | `https://api.fandrops.site` public HTTPS endpoint (EC2-2 → EC2-1, 동일 리전 내 호출) |
| s01·s03·s07 예외 | active slot 직접 접근 (`http://10.0.1.114:{active_port}`) — Nginx 우회 (k6-baseline-results.md·k6-tuned-results.md 실행 명령어 확인) |
| Prometheus Remote Write | `http://10.0.1.114:9090/api/v1/write` |
| 시각화 | Grafana (`http://43.203.3.196:3000`) |
| 토큰 | fan_id 1~2100 JWT (`/opt/fandrops/k6/seed/tokens.csv`) |
| 수치 집계 기준 | **k6 클라이언트 수치** (Grafana Micrometer는 실시간 추세 참고용) |
| s05 예외 | GitHub Actions runner에서 실행 (SSE 전용 — GHA 주소 분산 특성 활용) |

**발표 코멘트:**  
k6를 앱 서버와 별도 EC2에서 실행해 CPU·메모리 경합 없이 정확한 SLO 수치를 측정했습니다. 모든 공식 수치는 k6 클라이언트 집계 기준이며, Grafana Micrometer는 실시간 추세 모니터링에만 사용합니다.

---

## 5. SLO 기준표

| 시나리오 | SLO 유형 | P95 목표 | 에러율 목표 | 특이사항 |
|---|---|---|---|---|
| s01 주문 동시성 | Order 정합성 | 참고 지표 (P95 비SLO) | 5xx 0건 | 초기 Write P95<300ms → 팀 합의로 정합성(orders_reserved=100) 기준 변경 |
| s02 피드 Read | Read | < 120ms | < 0.1% | |
| s03 결제 확인 | Payment | < 2,000ms | < 0.1% | 초기 300ms → 결제 외부 API 의존성 인정, 2026-06-22 팀 합의 완화 |
| s04 드롭스 스파이크 | Write | < 300ms | < 0.1% (429 제외) | 스파이크 구간 Rate Limit 429는 정상 거부 |
| s05 SSE 대기열 | SSE Capacity | 2,000 동시 연결 | retryable:true만 허용 | 정상 구간 거부 0건, 초과 구간 429 retryable:true |
| s06 통합 워크로드 | Mixed | Feed<120ms, Payment<2,000ms | < 0.1% | Order 정합성: reserved=200 |
| s07 상품 조회 처리량 | Read + Throughput | < 120ms, 300 RPS | < 0.1% | constant-arrival-rate 구조 |

### SLO 완화 이력

| 항목 | 원래 기준 | 완화된 기준 | 사유 |
|---|---|---|---|
| s01 P95 기준 | P95 < 300ms | 정합성 (orders_reserved=100, 5xx=0) | 409는 동시성 충돌의 정상 응답 — P95에 포함 시 측정 기준 왜곡 |
| s03 P95 기준 | P95 < 300ms | P95 < 2,000ms | Toss API 외부 의존성, 실제 결제 SLA 반영 |

---

## 6. 전체 개선 타임라인

| Phase | 시기 | 주요 변경 | 결과 요약 |
|---|---|---|---|
| **Baseline** | 2026-06-19 | s01~s06 초기 측정 | s07 미포함(신규 미생성). s05 100% 실패. s06 블로커 2건 |
| **Tuned** | 2026-06-19~21 | N+1 제거(PR #330), 인덱스 추가, Redis 캐시(PR #341), HikariCP 30 | s02 P95 133→168ms (CPU 포화 지속). s04 ✅ 유지 |
| **Final** | 2026-06-22 | s07 신규 측정, s05 17차 달성, 결제 SLO 완화 합의 | s02 FeedLikeCache 미반영 276ms ❌. s01 낙관적 락 실패(reserved=16) ❌ |
| **RealFinal** | 2026-06-23~25 | atomic update 복귀(PR #459), FeedLikeCache(PR #394), TX 분리(PR #453) | s01·s02·s03·s06 SLO 달성. **전 시나리오 ✅** |

---

## 7. 시나리오별 정리

---

### Scenario 01. 주문 동시성

**목적**: 100명이 동시에 같은 상품을 주문할 때 오버셀 없이 정합성을 유지하는가

**SLO**: `orders_reserved = 100`, 5xx 0건 (P95는 참고 지표)

| Phase | P95 | orders_reserved | 5xx | 판정 |
|---|---|---|---|---|
| Baseline | 1,750ms(전체) / 865ms(성공) | 100 | 0 | ⚠️ 정합성 ✅ |
| Tuned | 1,770ms | 100 | 0 | ⚠️ 정합성 ✅ |
| Final | 4,970ms | **16** | 발생 | ❌ 정합성 위반 |
| **RealFinal** | **2,990ms (참고)** | **100** | **0** | ✅ **SLO 달성** |

**문제 (Final)**: 낙관적 락(`@Version`) 도입 후 `UnexpectedRollbackException` 발생. 재시도 로직 없이 예외 전파 → 5xx + orders_reserved=16.

**개선 (RealFinal)**: 낙관적 락 폐기 → **Atomic Update** 방식 복귀 (PR #459)
```sql
UPDATE inventory SET available_qty = available_qty - 1
WHERE product_id = ? AND available_qty > 0
```
결과: orders_reserved=100 정합성 복원, 5xx=0 달성.

**발표 메시지:**
> "낙관적 락은 재시도 전략이 필수입니다. 재시도 없이 도입하면 오히려 정합성이 무너집니다. Atomic Update로 복귀해 100건 정합성을 보장했습니다."

---

### Scenario 02. 피드 Read P95

**목적**: 팬이 아티스트 피드를 조회할 때 Read SLO P95 120ms 이하를 달성하는가

**SLO**: P95 < 120ms, 에러율 < 0.1%

| Phase | P95 | 에러율 | 판정 |
|---|---|---|---|
| Baseline | 133ms | 0.00% | ❌ |
| Tuned | 168~172ms | 0.00% | ❌ |
| Final | 276ms | 0.00% | ❌ |
| **RealFinal** | **66.47ms** | **0.00%** | ✅ **SLO 달성** |

**문제 단계별 원인**:
- Baseline: N+1 쿼리 (피드 이미지·좋아요 건별 조회, 최대 수십 회 DB 왕복)
- Tuned: N+1 제거 후에도 CPU 100% 포화 (t3.small → t3.medium 스케일업으로 완화). 그러나 P95 오히려 증가
- Final: FeedLikeCache 미반영 — 캐시 히트 후에도 `applyIsLiked()`가 매 요청 Redis 추가 조회

**개선 누적**:
1. N+1 제거 (PR #330): `findByFeedIdIn` / `findLikedFeedIdsByFanId` bulk IN 쿼리, 대댓글 bulk 조회
2. 커서 인덱스 추가: `idx_artist_feed_artist_cursor (artist_id, id DESC)` → Full Scan 제거
3. Redis 캐시 (PR #341): TTL 60s + jitter, SingleFlight, viewer-agnostic 캐시
4. **FeedLikeCache** (PR #394): 캐시 키 `feed:liked:{fanId}:{sortedFeedIds}` TTL 30s → **FeedCache hit + FeedLikeCache hit 시 DB 쿼리 0회**

**발표 메시지:**
> "N+1 제거와 viewer별 좋아요 캐시 분리로 warm cache 기준 P95를 133ms에서 66ms로 개선했습니다. 캐시를 적용해도 개인화 데이터 경로를 놓치면 성능이 생각만큼 오르지 않습니다."

---

### Scenario 03. 결제 확인

**목적**: 결제 완료 API가 P95 2,000ms 이하를 안정적으로 달성하는가

**SLO**: P95 < 2,000ms, 에러율 < 0.1%

| Phase | P95 | 에러율 | 판정 |
|---|---|---|---|
| Baseline | 1,540ms | 0.00% | ✅ |
| Tuned | 1,940ms | 0.00% | ✅ |
| Final | 2,080ms | **1.60%** | ❌ (5xx 발생) |
| **RealFinal** | **1,590ms** | **0.00%** | ✅ **SLO 달성** |

**문제 (Final)**: Toss 결제 확인 API 호출 중 `HttpTimeoutException` 발생 → 5xx 에러율 1.60%. WireMock stub 응답 지연이 트랜잭션 타임아웃 경계와 겹침.

**개선 (RealFinal)**: 결제 확인 트랜잭션 분리 (PR #453). Toss API 호출과 DB 반영 트랜잭션을 분리해 타임아웃 전파 차단. `TOSS_API_READ_TIMEOUT=2s` 환경변수 주입.

**발표 메시지:**
> "외부 결제 API 호출과 DB 트랜잭션을 분리해 타임아웃으로 인한 5xx를 완전히 제거했습니다."

---

### Scenario 04. 드롭스 스파이크

**목적**: 드롭스 오픈런 순간의 스파이크 트래픽에서 Write P95 300ms 이하를 유지하는가

**SLO**: P95 < 300ms, spike_orders_reserved=100, 에러율 < 0.1% (429 제외)

| Phase | P95 | spike_orders_reserved | 판정 |
|---|---|---|---|
| Baseline | 266ms | 100 | ✅ |
| Tuned | 287ms | 100 | ✅ |
| Final | 278ms | 100 | ✅ |
| **RealFinal** | **Final 이월** | **100** | ✅ **SLO 달성 유지** |

**Final 이월 사유**: Final 단계에서 SLO 달성 상태를 유지하므로 RealFinal 재측정 생략.

**특이사항**: 스파이크 구간 초과 요청 차단율 ~99.98%는 전량 Nginx Rate Limit 429 정상 거부 — 서버 장애성 5xx 아님. SLO 에러율 계산에서 제외. `fandrops_order` zone(5r/s, burst 10)이 Spring Boot 도달 RPS를 약 5 RPS 수준으로 제한해 앱 서버를 보호.

**발표 메시지:**
> "드롭스 오픈런 스파이크에서 앱 서버를 보호하는 방법은 버티는 것이 아니라 초과 요청을 빠르게 차단하는 것입니다. Rate Limit으로 약 5 RPS 수준만 앱에 도달시켜 P95 300ms 이하를 유지했습니다."

---

### Scenario 05. SSE 대기열

**목적**: 2,000명의 팬이 동시에 SSE 대기열에 연결될 때 정상 구간 거부 없이, 초과 구간을 안전하게 처리하는가

**SLO**: 2,000 동시 SSE 연결 (정상 구간 거부 0건), 초과 요청 429 retryable:true

| Phase | SSE 연결 | 429 retryable:true | 판정 |
|---|---|---|---|
| Baseline | 에러율 100% (전원 거부) | 0% | ❌ |
| Tuned (1~16차) | — | — | ❌ (반복 실패) |
| Final (17차) | **2,000** | **99.33%** | ✅ |
| **RealFinal** | **Final 이월** | **Final 이월** | ✅ **SLO 달성 유지** |

**주요 트러블슈팅 핵심 4건** (17차 중):

| 차수 | 문제 현상 | 원인 | 해결책 |
|---|---|---|---|
| 6차 | SSE 연결되나 이벤트 미전달 | Nginx↔Spring HTTP/1.0 → chunked transfer encoding 미지원 | `proxy_http_version 1.1` |
| 9차 | IP 경계 2,100 연결 차단 | `limit_conn fandrops_sse` IP 기반 경계 → GHA 단일 IP에서 모두 차단 | limit_conn 완전 제거 |
| 14차 | 초과 구간 403 (예상 429) | Accept:text/event-stream 요청에 JSON 예외 응답 충돌 (콘텐츠 협상 실패) | `PaymentControllerAdvice` contentType APPLICATION_JSON 추가 + /error permitAll |
| 16차 | 모든 metric 0/0 집계 | `ramping-vus` VU 감소 시 SSE interrupt → 성공/실패 metric 미기록 | **capacity_fill + overflow_probe 2단계 구조 재설계** |

**발표 메시지:**
> "SSE는 HTTP와 다릅니다. 17차례의 트러블슈팅 끝에 2,000 동시 연결과 초과 구간 안전 거부를 모두 달성했습니다."

---

### Scenario 07. 상품 조회 처리량

**목적**: constant-arrival-rate 300 RPS에서 상품 조회 Read P95 120ms 이하를 달성하는가

**SLO**: P95 < 120ms, 300 RPS 달성, 에러율 < 0.1%

| Phase | P95 | 실측 RPS | 판정 |
|---|---|---|---|
| Baseline | 미측정 (s07 신규 — 베이스라인 미포함) | — | — |
| Tuned | 133ms | ~298/s (k6) / ~250/s (Grafana 실측) — dropped_iterations 251건 ⚠️ | ❌ |
| Final | **16.35ms** | **299.47 RPS** | ✅ |
| **RealFinal** | **Final 이월** | **Final 이월** | ✅ **SLO 달성 유지** |

**Tuned 133ms 원인**: 캐시 미적용 — 300 RPS × product·inventory·이미지 3개 DB 쿼리 = 900 q/s DB 직행. dropped_iterations 251건, max latency 1.33s 발생.

**Final 16ms 개선 원인**: Redis 캐시(product + 이미지 TTL 120s) 적용으로 DB 쿼리 대폭 감소. Final 첫 번째 실행은 active port 오지정(8081 지정, 실제 active는 green 8082) → connection refused로 무효 처리 후 재실행.

**발표 메시지:**
> "설정 오류 수정과 캐시 히트로 P95가 133ms에서 16ms로 개선되어 300 RPS 목표를 달성했습니다."

---

### Scenario 06. 통합 워크로드

**목적**: 피드 조회·결제 확인·주문 동시성이 동시에 실행되는 실사용 시나리오에서 각 SLO를 모두 달성하는가

**SLO**: Feed P95 < 120ms, Payment P95 < 2,000ms, Order reserved=200건 (5xx 0건)

| Phase | Feed P95 | Payment P95 | Order reserved | 판정 |
|---|---|---|---|---|
| Baseline | 블로커 2건 (측정 불가) | — | — | ❌ |
| Tuned | — | — | — | — |
| Final | — | — | — | — |
| **RealFinal** | **58.84ms** | **148.88ms** | **200건** | ✅ **SLO 달성** |

**RealFinal 단일 측정 배경**:
- Baseline 차단 사유 1: 피드 조회 `ROLE_FAN` 403 → 인가 설정 수정
- Baseline 차단 사유 2: WireMock stub 응답 불일치 400 → stub 설정 수정
- s01·s02·s03 개별 SLO 달성 후, warm cache 상태에서 통합 측정 수행

**발표 메시지:**
> "개별 시나리오 최적화가 통합 시나리오에서도 그대로 유지됩니다. Feed 58ms, Payment 148ms, 주문 정합성 200건 모두 달성했습니다."

---

## 8. 시나리오별 단계 비교표

### P95 응답시간 요약 (ms)

| 시나리오 | Baseline | Tuned | Final | RealFinal | SLO 기준 |
|---|---|---|---|---|---|
| s01 주문 동시성 | 1,750 | 1,770 | 4,970 ❌ | **2,990 ✅** (참고) | 정합성 기준 |
| s02 피드 Read | 133 ❌ | 168~172 ❌ | 276 ❌ | **66 ✅** | < 120ms |
| s03 결제 확인 | 1,540 ✅ | 1,940 ✅ | 2,080 ❌ | **1,590 ✅** | < 2,000ms |
| s04 드롭스 스파이크 | 266 ✅ | 287 ✅ | 278 ✅ | **이월 ✅** | < 300ms |
| s05 SSE 대기열 | 100% 실패 ❌ | — | **2,000 ✅** (17차) | **이월 ✅** | 2,000 연결 |
| s06 통합 워크로드 | 블로커 ❌ | — | — | **58/148ms ✅** | <120/<2,000ms |
| s07 상품 조회 | 미측정 | 133 ❌ | **16 ✅** | **이월 ✅** | < 120ms + 300RPS |

### 정합성 지표

| 시나리오 | Baseline | Tuned | Final | RealFinal |
|---|---|---|---|---|
| s01 orders_reserved | 100 ✅ | 100 ✅ | **16 ❌** | **100 ✅** |
| s04 spike_reserved | 100 ✅ | 100 ✅ | 100 ✅ | 이월 ✅ |
| s06 orders_reserved | 블로커 | — | — | **200 ✅** |

---

## 9. SLO 기준 판단 종합

| 시나리오 | 최종 판정 | 달성 근거 |
|---|---|---|
| s01 주문 동시성 | ✅ **달성** | RealFinal: orders_reserved=100, 5xx=0 (PR #459 atomic update) |
| s02 피드 Read | ✅ **달성** | RealFinal: P95 66.47ms < 120ms (PR #394 FeedLikeCache) |
| s03 결제 확인 | ✅ **달성** | RealFinal: P95 1.59s < 2,000ms, 에러율 0.00% (PR #453 TX 분리) |
| s04 드롭스 스파이크 | ✅ **달성** | Final 이월: P95 278ms < 300ms, spike_reserved=100 |
| s05 SSE 대기열 | ✅ **달성** | Final 이월: 2,000 동시 연결, 429 retryable:true 99.33% (17차) |
| s06 통합 워크로드 | ✅ **달성** | RealFinal: Feed 58.84ms, Payment 148.88ms, Order 200건 |
| s07 상품 조회 처리량 | ✅ **달성** | Final 이월: P95 16.35ms, 299.47 RPS |

**결론: 전 시나리오(s01~s07) SLO 달성 ✅**

---

## 10. SLO 타협 내용과 타당성

### 타협 1: s01 — P95 기준 폐기 → 정합성 기준 채택

| 항목 | 내용 |
|---|---|
| 원래 기준 | P95 < 300ms |
| 변경 기준 | orders_reserved=100, 5xx 0건 (P95는 참고 지표) |
| 타당성 | 동시성 테스트의 본질 목적은 속도가 아니라 데이터 정합성. 409(Conflict)는 정상적인 동시성 처리 결과 — P95에 포함 시 기준 왜곡. P95 참고 수치 2.99s는 재고 소진 후 빠른 reject 포함이라 절대값 의미 낮음 |
| 팀 합의 | ✅ 전원 동의 |

### 타협 2: s03 — 결제 P95 300ms → 2,000ms

| 항목 | 내용 |
|---|---|
| 원래 기준 | P95 < 300ms |
| 변경 기준 | P95 < 2,000ms |
| 타당성 | Toss API 외부 호출이 필수 경로 — 300ms는 외부 API 한 번 왕복에도 불가능한 기준. 실제 결제 서비스 SLA는 1~3초 수준이 일반적. 2,000ms 기준은 사용자 경험과 기술 한계를 모두 반영한 현실적 기준 |
| 팀 합의 | ✅ 2026-06-22 합의 |

**발표 메시지:**
> "SLO 완화는 기술적 한계의 인정이 아니라 현실적인 측정 기준 정합을 위한 결정입니다. 두 완화 모두 팀 합의를 거쳤으며, 최종 달성 수치는 완화된 기준에서도 충분한 여유를 확보합니다."

---

## 11. 추후 개선사항

### 11-1. 분산 설계 검증 (단일 EC2 한계 — 최우선)

현재 구조의 근본적 제약:

| 구성요소 | 현재 상태 | 분산 시 문제 |
|---|---|---|
| `SseEmitterRegistry` | in-memory ConcurrentHashMap (EC2-1 단독) | 다중 노드에서 이벤트 브로드캐스트 불가. 노드 A 연결 사용자에게 노드 B 이벤트 미전달 |
| 대기열 상태 | Redis 단일 인스턴스 기반 | Redis Cluster/Sentinel 이중화 필요 |
| Nginx Blue/Green | 단일 EC2 내 포트 스위칭 | ALB + Auto Scaling Group으로 확장 시 재설계 필요 |

**분산 설계 예상 한계 (Phase 2 검증 계획)**:
- 현재 단일 노드(EC2-1)에서 s01/s04 오버셀 0건·중복 결제 0건을 확인했으나, 이는 단일 JVM에서 Redis를 공유하는 구조의 검증 결과다. 멀티 노드 환경에서 동일한 정합성이 유지되는지는 아직 실측하지 못했다.
- **SseEmitterRegistry 메시지 유실 예상**: in-memory 구조이므로, 노드 A에 연결된 사용자에게 노드 B에서 발행한 이벤트가 미전달될 가능성이 있다. 이는 실측 결과가 아니라 멀티 노드 확장 시 예상되는 구조적 한계다.
- **Phase 2 검증 계획**: EC2-2에 앱 노드를 추가한 뒤 s01/s04를 재실행해 멀티 노드에서도 오버셀 0건·중복 결제 0건이 유지되는지 실측할 예정이다. SSE는 Redis Pub/Sub 또는 Kafka 기반 이벤트 브로드캐스트로 개선을 검토한다.

---

### 11-2. ALB + Auto Scaling 도입

- 현재 예산(~72,000원/월) 내 ALB 추가 불가
- 프로덕션 확장 시 ALB + ASG → 무중단 수평 확장 구현 가능
- Blue/Green 전략도 ALB Target Group 스위칭 방식으로 대체

---

### 11-3. s02 Cold Cache 구간 처리

- 현재 SLO 달성은 **warm cache 기준**
- 서비스 재기동 직후 cold cache 구간(P95 250ms+ 피크) 처리 전략 필요
  - 옵션: 사전 워밍업 스크립트, TTL 단계적 확대, Circuit Breaker 패턴

---

### 11-4. RDS / Redis 사양 개선

- db.t3.micro, cache.t3.micro: 프로젝트 기간 내 처리량 한계 명확
- Read Replica 추가 또는 db.t3.small/medium 업그레이드 필요

---

### 11-5. CloudWatch 알람 미구성

- 현재 CloudWatch 네임드 알람 없음 (Prometheus/Grafana만 운영)
- 프로덕션 진입 시 EC2 CPU, RDS 연결수, Redis 메모리 CloudWatch 알람 필수

---

## 12. 트러블슈팅 핵심 사례

---

### 사례 1: SSE chunked 전송 실패 — Nginx HTTP/1.0 기본값 (s05 6차)

| 항목 | 내용 |
|---|---|
| **문제** | SSE 연결은 맺어지나 이벤트가 클라이언트에 전달되지 않음 |
| **원인** | Nginx가 Spring Boot로 HTTP/1.0으로 프록시 → HTTP/1.0은 chunked transfer encoding 미지원 → 청크 종료자 미전달 |
| **적용 기술/설정** | `proxy_http_version 1.1` Nginx 설정 추가 |
| **해결** | Nginx → Spring Boot 간 HTTP/1.1 유지 → chunked 정상 전달 |
| **결과** | SSE 이벤트 수신 복원 |
| **발표 메시지** | "SSE는 HTTP/1.1 chunked transfer가 필수입니다. Nginx 기본값 HTTP/1.0이 SSE 전송을 막고 있었습니다." |

---

### 사례 2: SSE ramping-vus 구조적 결함 — metric 0/0 (s05 16차)

| 항목 | 내용 |
|---|---|
| **문제** | SSE 테스트 결과 모든 metric이 0/0으로 집계됨 |
| **원인** | `ramping-vus`에서 VU 감소 시 진행 중인 SSE 연결이 interrupt 종료 → 성공/실패 metric 미기록 |
| **적용 기술/설정** | 시나리오 구조 전면 재설계: `capacity_fill`(정상 구간 채우기) + `overflow_probe`(초과 구간 검증) 2단계 분리 |
| **해결** | capacity_fill: 2,000 VU 순차 기동 후 유지. overflow_probe: 2,001번째 요청부터 429 수집 |
| **결과** | SSE 2,000 동시 연결 ✅, 429 retryable:true 99.33% ✅ |
| **발표 메시지** | "SSE 테스트는 연결 유지 시간이 본질입니다. VU 감소 interrupt를 피하기 위해 2단계 구조로 재설계했습니다." |

---

### 사례 3: 낙관적 락 도입 후 정합성 위반 (s01 Final → RealFinal)

| 항목 | 내용 |
|---|---|
| **문제** | Final 측정에서 orders_reserved=16 (100 예상), 5xx 다수 발생 |
| **원인** | 낙관적 락 `@Version` 도입 후 `UnexpectedRollbackException`이 재시도 로직 없이 상위로 전파 |
| **적용 기술/설정** | 낙관적 락 폐기 → Atomic Update 방식 복귀 (PR #459) |
| **해결** | 단건 UPDATE 원자성으로 동시성 처리 — 예외 전파 경로 없음 |
| **결과** | orders_reserved=100, 5xx=0 정합성 완전 복원 |
| **발표 메시지** | "낙관적 락은 재시도 전략이 필수입니다. 재시도 없이 도입하면 오히려 정합성이 무너집니다." |

---

### 사례 4: FeedLikeCache 미반영 — 캐시 히트 후에도 P95 276ms (s02 Final)

| 항목 | 내용 |
|---|---|
| **문제** | N+1 제거 + Redis 캐시 적용 후에도 Final P95 276ms (기준 120ms) |
| **원인** | FeedCache hit 이후 `applyIsLiked()`가 매 요청 Redis 추가 조회 → viewer별 좋아요 확인에 Redis GET 추가 왕복 |
| **적용 기술/설정** | `FeedLikeCachePort` / `FeedLikeCacheAdapter` (PR #394): `feed:liked:{fanId}:{sortedFeedIds}` TTL 30s |
| **해결** | FeedCache hit + FeedLikeCache hit 경로에서 DB 쿼리 0회, Redis 추가 왕복 0회 |
| **결과** | P95 276ms → **66.47ms** (warm cache 기준) |
| **발표 메시지** | "캐시를 적용해도 개인화 데이터 경로를 놓치면 성능이 생각만큼 오르지 않습니다. 세분화된 캐시 전략이 필요합니다." |

---

### 사례 5: SSM Windows 인코딩 이슈 (운영 환경)

| 항목 | 내용 |
|---|---|
| **문제** | Windows에서 SSM CLI로 EC2 명령 실행 시 stdout 인코딩 오류 (한글 깨짐, JSON parse 실패) |
| **원인** | Windows PowerShell 기본 인코딩(UTF-16 LE) + AWS CLI 출력 인코딩 불일치 |
| **적용 기술/설정** | `PYTHONUTF8=1` + `--cli-input-json file://` + `LC_ALL=C` 조합 |
| **해결** | SSM Document JSON 파일 입력으로 인코딩 충돌 우회 |
| **결과** | SSH 없이 SSM만으로 모든 EC2 운영 가능 |
| **발표 메시지** | "SSH 없이 SSM만으로 전체 운영이 가능했으며, Windows 인코딩 이슈는 환경변수 조합으로 해결했습니다." |

---

## 13. 시나리오별 스크린샷 배치표

### 배치 원칙

- 각 시나리오는 **실제 존재하는 캡처 파일 기준**으로 배치한다.
- 단계별 캡처가 없는 경우 빈칸을 채우지 않고 **없음**으로 표시한다.
- **Final 결과가 RealFinal로 이월된 경우** Final 캡처를 최종 판단 근거로 사용한다.
- **Scenario 06**은 RealFinal 단일 캡처만 사용한다.

> 모든 경로는 `docs/operations/k6/` 기준 상대 경로다.

---

### 배치표

| 시나리오 | 목적/제목 | Baseline 스크린샷 | Tuned 스크린샷 | Final 스크린샷 | RealFinal 스크린샷 | 최종 사용 캡처 | 배치 의도 |
|---|---|---|---|---|---|---|---|
| **01** | 주문 동시성 + 재고 정합성 | `screenshots/baseline/s01_order_concurrency_baseline.png` | `screenshots/tuned/s01_order_concurrency_tuned.png` | `screenshots/final/s01_order_concurrency_final.png` | RealFinal 1차: `screenshots/realfinal/s01_order_concurrency_realfinal.png` (PR #450 실패)<br>RealFinal 2차: `screenshots/realfinal/s01_order_concurrency_realfinal2.png` (PR #459 성공) | `screenshots/realfinal/s01_order_concurrency_realfinal2.png` | Baseline→Final 낙관적 락 실패 경로 시각화 + RealFinal 2차 atomic update 복귀로 정합성 달성 스토리 |
| **02** | 피드 목록 Read P95 | `screenshots/baseline/s02_feed_read_baseline.png` | `screenshots/tuned/s02_feed_read_tuned.png` | `screenshots/final/s02_feed_read_final.png` | `screenshots/realfinal/s02_feed_read_realfinal_warm.png` | `screenshots/realfinal/s02_feed_read_realfinal_warm.png` | FeedLikeCache 적용 전후 P95 개선 흐름 (Baseline→RealFinal warm cache 기준) |
| **03** | 결제 확인 P95 | `screenshots/baseline/s03_payment_confirm_baseline.png` | `screenshots/tuned/s03_payment_confirm_tuned.png` | `screenshots/final/s03_payment_confirm_final.png` | `screenshots/realfinal/s03_payment_confirm_realfinal.png` | `screenshots/realfinal/s03_payment_confirm_realfinal.png` | Baseline→RealFinal 4단계 전체 흐름 + SLO 완화(300ms → 2,000ms) 근거 병기 |
| **04** | 드롭스 오픈런 스파이크 | `screenshots/baseline/s04_drop_spike_baseline.png` | `screenshots/tuned/s04_drop_spike_tuned.png` | `screenshots/final/s04_drop_spike_final.png` | [Final 결과 이월 — RealFinal 별도 캡처 없음] | `screenshots/final/s04_drop_spike_final.png` | Rate Limit 적용 후 Final에서 SLO 달성 → 3차 Final에서 SLO 달성 후 RealFinal 최종 결과로 이월 |
| **05** | SSE 대기열 동시 연결 | `screenshots/baseline/s05_sse_queue_baseline.png` | `screenshots/tuned/s05_sse_queue_tuned.png` | `screenshots/final/s05_sse_queue_final.png` | [Final 결과 이월 — RealFinal 별도 캡처 없음] | `screenshots/final/s05_sse_queue_final.png` | 2단계(capacity_fill + overflow_probe) 재설계로 Final SLO 달성 → 3차 Final에서 SLO 달성 후 RealFinal 최종 결과로 이월 |
| **07** | 상품 상세 조회 처리량 | [스크린샷 없음] | `screenshots/tuned/s07_product_read_tuned.png` | `screenshots/final/s07_product_read_final.png` | [Final 결과 이월 — RealFinal 별도 캡처 없음] | `screenshots/final/s07_product_read_final.png` | Redis 캐시 적용 효과(Tuned 133ms → Final 16ms) 중심, Baseline 캡처 없음 명시 · 3차 Final에서 SLO 달성 후 RealFinal 최종 결과로 이월 |
| **06** | 통합 워크로드 모델 | 해당 없음 | 해당 없음 | 해당 없음 | `screenshots/realfinal/s06_workload_model_realfinal.png` | `screenshots/realfinal/s06_workload_model_realfinal.png` | 개별 시나리오 검증 이후 통합 워크로드 최종 안정성 확인 — RealFinal 단일 캡처만 사용 |

---

### 시나리오별 슬라이드 배치 설명

**Scenario 01 — 주문 동시성 + 재고 정합성**

- 상단: "Scenario 01. 주문 동시성 + 재고 정합성 검증" + 목적(동시 200 VU, 재고 100개)
- 중앙: Baseline → Tuned → Final(낙관적 락 실패) → RealFinal 1차(PR #450 실패) → RealFinal 2차(PR #459 성공) 순서 배치
- RealFinal 1차는 "실패 경과 기록"으로 라벨링, RealFinal 2차에 "최종 성공" 라벨 표시
- 하단: SLO 기준(orders_reserved=100, 5xx 0건, 오버셀 0건), P95는 참고 지표 명시

**Scenario 02 — 피드 목록 Read P95**

- 상단: "Scenario 02. 피드 목록 Read P95" + 목적(FeedLikeCache 효과 검증)
- 중앙: Baseline → Tuned → Final → RealFinal(warm cache) 순서 배치
- 하단: SLO 기준(P95 < 120ms), RealFinal 기준 달성 수치 기재

**Scenario 03 — 결제 확인 P95**

- 상단: "Scenario 03. 결제 확인 P95" + 목적 + SLO 완화(300ms → 2,000ms) 표시
- 중앙: Baseline → Tuned → Final → RealFinal 순서 배치
- 하단: SLO 완화 근거(Toss 외부 API 의존 + WireMock 대체 한계) 한 줄 병기

**Scenario 04 — 드롭스 오픈런 스파이크**

- 상단: "Scenario 04. 드롭스 오픈런 스파이크" + 목적(Rate Limit 효과 검증)
- 중앙: Baseline → Tuned → Final 순서 배치 / RealFinal 칸에 "Final 결과 이월" 라벨 표시
- 하단: SLO 기준(P95 < 300ms), Final 캡처에 "최종 결과 이월" 라벨

**Scenario 05 — SSE 대기열 동시 연결**

- 상단: "Scenario 05. SSE 대기열 동시 연결" + 목적(2,000 동시 연결 SLO)
- 중앙: Baseline → Tuned → Final(capacity_fill + overflow_probe 2단계) 순서 배치 / RealFinal 칸에 "Final 결과 이월" 라벨
- 하단: SLO 기준(정상 구간 연결 거부 없음), overflow_probe 429 비율 병기

**Scenario 07 — 상품 상세 조회 처리량**

- 상단: "Scenario 07. 상품 상세 조회 처리량" + 목적(300 RPS constant-arrival-rate)
- 중앙: Baseline 칸 "[캡처 없음]" → Tuned → Final 순서 배치 / RealFinal 칸에 "Final 결과 이월" 라벨
- 하단: SLO 기준(P95 < 120ms, 300 RPS 달성), Redis 캐시 적용 효과 수치(133ms → 16ms)

**Scenario 06 — 통합 워크로드 모델**

- 상단: "Scenario 06. 통합 워크로드 — warm cache 기준 최종 안정성 확인"
- 중앙: RealFinal 단일 캡처 1장만 배치 (Baseline/Tuned/Final 칸 없음 또는 "해당 없음" 라벨)
- 하단: s01·s02·s03 성공 이후 통합 측정임을 명시, 주요 SLO 달성 수치 기재

---

## 14. PPT 슬라이드 구성안

| 슬라이드 | 제목 | 핵심 내용 | 예상 시간 |
|---|---|---|---|
| 1 | 표지 | FANDROPS 프로젝트 개요 | — |
| 2 | AWS 인프라 설계 요약 | 7개 구성요소 + 예산 | 1분 |
| 3 | 최종 아키텍처 다이어그램 | 다이어그램 + 인스턴스 표 | 1분 |
| 4 | 설계 결정 근거 (1/2) | 단일 EC2 t3.medium, Blue/Green, SSM | 1분 30초 |
| 5 | 설계 결정 근거 (2/2) | HikariCP, Rate Limit 4 Zone, k6 EC2-2 분리 | 1분 |
| 6 | k6 테스트 환경 | 환경 요약 + 수치 집계 기준 | 30초 |
| 7 | SLO 기준표 + 완화 이력 | 7개 SLO + 완화 2건 | 1분 |
| 8 | 전체 개선 타임라인 | Baseline→Tuned→Final→RealFinal | 30초 |
| 9 | Scenario 01 주문 동시성 | 4단계 표 + atomic update | 1분 30초 |
| 10 | Scenario 02 피드 Read | 4단계 표 + FeedLikeCache | 1분 30초 |
| 11 | Scenario 03 결제 확인 | 4단계 표 + TX 분리 | 1분 |
| 12 | Scenario 04 드롭스 스파이크 | 4단계 표 + Rate Limit | 1분 |
| 13 | Scenario 05 SSE 대기열 (1/2) | 4단계 + 17차 핵심 문제 | 1분 30초 |
| 14 | Scenario 05 SSE 대기열 (2/2) | 2단계 재설계 (capacity_fill + overflow_probe) | 1분 |
| 15 | Scenario 07 상품 조회 처리량 | 4단계 표 + 포트 정정 스토리 | 1분 |
| 16 | Scenario 06 통합 워크로드 | RealFinal 단일 측정 결과 | 1분 |
| 17 | 단계별 비교표 | Section 8 표 (P95 + 정합성) | 1분 |
| 18 | SLO 최종 판정 종합 | 전 시나리오 ✅ | 30초 |
| 19 | SLO 타협과 타당성 | 2건 완화 근거 | 1분 |
| 20 | 트러블슈팅 핵심 사례 | 5건 요약 | 2분 |
| 21 | 추후 개선사항 | 분산 설계 검증 포함 | 1분 |
| 22 | Q&A | — | — |

**총 예상 발표 시간**: 약 22~25분

---

## 15. 팀원 전달 최종 요약

**지영재 파트 (SRE / CI/CD) — 최종 요약**

FANDROPS 프로젝트에서 AWS 인프라 설계와 k6 부하테스트 환경 전반을 담당했습니다.

인프라 측면에서는 월 72,000원 예산 안에서 ALB 없이 Nginx Blue/Green 배포를 구현해 무중단 배포를 실현했고, SSM Session Manager로 SSH 키페어 없는 보안 운영 체계를 갖췄습니다. EC2-1을 t3.small에서 t3.medium으로 스케일업해 OOM 문제를 해소했으며, k6 전용 EC2-2를 분리해 측정값 오염 없이 정확한 SLO 수치를 확보했습니다.

부하테스트 측면에서는 s01~s07 전 시나리오에 걸쳐 Baseline → Tuned → Final → RealFinal 4단계 측정 체계를 설계·운영했습니다. s05 SSE 대기열은 17차례 트러블슈팅 끝에 2,000 동시 연결 SLO를 달성했습니다. s01 주문 동시성은 낙관적 락 도입 후 정합성이 무너진 상황을 atomic update 복귀로 복원했습니다. s02 피드 Read는 N+1 제거·Redis 캐시·FeedLikeCache 3단계 최적화로 133ms에서 66ms로 개선했습니다.

최종적으로 s01~s07 전 시나리오의 SLO를 달성했습니다. SLO 완화 2건(s01 P95 기준 폐기, s03 결제 2,000ms 완화)은 모두 팀 합의를 거친 결정입니다.

단일 EC2 구조의 분산 한계(SseEmitterRegistry in-memory, SSE Redis Pub/Sub 미구현)는 현재 예산과 개발 기간 내 수용된 제약으로, 프로덕션 전환 시 반드시 해소해야 할 사항으로 문서화해 두었습니다.

---

*출처 문서: `docs/operations/k6/k6-baseline-results.md`, `k6-tuned-results.md`, `k6-final-results.md`, `k6-realfinal-result.md`, `k6-s05-sse-queue-troubleshooting.md`, `docs/operations/aws/current-infra-state.md`, `docs/operations/aws/nginx-bluegreen-strategy.md`, `docs/operations/observability-metrics.md`*
