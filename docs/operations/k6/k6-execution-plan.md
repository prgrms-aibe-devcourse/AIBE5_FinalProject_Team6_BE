# k6 부하 테스트 실행 계획

**작성일:** 2026-06-11
**최종 수정:** 2026-06-12
**작성자:** 지영재
**상태:** 2026-06-12 실행 예정

---

## 1. 배경 및 문제 분석

### 문제

EC2가 `--spring.profiles.active=prod`로 실행 중이라 k6 시나리오가 `X-Fan-Id` 헤더 방식(local 전용)으로 작성되어 있어 대부분 엔드포인트에서 401이 반환되는 상황.

`aws-phase4-runbook.md` 섹션 3-3 실행 명령어에 인증 처리 방법이 명시되어 있지 않아 설계 시점에 누락된 것으로 판단.

### 시나리오별 EC2(prod) 실행 가능 여부

| # | 시나리오 | 엔드포인트 | prod 상태 | 이유 |
|---|---------|-----------|---------|------|
| 01 | 주문 동시성 | POST /api/v1/orders + queue | ❌ 401 | JWT 없음 |
| 02 | 피드 조회 | GET /api/v1/artists/{id}/feeds | ✅ 가능 | permitAll |
| 03 | 결제 confirm | POST /api/v1/payments/toss/confirm | ❌ 401 | JWT 없음 |
| 04 | 드롭스 스파이크 | POST /api/v1/orders | ❌ 401 | JWT 없음 |
| 05 | SSE 대기열 | GET /api/v1/queue/stream/{productId} | ❌ 401 | JWT 없음 |
| 06 | 통합 워크로드 | 혼합 | ❌ 401 | JWT 없음 |

### 추가 발견된 문제

`LocalAccessTicketRepository.isValid()` 버그 (local 프로파일 전환 시에도 발생):
- `setup()`에서 fanId=1로 accessToken 발급 → store에 key `"1:1"`만 저장
- default 함수에서 VU=2번은 `X-Fan-Id: 2`로 요청 → `isValid(token, fanId=2, productId=1)` → key `"1:2"` 조회 → **null → false → 400**
- 결과: fanId=1인 VU 1번만 통과, 나머지 199개 VU 전부 실패

---

## 2. 결정된 방향

**최종안: JWT 사전 생성(CSV) + Redis AccessTicket 사전 적재 (운영 코드 수정 0건)**

### 옵션 A(local 프로파일 전환) 기각 이유

| 제약 | 설명 |
|------|------|
| 분산 검증 불가 | local 프로파일에서 AccessTicket이 메모리(`ConcurrentHashMap`)에 저장 → EC2 2대 분산 환경에서 세션 공유 불가 |
| Redis 병목 미측정 | 대기열·티켓 검증이 메모리 처리 → Redis 커넥션 풀 고갈 및 지연 실측 불가 |
| JWT 검증 부하 누락 | local 프로파일에서 JWT 검증 필터 비활성화 → 실제 CPU 오버헤드 미측정 |
| 선행 작업 발생 | LocalAccessTicketRepository 버그 수정(장성재)이 먼저 필요 |

### 최종안 선택 이유

- **운영 코드 수정 0건:** 보안 백도어 위험 없음, prod 프로파일 유지
- **실제 환경 동일:** JWT 검증·Redis·Rate Limit 필터 포함 → 더 정확한 부하 측정
- **EC2 2대 분산 검증 가능:** Redis가 공유 저장소 → 분산 환경 정합성 검증
- **setup() 대기열 플로우 제거:** Redis에 티켓 직접 적재 → k6 시나리오 단순화

---

## 3. 사전 준비

### Step 0. k6 스크립트 수정 (Bearer JWT + Redis 티켓 방식)

`X-Fan-Id` 헤더 방식 → Bearer JWT + 고정 accessTicket 방식으로 전환.

**수정 대상 파일:**

| 파일 | 변경 내용 |
|------|---------|
| `infra/k6/lib/auth.js` | `localHeaders()` 제거, CSV 로드 + `authHeaders(token)` 방식 추가 |
| `infra/k6/lib/sse.js` | `waitForAccessToken()` Bearer token 방식으로 수정 |
| `infra/k6/scenarios/01_order_concurrency.js` | setup() 대기열 플로우 제거, CSV 토큰 + 고정 accessTicket 주입 |
| `infra/k6/scenarios/03_payment_confirm.js` | CSV 토큰 주입 |
| `infra/k6/scenarios/04_drop_spike.js` | setup() 대기열 플로우 제거, CSV 토큰 + 고정 accessTicket 주입 |
| `infra/k6/scenarios/05_sse_queue.js` | `X-Fan-Id` → Bearer token 방식으로 교체 |
| `infra/k6/scenarios/06_workload_model.js` | CSV 토큰 주입 |

**k6 스크립트 변경 패턴 (01/04 기준):**

```javascript
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

// setup() 제거 — Redis 사전 적재로 대체

export default function () {
  const token = userTokens[__VU - 1].token;
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
  http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: 'test-ticket-token', items: [{ productId: 1, quantity: 1 }] }),
    { headers },
  );
}
```

---

### Step 1. JWT 사전 생성 (로컬)

`JWT_SECRET` 환경변수를 주입받아 fan_id 1~2100 토큰 생성. 출력 경로: `infra/k6/seed/tokens.csv`

> `tokens.csv`는 `.gitignore` 등록 완료 — 유효한 JWT 2100개이므로 git 노출 금지.

```java
// 임시 JUnit 테스트 (실행 후 삭제)
@Test
void generateTokensForK6() throws Exception {
    String base64Secret = System.getenv("JWT_SECRET"); // 하드코딩 금지
    SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    long expireMillis = 86400L * 1000 * 7; // 7일 유효

    try (PrintWriter writer = new PrintWriter(new FileWriter("infra/k6/seed/tokens.csv"))) {
        writer.println("fanId,token");
        for (int i = 1; i <= 2100; i++) {
            String token = Jwts.builder()
                .subject(String.valueOf(i))
                .claim("role", "FAN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMillis))
                .signWith(secretKey)
                .compact();
            writer.println(i + "," + token);
        }
    }
}
```

---

### Step 2. tokens.csv S3 업로드 → EC2 다운로드

SSH Key Pair 없음 — 기존 JAR 배포와 동일하게 S3 경유.

```bash
# 로컬 — S3 업로드
aws s3 cp infra/k6/seed/tokens.csv s3://<버킷명>/k6/tokens.csv

# EC2 SSM 세션 — 다운로드
aws s3 cp s3://<버킷명>/k6/tokens.csv /opt/fandrops/k6/seed/tokens.csv
```

---

### Step 3. DB seed 실행

```bash
cd /opt/fandrops/k6
mysql -u fandrops -p<password> -h <RDS_ENDPOINT> fandrops <<'SQL'
SET SESSION cte_max_recursion_depth=5000;
SQL
mysql -u fandrops -p<password> -h <RDS_ENDPOINT> fandrops < seed/seed.sql
```

---

### Step 4. Redis AccessTicket 사전 적재

Redis key 구조: `access:ticket:{productId}:{fanId}` (`RedisAccessTicketRepository` 기준)

```bash
# EC2 SSM 세션 — productId=1, fan_id 1~2100 일괄 적재 (TTL 86400초)
for i in {1..2100}; do
  redis-cli -h <REDIS_ENDPOINT> setex "access:ticket:1:$i" 86400 "test-ticket-token"
done

# 적재 확인
redis-cli -h <REDIS_ENDPOINT> get "access:ticket:1:1"    # → "test-ticket-token"
redis-cli -h <REDIS_ENDPOINT> get "access:ticket:1:2100" # → "test-ticket-token"
```

> **주의:** 주문 성공 시 `invalidate(fanId, productId)` 호출로 해당 fanId 티켓이 삭제됨.
> 시나리오 01은 `shared-iterations`(VU당 1회)이므로 문제없음.
> 시나리오 04(`ramping-vus`, 반복 실행)는 **01 실행 후 Redis 재적재 필요** (아래 Step 5 참고).

---

## 4. 시나리오 실행 순서

```bash
cd /opt/fandrops/k6

# 02. 피드 조회 (가장 단순 — 먼저 서버 정상 확인)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  scenarios/02_feed_read.js

# 01. 주문 동시성 (오버셀 0건 핵심)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  scenarios/01_order_concurrency.js

# 01 완료 후 Redis 재적재 (04 실행 전 — 01에서 성공한 fanId 티켓 복원)
for i in {1..2100}; do
  redis-cli -h <REDIS_ENDPOINT> setex "access:ticket:1:$i" 86400 "test-ticket-token"
done

# 04. 드롭스 스파이크
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  scenarios/04_drop_spike.js

# 03. 결제 확인 (Wiremock 8090 포트 기동 확인 후)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e ORDERS_JSON="$(cat seed/orders.json)" \
  scenarios/03_payment_confirm.js

# 05. SSE 대기열 (2100 VU)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  scenarios/05_sse_queue.js

# 06. 통합 워크로드 (마지막)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  scenarios/06_workload_model.js
```

---

## 5. Baseline 수치 기록

Grafana 대시보드(`api.fandrops.site:3000`)에서 확인 후 아래 표 채우기.

| 시나리오 | P95 (성공 기준) | 에러율 | 오버셀 건수 | SLO 통과 | 비고 |
|---------|--------------|--------|-----------|---------|------|
| 01 주문 동시성 | 975ms (성공) / 2.85s (전체) | 75%\* | **0건** | ❌ P95 초과 | 2026-06-15 2회차 공식 |
| 02 피드 조회 | **231ms** | 0.00% | — | ❌ (목표 120ms) | 2026-06-12 4회차 공식 |
| 03 결제 확인 | 119.32ms (성공) | 99.62% | — | ❌ 코드 버그 | [#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318) 수정 후 재측정 |
| 04 드롭스 스파이크 | 508ms (성공) / 12.34s (전체) | 99.16%\*\* | **0건** | ❌ P95 초과 | 2026-06-17 1회차 공식 |
| 05 SSE 대기열 | — | — | — | ❌ OOM 크래시 | k6 분리 실행 후 재측정 필요 |
| 06 통합 워크로드 | 2.48s (성공) | 99.43% | — | ❌ 과부하+버그 | k6 분리 + #318 수정 후 재측정 |

> \* 01 에러율 75%: 200건 201 RESERVED + 300건 409 DEPLETED(재고 소진 정상). 오버셀 없음.  
> \*\* 04 에러율 99.16%: 100건 201 RESERVED + 8,433건 409 DEPLETED(정상) + 3,375건 기타. 오버셀 없음.

---

## 6. 테스트 완료 후 정리

```bash
# S3 tokens.csv 삭제 (유효한 JWT 2100개 — 테스트 완료 후 즉시 삭제)
aws s3 rm s3://<버킷명>/k6/tokens.csv

# Redis AccessTicket 테스트용 키 삭제
for i in {1..2100}; do
  redis-cli -h <REDIS_ENDPOINT> del "access:ticket:1:$i"
done
```

---

## 7. 담당자별 전달 사항

| 담당자 | 내용 | 긴급도 |
|--------|------|--------|
| 지영재 | Step 0: k6 스크립트 전면 수정 (Bearer JWT + Redis 티켓 방식) | 🔴 선행 필요 |
| 지영재 | Step 1~2: JwtGeneratorTest 실행 → tokens.csv 생성 → S3 업로드 → EC2 다운로드 | 🔴 선행 필요 |
| 지영재 | Step 4: Redis AccessTicket 사전 적재 (실행 직전) | 🔴 실행 전 필요 |
| 형성빈 | 시나리오 01/04 실행 전 `POST /api/v1/orders` + queue 흐름 EC2 정상 동작 여부 확인 | 🟡 |
| 장성재 | 시나리오 03 실행 전 Wiremock 8090 포트 정상 동작 확인 (이미 설정 완료) | 🟢 |