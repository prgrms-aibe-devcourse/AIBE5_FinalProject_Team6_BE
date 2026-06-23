# 시나리오 05 트러블슈팅 기록 — SSE 대기열 연결 안정성

> **본 파일 목적**: `k6-tuned-results.md` s05 섹션의 트러블슈팅 기록 분리  
> **담당**: 장성재, 지영재  
> **원본 시나리오**: `infra/k6/scenarios/05_sse_queue.js`

---

## 1차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#27995728824](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/27995728824)

### 증상

| 구간 | http_req_failed | threshold |
|---|---|---|
| normal_load (1,000 VU) | **100%** | < 0.1% ✗ |
| boundary (1,800 VU) | **100%** | < 1% ✗ |
| sse_connections_rejected | 1,809,228건 | count>0 ✓ |

실패 유형: 1,809,228건 HTTP 429 + 425,542건 `dial: i/o timeout`. **Spring Boot 로그는 0줄** — 요청이 앱까지 도달하지 않음.

### 원인

**Nginx `limit_conn fandrops_sse 3`** (`/etc/nginx/default.d/fandrops-location.conf`)

```nginx
location /api/v1/queue/stream {
    limit_conn fandrops_sse 3;   # IP당 SSE 동시 연결 3개 제한
    limit_conn_status 429;
    ...
}
```

GHA runner는 단일 외부 IP에서 최대 2,100 VU가 요청을 보낸다. 동일 IP 기준으로 3개 초과 즉시 Nginx 레이어에서 429 반환 → Spring Boot 미도달. normal_load(1,000 VU)에서도 100% 실패한 이유와 일치한다.

- `limit_conn_zone $binary_remote_addr zone=fandrops_sse:10m` — IP 단위 카운트
- 프로덕션 설정으로는 적절하지만 부하 테스트 환경(단일 IP 다중 VU)에 부적합

### 조치

```bash
# EC2-1 SSM
sed -i "s/limit_conn fandrops_sse 3;/limit_conn fandrops_sse 2100;/" \
  /etc/nginx/default.d/fandrops-location.conf
nginx -s reload
```

`3 → 2100`으로 변경 후 nginx reload 완료 (2026-06-23). 전체 k6 테스트 완료 후 원복 필요.

---

## 2차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#27997295862](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/27997295862)

### 증상

| 구간 | 실패 유형 | 건수 |
|---|---|---|
| 전 구간 | HTTP 403 | 568,209건 |
| 전 구간 | 기타 비정상 | 4건 |

전 구간 에러율 100%. Spring Security 필터에서 요청을 차단하므로 Spring Boot 애플리케이션 로그에는 아무것도 남지 않음("No entries").

### 원인

**JWT tokens.csv — 이전 `JWT_SECRET`으로 서명된 토큰**

1. 1차 실행 후 `limit_conn 3 → 2100` 수정을 위해 CD 배포 실행 (GHA #27998829461, ~01:20 UTC)
2. CD 배포로 blue 슬롯이 새 `JWT_SECRET`으로 기동됨
3. S3에 남아있던 `tokens.csv`는 배포 전 secret으로 서명된 상태
4. 2차 실행 시 모든 요청이 Spring Security `JwtAuthenticationFilter`에서 `SignatureException` → 403 거부

```
[흐름]
tokens.csv(old secret) → k6 Bearer 헤더 → Nginx → Spring Boot
                                                      ↓
                                              JwtFilter: 서명 불일치 → 403
```

### 조치

1. `JwtGeneratorTest.java` git 이력에서 복원 (commit `369ed0f`)
2. EC2-1 `/etc/fandrops/fandrops-prod.conf`에서 현재 `JWT_SECRET` 추출 (SSM)
3. `JWT_SECRET=<secret> ./gradlew :modules:user:user-infrastructure:test --tests "com.fandrops.user.infrastructure.k6.JwtGeneratorTest"` 실행 → `infra/k6/seed/tokens.csv` 생성 (2,100개 토큰, 7일 만료)
4. `aws s3 cp infra/k6/seed/tokens.csv s3://<bucket>/k6/seed/tokens.csv` 업로드
5. 로컬 `tokens.csv` 삭제 (보안)

> **재발 방지**: CD 배포 후 tokens.csv는 반드시 재생성·재업로드 필요. `JWT_SECRET` 갱신 주기(7일)에 맞춰 재생성 권장.

---

## 서버 다운 · CD 장애 트러블슈팅 (2026-06-23)

### 증상

2차 실행 직후 `curl https://api.fandrops.site/actuator/health` → **exit 28 (TCP timeout)**. CD 파이프라인도 동시에 불통 상태.

### 원인 분석

**1단계 — EC2 인스턴스 상태 확인**

`aws ec2 describe-instance-status` → `InstanceStatus: ok`, `SystemStatus: ok`. 인스턴스 자체는 정상.

**2단계 — 서비스 상태 확인 (SSM)**

```
nginx:          active ✅
fandrops-blue:  active ✅
fandrops-green: active ✅
```

Spring Boot 직접 curl `http://127.0.0.1:8081/actuator/health` → `{"status":"UP"}` ✅

Nginx 경유 curl `http://127.0.0.1/actuator/health` → `NGINX_FAIL:22` (HTTP 4xx/5xx) ❌

**3단계 — 원인 특정**

`/etc/nginx/default.d/fandrops-location.conf` 확인 결과:

```nginx
location /actuator/health {
    proxy_pass http://fandrops_backend/actuator/health;
    access_log off;
    # proxy_set_header Host $host; ← 누락!
}
```

`proxy_set_header Host` 미선언 → Nginx가 `Host: fandrops_backend`(upstream 이름, 언더스코어 포함)를 Tomcat으로 전달 → Tomcat `IllegalArgumentException: The character [_] is never valid in a domain name` → HTTP 400.

외부에서는 `curl https://api.fandrops.site/actuator/health` → `HTTP 400 Tomcat Bad Request`. 헬스체크 엔드포인트만 이 버그에 영향을 받으며, 다른 location 블록(`/`, `/api/v1/…`)은 모두 `proxy_set_header Host $host;`가 이미 선언되어 있어 정상 동작.

> **근본 원인**: `nginx/fandrops-location.conf`에서 `/actuator/health` location 블록에만 `proxy_set_header Host $host;`가 누락됨. EC2 재부팅 전에는 서버 블록 수준의 헤더 설정이 상속되었을 가능성이 있으나, 재부팅 후 nginx 설정 로딩 순서 변경으로 상속이 끊어진 것으로 추정.

### CD 장애 원인

**GHA Run**: [#27998829461](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/27998829461)

bluegreen-deploy.sh 헬스체크가 `http://127.0.0.1:$NEW_PORT/actuator/health`(Nginx 우회, Spring Boot 직접)로 수행되므로 `/actuator/health` 버그와는 무관. 실패 원인은 t3.small(2 GB) 메모리 부족 — blue(-Xmx768m) 실행 중 green(-Xmx768m) 기동 시도 → OOM → green 120초 이내 `UP` 미달 → 헬스체크 타임아웃 → 배포 실패.

### 조치

**서버 복구**

```bash
# 1. EC2-1 재부팅 (AWS CLI)
aws ec2 reboot-instances --instance-ids i-07d1c60d175cdb8ca --region ap-northeast-2

# 2. SSM으로 location conf 패치 (재부팅 후 SSM 복구 확인 후 실행)
sudo python3 -c "
f='/etc/nginx/default.d/fandrops-location.conf'
c=open(f).read()
c=c.replace('location /actuator/health {',
            'location /actuator/health {\n    proxy_set_header Host \$host;')
open(f,'w').write(c)
"

# 3. Nginx 설정 검증 및 reload
sudo nginx -t && sudo systemctl reload nginx
```

**리포 영구 반영**

`nginx/fandrops-location.conf` 수정 — `/actuator/health` 블록에 `proxy_set_header Host $host;` 추가. 추가로 `limit_conn fandrops_sse 3 → 2100` 반영 (1차 트러블슈팅 EC2 직접 수정 내용 동기화).

`deploy-nginx.yml` 워크플로우 실행으로 S3 → EC2 영구 배포.

### 재발 방지

| 항목 | 상태 |
|---|---|
| `/actuator/health` Host 헤더 버그 | ✅ 리포 수정 완료 — `deploy-nginx.yml` 트리거로 영구 반영 |
| tokens.csv JWT 갱신 절차 | ✅ CD 배포 후 재생성 필수 절차로 확인 |
| CD OOM (t3.small blue+green 동시 기동) | ✅ 해결 — EC2-1 swap 2GB 추가 (2026-06-23), 재부팅 후에도 유지 (`/etc/fstab` 등록) |

---

## 3차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28007076554](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28007076554/job/82891402127)

### 증상

| 구간 | 실패 유형 | 건수 |
|---|---|---|
| 전 구간 | `unexpected status` (non-200/429) | 537,990건 |

- `checks_succeeded: 0.00%` — 전 요청 체크 실패
- `http_req_failed: 100%`
- `sse_connections_rejected: count=0` — 429 한 건도 없음
- `http_req_duration avg=621ms` — SSE 연결이 65s 유지되지 않고 즉시 종료됨

### 원인

**tokens.csv S3 업로드 누락 — June 18 생성 토큰 잔존**

1. 2차 실행 트러블슈팅 세션(2026-06-23 이전)에서 `JwtGeneratorTest` 실행으로 tokens.csv를 재생성했으나 S3 업로드 단계가 누락됨
2. S3에는 June 18 생성 토큰(`iat=1781765603`)이 그대로 남아있었음
3. 현재 서버 `JWT_SECRET`과 June 18 토큰의 서명 secret이 달라 `JwtProviderImpl.parse()` → `JwtException` → `InvalidTokenException`
4. `JwtAuthenticationFilter`가 예외를 catch하고 `SecurityContext` 미설정 → Spring Security가 anonymous user 처리
5. `/api/v1/queue/stream/**` → `authenticated()` 요건 미충족 → `AccessDeniedException` → **HTTP 403**

```
[흐름]
tokens.csv(June 18 서명) → k6 Bearer 헤더 → Spring Boot
                                                 ↓
                                    JwtFilter: SignatureException → 인증 컨텍스트 미설정
                                                 ↓
                                    Security: anonymous → 403
```

진단 과정:
- `curl https://api.fandrops.site/api/v1/queue/stream/4` → HTTP 403, `Content-Length: 0` (Spring Security 응답)
- EC2-1 포트 8081 직접 curl → 동일 403 → nginx가 아닌 앱 레벨 문제 확인
- S3 tokens.csv 1행 iat 디코딩 → June 18 생성 확인
- `JwtProviderImpl`: `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))` 사용 확인 → secret 불일치 시 서명 검증 실패

### 조치

1. EC2-1 현재 `JWT_SECRET` 확인 (SSM): `/OOkHMR5OAEzNxPBvRN5c0yGbMfI9f3VJaRXXN9Lboo`
2. `$env:JWT_SECRET = "..."` 설정 후 `./gradlew :modules:user:user-infrastructure:test --tests "...JwtGeneratorTest" --rerun-tasks` 실행
3. `aws s3 cp infra/k6/seed/tokens.csv s3://<bucket>/k6/tokens.csv` 업로드
4. 신규 토큰으로 SSE 엔드포인트 검증: `curl -H "Authorization: Bearer <token>" .../api/v1/queue/stream/4` → **HTTP 200** ✅
5. 로컬 tokens.csv 삭제

> **재발 방지**: tokens.csv 재생성 후 반드시 S3 업로드까지 완료 확인. 신규 토큰 1건을 curl로 검증한 뒤 k6 실행.

---

## 4차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28008723635](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28008723635/job/82896594087)

### 증상

| 지표 | 값 |
|---|---|
| checks_succeeded | 0% (6,734건 전부 실패) |
| http_req_failed | 100% |
| http_req_duration median | 0s |
| sse_connections_rejected | 0건 (429 없음) |
| interrupted iterations | 4,240건 |

실패 유형: `unexpected EOF` + `dial: i/o timeout`. 3차와 달리 HTTP 레벨 응답(403/429)이 아닌 네트워크 레벨 에러.

### 원인 분석

**GHA 로그 타임라인 (k6 진행 상황)**

| 시점 | 진행 상황 | 판정 |
|---|---|---|
| t=0~30s | 1,000 VU 램프업, 완료 0건 | ✅ 연결 수립 정상 |
| t=30~60s | 1,000 VU 유지, 완료 0건 | ✅ 연결 60초간 유지됨 |
| t=61s | 첫 29건 완료 + mass `unexpected EOF` 시작 | ⚠️ 60초 타임아웃 발화 |

**핵심 관찰**: t=0~60s 동안 완료 0건 = 연결 자체는 정상 유지됨. `sse_connections_rejected=0` = 429 없음 = 용량 한도 문제 아님. t=61s에 mass EOF 발생은 `SseEmitter` 60초 타임아웃 첫 배치와 정확히 일치.

**근본 원인**: `SseEmitterRegistry.register()` `onTimeout` 콜백이 `emitter.complete()`를 호출하지 않아 Spring이 HTTP 응답을 정상 종료하지 않음.

```java
// 문제 코드
emitter.onTimeout(() -> emitters.remove(key, emitter));  // complete() 누락
```

타임아웃 발화 시 동작 흐름:

```
60초 타임아웃
→ Spring: onTimeout 콜백 실행 (registry에서만 제거)
→ Spring: HTTP 응답 final empty chunk 없이 TCP 연결 닫음
→ k6: unexpected EOF, res.status=0
→ k6 check: else { check(res, { 'unexpected status': () => false }) }  ← 항상 false
→ checks_succeeded: 0%
```

이전 실패들과의 차이:
- 1차: `limit_conn 3` → Nginx가 429 반환 (HTTP 레벨)
- 2·3차: JWT 서명 불일치 → Spring Security가 403 반환 (HTTP 레벨)
- 4차: `SseEmitter` 타임아웃 → Spring이 TCP 연결 비정상 종료 (네트워크 레벨)

### 조치

**장성재** — `SseEmitterRegistry.register()` `onTimeout` 수정:

```java
emitter.onTimeout(() -> {
    emitters.remove(key, emitter);
    try {
        emitter.complete();  // 추가: HTTP 200 정상 종료 보장
    } catch (IllegalStateException ignored) {
        // heartbeat가 이미 complete 처리한 경우 무시
    }
});
```

수정 후 기대 흐름:
- 60초 타임아웃 → Spring이 final empty HTTP chunk 전송 후 정상 종료
- k6: `res.status=200` → `connectionAccepted.add(1)` → check 통과
- `http_req_failed=false`, `checks_succeeded` 정상 집계

> **재발 방지**: `onTimeout`/`onError` 콜백에서 `emitter.complete()`를 명시적으로 호출하지 않으면 Spring이 연결을 비정상 종료할 수 있다. 신규 `SseEmitter` 구현 시 반드시 확인.

---

## 5차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28012745830](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28012745830)

### 증상

| 지표 | 값 |
|---|---|
| `checks_succeeded` | 0.00% (0 / 12,972) |
| `sse_connections_accepted` | 0 |
| `sse_connections_rejected` | 0 |
| `http_req_failed` | 100.00% (12,972 / 12,972) |
| `http_req_duration` p50 / p90 | 163ms / 60s |
| GHA 결론 | exit 99 (threshold 미달) |

```
time="2026-06-23T08:26:05Z" level=warning msg="Request Failed" error="unexpected EOF"
```

- 테스트 시작(08:25:04)로부터 **정확히 61초** 뒤(08:26:05)에 대량 EOF 발생 → 4차와 동일 패턴
- **신규 현상**: p50=163ms의 빠른 실패가 다수 섞임 (4차는 전부 60초 대기 후 EOF)

### 타임라인

| 시각 | 이벤트 |
|---|---|
| 08:24 | tokens.csv S3 업로드 시각 확인(16:01), fanId=1 curl 검증 → **HTTP 200** |
| 08:24 | EC2-1 JWT_SECRET SSM 확인 (`/OOkHMR5...Lboo`) → 변경 없음 |
| 08:25:04 | k6 시작, normal_load VU 램프업 |
| 08:26:05 | **60초 SseEmitter 타임아웃** 동시 도달 → 대량 `unexpected EOF` |
| 08:26~30 | VU 재연결 시도 → 163ms 빠른 실패 반복 |
| 08:30:59 | k6 종료, exit 99 |

### 근본 원인 분석

4차 수정 (`emitter.complete()` in `onTimeout()`) 이 **효과 없음** 으로 판명.

```
[Spring async timeout 발생 시 내부 처리 순서]
1. Tomcat: async 컨텍스트 timeout 처리 시작 → TCP 연결 abrupt close 준비
2. Spring: onTimeout 콜백 호출 → emitter.complete() 실행 시도
3. 하지만 Tomcat이 이미 response를 닫는 중 → IllegalStateException 발생
4. catch (IllegalStateException ignored) 로 무시
5. 결과: HTTP 200 아닌 EOF 그대로 발생
```

`onTimeout()` 콜백 안에서 `complete()`를 호출하면 Spring 내부 async timeout 핸들러가 선점하여 **이미 늦은 상태**다. `complete()`는 정상적인 컨텍스트(스케줄러 스레드 등)에서 호출해야 HTTP 200이 보장된다.

### 신규 현상: 163ms 빠른 실패 원인

60초 대기 후 대량 EOF → 1,000+ VU 동시 재연결 → 서버 순간 과부하 → 재연결 요청 즉시 실패(status=0, EOF). 4차까지 없던 패턴으로 VU 재연결 스톰(reconnection storm)에 해당한다.

### 조치 방향 (장성재)

`onTimeout` 의존을 제거하고, 스케줄러(정상 컨텍스트)에서 proactive `complete()`를 호출하는 방식으로 교체.

```java
// SseEmitterRegistry.java — 수정 방향
// 1. 등록 시각 추적
private final ConcurrentHashMap<String, Long> registrationTimes = new ConcurrentHashMap<>();

private SseEmitter register(Long productId, Long fanId) {
    String key = key(productId, fanId);
    SseEmitter emitter = new SseEmitter(sseTimeoutMs);
    registrationTimes.put(key, System.currentTimeMillis());
    emitters.put(key, emitter);
    emitter.onCompletion(() -> {
        emitters.remove(key, emitter);
        registrationTimes.remove(key);
    });
    emitter.onTimeout(() -> emitters.remove(key, emitter)); // complete() 제거
    emitter.onError(e -> emitters.remove(key, emitter));
    return emitter;
}

// 2. sendHeartbeat()에서 55초 초과 emitter proactive close
public void sendHeartbeat() {
    long now = System.currentTimeMillis();
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
        String key = entry.getKey();
        Long registeredAt = registrationTimes.get(key);
        if (registeredAt != null && (now - registeredAt) > 55_000) {
            // Spring timeout(60s) 전에 정상 컨텍스트에서 complete() → HTTP 200 보장
            try { entry.getValue().complete(); } catch (IllegalStateException ignored) {}
            continue;
        }
        try {
            entry.getValue().send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(key);
        }
    }
}
```

기대 흐름:
- t=55s: 스케줄러가 `complete()` 호출 (정상 컨텍스트) → HTTP 200 정상 종료
- k6: `res.status=200` → `connectionAccepted.add(1)` → check 통과
- t=60s: Spring timeout 도달 전 이미 complete 상태 → `onTimeout`은 no-op

> **교훈**: `SseEmitter.onTimeout()` 콜백은 Spring 내부 타임아웃 핸들러가 response를 먼저 닫을 수 있어 `complete()`가 보장되지 않는다. SSE 연결 수명 관리는 반드시 외부 스케줄러(정상 컨텍스트)에서 proactive하게 처리해야 한다.

---

## 6차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28014460997](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28014460997)

### 증상

| 지표 | 5차 | 6차 |
|---|---|---|
| `http_reqs` | 12,972 | **264,402** (×20) |
| `http_req_duration` p50 | 163ms | **0ms** |
| `iteration_duration` p50 | 163ms | **31ms** |
| `sse_connections_accepted` | 0 | 0 |
| `http_req_failed` | 100% | 100% |
| EOF 발생 시각 | 시작 +61s | 시작 +60s |

```
time="2026-06-23T08:56:34Z" level=warning msg="Request Failed" error="unexpected EOF"
time="2026-06-23T09:00:53Z" level=warning msg="Request Failed" error="Get \"***/api/v1/queue/stream/4\": dial: i/o timeout"
```

- `unexpected EOF` 계속 발생 — 5차와 동일 패턴
- **신규**: `http_req_duration p50=0ms` → proactive `complete()` 호출 후 k6가 여전히 실패 응답을 받아 VU가 즉시 재연결 폭풍(reconnection storm) 발생
- **신규**: `dial: i/o timeout` — overflow 구간(2100 VU 동시 재연결) TCP backlog 포화

### 근본 원인 분석

5차 fix(proactive complete)가 `complete()` 호출 자체는 성공하지만, **k6가 여전히 `unexpected EOF`(status=0)**를 수신하는 이유가 밝혀짐.

```
[HTTP 프로토콜 버전 불일치]

k6 → Nginx     : HTTP/1.1 (chunked transfer encoding)
Nginx → Spring : HTTP/1.0 (기본값, chunked 없음)

Spring complete() 호출
  → HTTP/1.0 TCP close (final 0\r\n\r\n 청크 없음)
  → Nginx: upstream EOF 수신 → k6에 final chunk 없이 TCP FIN
  → k6 Go HTTP client: 청크 종료자 미수신 → "unexpected EOF"
  → res.status = 0 (200 헤더 수신했어도 body 비정상 종료)
```

Nginx가 upstream(Spring)과 HTTP/1.0으로 통신하면 chunked transfer encoding을 사용하지 않아 `0\r\n\r\n` 종료 청크가 k6에 전달되지 않는다. k6의 Go HTTP client는 이를 비정상 EOF로 처리한다.

### 두 에러 원인 요약

| 에러 | 원인 |
|---|---|
| `unexpected EOF` | Nginx↔Spring HTTP/1.0 — chunked 종료자 미전달 |
| `dial: i/o timeout` | overflow VU 2100개 동시 재연결 → OS TCP accept backlog 포화 |

### 조치 (지영재)

`nginx/fandrops-location.conf` SSE 블록에 HTTP/1.1 명시:

```nginx
location /api/v1/queue/stream {
    # ... 기존 설정 ...
    proxy_http_version 1.1;   # Nginx↔Spring HTTP/1.1 → chunked 종료자 정상 전달
    proxy_set_header Connection "";  # keep-alive 헤더 클리어
    # ...
}
```

기대 흐름:
- `complete()` 호출 → Spring이 `0\r\n\r\n` 최종 청크 전송
- Nginx가 HTTP/1.1로 이를 k6에 정상 전달
- k6: `res.status=200` → `connectionAccepted.add(1)` → check 통과

> **교훈**: SSE를 Nginx로 프록시할 때 `proxy_http_version 1.1`은 필수다. HTTP/1.0 기본값은 chunked transfer encoding을 지원하지 않아 `SseEmitter.complete()`가 정상 동작해도 k6(Go HTTP client)가 EOF로 인식한다.

---

## 7차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28025103211](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28025103211/job/82950796500)

### 증상

| 지표 | 값 |
|---|---|
| `checks_succeeded` | 0.00% (0 / 12,850) |
| `http_req_failed` | 100.00% (전 구간) |
| `http_req_duration` p50 / p95 | 158ms / 60s |
| `sse_connections_rejected` | 0건 (threshold `count>0` ✗) |
| `interrupted iterations` | 4,252건 |

```
time="2026-06-23T12:10:37Z" level=warning msg="Request Failed" error="unexpected EOF"
time="2026-06-23T12:15:08Z" level=warning msg="Request Failed" error="Get ".../api/v1/queue/stream/4": dial: i/o timeout"
```

- 시작(12:09:38) + 59초 = 12:10:37에 mass EOF → 6차와 동일한 60초 타임아웃 패턴
- t=5m30s 이후 overflow 구간에서 `dial: i/o timeout` 추가 발생

### 원인 분석

두 가지 원인이 독립적으로 작용한다.

**원인 1: nginx `proxy_http_version 1.1` 미배포**

6차에서 `f030197` 커밋으로 nginx SSE 블록에 `proxy_http_version 1.1`을 추가했으나, `deploy-nginx.yml` 워크플로를 수동 실행하지 않아 EC2-1 Nginx에 반영되지 않음. EC2-1은 여전히 HTTP/1.0 기본값으로 Spring과 통신 → chunked 종료자 미전달 → `unexpected EOF`.

**원인 2: `SseEmitterRegistry` 60초 타임아웃 vs 시나리오 지속 시간 불일치**

nginx 배포 후에도 이 문제는 독립적으로 남는다.

```java
// SseEmitterRegistry.java
@Value("${fandrops.queue.sse-timeout-ms:60000}")
private long sseTimeoutMs;  // Spring SseEmitter 수명: 60초

// sendHeartbeat() — 5초 주기 스케줄러
if ((now - registeredAt) > 55_000) {  // 하드코딩: 55초 초과 시 강제 complete()
    entry.getValue().complete();
}
```

| 항목 | 값 |
|---|---|
| heartbeat proactive close 임계값 | **55,000ms (하드코딩)** |
| `normal_load` 구간 지속 시간 | 105s (1m45s) |
| `boundary` 구간 지속 시간 | 105s (1m45s) |

`sseTimeoutMs`를 속성 파일로 올려도 `55_000` 하드코딩이 항상 55초에 먼저 `complete()`를 호출하므로 연결이 시나리오 종료 전에 서버 측에서 강제 종료된다.

### 두 에러 원인 요약

| 에러 | 원인 |
|---|---|
| `unexpected EOF` (t=60s) | nginx HTTP/1.0 미배포 (원인 1) + SseEmitter 55초 proactive close (원인 2) |
| `dial: i/o timeout` (t=5m+) | overflow 2,100 VU 동시 재연결 → OS TCP accept backlog 포화 |

### 조치

**조치 1 — `deploy-nginx.yml` 수동 실행 (지영재)**

GitHub Actions → `Deploy Nginx Config` → develop 기준 수동 실행. `proxy_http_version 1.1` EC2-1 반영.

**조치 2 — `SseEmitterRegistry.java` 수정 (장성재)**

`sseTimeoutMs` 기본값 증가 + heartbeat 임계값을 `sseTimeoutMs` 기반으로 동적 계산:

```java
// 변경 전
@Value("${fandrops.queue.sse-timeout-ms:60000}")
private long sseTimeoutMs;

// sendHeartbeat() 내
if ((now - registeredAt) > 55_000) { ... }

// 변경 후
@Value("${fandrops.queue.sse-timeout-ms:300000}")  // 기본값 5분으로 증가
private long sseTimeoutMs;

// sendHeartbeat() 내
if ((now - registeredAt) > sseTimeoutMs - 5_000) { ... }  // 속성값 기반 동적 계산
```

> **교훈**: `sseTimeoutMs` 속성과 heartbeat 임계값(`55_000`)이 각자 별개 상수로 선언되어 있어, 속성 파일로 timeout을 올려도 heartbeat 하드코딩이 선점한다. 타임아웃 관련 상수는 단일 소스(속성값)에서 파생해야 한다.

---

## 8차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28028688794](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28028688794/job/82963176694)

### 증상

| 지표 | 값 |
|---|---|
| `checks_succeeded` | 0.00% (0 / 14,351) |
| `http_req_failed` | 100.00% |
| `http_req_duration` p90 / p95 | 64s / 64s |

```
time="2026-06-23T13:12:23Z" level=warning msg="Request Failed" error="request timeout"
```

- **에러 타입이 `unexpected EOF` → `request timeout`으로 변경** — 7차 서버 사이드 fix(sseTimeoutMs 300s) 적용 효과 확인
- t=~60s에 mass timeout 발생

### 원인 분석

7차 fix로 서버가 295s까지 연결을 유지하게 됐으나, **k6 스크립트의 HTTP timeout이 여전히 `65s`**로 설정되어 있어 k6가 서버보다 먼저 연결을 포기한다.

```
[타임아웃 주체 역전]

fix 이전: 서버(55s proactive close) < k6 timeout(65s) → 서버가 먼저 EOF
fix 이후: 서버(295s proactive close) > k6 timeout(65s) → k6가 먼저 request timeout
```

| 항목 | 값 |
|---|---|
| `sseTimeoutMs` (서버 proactive close) | 295,000ms (300s - 5s) |
| k6 `timeout` (05_sse_queue.js line 82) | **65s** |
| `normal_load` 스테이지 최대 지속 | 105s |

`65s` timeout은 서버가 55s에 먼저 닫던 시절 기준값이었다. sseTimeoutMs 증가 이후 기준이 무효화됨.

### 조치

`infra/k6/scenarios/05_sse_queue.js` timeout 증가:

```js
// 변경 전
timeout: '65s',

// 변경 후
timeout: '310s',  // sseTimeoutMs(300s) + 10s 여유. 서버가 295s에 먼저 graceful close
```

> **교훈**: k6 timeout과 서버 SseEmitter timeout은 연동된 값이다. 어느 한쪽을 변경하면 반드시 다른 쪽도 검토해야 한다.

---

## 9차 실행 트러블슈팅 (2026-06-23)

**GHA Run**: [#28030028231](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28030028231)

### 증상

| 지표 | 값 |
|---|---|
| `normal_load` (1,000 VU) | **PASSED** ✅ |
| `boundary` (1,800 VU) | **FAILED** — `dial: i/o timeout` |
| `sse_connections_rejected` | 0 (앱 레벨 2000 상한 미도달) |
| `http_req_duration` avg | 0s (연결 자체가 미수립) |

```
time="2026-06-23T..." level=warning msg="Request Failed"
  error="Get \"…/api/v1/queue/stream/4\": dial: i/o timeout"
```

- `normal_load` 1,000 VU 구간은 통과 — 8차 k6 timeout 310s 수정 효과 확인
- `boundary` 진입(t≈3m4s) 후 TCP dial 단계에서 즉시 타임아웃, HTTP 응답 없음
- `sse_connections_rejected=0` → 앱의 `SseCapacityExceededException`(2000 상한)에는 도달하지 못함

### 원인 분석

`nginx/fandrops-location.conf`의 `limit_conn fandrops_sse` 설정이 **IP당** 동시 연결 수를 제한한다.

```nginx
location /api/v1/queue/stream {
    limit_conn fandrops_sse 2100;   # ← IP 단위
    ...
}
```

k6는 **ec2-2 단일 IP**에서 모든 VU를 발사한다. 따라서:

```
[연결 수 계산]
normal_load gracefulRampDown 종료 후 lingering 연결 잔존 + boundary 1,800 VU
= 단일 IP 기준 합산 > 2,100 → Nginx TCP 레벨 차단 → dial: i/o timeout
```

| 계층 | 제한 | 작동 방식 | 이번 문제 |
|---|---|---|---|
| Nginx `limit_conn` | 2,100 (IP당) | TCP 수립 전 차단 → timeout | ✅ 이것이 원인 |
| App `SseCapacityExceededException` | 2,000 (전역) | HTTP 429 반환 | ❌ 도달 못 함 |

시나리오가 검증하려는 계약은 **앱 레벨 429**(`sse_connections_rejected count>0`)인데, Nginx `limit_conn`이 그 앞에서 TCP를 차단하므로 앱까지 요청이 전달되지 않는다.

### 조치

`nginx/fandrops-location.conf` `/api/v1/queue/stream` 블록에서 `limit_conn` 3줄 제거:

```diff
 location /api/v1/queue/stream {
-    limit_conn fandrops_sse 2100;
-    limit_conn_status 429;
-    add_header Retry-After 1 always;
     proxy_pass http://fandrops_backend;
```

> **운영 복원 참고**: `limit_conn`은 단일 IP 과다 연결(DDoS 방어)을 위한 설정이다. 실사용 트래픽은 IP가 분산되므로 의미가 있으나, 단일 IP k6 부하 테스트 환경에서는 앱 레벨 상한 검증을 방해한다. 부하 테스트 완료 후 운영 보호 목적으로 재적용 여부를 검토한다.

> **교훈**: `limit_conn`은 IP 단위이므로 단일 IP 발원 k6 시나리오에서는 사실상 VU 총합 제한이 된다. 앱 레벨 용량 검증 시나리오는 Nginx 레이어 제한을 해제하거나 앱 상한보다 충분히 크게 설정해야 한다.

---

## 10차 실행 트러블슈팅 (2026-06-24)

**GHA Run**: [#28028688794](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/actions/runs/28028688794)

### 증상

| 구간 | VU | 결과 |
|---|---|---|
| normal_load | 1,000 | **PASSED** ✅ — 에러율 0.00% |
| boundary | 1,800 | **PASSED** ✅ — 에러율 0.00% |
| overflow | 2,100 | **FAILED** — `sse_connections_rejected=0`, 전 요청 `unexpected status` |

- `normal_load` · `boundary` 통과 — 9차 `limit_conn` 제거 효과 확인
- overflow: `sse_connections_rejected=0` — Spring 앱 레벨 2,000 상한 미도달
- overflow: 모든 요청이 200도 429도 아닌 status → k6 스크립트 `else` 브랜치 → `unexpected status: false`
- overflow 에러 유형: `dial: i/o timeout` (status=0) — HTTP 레벨 응답 없이 TCP 레벨 차단

### 원인 분석

**Nginx `worker_connections` FD 고갈**

```nginx
# /etc/nginx/nginx.conf (EC2 실측)
worker_processes auto;   # 단일 vCPU EC2 → 실질 1 worker
events {
    worker_connections 4096;
}
```

SSE는 Nginx 프록시 연결 1개당 FD 2개를 소비한다 (client↔Nginx 1개 + Nginx↔Spring upstream 1개).

| 구간 | VU | FD 소비 | 한도(4,096) | 결과 |
|---|---|---|---|---|
| boundary | 1,800 | 1,800 × 2 = 3,600 | 3,600 < 4,096 | ✅ 통과 |
| overflow | 2,100 | 2,100 × 2 = **4,200** | **4,200 > 4,096** | ✗ FD 고갈 |

overflow 구간에서 Nginx worker FD pool이 고갈되면 신규 TCP SYN을 처리하지 못한다 → k6 `dial: i/o timeout` (status=0) → 200·429 아닌 status → `else` 브랜치 → `sse_connections_rejected` 미증가.

9차(limit_conn 제거) 이전에도 overflow 측정이 불가능했으므로 이 문제는 9차에서 처음 관찰 가능해진 새로운 레이어의 병목이다.

### 조치

`worker_connections 4096 → 8192` 변경 (SSM 즉시 적용 + 레포 코드화):

```nginx
events {
    worker_connections 8192;  # 단일 worker 기준 최대 동시 SSE 4,096개 (8192 ÷ 2)
}
```

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| `worker_connections` | 4,096 | **8,192** |
| 최대 동시 SSE 연결 | 2,048 | **4,096** |
| overflow 2,100 VU 수용 | ✗ (4,200 > 4,096) | ✅ (4,200 < 8,192) |

SSM `sed -i 's/worker_connections 4096/worker_connections 8192/'` → `nginx -t && nginx -s reload` 완료.
`nginx/nginx.conf` 레포 추가 + `deploy-nginx.yml`에 nginx.conf 배포 단계 추가 → PR #422.

> **교훈**: SSE는 Nginx 통과 시 FD를 2개 소비(client + upstream)한다. `worker_connections`는 단순 HTTP 요청 기준이 아닌 SSE × 2를 고려해 설정해야 한다. boundary(1,800) 통과 + overflow(2,100) 실패의 경계가 `4096 ÷ 2 = 2,048`에 정확히 일치하므로, FD 산술로 상한을 정확히 특정할 수 있다.

---

## 11차 실행 트러블슈팅 (2026-06-24)

**GHA Run**: (run ID 기록 필요)

### 증상

```
time="2026-06-23T14:08:38Z" level=warning msg="Request Failed"
  error="Get \"***/api/v1/queue/stream/4\": dial: i/o timeout"
```

| 구간 | VU | http_req_failed | 결과 |
|---|---|---|---|
| normal_load | 1,000 | **0.00%** | ✅ PASSED |
| boundary | 1,800 | **0.00%** | ✅ PASSED |
| overflow | 2,100 | **100%** (`dial: i/o timeout`) | ✗ FAILED |

```
checks_total.......: 8923
checks_succeeded...: 0.00%   0 out of 8923
sse_connections_rejected.....: 0
http_req_duration: avg=168.81ms  p(90)=198.03ms  p(95)=220.78ms
```

- `worker_connections 8192` 적용 상태에서 실행 — 10차 FD 고갈 원인은 해소됨
- overflow: `sse_connections_rejected=0` — Spring 앱 여전히 미도달
- overflow: `dial: i/o timeout` (status=0) — TCP 레벨 차단 지속

### 원인 분석

`worker_connections 8192` 적용 후에도 동일 패턴이 재현됨. FD 이외의 레이어를 확인.

**EC2-1 OS TCP 스택 파라미터 확인 (SSM)**

```bash
cat /proc/sys/net/core/somaxconn          # → 4096
cat /proc/sys/net/ipv4/tcp_max_syn_backlog # → 256
```

| 항목 | 실측값 | 판정 |
|---|---|---|
| `net.core.somaxconn` | 4,096 | ✅ 충분 |
| `net.ipv4.tcp_max_syn_backlog` | **256** | ✗ **SYN 큐 부족** |

**근본 원인**: `tcp_max_syn_backlog=256` — OS 커널 SYN 큐 크기가 256으로 매우 작다.

overflow 구간에서 2,100 VU가 30초 ramp-up 중 burst SYN을 보낼 때 SYN 큐 256을 초과하면 커널이 SYN 패킷을 drop한다. k6는 SYN 응답을 받지 못해 `dial: i/o timeout`이 발생하고, 요청이 Nginx에도 Spring에도 도달하지 않아 `sse_connections_rejected=0`이 된다.

`dial: i/o timeout`은 TCP 3-way handshake 단계 실패이므로 Nginx `worker_connections`(accept 큐 FD)·`listen backlog`(accept 큐)와는 별개 레이어다.

```
[레이어별 제한 정리]
OS SYN 큐 (tcp_max_syn_backlog=256) ← 이번 원인
  → accept 큐 (somaxconn=4096, nginx listen backlog)
    → Nginx worker FD (worker_connections=8192)
      → Spring Tomcat
```

boundary(1,800 VU)가 통과한 이유: 30초 ramp-up이지만 이전 구간(normal_load) VU들이 모두 ramp-down된 뒤 startTime 여유가 있어 순간 SYN burst가 256 미만으로 유지됐을 가능성이 있다. overflow는 2,100 VU를 30초에 집중적으로 발사하며 burst 강도가 더 높다.

### 조치

**SSM 즉시 적용**

```bash
sysctl -w net.ipv4.tcp_max_syn_backlog=8192
sysctl -w net.core.somaxconn=8192
```

**영구 반영 (`/etc/sysctl.d/99-fandrops.conf`)**

```
net.ipv4.tcp_max_syn_backlog=8192
net.core.somaxconn=8192
```

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| `tcp_max_syn_backlog` | **256** | 8,192 |
| `net.core.somaxconn` | 4,096 | 8,192 |

적용 확인:

```bash
# SSM 결과
net.ipv4.tcp_max_syn_backlog = 8192
net.core.somaxconn = 8192
```

> **CD 배포 영향 없음**: sysctl은 OS 커널 파라미터로 CD 배포(JAR 교체, nginx conf 덮어쓰기)와 완전히 별개 레이어다. `/etc/sysctl.d/99-fandrops.conf` 영구 설정으로 EC2 재부팅 후에도 유지. 인스턴스 교체 시에만 재설정 필요.

> **Nginx listen backlog 수정 불필요**: `dial: i/o timeout`은 SYN 큐(tcp_max_syn_backlog) 고갈로 3-way handshake 단계에서 발생한다. Nginx `listen backlog`는 완전히 연결된 후의 accept 큐이므로 이번 문제와 무관하다.

> **교훈**: `worker_connections`(FD), `somaxconn`(accept 큐), `tcp_max_syn_backlog`(SYN 큐)는 독립된 3개 레이어다. SSE 고부하 환경에서는 세 값 모두 충분히 설정해야 한다. `dial: i/o timeout`이 FD 고갈 조치 후에도 지속된다면 SYN 큐를 다음 의심 대상으로 확인한다.

---

## 12차 — GHA Runner ulimit-n 고갈 원인 분석 및 조치 (2026-06-23)

### 실행 조건

- EC2-1: `worker_connections=8192`, `tcp_max_syn_backlog=8192`, `somaxconn=8192` **모두 적용된 상태**
- GHA runner: ubuntu-latest 기본값 (`ulimit -n=1024` — 미수정)

### 증상

11차와 동일한 패턴 지속:

```
time='2026-06-23T17:09:30Z' level=warning msg='Request Failed'
error='Get "https://api.fandrops.site/api/v1/queue/stream/4": dial: i/o timeout'
```

- boundary 구간: `dial: i/o timeout` 다수 발생
- overflow 구간: 동일 패턴, `sse_connections_rejected` 미집계

### 원인 분석

EC2-1 Nginx error log를 SSM으로 확인한 결과 **에러 로그가 비어있음** — 서버가 연결 자체를 정상 수신하고 있었으나 클라이언트(runner) 측에서 소켓 생성 실패.

**GHA runner 기본 `ulimit -n = 1024`** 가 실제 원인:

| 구간 | 상태 | FD 소비 추정 |
|---|---|---|
| normal_load ramp-down 완료 후 | TIME_WAIT 소켓 잔류 | ~1,000 FD (tcp_fin_timeout=60s 동안 점유) |
| boundary 1,800 VU 연결 시도 | TIME_WAIT + 신규 = 초과 | 1,000 + 1,800 = **2,800 > 1,024** |
| overflow 2,100 VU | 더 심각 | 누적 **3,900 > 1,024** |

SSE는 long-lived connection이므로 ramp-down 후에도 TIME_WAIT 상태의 소켓이 `tcp_fin_timeout`(60s) 동안 FD를 점유한다. runner의 낮은 ulimit과 맞물려 boundary 구간부터 새 소켓 생성이 차단되어 `dial: i/o timeout`이 발생했다.

> **서버 측 완전 배제**: EC2-1 Nginx error log 비어있음 → worker_connections/SYN 큐/accept 큐 모두 충분한 상태에서 서버는 정상 대기 중이었고, 클라이언트(runner)가 TCP 연결 자체를 열지 못했음.

### 조치

`.github/workflows/run-k6.yml` k6 실행 스텝 맨 앞에 ulimit 설정 추가:

```yaml
- name: k6 실행
  run: |
    ulimit -Sn 65536
    echo "ulimit -n: $(ulimit -n)"

    SCENARIO="${{ github.event.inputs.scenario }}"
    ...
```

- `ulimit -Sn 65536`: soft limit만 올림 (hard limit은 ubuntu-latest에서 65536으로 허용됨)
- 2,100 VU × SSE 1연결 + TIME_WAIT 여유분까지 충분히 커버

> **교훈**: k6를 GHA runner에서 직접 실행할 때 SSE처럼 long-lived connection을 대량으로 열면 runner의 기본 `ulimit -n=1024`가 병목이 된다. EC2/Nginx 쪽 조치가 완료되었는데도 `dial: i/o timeout`이 재현된다면 **클라이언트(runner) 소켓 한도**를 먼저 점검한다.
