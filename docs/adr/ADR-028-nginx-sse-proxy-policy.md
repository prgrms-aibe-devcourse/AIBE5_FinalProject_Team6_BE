# ADR-028: SSE endpoint를 위한 Nginx HTTP/1.1 · buffering off · limit_conn 예외 정책

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-20 |
| **구현 완료일** | 2026-06-21 (PR #411 — HTTP/1.1, PR #419 — limit_conn 제거) |
| **선행 ADR** | [ADR-010 SSE 실시간 알림](./ADR-010-queue-realtime-notification-sse.md) · [ADR-008 Blue/Green 배포](./ADR-008-nginx-bluegreen-deployment.md) |
| **관련** | [nginx/fandrops-location.conf](../../nginx/fandrops-location.conf) · [current-infra-state.md §8](../operations/aws/current-infra-state.md) · [k6 s05 결과](../operations/k6/k6-realfinal-result.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

Nginx가 SSE(Server-Sent Events) 엔드포인트를 안정적으로 프록시하기 위해 **HTTP/1.1 업그레이드, 버퍼링 비활성화, limit_conn 예외** 세 가지 정책을 SSE 전용 `location` 블록에 적용한다.

---

## Context

### SSE 엔드포인트

| 항목 | 값 |
| --- | --- |
| 경로 | `GET /api/v1/queue/stream` |
| 프로토콜 | Server-Sent Events (ADR-010 채택) |
| k6 s05 목표 | 2,000 동시 SSE 연결 유지 |
| SLO | 연결 수립 성공률 100%, 2,000연결 안정 유지 |

### 문제 발생 이력

SSE 엔드포인트를 일반 API와 동일한 Nginx 설정으로 운영하면 두 가지 독립적인 문제가 발생했다.

**문제 1 — SSE 연결이 첫 청크에서 즉시 종료됨 (PR #411 이전):**

Nginx의 기본 upstream 프로토콜은 HTTP/1.0이다. HTTP/1.0은 chunked transfer encoding을 지원하지 않는다. SSE는 `Transfer-Encoding: chunked`로 지속 연결을 유지하므로 Nginx가 첫 응답 청크 수신 후 연결을 닫아버린다.

**문제 2 — k6 s05 실행 시 2,100 VU가 전원 연결 거부됨 (PR #419 이전):**

`/api/v1/queue/stream` location에 `fandrops_sse` connection zone(`limit_conn`)이 적용되어 있었다. k6를 GitHub Actions runner에서 실행할 경우 모든 VU가 동일한 단일 public IP에서 연결을 시도한다. `limit_conn`이 IP 기준으로 동시 연결 수를 제한하므로 GHA 단일 IP가 제한에 걸려 2,100 VU의 연결이 모두 거부되었다.

---

## 방안 비교

### 문제 1: HTTP 프로토콜 버전 — HTTP/1.0 유지 vs HTTP/1.1 업그레이드

**기존 (기각):**

```nginx
# HTTP/1.0 기본값 — chunked encoding 미지원
proxy_pass http://fandrops_backend;
```

`proxy_http_version`을 명시하지 않으면 Nginx는 업스트림과 HTTP/1.0으로 통신한다. HTTP/1.0은 `Content-Length` 헤더가 없으면 연결 종료로 응답 끝을 판단하므로 SSE 스트림이 정상 동작하지 않는다.

**채택:**

```nginx
proxy_http_version 1.1;
proxy_set_header Connection "";
```

HTTP/1.1은 chunked transfer encoding을 지원한다. `Connection: ""` 헤더로 hop-by-hop 헤더 전파를 방지한다. SSE 스트림이 Spring Boot에서 보내는 청크를 끊기지 않고 클라이언트까지 전달한다.

### 문제 2: SSE connection limit — limit_conn 유지 vs 예외 처리

**기존 (기각):**

```nginx
location /api/v1/queue/stream {
    limit_conn fandrops_sse 50;  # IP당 50연결 제한
    ...
}
```

`limit_conn`은 Nginx에서 IP 단위로 동시 연결 수를 제한한다. GHA runner는 단일 public IP로 나가므로 2,100 VU가 모두 같은 IP → 제한에 즉시 도달.

**A안: limit_conn 값만 상향 조정 (기각):**

```nginx
limit_conn fandrops_sse 3000;  # 임의 상한 설정
```

기각 이유: GHA IP 공유 특성상 어떤 숫자를 설정해도 실행 환경에 따라 다시 문제가 발생할 수 있다. 임의 숫자를 운영에 남기는 것보다 SSE location에서 connection limit 자체를 제거하는 것이 명확하다.

**B안: SSE location에서 limit_conn 제거 ✅ 채택:**

```nginx
location /api/v1/queue/stream {
    # limit_conn 없음 — SSE 장기 연결 특성상 IP 기준 제한 비적합
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    ...
}
```

기각 이유 없음 — 연결 수 보호는 서버 레벨(worker_connections, ulimit)과 애플리케이션 레벨(`SseEmitterRegistry` 관리)로 처리한다. Nginx IP 기준 연결 제한은 SSE 패턴에 맞지 않는다.

일반 API(`/api/v1/orders`, `/api/v1/payments/*`)의 `limit_conn`은 영향 없이 유지된다.

---

## Decision

**SSE 전용 `location /api/v1/queue/stream` 블록에 세 가지 정책을 적용한다.**

### 하위 결정 1: `proxy_http_version 1.1` + `proxy_set_header Connection ""`

- **근거:** HTTP/1.1 chunked transfer encoding 활성화 → SSE 스트림 정상 전달
- **적용 범위:** SSE location 블록 전용 (일반 API는 기본값 유지)

### 하위 결정 2: `proxy_buffering off`

- **근거:** Nginx의 기본 응답 버퍼링이 활성화되면 SSE 이벤트가 버퍼가 찰 때까지 클라이언트에 전달되지 않는다. `proxy_buffering off`로 청크가 생성될 때마다 즉시 클라이언트로 전달한다
- **적용 범위:** SSE location 블록 전용

### 하위 결정 3: SSE location에서 `limit_conn` 제거

- **근거:** GHA 단일 IP → 2,100 VU 전원 연결 거부 문제. IP 기준 연결 제한은 SSE 장기 연결 패턴에 적합하지 않다
- **적용 범위:** `/api/v1/queue/stream` location만 제거. 다른 endpoint의 `limit_conn`은 유지

**결과 설정 (SSE location 핵심 부분):**

```nginx
location /api/v1/queue/stream {
    proxy_pass http://fandrops_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_read_timeout 3600s;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    # limit_conn 없음
}
```

---

## Consequences

### 긍정

- SSE 스트림이 Nginx를 통과해 정상 전달 — chunked encoding 문제 완전 해소
- k6 s05 2,000 동시 연결 SLO 달성
- `proxy_buffering off`로 SSE 이벤트가 버퍼 지연 없이 클라이언트에 즉시 전달

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| SSE endpoint 연결 수 Nginx 레벨 제한 없음 | limit_conn 제거 | worker_connections(8192), OS ulimit, 앱 `SseEmitterRegistry`로 상한 관리 |
| proxy_buffering off 메모리 | 버퍼 미사용 → Nginx 메모리 소비 방식 변경 | SSE 메시지 크기가 작고(수십~수백 바이트) 빈도가 낮아 실질적 메모리 영향 없음 |
| HTTP/1.1 keep-alive 업스트림 | Connection "" 헤더 필요 | `proxy_set_header Connection ""`로 keep-alive 헤더 전파 방지 처리 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| SSE location | `proxy_http_version 1.1`, `proxy_buffering off` 유지 필수 |
| 일반 API | `limit_conn` 설정 변경 없음 (order/queue/payment zone 유지) |
| SSE 연결 수 보호 | OS `ulimit -n 65536` + Nginx `worker_connections 8192` 유지 |

---

## 검증

- [x] PR #411 — `proxy_http_version 1.1` 적용 후 SSE 연결 즉시 종료 버그 해소 확인
- [x] PR #419 — `limit_conn` 제거 후 k6 s05 2,000 연결 성공
- [x] s05 SLO 달성: 2,000 동시 SSE 연결 유지 (k6-realfinal-result.md)
- [x] 일반 API `limit_conn` 동작에 변화 없음 확인

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| Nginx 설정 (location 블록) | [nginx/fandrops-location.conf](../../nginx/fandrops-location.conf) |
| 현행 인프라 (Nginx rate-limit zones) | [current-infra-state.md §8](../operations/aws/current-infra-state.md) |
| SSE 프로토콜 채택 근거 | [ADR-010](./ADR-010-queue-realtime-notification-sse.md) |
| k6 s05 SSE 연결 결과 | [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
