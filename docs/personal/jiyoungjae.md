# 지영재 — 기술 기여 및 트러블슈팅 기록

> FANDROPS 프로젝트 (2026.05 ~ 2026.06)  
> 담당: CI/CD 파이프라인, 부하 테스트 인프라, 인프라 운영  
> 스택: Spring Boot, AWS EC2/SSM/S3, Nginx, k6, Prometheus, GitHub Actions

---

## 주요 기여 요약

| 영역 | 기여 내용 |
|---|---|
| CI/CD | Blue-Green 무중단 배포 구축 및 배포 스크립트 버그 수정 |
| 부하 테스트 | k6 + Prometheus 기반 SLO 베이스라인 측정 체계 구축 및 전 시나리오 운영 |
| 장애 진단 | SSM stdout 제한 우회, Prometheus stale series 해결, SSE stale emitter 원인 분석 |
| 스크립트 수정 | s06 통합 워크로드 check 조건 버그 수정 (rate limit 정상 응답 오집계 제거) |

---

## 기술 기여 상세

---

### 1. Blue-Green 무중단 배포 — 배포 스크립트 버그 수정

**상황**

k6 s03 결제 시나리오 베이스라인 측정을 위해 EC2에 새 JAR를 배포했으나, 전체 요청에서 401 Unauthorized가 발생했다. 최신 코드가 서버에 반영되지 않은 것으로 의심됐다.

**원인 분석**

Blue-Green 배포 스크립트(`bluegreen-deploy.sh`)에서 새 슬롯을 기동할 때 `systemctl start`를 사용하고 있었다. `start` 명령은 **이미 실행 중인 서비스에 no-op**으로 동작하기 때문에, 새 JAR를 복사했음에도 구 프로세스가 그대로 실행되고 있었다.

```bash
# 변경 전 — 이미 실행 중이면 새 JAR가 로드되지 않음
systemctl start "fandrops-$NEW_SLOT"

# 변경 후 — 항상 프로세스를 재시작하여 새 JAR 로드 보장
systemctl restart "fandrops-$NEW_SLOT"
```

**결과**

- 수정 후 배포 시 신규 JAR가 정상 로드됨을 확인
- s03 재실행 시 P95 1,540ms, 에러율 0.00% — SLO 달성 ✅
- 이후 전 시나리오 배포 신뢰성 확보

**배운 점**

Blue-Green 배포에서 슬롯이 이미 기동 중인 상황을 항상 전제해야 한다. `start` 대신 `restart`를 쓰거나, 배포 전에 명시적으로 `stop` → `start`를 분리하는 방어적 설계가 필요하다.

---

### 2. k6 부하 테스트 인프라 운영 — SSM 한계 극복

**상황**

EC2-2에서 k6를 실행하고 결과를 수집하는 과정에서 AWS SSM `send-command`의 stdout 출력이 24KB에서 절단되어 6분 이상 실행되는 k6 결과를 받지 못하는 문제가 발생했다.

**해결 접근**

직접 stdout을 수집하는 대신, k6 실행 중 **Prometheus remote write**로 메트릭을 EC2-1에 실시간 전송하도록 구성한 뒤, 실행 완료 후 Prometheus API로 사후 조회하는 방식으로 전환했다.

이 과정에서 두 번째 문제가 발생했다. k6 종료 후 Prometheus instant query가 빈 결과를 반환했다. k6가 종료되면 Prometheus가 해당 시계열을 **stale로 마킹**하여 instant query에서 반환하지 않기 때문이었다.

```bash
# instant query — stale series로 인해 빈 결과
GET /api/v1/query?query=k6_http_req_duration_p95

# range query + max_over_time으로 해결 — 과거 2시간 내 peak 값 조회
GET /api/v1/query?query=max_over_time(k6_http_req_duration_p95[2h])
```

**결과**

- SSM stdout 제한과 무관하게 전 시나리오의 P95, 에러율, RPS 등 핵심 지표를 안정적으로 수집
- s03~s06 베이스라인 수치 전체 기록 완료

**배운 점**

k6 결과 수집에는 stdout보다 Prometheus remote write가 훨씬 안정적이다. 또한 Prometheus의 staleness 동작을 이해하지 못하면 "메트릭이 없다"는 잘못된 결론을 내릴 수 있다. 시계열 DB의 쿼리 특성을 파악하는 것이 관측 가능성 구현에서 중요하다.

---

### 3. SSE 대기열 429 원인 분석 — 계층별 가설 검증

**상황**

s05 SSE 대기열 시나리오에서 1,000 VU 정상 구간에서도 에러율이 100%로 나타났다. `GET /api/v1/queue/stream/{productId}` 요청의 83%가 429 응답을 받았고, Nginx 설정 문제라는 가설이 있었다.

**원인 분석 — 계층별 검증**

인프라 레이어부터 애플리케이션 레이어까지 순서대로 가설을 검증했다.

**① Nginx·OS 설정 (AWS SSM으로 EC2-1 직접 조회)**

```bash
grep -r 'worker_connections' /etc/nginx/   # → 4,096
ulimit -n                                   # → 65,535
```

| 항목 | 실측값 | 판정 |
|---|---|---|
| Nginx `worker_connections` | 4,096 | ✅ 무관 |
| OS `ulimit -n` | 65,535 | ✅ 무관 |
| Nginx master FD limit | 65,535 | ✅ 무관 |

**② 앱 레벨 RateLimitFilter 코드 분석**

`RateLimitFilter.resolveGroup()`이 POST 요청에만 적용되고 GET은 즉시 통과함을 확인 → `GET /queue/stream`은 이 필터와 무관.

**③ SseEmitterRegistry 분석 — 실제 원인 확인**

```java
public synchronized SseEmitter registerOrReject(Long productId, Long fanId) {
    if (emitters.size() >= sseMaxEmitters) {  // 기본값 2,000
        throw new SseCapacityExceededException();  // → 429
    }
    return register(productId, fanId);
}

private SseEmitter register(Long productId, Long fanId) {
    SseEmitter emitter = new SseEmitter(sseTimeoutMs);  // 60초 타임아웃
    emitters.put(key, emitter);
    emitter.onCompletion(() -> emitters.remove(key, emitter));
    emitter.onTimeout(() -> emitters.remove(key, emitter));
    emitter.onError(e -> emitters.remove(key, emitter));
    return emitter;
}
```

**근본 원인**: k6는 SSE를 브라우저처럼 연결을 유지하지 않는다. 첫 청크(초기 `waiting` 이벤트)를 수신한 직후 루프를 돌아 반복 연결한다. 반면 Spring은 클라이언트 disconnect를 소켓에 write를 시도할 때만 감지할 수 있으므로, 연결이 끊긴 emitter가 `onTimeout`(60초)까지 map에 잔류한다. 이로 인해 stale emitter가 빠르게 누적되어 2,000 한도를 초과한다.

**해결 방향 제안**

Heartbeat 이벤트(SSE 업계 표준 패턴): 5초 주기로 SSE comment를 전송하면, 클라이언트가 이미 끊긴 경우 write 시 `IOException`이 발생하여 즉시 emitter를 정리할 수 있다.

```java
public void sendHeartbeat() {
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
        try {
            entry.getValue().send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(entry.getKey());
        }
    }
}
```

이 방식은 실서비스에서도 연결 유지와 disconnect 감지를 동시에 해결하는 표준 접근이다. `IllegalStateException`은 Spring이 이미 완료 처리한 emitter에 write할 때 발생하므로 반드시 함께 catch해야 한다.

**결과**

- Nginx·OS가 원인이 아님을 데이터로 확인
- 앱 레벨 stale emitter 누적이 실제 원인임을 코드 분석으로 특정
- 해결 방향과 구현 코드를 담당 팀원에게 전달하여 수정 예정

**배운 점**

SSE는 HTTP long-polling과 달리 서버가 disconnect를 능동적으로 감지하지 못한다. 프로덕션 SSE 구현에서는 heartbeat가 필수적이며, 부하 테스트 도구가 SSE를 실제 클라이언트처럼 동작하지 않는다는 점도 고려해야 한다. 또한 "숫자가 이상하다"는 현상에서 바로 코드를 보지 않고 인프라 → 미들웨어 → 애플리케이션 순서로 계층별 가설 검증을 했던 것이 원인을 효율적으로 좁히는 데 도움이 됐다.

---

### 4. k6 시나리오 버그 수정 — rate limit 정상 응답 오집계

**상황**

s06 통합 워크로드 시나리오에서 rate limiter가 반환하는 429를 check 실패로 집계하고 있었다. rate limit은 정상 동작이므로 429는 성공으로 처리해야 한다.

**수정 내용**

```javascript
// 변경 전
check(res, { '[payment] confirm accepted': (r) => r.status === 200 || r.status === 201 });
check(res, { '[queue] join accepted': (r) => r.status === 200 || r.status === 201 || r.status === 409 });

// 변경 후
check(res, { '[payment] confirm accepted': (r) => r.status === 200 || r.status === 201 || r.status === 429 });
check(res, { '[queue] join accepted': (r) => r.status === 200 || r.status === 201 || r.status === 409 || r.status === 429 });
```

**결과**

rate limit 응답이 check 실패로 집계되지 않아 s06 재측정 시 실제 시스템 에러와 정상 rate limit 응답을 구분할 수 있게 됐다.

---

### 5. k6 s01 100% 실패 원인 분석 — product_id 불일치 + 활성 슬롯 오지정

**상황**

k6 s01 주문 동시성 시나리오를 EC2-2에서 실행했을 때 `http_req_failed: 100.00%`, `orders_reserved: 0`으로 모든 요청이 실패했다. Redis 티켓과 inventory는 정상이었고, 포트를 blue(8081) → green(8082)으로 바꿔도 동일하게 실패했다.

**원인 분석 — 계층별 검증**

**① 활성 슬롯 확인 (AWS SSM)**

```bash
aws ec2 describe-instances --filters "Name=private-ip-address,Values=10.0.1.114" \
  --query "Reservations[0].Instances[0].InstanceId" --output text
# → i-07d1c60d175cdb8ca

aws ssm send-command ... "sudo cat /etc/fandrops/active-slot"
# → green (8082)
```

| 항목 | 실측값 | 판정 |
|---|---|---|
| 활성 슬롯 | green (8082) | ⚠️ 사용자가 8081(blue)로 실행 — 구버전 JAR |
| Redis `access:ticket:1:1` | EXISTS=1 | ✅ 티켓 존재 |
| inventory available_qty | 100, reserved_qty=0 | ✅ 정상 |

8082로 재실행했지만 동일하게 100% 실패 → 포트가 유일한 원인이 아님.

**② EC2-1 직접 API 호출 (Python via SSM)**

SSM curl 이스케이핑이 복잡하여 Python urllib 방식으로 전환:

```python
# /tmp 경유 Python heredoc 실행
import urllib.request, json
token = open('/opt/fandrops/k6/seed/tokens.csv').readlines()[50].strip().split(',')[1]
body = json.dumps({'accessTicket':'test-ticket-token','items':[{'productId':1,'quantity':1}]}).encode()
req = urllib.request.Request('http://localhost:8082/api/v1/orders', ...)
# → STATUS: 404
# → {"code":"PRODUCT_NOT_FOUND","message":"상품을 찾을 수 없습니다: productId=1"}
```

**③ product 테이블 확인**

```sql
SELECT id, status FROM product ORDER BY id LIMIT 10;
-- id: 4, 5, 7, 8, 9, 10, 12 ...  (id=1 없음)
```

**근본 원인**: DB 재시드 후 product 테이블 최소 id가 4부터 시작. k6 스크립트 기본값 `PRODUCT_ID=1`은 product 테이블에 존재하지 않아 주문 시 `PRODUCT_NOT_FOUND(404)` 반환 → 전체 400건 실패. inventory의 `product_id=1` 레코드는 orphan 상태.

**해결 방법**

```bash
# ON_SALE + inventory 존재 product 확인
SELECT p.id, i.available_qty FROM product p
  JOIN inventory i ON p.id = i.product_id
  WHERE p.status = 'ON_SALE' ORDER BY p.id LIMIT 5;
# → product_id=4 (available_qty=100) 확인

# Redis 재시드 (product_id=4)
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:4:$i" 86400 "test-ticket-token"
done

# k6 실행 시 PRODUCT_ID=4 명시
k6 run -e BASE_URL=http://10.0.1.114:8082 -e PRODUCT_ID=4 -e FAN_POOL_SIZE=200 ...
```

**배운 점**

1. k6 스크립트의 `PRODUCT_ID` 기본값(1)이 실제 DB 데이터와 일치한다는 전제를 항상 검증해야 한다. 재시드 후 id auto_increment가 달라지면 모든 k6 시나리오가 조용히 실패한다.
2. SSM으로 복잡한 curl 명령을 보낼 때는 `--cli-input-json file://`(JSON 파일) + Python urllib 방식이 shell 이스케이핑 문제를 원천 차단한다.
3. 활성 슬롯 확인은 s01·s07 등 Spring Boot 직접 연결 시나리오 실행 전 필수 체크 항목이다. (`sudo cat /etc/fandrops/active-slot`)

---

## 기술 스택 키워드

`AWS EC2` `AWS SSM` `AWS S3` `Nginx` `Blue-Green 배포` `GitHub Actions`  
`k6` `Prometheus` `Grafana` `부하 테스트` `SLO 측정`  
`Spring Boot` `Spring SSE (SseEmitter)` `async servlet`  
`트러블슈팅` `성능 분석` `관측 가능성(Observability)`

---

## 재측정 전 체크리스트 (s05)

- [ ] 장성재: `SseEmitterRegistry.sendHeartbeat()` + 스케줄러 등록
- [ ] 장성재: 429 응답 body `"retryable": true` 추가
- [ ] 재측정 후 1,000 VU 정상 구간 에러율 < 0.1% 달성 확인
