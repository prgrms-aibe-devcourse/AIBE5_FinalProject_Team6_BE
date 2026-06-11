# AWS Phase 3 고도화 결과

> **관련:** [aws-phase2-runbook.md](./aws-phase2-runbook.md) · [observability-metrics.md](./observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

Phase 2(자동화·관측·트래픽 제어)에서 Phase 3(고도화·FE 연동·부하 테스트 준비)로 이행한 결과를 기록한다.
팀원이 모니터링 구성과 부하 테스트 설계를 이해하거나, Phase 4 k6 SLO 검증 작업을 설계할 때 이 문서를 참고한다.

---

## 1. 개요

| 항목 | 내용 |
| --- | --- |
| 대상 환경 | AWS 단일 prod (Phase 2와 동일) |
| Phase 3 범위 | Redis 관측 추가 · DNS 설정 · Redis AUTH 보안 강화 · S3 CORS · k6 부하 테스트 스크립트 · Blue/Green 무중단 배포 |
| 현재 상태 | **Phase 3 핵심 완료** |
| 미완료 | Grafana 커스텀 메트릭 알람 NoData — 형성빈·표지민 MeterRegistry 등록 후 활성화 예정 (#191) |

**Phase 3에서 구현한 항목 요약:**

| 항목 | 관련 PR | 방식 |
| --- | --- | --- |
| Grafana SLO 대시보드 Redis 패널 추가 | #183 | fandrops-slo.json 패널 2개 추가 |
| Lettuce Micrometer 메트릭 활성화 | #185, #187 | ClientResources 명시적 Bean 등록 + 메트릭 이름 수정 |
| k6 부하 테스트 스크립트 5종 | #115 | infra/k6/scenarios/ + lib/ |
| DNS api.fandrops.site | — | 가비아 A 레코드 추가 |
| Redis AUTH Token 활성화 | #197 | setup-redis-auth.yml workflow_dispatch |
| S3 CORS 설정 | #198 | setup-s3-cors.yml workflow_dispatch |
| **Blue/Green 무중단 배포** | #221, #222 | systemd 이중 슬롯 + Nginx active.conf 포트 스위칭 |

---

## 2. Grafana SLO 대시보드 Redis 패널 추가

### 2-1. 배경

Phase 2에서 Grafana SLO 대시보드에 HTTP·JVM·DB 관련 패널은 존재했으나, Redis 관련 패널이 없었다.
캐시 응답 지연이나 Redis 명령 급증이 발생해도 대시보드에서 즉시 인지할 수 없는 관측 공백 상태였다.
Phase 3에서 Redis Read P95가 `< 120ms` SLO에 영향을 주는 핵심 경로임을 확인하고 패널을 추가했다.

### 2-2. 추가된 패널

| 패널 ID | 제목 | 지표 |
| --- | --- | --- |
| 7 | Redis 명령 P95 레이턴시 | `lettuce_command_completion_seconds_bucket` histogram_quantile P95/P50 |
| 8 | Redis 명령 처리율 (ops/s) | `lettuce_command_completion_seconds_count` by command |

**SLO 기준 (패널 7 임계값):**

| 색상 | 기준 |
| --- | --- |
| 녹색 | < 10ms |
| 노란색 | 10ms ~ 50ms |
| 빨간색 | > 50ms |

### 2-3. 구성 파일

```
infra/monitoring/grafana/provisioning/dashboards/fandrops-slo.json
```

Grafana 프로비저닝 방식으로 관리하므로 EC2 재배포 시에도 패널이 자동 복원된다.
패널 추가 후 `deploy-monitoring.yml` 워크플로우를 실행해야 EC2에 반영된다.

---

## 3. Lettuce Micrometer 메트릭 활성화

### 3-1. 배경

Redis 패널을 추가한 뒤 Prometheus에서 `lettuce_command_completion_seconds_*` 메트릭이 수집되지 않는 문제가 발견됐다.
`spring-boot-starter-data-redis`와 Actuator가 모두 존재함에도 Lettuce 메트릭 자동 등록이 동작하지 않았다.

**원인:** Spring Boot의 `LettuceMetricsAutoConfiguration`은 `ClientResources` Bean이 없을 때 자동으로 Micrometer를 연결하도록 설계됐으나, 특정 조건에서 자동 등록이 누락됐다. 명시적으로 `ClientResources` Bean을 등록해야 안정적으로 동작한다.

### 3-2. 해결 방법

`apps/api-server/src/main/java/com/fandrops/ops/LettuceMetricsConfig.java` 신규 생성:

```java
@Configuration
public class LettuceMetricsConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public ClientResources clientResources(MeterRegistry meterRegistry) {
        return ClientResources.builder()
                .commandLatencyRecorder(
                        new MicrometerCommandLatencyRecorder(meterRegistry, MicrometerOptions.create()))
                .build();
    }
}
```

`MicrometerCommandLatencyRecorder`가 모든 Lettuce 명령 완료 시 레이턴시를 히스토그램으로 기록한다.
`@ConditionalOnMissingBean`으로 다른 모듈이 `ClientResources`를 이미 등록한 경우 충돌하지 않는다.

### 3-3. percentiles-histogram 활성화

`_bucket` 메트릭(히스토그램)이 Prometheus에 노출되려면 `application-prod.yml`에 설정이 필요하다:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        lettuce.command.completion: true   # ← 이 줄 추가
```

`percentiles-histogram: true`가 없으면 `_sum`, `_count`, `_max`만 수집되고 `_bucket`이 없어 `histogram_quantile(P95)` 계산이 불가능하다.

### 3-4. 검증 방법

배포 후 `http://api.fandrops.site:9090` Prometheus에서 검색:

```
lettuce_command_completion_seconds_bucket
```

버킷 메트릭이 조회되면 정상. Grafana 패널 7·8에 데이터가 표시된다.

> **체크리스트 추가:** Grafana 쿼리 작성 전 반드시 Prometheus(`api.fandrops.site:9090`)에서 실제 메트릭 이름을 먼저 확인한다. 로컬 `/actuator/prometheus`와 배포 환경의 메트릭 이름이 다를 수 있다.

---

## 4. DNS 설정 (api.fandrops.site)

### 4-1. 배경

Grafana(`api.fandrops.site:3000`)와 Prometheus(`api.fandrops.site:9090`)에 외부에서 접근하려면 `api.fandrops.site` 서브도메인이 EC2에 연결돼야 한다.
Phase 2까지는 EC2 Public IP(`43.203.3.196`)로만 접근 가능했고, 도메인 접근이 불가했다.

**FE/BE 도메인 분리 구조:**

| 도메인 | 대상 | 배포 방식 |
| --- | --- | --- |
| `fandrops.site` | 프론트엔드 | Vercel |
| `api.fandrops.site` | 백엔드 EC2 (Nginx) | EC2 43.203.3.196 |

### 4-2. 적용 방법

가비아 DNS 관리 → A 레코드 추가:

| 타입 | 호스트 | IP | TTL |
| --- | --- | --- | --- |
| A | api | 43.203.3.196 | 300 |

적용 후 `api.fandrops.site:8080/actuator/health`, `api.fandrops.site:3000`(Grafana), `api.fandrops.site:9090`(Prometheus) 접근 확인.

---

## 5. Redis AUTH Token 활성화

### 5-1. 배경

Phase 1에서 ElastiCache Redis는 VPC Security Group으로 네트워크 격리는 됐으나, AUTH Token이 비활성화 상태였다.
SG가 뚫리는 경우 패스워드 없이 Redis 명령을 실행할 수 있는 구조였다.
Phase 3에서 Defense in Depth 원칙으로 AUTH Token을 추가 적용했다.

### 5-2. 동작 원리

```
앱 시작
  └─ Lettuce가 ElastiCache 엔드포인트에 TLS 연결
       └─ AUTH <REDIS_PASSWORD> 전송
            └─ ElastiCache: 토큰 일치 → OK → 이후 명령 처리
```

패스워드 없이 연결하면 `NOAUTH Authentication required` 에러 반환. AUTH Token 활성화 후 앱에 `REDIS_PASSWORD` 환경변수가 없으면 접속 불가.

### 5-3. 적용 순서 (`setup-redis-auth.yml`)

| 단계 | 작업 |
| --- | --- |
| Step 1 | SSM RunCommand — EC2 `/etc/fandrops/fandrops-prod.conf`에 `REDIS_PASSWORD` upsert |
| Step 2 | AWS CLI — `modify-replication-group --auth-token-update-strategy ROTATE` |
| Step 3 | ElastiCache `MODIFYING → available` 폴링 (24×15s, 최대 6분) |
| Step 4 | SSM RunCommand — `systemctl restart fandrops` |
| Step 5 | SSM RunCommand — `/actuator/health` UP 헬스체크 |

**왜 `ROTATE`인가:**
ElastiCache AUTH 최초 설정에는 반드시 `ROTATE` 전략을 사용해야 한다.
`SET`은 기존 토큰을 교체할 때만 사용 가능하며, AUTH가 없는 상태에서 `SET`을 사용하면 `InvalidParameterValue` 에러가 발생한다.

**토큰 교체 시:**
나중에 패스워드를 바꿀 때도 동일하게 `ROTATE` 전략을 사용한다.
`ROTATE`는 기존 토큰 + 새 토큰을 일정 시간 동시 허용하므로 앱 재시작 전까지 기존 토큰으로도 연결 유지된다.

### 5-4. REDIS_PASSWORD 패스워드 규칙

ElastiCache AUTH Token 문자 제한: `/`, `@`, `"`, 공백 사용 금지.
영숫자 + `-` 조합 권장. PowerShell에서 생성 예시:

```powershell
$bytes = New-Object Byte[] 32
[Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes) -replace '[/=]','' -replace '\+','-'
```

생성한 값은 GitHub Secret `REDIS_PASSWORD`로 등록.

### 5-5. application-prod.yml 설정

```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD:}   # 빈 문자열 기본값 — AUTH 미설정 환경도 기동 가능
```

`REDIS_PASSWORD` 환경변수가 없으면 빈 문자열로 AUTH 없이 동작한다 (로컬 개발 환경 호환).
prod에서는 환경파일에 `REDIS_PASSWORD=<값>`이 있어야 AUTH Token 연결이 성공한다.

---

## 6. S3 CORS 설정

### 6-1. 배경

표지민님이 담당하는 배너 이미지 업로드(`GET /api/v1/admin/uploads/presign`) 기능이 Pre-signed URL 방식으로 구현될 예정이다.
Pre-signed URL 방식은 프론트엔드 브라우저가 직접 S3로 `PUT` 요청을 보내므로, S3 버킷에 CORS 정책이 없으면 브라우저가 차단한다.

**Pre-signed URL 업로드 흐름:**

```
1. GET /api/v1/admin/uploads/presign?filename=xxx
   → { uploadUrl, imageUrl } 응답 (만료 15분)

2. 브라우저가 uploadUrl(S3 Pre-signed URL)로 PUT 직접 전송
   → CORS 정책으로 fandrops.site 오리진 허용됨

3. imageUrl을 배너 등록 API에 사용
```

### 6-2. 적용된 CORS 정책

| 항목 | 값 |
| --- | --- |
| AllowedOrigins | `https://fandrops.site`, `http://localhost:3000` |
| AllowedMethods | `PUT` |
| AllowedHeaders | `*` |
| ExposeHeaders | `ETag` |
| MaxAgeSeconds | `3000` |

`localhost:3000`을 포함한 이유: 팀원 로컬 개발 환경에서 admin 업로드 기능 테스트 시 브라우저 CORS 차단을 방지하기 위함.
`ETag`를 `ExposeHeaders`에 포함한 이유: 업로드 완료 후 프론트엔드가 ETag를 통해 업로드 성공 여부를 확인할 수 있도록 한다.

### 6-3. 적용 방법 (`setup-s3-cors.yml`)

`workflow_dispatch` + `confirm: yes` 입력 가드.
`aws s3api put-bucket-cors` 실행 후 `get-bucket-cors`로 적용 결과를 출력한다.

---

## 7. k6 부하 테스트 스크립트

### 7-1. 구성 위치

```
infra/k6/
├── lib/
│   ├── auth.js          # BASE_URL, 인증 헤더
│   ├── sse.js           # SSE 대기열 접근 토큰 획득 헬퍼
│   └── thresholds.js    # SLO 기준 임계값 (Write/Read/Payment)
├── scenarios/
│   ├── 01_order_concurrency.js   # 주문 동시성 기준선
│   ├── 02_feed_read.js           # 피드 조회 Read P95
│   ├── 03_payment_confirm.js     # 결제 확인 흐름 (Wiremock)
│   ├── 04_drop_spike.js          # 드롭스 스파이크
│   ├── 05_sse_queue.js           # SSE 대기열 연결 안정성
│   └── 06_workload_model.js      # 통합 워크로드 모델 (혼합 부하)
├── wiremock/
│   └── mappings/                 # Toss PG 모킹 stub 4종 (성공·타임아웃·실패·지연)
└── seed/
    ├── fans.csv                  # VU 파라미터화용 fan_id 목록
    ├── orders.json               # 03 결제 시나리오 RESERVED 주문 픽스처
    └── seed.sql                  # product·inventory·artist·fan 기초 INSERT
```

### 7-2. 시나리오별 목표

| 파일 | 목표 | VU / 부하 | SLO 기준 |
| --- | --- | --- | --- |
| `01_order_concurrency.js` | 200 VU 동시 주문 → 재고 100개 → RESERVED 100건, 오버셀 0건 | 200 VU shared-iterations | Write P95 < 300ms |
| `02_feed_read.js` | GET /artists/1/feeds Read P95 기준선 측정 | 50 VU ramping | Read P95 < 120ms |
| `03_payment_confirm.js` | POST /payments/toss/confirm — Wiremock PG 모킹 | 50 VU | Payment P95 < 3s |
| `04_drop_spike.js` | 0 → 1,000 VU 30초 급상승, 오버셀 0건 | ramping 0→1000→0 | Write P95 < 300ms |
| `05_sse_queue.js` | Nginx worker_connections · JVM FD 한계 검증 | 1,000→1,800→2,100 VU | 429 계약 확인 |
| `06_workload_model.js` | 피드 60% · 대기열 20% · 주문 15% · 결제 5% 혼합 부하 — 실사용 패턴 재현 | ramping 0→300 VU | Write P95 < 300ms, Read P95 < 120ms |

### 7-3. SLO 임계값 (`lib/thresholds.js`)

```javascript
export const WRITE_THRESHOLDS   = { http_req_duration: ['p(95)<300'],  http_req_failed: ['rate<0.001'] };
export const READ_THRESHOLDS    = { http_req_duration: ['p(95)<120'],  http_req_failed: ['rate<0.001'] };
export const PAYMENT_THRESHOLDS = { http_req_duration: ['p(95)<3000'], http_req_failed: ['rate<0.01']  };
```

### 7-4. 실행 방법

서버에서 직접 실행 (Prometheus Remote Write 출력 포함):

```bash
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8081 \
  -e FAN_POOL_SIZE=1000 \
  scenarios/01_order_concurrency.js
```

`FAN_POOL_SIZE`: VU별로 고유 fan_id를 뽑을 풀 크기. `infra/k6/seed/fans.csv`에 해당 수만큼 fan 레코드가 사전 삽입돼 있어야 한다.

`--out experimental-prometheus-rw`로 k6 메트릭을 Prometheus에 실시간 전송 → Grafana에서 부하 테스트 결과를 SLO 패널과 함께 조회할 수 있다.

### 7-5. 사전 준비 (Phase 4 실행 전)

| 항목 | 내용 |
| --- | --- |
| DB seed | `infra/k6/seed/seed.sql` 실행 후 각 시나리오 사전 준비 확인 |
| 01·02·04 시나리오 | `FAN_POOL_SIZE=1000` 환경변수 지정, `infra/k6/seed/fans.csv` 기준 fan 레코드 사전 삽입 |
| 03번 결제 시나리오 | Wiremock 서비스 기동 + `TOSS_API_BASE_URL=http://localhost:8090` 앱 재기동, `infra/k6/wiremock/` 참고 |
| 05번 SSE 시나리오 | Nginx `worker_connections ≥ 2048`, JVM `ulimit -n ≥ 8192` 확인 |
| Baseline 실행 | 튜닝 전 1회 실행해 기준선 수치 확보 |

---

## 8. Blue/Green 무중단 배포 (Phase 4 시작 전 적용)

### 8-1. 배경

Phase 2 CD는 `systemctl restart fandrops`로 배포했다. Spring Boot Graceful Shutdown(30s) 설정이 있어도 재시작 구간에 새 요청을 받지 못하는 30~60초 다운타임이 발생했다.
Phase 4 부하 테스트에서 배포 중 k6를 동시에 실행해 5xx 0건을 증명하려면 무중단 배포가 필수다.
ALB가 없는 예산 제약 환경에서 Nginx upstream 포트 스위칭으로 동일 효과를 구현한다.

**왜 ALB를 쓰지 않는가:**
ALB 고정 요금만 ~22,600원/월 → 현재 예산(90,000원) 초과. 아키텍처 의사결정 전체는 [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) 참고.

### 8-2. 구조

```
EC2 1대
├── Nginx :80
│     └─ upstream fandrops_backend
│           include /etc/nginx/fandrops-active.conf  ← 배포 시 이 파일만 교체
├── Spring Boot blue  :8081  ← 평상시 active
└── Spring Boot green :8082  ← 배포 시만 기동, 전환 후 종료
```

배포 시에만 두 프로세스가 동시에 존재하고, 전환 완료 후 구 슬롯이 종료된다. 상시 운영은 단일 프로세스다.

### 8-3. 핵심 구성 파일

| 파일 | 역할 |
| --- | --- |
| `/etc/systemd/system/fandrops-blue.service` | Spring Boot blue 슬롯 (:8081), `-Xmx768m` |
| `/etc/systemd/system/fandrops-green.service` | Spring Boot green 슬롯 (:8082), `-Xmx768m` |
| `/etc/fandrops/active-slot` | 현재 active 슬롯 기록 (`blue` 또는 `green`) |
| `/etc/nginx/fandrops-active.conf` | 현재 upstream 포트 정의, 배포 스크립트가 교체 |

### 8-4. t3.small 메모리 관리

기존 단일 프로세스: `-Xmx1024m`. 두 프로세스 동시 기동 시 heap 2GB → OOM 위험.
각 슬롯을 `-Xmx768m`으로 설정 → 동시 기동 peak 1.5GB heap + OS 300MB ≈ 1.8GB, t3.small 2GB 내 수용.

### 8-5. EC2 적용 절차

EC2에 SSM Session Manager로 접속해 아래를 순서대로 적용한다.

**Step 1 — JAR·슬롯 파일 준비**

```bash
sudo cp /opt/fandrops/app.jar /opt/fandrops/blue.jar
sudo chown fandrops:fandrops /opt/fandrops/blue.jar
echo "blue" | sudo tee /etc/fandrops/active-slot
```

**Step 2 — systemd 유닛 2개 생성**

```bash
sudo tee /etc/systemd/system/fandrops-blue.service <<'EOF'
[Unit]
Description=FANDROPS API Server (Blue)
After=network.target

[Service]
Type=simple
User=fandrops
EnvironmentFile=/etc/fandrops/fandrops-prod.conf
ExecStart=/usr/bin/java -Xms256m -Xmx768m \
  -jar /opt/fandrops/blue.jar \
  --server.port=8081 \
  --spring.profiles.active=prod
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=fandrops-blue

[Install]
WantedBy=multi-user.target
EOF

sudo tee /etc/systemd/system/fandrops-green.service <<'EOF'
[Unit]
Description=FANDROPS API Server (Green)
After=network.target

[Service]
Type=simple
User=fandrops
EnvironmentFile=/etc/fandrops/fandrops-prod.conf
ExecStart=/usr/bin/java -Xms256m -Xmx768m \
  -jar /opt/fandrops/green.jar \
  --server.port=8082 \
  --spring.profiles.active=prod
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=fandrops-green

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable fandrops-blue
sudo systemctl start fandrops-blue
```

**Step 3 — Nginx active.conf 생성 및 location.conf 수정**

```bash
# upstream 파일 생성
sudo tee /etc/nginx/fandrops-active.conf <<'EOF'
upstream fandrops_backend {
    server 127.0.0.1:8081;
    keepalive 32;
}
EOF

# fandrops-location.conf: proxy_pass http://127.0.0.1:8080 → upstream 방식으로 교체
# include /etc/nginx/fandrops-active.conf; 추가
# proxy_pass http://fandrops_backend; 로 변경
# proxy_next_upstream error timeout http_502 http_503; 추가

sudo nginx -t && sudo systemctl reload nginx
```

**Step 4 — 기존 단일 서비스 중지**

```bash
sudo systemctl stop fandrops
sudo systemctl disable fandrops
```

**Step 5 — 헬스체크**

```bash
# blue 슬롯 직접
curl -s http://localhost:8081/actuator/health

# Nginx 경유
curl -s http://localhost:80/actuator/health
```

### 8-6. cd.yml 수정

기존 SSM RunCommand 배포 스크립트를 Blue/Green 방식으로 교체한다.
배포 스크립트 전문은 [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) §4-4 참고.

### 8-7. 배포 흐름 요약

```
1. 비활성 슬롯 S3에서 JAR 다운로드 + 기동
2. /actuator/health UP 확인 (최대 60초)
3. fandrops-active.conf → 새 포트로 교체
4. nginx -t 검증 → systemctl reload nginx
5. 구 슬롯 Graceful Shutdown (최대 30초)
6. active-slot 파일 갱신
```

헬스체크 실패 또는 nginx -t 오류 시 자동 롤백(새 슬롯 종료, 구 슬롯 계속 서비스).
롤백 시나리오 전체는 [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) §5 참고.

---

## 9. 트러블슈팅 기록

| # | 증상 | 원인 | 해결 |
| --- | --- | --- | --- |
| N | Grafana Redis 패널 NoData — Prometheus에 `lettuce_*` 없음 | `ClientResources` 명시적 Bean 미등록으로 Lettuce 메트릭 자동 등록 누락 | `LettuceMetricsConfig.java` 신규 생성, `ClientResources` Bean 명시 등록 |
| O | `lettuce_command_completion_seconds_bucket` 조회 안 됨 | `application-prod.yml` percentiles-histogram 키가 `lettuce.command.completion.delay`로 잘못 설정됨 | 키를 `lettuce.command.completion`으로 수정 (PR #187) |
| P | Redis AUTH 워크플로우 — `AccessDenied` | `fandrops-github-actions-role`에 `elasticache:ModifyReplicationGroup` 권한 없음 | IAM 인라인 정책 `fandrops-elasticache-auth-policy` 추가 |
| Q | Redis AUTH 워크플로우 — `InvalidParameterValue` (토큰 형식 오류) | base64 생성 패스워드에 `/` 문자 포함 — ElastiCache AUTH 불허 문자 | PowerShell에서 `/`, `=`, `+` 제거 후 재생성 |
| R | Redis AUTH 워크플로우 — `InvalidParameterValue` (SET 전략 오류) | `--auth-token-update-strategy SET` 사용 — AUTH 없는 상태에서 SET 불가 | `ROTATE`로 변경 (최초 설정은 반드시 ROTATE) |
| S | S3 CORS 워크플로우 — `AccessDenied` 발생 시 | `fandrops-github-actions-role`에 `s3:PutBucketCORS` 권한 없을 수 있음 | IAM 인라인 정책 `fandrops-s3-cors-policy` 추가 |
| T | CD 배포 실패 — `s3:PutObject AccessDenied` (`scripts/` prefix) | `fandrops-github-actions-role` IAM 정책이 `deploy/` prefix만 허용, `scripts/` prefix 없음 | cd.yml 스크립트 S3 경로를 `scripts/bluegreen-deploy.sh` → `deploy/bluegreen-deploy.sh`로 수정 (PR #222) |

---

## 10. 최종 DoD

### 완료

- [x] Grafana SLO 대시보드 Redis P95/P50 레이턴시 패널 추가 (패널 7)
- [x] Grafana SLO 대시보드 Redis ops/s 패널 추가 (패널 8)
- [x] Lettuce Micrometer 메트릭 활성화 (`LettuceMetricsConfig.java`)
- [x] `lettuce.command.completion` percentiles-histogram 활성화
- [x] DNS `api.fandrops.site` → EC2 43.203.3.196 A 레코드 등록
- [x] Redis AUTH Token 활성화 (`setup-redis-auth.yml`, ElastiCache ROTATE 전략)
- [x] S3 CORS 설정 (`setup-s3-cors.yml`, fandrops.site + localhost:3000 허용)
- [x] k6 부하 테스트 스크립트 5종 (`infra/k6/scenarios/`)
- [x] Blue/Green 무중단 배포 구조 설계 및 문서화
- [x] **Blue/Green 배포 EC2 적용** — systemd 유닛 2개 + Nginx active.conf + cd.yml 수정 (PR #221, #222, 2026-06-11)

### 미완료

- [ ] **Grafana 커스텀 메트릭 알람 NoData 해소** — Issue #191
  - `fandrops_orders_status` (형성빈) · `fandrops_outbox_pending` (표지민) MeterRegistry Gauge 등록 PR 머지 후
  - 지영재: Prometheus 수집 확인 + Grafana Alert Rule `NoData → Normal/Firing` 전환 검증

---

## 11. Phase 4 준비 사항

| 항목 | 담당 | 비고 |
| --- | --- | --- |
| k6 Baseline 실행 | 지영재 | 서버에서 직접, 시나리오별 P95 수치 기록 |
| k6 통합 워크로드 시나리오 | 지영재 | 01~05 조합 시나리오 스크립트 추가 |
| SLO 미달 항목 튜닝 | 전체 | Baseline 결과 기반 병목 식별 후 도메인 오너 대응 |
| Grafana 커스텀 알람 활성화 | 지영재 | #191 완료 후 Alert firing 테스트 |

---

## 12. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [aws-phase1-runbook.md](./aws-phase1-runbook.md) | VPC/EC2/RDS/Redis/Nginx 기초 구성 |
| [aws-phase2-runbook.md](./aws-phase2-runbook.md) | CI/CD 자동화 · 모니터링 · CloudWatch · Rate Limit |
| [aws-phase4-runbook.md](./aws-phase4-runbook.md) | k6 부하 테스트 · D 분산 실험 · SLO 튜닝 |
| [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) | Blue/Green 아키텍처 의사결정 · 배포 스크립트 · 롤백 시나리오 |
| [incident-response.md](./incident-response.md) | P0~P2 장애 대응 절차 |
| [observability-metrics.md](./observability-metrics.md) | SLO·메트릭·알람 기준 |
| [personas/jiyoungjae.md](../ai/personas/jiyoungjae.md) | SRE 담당 체크리스트 |
