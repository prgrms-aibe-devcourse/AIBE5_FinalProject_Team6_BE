# k6 부하 테스트 실행 계획

**작성일:** 2026-06-11  
**작성자:** 지영재  
**상태:** 내일(2026-06-12) 실행 예정

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

### 추가 발견된 버그 (local 프로파일 전환 시에도 해결 필요)

`LocalAccessTicketRepository.isValid()` 버그:
- `setup()`에서 fanId=1로 accessToken 발급 → store에 key `"1:1"`만 저장
- default 함수에서 VU=2번은 `X-Fan-Id: 2`로 요청 → `isValid(token, fanId=2, productId=1)` → key `"1:2"` 조회 → **null → false → 400**
- 결과: fanId=1인 VU 1번만 통과, 나머지 199개 VU 전부 실패

---

## 2. 결정된 방향

**옵션 A: EC2를 local 프로파일로 임시 전환 + LocalAccessTicketRepository 수정**

### 선택 이유
- 옵션 B(JWT 1개 공유)로는 시나리오 05(SSE)에서 `SseEmitterRegistry` key가 `"productId:fanId"`라 동일 fanId로 연결 시 emitter가 덮어씌워져 실제 연결 수가 1이 됨 → 테스트 무효
- 시나리오 06(통합 워크로드)에서 단일 JWT는 모든 행위가 1명에 의해 발생하는 비현실적 상황
- JWT 방식으로 시나리오 05를 해결하려면 계정 2,100개 필요 → 현실 불가
- local 프로파일 보안 설정이 prod와 달라도 **재고 동시성·SSE 연결·결제 처리 성능 자체는 동일하게 측정됨**

---

## 3. 실행 순서

### Step 0. 장성재 님 — LocalAccessTicketRepository 수정 (선행 필요)

`modules/payment/payment-infrastructure/src/main/java/com/fandrops/payment/infrastructure/queue/LocalAccessTicketRepository.java`

```java
// 수정 전
@Override
public boolean isValid(String token, Long fanId, Long productId) {
    String key = storeKey(fanId, productId);
    TokenEntry entry = store.get(key);
    if (entry == null) return false;
    if (!Instant.now().isBefore(entry.expiresAt)) {
        store.remove(key);
        return false;
    }
    return entry.token.equals(token);
}

// 수정 후
@Override
public boolean isValid(String token, Long fanId, Long productId) {
    // 로컬 부하 테스트: setup()에서 fanId=1로 발급한 토큰을 여러 VU가 공유하므로
    // fanId 매칭 대신 유효한 토큰 존재 여부만 검증
    return store.values().stream()
            .anyMatch(entry -> entry.token.equals(token) && Instant.now().isBefore(entry.expiresAt));
}
```

> `@Profile("local")` 전용 클래스 — prod 코드에 영향 없음

---

### Step 1. EC2 local 프로파일로 전환 (SSM)

```bash
# active 슬롯 확인
ACTIVE=$(cat /etc/fandrops/active-slot)

# 서비스 파일 수정
sudo sed -i 's/profiles.active=prod/profiles.active=local/' /etc/systemd/system/fandrops-$ACTIVE.service
sudo systemctl daemon-reload
sudo systemctl restart fandrops-$ACTIVE

# 헬스체크
curl http://localhost:8081/actuator/health
```

---

### Step 2. DB seed 실행

```bash
cd /opt/fandrops/k6
mysql -u fandrops -p<password> -h <RDS_ENDPOINT> fandrops <<'SQL'
SET SESSION cte_max_recursion_depth=5000;
SQL
mysql -u fandrops -p<password> -h <RDS_ENDPOINT> fandrops < seed/seed.sql
```

---

### Step 3. 시나리오 실행 순서

```bash
cd /opt/fandrops/k6

# 02. 피드 조회 (가장 단순 — 먼저 서버 정상 확인)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e FAN_POOL_SIZE=2100 \
  scenarios/02_feed_read.js

# 01. 주문 동시성 (오버셀 0건 핵심)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e FAN_POOL_SIZE=2100 \
  scenarios/01_order_concurrency.js

# 03. 결제 확인 (Wiremock 기동 확인 후)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e ORDERS_JSON="$(cat seed/orders.json)" \
  scenarios/03_payment_confirm.js

# 04. 드롭스 스파이크
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e FAN_POOL_SIZE=2100 \
  scenarios/04_drop_spike.js

# 05. SSE 대기열 (2100 VU)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  scenarios/05_sse_queue.js

# 06. 통합 워크로드 (마지막)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e FAN_POOL_SIZE=2100 \
  scenarios/06_workload_model.js
```

---

### Step 4. Baseline 수치 기록

Grafana 대시보드(`api.fandrops.site:3000`)에서 확인 후 아래 표 채우기.

| 시나리오 | Write/Read P95 | 5xx 에러율 | 오버셀 건수 | SLO 통과 |
|---------|--------------|----------|-----------|---------|
| 01 주문 동시성 | ms | % | 건 | ✅/❌ |
| 02 피드 조회 | ms | % | — | ✅/❌ |
| 03 결제 확인 | ms | % | — | ✅/❌ |
| 04 드롭스 스파이크 | ms | % | 건 | ✅/❌ |
| 05 SSE 대기열 | — | % | — | ✅/❌ |
| 06 통합 워크로드 | ms | % | 건 | ✅/❌ |

---

### Step 5. EC2 prod 프로파일 원복

```bash
ACTIVE=$(cat /etc/fandrops/active-slot)
sudo sed -i 's/profiles.active=local/profiles.active=prod/' /etc/systemd/system/fandrops-$ACTIVE.service
sudo systemctl daemon-reload
sudo systemctl restart fandrops-$ACTIVE

# 원복 확인
curl http://localhost:8081/actuator/health
```

---

## 4. 담당자별 전달 사항

| 담당자 | 내용 | 긴급도 |
|--------|------|--------|
| 장성재 | `LocalAccessTicketRepository.isValid()` 수정 (Step 0 참고) | 🔴 선행 필요 |
| 형성빈 | 시나리오 01/04 실행 전 `POST /api/v1/orders` + queue 흐름 EC2 정상 동작 여부 확인 | 🟡 |
| 장성재 | 시나리오 03 실행 전 Wiremock 8090 포트 정상 동작 확인 (이미 설정 완료) | 🟢 |
