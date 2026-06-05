# k6 부하 테스트

## 목차

1. [디렉터리 구조](#디렉터리-구조)
2. [사전 준비](#사전-준비)
3. [시나리오 설명](#시나리오-설명)
4. [실행 방법](#실행-방법)
5. [결과 해석](#결과-해석)
6. [트러블슈팅](#트러블슈팅)

---

## 디렉터리 구조

```
infra/k6/
├── lib/
│   ├── auth.js          # 인증 헬퍼 (login, localHeaders)
│   ├── sse.js           # 대기열 SSE accessToken 수신
│   └── thresholds.js    # SLO 기준값 (P95, 에러율)
├── scenarios/
│   ├── 01_order_concurrency.js  # 동시 주문 / 오버셀 방지 검증
│   ├── 02_feed_read.js          # 피드 조회 읽기 성능
│   ├── 03_payment_confirm.js    # 결제 확인 (Wiremock)
│   ├── 04_drop_spike.js         # 드롭 스파이크 (0→1000 VU)
│   └── 05_sse_queue.js          # SSE 동시 연결 한계
├── seed/
│   ├── seed.sql         # DB 테스트 데이터 삽입 스크립트
│   └── orders.json      # 시나리오 03 주문 픽스처
└── wiremock/
    └── stubs/
        └── toss-confirm.json  # 가짜 Toss PG 응답 규칙
```

---

## 사전 준비

### 1. k6 설치

```bash
# Windows
winget install k6 --source winget

# macOS
brew install k6

# 설치 확인
k6 version
```

### 2. 인프라 실행

프로젝트 루트에서 실행한다. MySQL, Redis, Wiremock(가짜 Toss PG)이 함께 뜬다.

```bash
docker compose up -d

# 정상 확인
docker compose ps
```

| 컨테이너 | 포트 | 역할 |
|---------|------|------|
| mysql | 3307 | 테스트 DB |
| redis | 6379 | 대기열·세션 |
| wiremock | 8089 | 가짜 Toss PG (시나리오 03용) |

### 3. 서버 설정 (`application-local.override.yml`)

`apps/api-server/src/main/resources/application-local.override.yml` 파일에 아래 항목이 있어야 한다.

```yaml
# k6 부하 테스트용 설정
fandrops:
  queue:
    max-concurrent-processing: 300   # 기본 10 → 200 VU 수용
    advance-batch-size: 300          # 기본 5  → 한 번에 300명 진입
    scheduler:
      interval-ms: 1000              # 기본 3000ms → 1초로 단축

toss:
  api:
    base-url: http://localhost:8089  # 실제 Toss 대신 Wiremock 사용
```

> **왜 이 설정이 필요한가?**
> - `max-concurrent-processing`: 기본값 10이면 200명 중 10명만 구매 가능 → 나머지 190명은 계속 대기
> - `advance-batch-size`: 기본값 5이면 200명 처리에 40 tick = 최소 2분 소요 → 타임아웃 발생
> - `scheduler.interval-ms`: 기본값 3초이면 accessToken 수신까지 최대 수십 초 → setup() 타임아웃
> - `toss.api.base-url`: 실제 Toss PG에 수천 건 테스트 요청 불가 → Wiremock으로 대체

### 4. 앱 서버 실행

`local` 프로필로 실행해야 `X-Fan-Id` 헤더 인증 우회가 활성화된다.

```bash
./gradlew :apps:api-server:bootRun --args="--spring.profiles.active=local"
```

IntelliJ 사용 시 Run Configuration → Active profiles: `local`

서버 기동 확인:
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"} 가 나와야 정상
```

### 5. DB seed 데이터 삽입

```bash
# 프로젝트 루트에서 실행
docker exec -i $(docker compose ps -q mysql) \
  mysql -u fandrops -pchange-me fandrops < infra/k6/seed/seed.sql
```

삽입 완료 시 아래와 같이 출력된다:

```
항목          값
fan 수        2100
inventory    1
artist_feed  20
user_follow  1
orders       50
order_item   50
```

| 테이블 | 데이터 | 사용 시나리오 |
|--------|--------|-------------|
| `fan` | id 1~2100 | 전체 (X-Fan-Id 헤더) |
| `inventory` | product_id=1, 재고 100개 | 01, 04 |
| `artist_feed` | artist_id=1 피드 20건 | 02 |
| `user_follow` | fan_id=1 → artist_id=1 | 02 |
| `orders` + `order_item` | RESERVED 주문 50건 | 03 |

seed.sql은 재실행 가능하다. 기존 데이터를 먼저 삭제 후 재삽입한다.

---

## 시나리오 설명

### 01. 주문 동시성 (`01_order_concurrency.js`)

**목적**: 재고 100개 상품에 200명이 동시 주문할 때 오버셀(재고 초과 판매)이 발생하지 않는지 검증한다.

**검증 기준 (Threshold)**:
| 항목 | 기준 |
|------|------|
| 응답시간 P95 | 300ms 이하 |
| 에러율 | 0.1% 미만 |
| `orders_reserved` | 100 이하 (재고 초과 = 오버셀 버그) |

**흐름**:
```
[setup — 딱 1번 실행]
  1. POST /api/v1/queue/join/1  → 대기열 등록
  2. GET  /api/v1/queue/stream/1 (SSE, 20초 타임아웃)
     → 스케줄러가 PROCESSING 전이 후 accessToken 반환

[default — 200 VU × 1회씩 총 200회]
  3. POST /api/v1/orders
     body: { accessTicket, items: [{productId:1, quantity:1}] }
     → 201: RESERVED (재고 차감 성공)
     → 409: CANCELLED (재고 소진)
```

**파라미터**:
| 환경변수 | 기본값 | 설명 |
|---------|--------|------|
| `PRODUCT_ID` | 1 | 테스트 대상 상품 ID |
| `FAN_ID` | 1 | 대기열 등록에 사용할 팬 ID |

**executor**: `shared-iterations` — 200 VU가 총 200 iteration을 나눠서 실행한다. 한 VU가 여러 번 실행되지 않는다.

**참고**: `accessTicket`은 서버에서 사용 후 무효화되지 않으므로 200 VU 전원이 같은 토큰을 공유한다.
재고 차감 동시성(낙관적 락)만 검증하는 시나리오다.

---

### 02. 피드 조회 (`02_feed_read.js`)

**목적**: 50명이 2분간 지속적으로 피드를 조회할 때 응답속도가 SLO를 만족하는지 확인한다.

**검증 기준 (Threshold)**:
| 항목 | 기준 |
|------|------|
| 응답시간 P95 | 120ms 이하 |
| 에러율 | 0.1% 미만 |

**흐름**:
```
[default — 50 VU × 2분 연속]
  GET /api/v1/artists/1/feeds
  → 200 OK + data.items 배열 존재 여부 확인
```

**파라미터**:
| 환경변수 | 기본값 | 설명 |
|---------|--------|------|
| `ARTIST_ID` | 1 | 조회할 아티스트 ID |
| `FAN_ID` | 1 | X-Fan-Id 헤더에 사용할 팬 ID |

**executor**: `constant-vus` — 50 VU가 2분 내내 쉬지 않고 요청을 보낸다.

---

### 03. 결제 확인 (`03_payment_confirm.js`)

**목적**: Toss PG 결제 확인 API가 동시 요청에서도 P95 3초 이내로 응답하는지 검증한다. 실제 Toss 서버 대신 Wiremock으로 모킹한다.

**검증 기준 (Threshold)**:
| 항목 | 기준 |
|------|------|
| 응답시간 P95 | 3,000ms 이하 |
| 에러율 | 1% 미만 |

**흐름**:
```
[default — 50 VU ramping]
  POST /api/v1/payments/toss/confirm
  body: { tossPaymentKey, orderId, amount }
  → 앱 서버가 Wiremock(localhost:8089)으로 Toss 확인 요청
  → Wiremock이 {"status":"DONE"} 반환
  → 앱 서버가 주문 상태를 PAID로 업데이트
```

**VU 스케줄 (ramping-vus)**:
```
0명 ──30초──▶ 50명 ──2분──▶ 50명 ──10초──▶ 0명
```

**파라미터**:
| 환경변수 | 기본값 | 설명 |
|---------|--------|------|
| `ORDERS_JSON` | `[{"orderId":1,"amount":15000}]` | RESERVED 주문 픽스처 JSON |

**멱등키**: 각 VU가 `load-test-{orderId}-{__ITER}` 형태로 고유한 `tossPaymentKey`를 생성하여 중복 결제를 방지한다.

**Wiremock 동작**:
- `POST /v1/payments/confirm` 요청이 오면 요청 body의 `paymentKey`, `orderId`, `amount`를 그대로 echoing하여 `{"status":"DONE"}` 응답 반환
- `compose.yaml`의 wiremock 서비스가 `--global-response-templating` 옵션으로 실행되어 이 동적 응답이 가능하다

---

### 04. 드롭 스파이크 (`04_drop_spike.js`)

**목적**: 아이돌 굿즈 드롭처럼 갑자기 1,000명이 몰리는 상황을 시뮬레이션한다. ~1,000 TPS 하에서도 오버셀이 발생하지 않는지 검증한다.

**검증 기준 (Threshold)**:
| 항목 | 기준 |
|------|------|
| 응답시간 P95 | 300ms 이하 |
| 에러율 | 0.1% 미만 |
| `spike_orders_reserved` | 100 이하 |

**흐름**: 01번과 동일하나 VU 수가 다르다.

**VU 스케줄 (ramping-vus)**:
```
0명 ──30초──▶ 1,000명 ──30초──▶ 1,000명 ──15초──▶ 0명
```

**executor**: `ramping-vus` — 시간에 따라 VU 수를 늘리고 줄인다. 급격한 트래픽 증가를 모방한다.

---

### 05. SSE 연결 한계 (`05_sse_queue.js`)

**목적**: SSE 대기열 스트림의 동시 연결 한계를 검증한다. 한계 초과 시 서버가 적절히 429를 반환하는지 확인한다.

**검증 기준 (Threshold)**:
| 구간 | 기준 |
|------|------|
| normal_load (1,000 VU) | 에러율 0.1% 미만 |
| boundary (1,800 VU) | 에러율 1% 미만 |
| overflow (2,100 VU) | 429 거부가 반드시 1건 이상 발생 |

**3단계 시나리오**:
```
[1단계: normal_load]  0명 →30s→ 1,000명 →1분→ 1,000명 →15s→ 0명
[2단계: boundary]     0명 →30s→ 1,800명 →1분→ 1,800명 →15s→ 0명  (2분 후 시작)
[3단계: overflow]     0명 →30s→ 2,100명 →30s→ 2,100명 →15s→ 0명  (4분 30초 후 시작)
```

**흐름**:
```
[default — 각 VU마다]
  GET /api/v1/queue/stream/{productId}  (SSE, 65초 타임아웃)
  → 200: 연결 수락 (sse_connections_accepted++)
  → 429: 연결 거부 (sse_connections_rejected++)
        + error.retryable == true 검증
```

**핵심 포인트**: overflow 구간에서 429가 발생해야 테스트가 통과다. 429가 안 나오면 서버가 한도를 넘어도 무한정 연결을 받고 있다는 뜻이므로 장애 위험이 있다.

**서버 사전 확인 사항**:
- Nginx `worker_connections` ≥ 2048
- JVM `ulimit -n` ≥ 8192
- `fandrops.ratelimit.sse-max-emitters` 설정값 확인 (`application.yml` 기본값: 2000)

---

## 실행 방법

모든 명령은 `infra/k6/` 디렉터리에서 실행한다.

```bash
cd infra/k6
```

### 기본 실행

```bash
# 01. 주문 동시성
k6 run scenarios/01_order_concurrency.js

# 02. 피드 조회 (가장 먼저 실행 권장)
k6 run scenarios/02_feed_read.js

# 03. 결제 확인
k6 run -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js

# 04. 드롭 스파이크
k6 run scenarios/04_drop_spike.js

# 05. SSE 연결 한계
k6 run scenarios/05_sse_queue.js
```

### 환경변수로 대상 서버 변경

```bash
# 스테이징 서버 대상 실행
k6 run -e BASE_URL=https://api-stg.fandrops.com scenarios/02_feed_read.js
```

### Grafana로 실시간 모니터링

Prometheus Remote Write 엔드포인트가 있는 경우:

```bash
k6 run \
  --out experimental-prometheus-rw \
  scenarios/01_order_concurrency.js
```

### 오버셀 결과 수동 확인 (시나리오 01, 04)

k6 종료 후 DB에서 직접 확인:

```sql
SELECT reserved_qty, available_qty FROM inventory WHERE product_id = 1;
-- reserved_qty > 100 이면 오버셀 발생
```

---

## 결과 해석

k6 실행 완료 후 터미널에 출력되는 요약 예시:

```
✓ status 200          100% 5000/5000
✓ has items           100% 5000/5000

http_req_duration............: avg=42ms  p(95)=98ms   ← 목표 120ms → 통과
http_req_failed..............: 0.00%                  ← 목표 0.1%  → 통과
orders_reserved..............: 100                    ← 목표 ≤100  → 통과

✓ thresholds: all passed
```

| 기호 | 의미 |
|------|------|
| `✓` | 모든 threshold 통과 |
| `✗` | 하나 이상 threshold 실패 |

threshold 실패 예시:
```
✗ http_req_duration..............: p(95)=342ms threshold p(95)<300 failed
```
→ 95번째 응답이 342ms로 목표 300ms 초과. 서버 병목 조사 필요.

---

## 트러블슈팅

| 오류 메시지 | 원인 | 해결 |
|------------|------|------|
| `ECONNREFUSED localhost:8080` | 앱 서버 미실행 | 서버 재시작 |
| `queue join failed: 429` | 대기열 rate limit 초과 | 잠시 후 재시도 |
| `accessToken 획득 실패` | 스케줄러 설정 미적용 또는 타임아웃 | override.yml 확인 후 서버 재시작 |
| `orders_reserved > 100` | 오버셀 버그 발생 | 인벤토리 낙관적 락 로직 점검 |
| `429 has retryable:true` check 실패 | SSE 과부하 응답에 retryable 필드 누락 | `SseEmitterRegistry` 429 응답 스펙 확인 |
| Wiremock 연결 실패 | `docker compose up -d` 미실행 | `docker compose ps` 로 wiremock 상태 확인 |
