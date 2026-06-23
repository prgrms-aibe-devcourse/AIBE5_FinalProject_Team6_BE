# ADR-019: 대기열 FE-BE 연동 장애 — 진단·수정 기록

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-24 |
| **선행 ADR** | [ADR-010 SSE 채택](./ADR-010-queue-realtime-notification-sse.md) · [ADR-012 Redis ZSET](./ADR-012-queue-storage-redis-zset.md) |
| **관련** | [WaitQueueController](../../modules/payment/payment-api/src/main/java/com/fandrops/payment/api/queue/WaitQueueController.java) · [LocalSecurityConfig](../../apps/api-server/src/main/java/com/fandrops/config/LocalSecurityConfig.java) · [WaitQueueService](../../modules/payment/payment-application/src/main/java/com/fandrops/payment/application/queue/WaitQueueService.java) |
| **담당** | 장성재 (`payment` — 대기열·Access Ticket) |

---

## Title

대기열 FE-BE 연동 과정에서 발생한 세 가지 독립적 결함(응답 래퍼 누락·로컬 JWT 필터 누락·대기시간 오계산)을 순차 진단·수정하고, 배포 환경 프로파일 전략을 정정한다.

---

## Context

대기열 기능의 BE 구현(SSE·Redis ZSET, ADR-010·012)이 완료된 후 FE 연동 테스트를 시작했다. FE는 `joinQueue()` 호출 즉시 "대기열이 만료되었습니다" 오류를 표시했다. Gemini 에이전트가 SSE 청크 파싱 버그를 수정했다고 보고했지만 증상은 재현됐다.

이 ADR은 이후 진행된 원인 추적과 각 수정 결정을 기승전결로 기록한다.

---

## 1막 — ApiResponse 래퍼 누락 (즉시 만료 원인)

### 증상

FE가 `joinQueue()`를 호출하면 응답을 받자마자 `useQueue.ts`가 EXPIRED 상태로 전환하고 "대기열이 만료되었습니다"를 표시했다. SSE 연결조차 시도하지 않았다.

### 원인 추적

FE `queue.ts`는 모든 BE 응답을 `ApiResponse<T>` 구조(`{ success, data, error, traceId }`)로 가정하고 `body.data.queueId` 형태로 접근했다.

```ts
// FE queue.ts
const body = await res.json();
return body.data;   // ← ApiResponse<T>.data 기대
```

그런데 `WaitQueueController.join()`과 `status()`는 DTO를 **직접** 반환하고 있었다.

```java
// 수정 전
return ResponseEntity.ok(new QueueJoinResponse(
        result.getQueueId(), result.getPosition(), ...
));
```

`body.data`가 `undefined`가 되므로 `useQueue.ts`의 다음 코드가 `TypeError`를 던졌다.

```ts
// useQueue.ts
if (res.status === 'DONE') { ... }  // res === undefined → TypeError
```

`useQueue.ts`는 `startQueue()`의 catch 블록에서 `TypeError`를 "응답 없음 = 만료"로 해석해 상태를 EXPIRED로 전환했다. SSE 연결 전 join 단계에서 이미 실패하므로 대기열 화면에 진입 자체가 불가능했다.

### 수정 결정

`join()`과 `status()`에 `ApiResponse.ok(...)` 래퍼를 추가했다. `stream()`은 `SseEmitter`를 직접 반환(래퍼 불필요), `exit()`는 204 No Body로 변경 없음.

```java
// WaitQueueController.java — 수정 후
@PostMapping("/join/{productId}")
public ResponseEntity<ApiResponse<QueueJoinResponse>> join(...) {
    ...
    return ResponseEntity.ok(ApiResponse.ok(new QueueJoinResponse(
            result.getQueueId(), result.getPosition(), ApiQueueStatus.from(result.getStatus())
    ), MDC.get("traceId")));
}

@GetMapping("/status")
public ResponseEntity<ApiResponse<QueueStatusResponse>> status(...) {
    ...
    return ResponseEntity.ok(ApiResponse.ok(new QueueStatusResponse(
            result.getPosition(), ApiQueueStatus.from(result.getStatus()),
            result.getEstimatedWaitSec(), result.getToken()
    ), MDC.get("traceId")));
}
```

---

## 2막 — LocalSecurityConfig JWT 필터 누락 (position 항상 1 원인)

### 증상

1막 수정 후 단독 테스트(팬 1명)는 정상 동작했다. 그러나 `trigger_queue.js`로 1,000명을 시뮬레이션한 뒤 실제 유저가 입장하면 position이 ~1,001이 아닌 **1**로 표시됐다.

### 원인 추적

`trigger_queue.js`는 각 가상 유저를 signup → login → joinQueue 순서로 처리하며 `Authorization: Bearer <token>` 헤더를 첨부했다. BE는 `X-Fan-Id` 헤더가 없으면 JWT에서 fanId를 추출한다.

그런데 로컬 프로파일의 `LocalSecurityConfig`는 Spring Security를 열린 상태(`anyRequest().permitAll()`)로만 설정하고 **JWT 필터를 체인에 등록하지 않았다**.

```java
// 수정 전 LocalSecurityConfig — JWT 필터 없음
return http
    .csrf(AbstractHttpConfigurer::disable)
    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
    .build();
```

결과적으로 Bearer 토큰이 무시되고 `authentication`이 `null`로 남아 `resolveFanId()`에서 400이 반환됐다. 시뮬레이터 1,000명이 모두 대기열 입장에 실패했고 실제 유저는 빈 대기열에 1번으로 입장했다.

### 수정 결정

`LocalSecurityConfig`에 JWT 필터를 Security 체인 내에 추가하고, 서블릿 컨테이너 자동 등록을 `FilterRegistrationBean`으로 비활성화해 필터가 두 번 실행되지 않도록 했다.

```java
@Profile("local")
@Configuration
public class LocalSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);   // 서블릿 자동 등록 방지
        return registration;
    }

    @Bean
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
```

---

## 3막 — 테스트 간 PROCESSING 잔존 · 대기시간 오계산

### 증상 A — 재테스트 시 바로 결제창 이동

2막 수정 확인 후 재테스트를 위해 시뮬레이터를 다시 주입하면, 시뮬레이터 1,000명이 나오지 않고 실제 유저가 대기열 화면 진입 즉시 결제창으로 이동했다.

### 원인 A

`WaitQueueStatus.isTerminal()`은 `DONE`과 `EXPIRED`만 terminal로 정의한다. `PROCESSING`은 terminal이 아니다.

이전 테스트에서 실제 유저의 대기열 entry가 `PROCESSING` 상태로 남아 있었고, 로컬 H2 인메모리 DB는 BE 재시작 전까지 상태를 보존한다. 다음 테스트에서 `join()`을 호출하면 PROCESSING이 terminal이 아니므로 새 WAITING entry를 만들지 않고 **기존 PROCESSING entry를 그대로 반환**했다. PROCESSING 상태는 Access Token이 이미 발급된 상태이므로 FE는 즉시 결제창으로 이동했다.

### 수정 A

`application-local.yml`에 빠른 처리 설정을 추가해 PROCESSING 상태가 10초 내 만료되도록 했다. 이제 재테스트 시 이전 entry가 EXPIRED로 전환된 뒤 새 WAITING entry가 생성된다.

```yaml
# application-local.yml
fandrops:
  queue:
    processing-timeout-seconds: 10
    advance-batch-size: 50
    max-concurrent-processing: 50
    scheduler:
      interval-ms: 1000
```

### 증상 B — 예상 대기시간 비현실적

예상 대기시간이 로컬에서는 실제보다 15배 높게 표시됐고, prod 기준으로는 15시간을 초과하는 값이 나왔다.

### 원인 B

`WaitQueueService`에 하드코딩된 상수가 있었다.

```java
// 수정 전
private static final long SECONDS_PER_POSITION = 3L;
long estimatedWaitSec = position * SECONDS_PER_POSITION;
```

실제 처리 속도는 `maxConcurrentProcessing / processingTimeoutSeconds` (명/초)다. prod 기본값은 `maxConcurrent=10, timeout=600s`이므로 1명당 60초가 소요되는데, 하드코딩 3은 이를 20배 과소평가했다.

### 수정 B

생성자 주입으로 실제 설정값을 받아 동적으로 계산하도록 변경했다.

```java
// WaitQueueService.java — 수정 후
long estimatedWaitSec = position > 0
    ? Math.max(1L, Math.round(position * ((double) processingTimeoutSeconds / maxConcurrentProcessing)))
    : 0L;
```

`WaitQueueConfig.java`에서 `@Value`로 설정값을 읽어 생성자에 전달한다.

```java
// WaitQueueConfig.java
@Value("${fandrops.queue.processing-timeout-seconds:600}")
private long processingTimeoutSeconds;

@Value("${fandrops.queue.max-concurrent-processing:10}")
private long maxConcurrentProcessing;

@Bean
public WaitQueueService waitQueueService(...) {
    return new WaitQueueService(repo, ticketRepo, processingTimeoutSeconds, maxConcurrentProcessing);
}
```

---

## 4막 — 배포 프로파일 전략 오류 수정

### 증상

시연용 큐 설정(`timeout=15s, maxConcurrent=20`)을 `application-stg.yml`에 추가했으나, 배포 서버에서 설정이 반영되지 않을 것임을 확인했다.

### 원인

EC2의 systemd 서비스 파일이 `--spring.profiles.active=prod`로 하드코딩되어 있고, stg/prod 물리 서버 분리가 없다.

```ini
# /etc/systemd/system/fandrops-blue.service
ExecStart=/usr/bin/java -Xms256m -Xmx768m \
  -jar /opt/fandrops/blue.jar \
  --server.port=8081 \
  --spring.profiles.active=prod   ← stg 프로파일은 절대 로드되지 않음
```

`application-stg.yml`에 아무리 설정을 추가해도 배포 서버에서는 읽히지 않는다.

### 수정 결정

시연용 큐 설정을 `application-prod.yml`로 이전하고 `application-stg.yml`에서 제거했다. 배포 담당자에게 별도 환경변수 추가는 불필요하다. `develop` 브랜치 머지 후 CD 파이프라인이 자동으로 새 JAR를 배포하면 설정이 적용된다.

```yaml
# application-prod.yml — 추가
fandrops:
  queue:
    processing-timeout-seconds: 15
    advance-batch-size: 20
    max-concurrent-processing: 20
    scheduler:
      interval-ms: 1000
```

처리 속도 계산:
```
rate = 20 / 15 = 1.33명/초
position 100 대기 → 100 × (15/20) = 75초 ≈ 1.25분
```

---

## Decision

네 가지 수정을 모두 적용한다.

| # | 수정 대상 | 내용 |
| --- | --- | --- |
| 1 | `WaitQueueController` | `join()`·`status()` 응답을 `ApiResponse.ok()`로 래핑 |
| 2 | `LocalSecurityConfig` | JWT 필터를 Security 체인에 추가, `FilterRegistrationBean`으로 이중 등록 방지 |
| 3 | `WaitQueueService` · `WaitQueueConfig` | 예상 대기시간을 `position × (timeout / maxConcurrent)` 동적 계산으로 변경 |
| 4 | `application-prod.yml` | 시연용 큐 설정(`timeout=15s, maxConcurrent=20`) 추가 |

---

## Consequences

### 긍정

- `ApiResponse` 계약이 모든 큐 엔드포인트에서 일관되게 적용됨 (join·status·stream·exit)
- 로컬 프로파일에서도 Bearer 토큰 인증이 동작하므로 시뮬레이터를 포함한 E2E 테스트가 가능해짐
- 예상 대기시간이 실제 처리 설정값 기반으로 계산되어 신뢰 가능한 값을 표시함
- 배포 환경(prod 프로파일 고정)에서도 큐 설정이 의도대로 반영됨

### 부정 · 수용

- `LocalSecurityConfig`에 JWT 필터가 추가되어 로컬 환경에서도 유효한 Bearer 토큰이 필요함. `X-Fan-Id` 헤더 방식은 여전히 fallback으로 동작하므로 Swagger·curl 테스트에는 영향 없음
- `application-prod.yml`의 큐 설정(`timeout=15s`)은 시연 목적으로 조정된 값임. 실서비스 트래픽 규모에 따라 `processing-timeout-seconds`와 `max-concurrent-processing`을 재산정해야 함

### 불변

| 규칙 | 내용 |
| --- | --- |
| 모든 REST 엔드포인트 응답 구조 | `ApiResponse<T>` 래퍼 필수 (stream·204 제외) |
| 로컬 보안 설정 변경 시 | JWT 필터 포함 여부를 시뮬레이터 연동 관점에서 반드시 검토 |
| 대기시간 계산식 | `position × (processingTimeoutSeconds / maxConcurrentProcessing)` |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| SSE 채택 결정 | [ADR-010](./ADR-010-queue-realtime-notification-sse.md) |
| 대기열 Redis ZSET 저장 | [ADR-012](./ADR-012-queue-storage-redis-zset.md) |
| 대기열 불변식 | [invariants-and-state-machines.md §6](../state/invariants-and-state-machines.md) |
| Blue/Green 배포 · systemd 구조 | [aws-phase3-runbook.md](../operations/aws-phase3-runbook.md) |
