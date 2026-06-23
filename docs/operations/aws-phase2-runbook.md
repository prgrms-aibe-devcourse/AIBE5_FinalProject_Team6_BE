# AWS Phase 2 운영 자동화 구축 결과

> **관련:** [aws-phase1-runbook.md](./aws-phase1-runbook.md) · [incident-response.md](./incident-response.md) · [observability-metrics.md](./observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

Phase 1(수동 배포·인프라 구축)에서 Phase 2(자동화·관측·트래픽 제어)로 이행한 결과를 기록한다.
팀원이 운영 자동화 흐름을 이해하거나, 동일 환경을 재현하거나, Phase 3·4 k6·SLO 검증 작업을 설계할 때 이 문서를 참고한다.

---

## 1. 개요

| 항목 | 내용 |
| --- | --- |
| 대상 환경 | AWS 단일 prod (Phase 1과 동일) |
| Phase 2 범위 | CD 자동화 · 모니터링 AWS 반영 · CloudWatch 로그 · Grafana Alert · Nginx Rate Limit |
| 현재 상태 | **Phase 2 핵심 완료** |
| 미완료 | Redis AUTH Token · k6 (Phase 4) |

**Phase 2에서 구현한 항목 요약:**

| 항목 | 관련 이슈 | 방식 |
| --- | --- | --- |
| GitHub Actions CD | #27 | CI 통과 후 자동 배포 (OIDC + SSM RunCommand) |
| Prometheus + Grafana EC2 배포 | #28, #31 | Docker Compose + workflow_dispatch |
| CloudWatch Logs | #30 | rsyslog + CloudWatch Agent + logrotate |
| Grafana Alert Rules + Gmail SMTP | #47 | 프로비저닝 파일 + Gmail App Password |
| Nginx 설정 파일 레포 관리 + 자동 배포 | #29 | `nginx/` 디렉터리 + deploy-nginx.yml |
| Nginx Rate Limit + SSE 연결 수 제한 | #94 | IP 기반 zone + limit_req / limit_conn |

---

## 2. GitHub Actions CI/CD 파이프라인

### 2-1. CI (`ci.yml`)

| 항목 | 내용 |
| --- | --- |
| 트리거 | `develop` push / PR |
| 작업 | `./gradlew clean build` (전체 빌드 + 테스트) |
| 결과 | JUnit 리포트 발행 (`dorny/test-reporter`) |

### 2-2. CD (`cd.yml`)

```
CI 성공
  → JAR 빌드 (--no-daemon, 테스트 생략)
  → AWS OIDC 인증 (AWS_ROLE_ARN Secret)
  → JAR → S3 업로드 (s3://<S3_BUCKET>/deploy/api-server.jar)
  → SSM RunCommand: S3 다운로드 → /opt/fandrops/app.jar → systemctl restart fandrops
  → SSM RunCommand: curl /actuator/health → UP 확인
```

**왜 이 방식인가:**

- **OIDC Role** — Access Key를 발급하지 않아도 GitHub Actions에서 AWS 인증 가능. 키 유출·순환 부담 없음.
- **workflow_run + CI 의존** — CI가 실패하면 CD가 실행되지 않으므로 깨진 빌드가 prod에 올라가지 않는다.
- **SSM RunCommand** — SSH 키 없이 EC2에 명령 실행. Phase 1에서 확립한 `/tmp` 경유 배포 흐름을 그대로 자동화했다.
- **헬스체크** — 배포 후 30초 대기 후 `/actuator/health` UP 확인. 실패 시 workflow 실패 처리 → 즉시 인지 가능.

**관련 Secret:**

| Secret 키 | 내용 |
| --- | --- |
| `AWS_ROLE_ARN` | `fandrops-github-actions-role` ARN |
| `S3_BUCKET` | `fandrops-prod-storage-495264909330-ap-northeast-2-an` |
| `EC2_INSTANCE_ID` | prod EC2 인스턴스 ID |

---

## 3. Prometheus + Grafana EC2 배포

### 3-1. 구성 위치

```
infra/monitoring/
├── prometheus/
│   └── prometheus.yml               # scrape 설정
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── prometheus.yml       # Prometheus datasource 자동 등록
        ├── dashboards/
        │   ├── dashboards.yml       # 대시보드 프로바이더
        │   └── fandrops-slo.json    # SLO 대시보드
        └── alerting/
            ├── rules.yml            # Alert Rules (P0/P1/P2)
            ├── contact-points.yml   # Gmail SMTP 수신자
            └── notification-policies.yml

compose.monitoring.yml               # Prometheus + Grafana 컨테이너 정의
```

### 3-2. 배포 방식 (`deploy-monitoring.yml`)

| 항목 | 내용 |
| --- | --- |
| 트리거 | `workflow_dispatch` (수동, `"yes"` 입력 확인) |
| 작업 | `infra/monitoring/` + `compose.monitoring.yml` → tarball → S3 업로드 → SSM RunCommand로 EC2 압축 해제 + `docker compose up` |
| 적용 시점 | EC2 교체 또는 모니터링 설정 변경 시 1회성 실행 |

**Prometheus scrape 설정:**

```yaml
scrape_configs:
  - job_name: "fandrops-api"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:8080"]
```

`host.docker.internal` — Docker 컨테이너(Prometheus)에서 EC2 호스트(Spring Boot :8080)를 참조하기 위한 호스트명. `compose.monitoring.yml`의 `extra_hosts`로 Linux EC2에서도 동작하도록 설정했다.

**왜 이 방식인가:**

- **Docker Compose on 단일 EC2** — Phase 1에서 Prometheus/Grafana는 별도 EC2를 검토했으나, t3.small 비용·팀 규모를 고려해 앱 EC2에 컨테이너로 함께 올렸다. (당시 t3.small 기준 결정. 현재 ec2-1은 t3.medium으로 스케일업됨)
- **프로비저닝 파일** — Grafana UI에서 수동으로 대시보드·알람을 설정하면 EC2 재배포 시 초기화된다. `infra/monitoring/` 하위 YAML/JSON 파일로 코드 관리하여 재현 가능하게 한다.

### 3-3. 접속 포트 (EC2 Security Group 추가 필요)

| 서비스 | 포트 |
| --- | --- |
| Prometheus | 9090 |
| Grafana | 3000 |

> EC2 SG에서 팀원 IP 또는 VPN 대역만 허용 권장 (공개 노출 최소화).

---

## 4. Grafana Alert Rules

`infra/monitoring/grafana/provisioning/alerting/rules.yml`에 프로비저닝.

### 4-1. 알람 목록

| 등급 | 룰 UID | 조건 | for |
| --- | --- | --- | --- |
| **P0** | `fandrops-5xx-p0` | 5xx 에러율 > 1% | 5m |
| **P0** | `fandrops-failed-order-p0` | FAILED 주문 건수 > 0 | 5m |
| **P1** | `fandrops-write-p95-p1` | Write P95 > 300ms (POST/PUT/PATCH/DELETE) | 10m |
| **P1** | `fandrops-outbox-pending-p1` | `fandrops_outbox_pending` > 1000 | 15m |
| **P1** | `fandrops-db-pool-p1` | HikariCP 활성 커넥션 / 최대 커넥션 > 80% | 5m |
| **P2** | `fandrops-cache-hit-p2` | 캐시 히트율 < 80% | 10m |

### 4-2. 알람 수신 (Contact Point)

| 항목 | 값 |
| --- | --- |
| 방식 | Gmail SMTP |
| 수신 주소 | `xxjyj1022@gmail.com` |
| Secret | `GMAIL_APP_PASSWORD` (GitHub Secret) |

**왜 Gmail App Password인가:**
Gmail 계정 비밀번호를 직접 쓰면 2단계 인증 우회 불가. App Password는 SMTP 전용 별도 발급 토큰으로 계정 보안을 유지하면서 알람 발송 가능. Secret으로 관리하므로 레포에 노출되지 않는다.

**커스텀 메트릭 (도메인 오너 PR 필요):**

`fandrops_orders_status`, `fandrops_outbox_pending`은 도메인 오너가 Micrometer Gauge로 등록해야 Grafana Alert이 데이터를 받는다. 현재 알람 룰은 정의됐으나 메트릭 미등록 시 `NoData` 상태로 유지된다.

---

## 5. CloudWatch Logs

### 5-1. 아키텍처

```
Spring Boot (systemd → SyslogIdentifier=fandrops)
  → journald
  → rsyslog (ForwardToSyslog=yes)
  → /var/log/fandrops/app.log   (rsyslog-fandrops.conf)
  → CloudWatch Agent
  → /fandrops/prod 로그 그룹 (AWS CloudWatch)
```

**왜 journald → rsyslog → 파일 경유인가:**

CloudWatch Agent의 journald 직접 수집은 AL2023에서 불안정했다 (에이전트가 journald socket을 직접 읽는 방식이 권한 문제 발생). rsyslog를 경유해 파일로 내보낸 뒤 파일을 수집하는 방식이 안정적이고 logrotate 적용도 쉽다.

### 5-2. 보관 정책

| 위치 | 보관 기간 | 정책 |
| --- | --- | --- |
| EC2 로컬 `/var/log/fandrops/app.log` | 7일 | logrotate (daily, rotate 7, compress) |
| EC2 journald | 7일 | `journald-retention.conf` SystemMaxUse |
| CloudWatch `/fandrops/prod` | **30일** | `put-retention-policy` (data-lifecycle.md 준수) |

**왜 30일인가:**
`docs/erd/data-retention-and-audit-policy.md` §5 — prod 로그 보관 기준 30일. CloudWatch 무한 보관 시 비용 증가.

### 5-3. 배포 방식 (`setup-cloudwatch.yml`)

| 트리거 | EC2 교체 또는 설정 변경 시 `workflow_dispatch` (1회성) |
| --- | --- |
| 설정 파일 위치 | `infra/cloudwatch/` |
| IAM 권한 | EC2 Role에 `CloudWatchAgentServerPolicy` 필요 (Phase 1 runbook §3-3 참고) |

---

## 6. Nginx 설정 파일 관리 및 자동 배포

### 6-1. 설정 파일 위치

```
nginx/
├── fandrops-zones.conf      → /etc/nginx/conf.d/fandrops-zones.conf   (limit_req_zone 정의)
└── fandrops-location.conf   → /etc/nginx/default.d/fandrops-location.conf (location 블록)
```

**왜 두 파일로 분리했는가:**

`limit_req_zone`은 `http {}` 블록 레벨에서 선언해야 한다. AL2023 기본 Nginx 구조에서 `conf.d/*.conf`는 `http {}` 안에 include되므로 zone 선언을 `fandrops-zones.conf`에 넣고, 실제 `limit_req` 적용은 `default.d/fandrops-location.conf` (server 블록 안 location)에 넣는다.

### 6-2. Rate Limit 정책 (`fandrops-zones.conf`)

| Zone | 대상 경로 | 속도 | Burst | 이유 |
| --- | --- | --- | --- | --- |
| `fandrops_order` | `POST /api/v1/orders` | 5r/s | 10 | 주문 생성 — 재고 비관락 TX 보호, DB 커넥션 고갈 방지 |
| `fandrops_queue` | `POST /api/v1/queue/join` | 10r/s | 20 | 대기열 진입 — Redis 부하 분산, 매크로 방어 |
| `fandrops_payment` | `POST /api/v1/payments/toss/confirm` | 5r/s | 10 | 결제 confirm — PG API 호출 보호, 중복 요청 차단 |
| `fandrops_sse` (연결 수) | `GET /api/v1/queue/stream` | IP당 3 연결 | — | SSE 장기 연결 — Nginx Worker 연결 수·메모리 보호 |

> **정책 오너십:** 임계값은 장성재가 결정한 정책 기준. Nginx/ALB 반영값은 지영재가 구현. 변경 시 양쪽 동시 리뷰 필요 (`architecture.md` § 경계 협업 참고).

### 6-3. SSE 전용 프록시 설정

```nginx
location /api/v1/queue/stream {
    limit_conn fandrops_sse 3;
    proxy_buffering off;      # 버퍼링 off — SSE 실시간 전달
    proxy_cache off;
    proxy_read_timeout 3600s; # 1시간 — 장기 연결 유지
}
```

SSE는 HTTP Keep-Alive 장기 연결이므로 `proxy_buffering off`와 긴 `proxy_read_timeout`이 필수다. 기본 60s timeout이면 연결이 중간에 끊긴다.

### 6-4. 배포 방식 (`deploy-nginx.yml`)

| 트리거 | `nginx/**` 변경 push 또는 `workflow_dispatch` |
| --- | --- |
| 작업 | `nginx/*.conf` → S3 업로드 → SSM RunCommand: 다운로드 → `/etc/nginx/` 복사 → `nginx -t` → `nginx -s reload` |

`nginx -t`로 문법 검증 후 reload하므로 설정 오류 시 현재 서비스를 중단하지 않는다.

### 6-5. 도메인 및 HTTPS

| 항목 | 값 |
| --- | --- |
| 도메인 | `fandrops.site` |
| HTTPS | Let's Encrypt certbot 적용 완료 |
| Nginx `server_name` | `fandrops.site` |

---

## 7. 트러블슈팅 기록

| # | 증상 | 원인 | 해결 |
| --- | --- | --- | --- |
| G | CD workflow에서 JAR 파일명 불일치 | `api-server-0.0.1-SNAPSHOT.jar` vs `api-server.jar` | `find ... ! -name "*plain*" \| head -1` 로 동적 탐색 |
| H | CloudWatch Agent journald 직접 수집 실패 | AL2023에서 journald socket 권한 문제 | rsyslog + 파일 경유 방식으로 전환 |
| I | Grafana Alert Rule NoData 상태 | `relativeTimeRange` 누락 | rules.yml에 `relativeTimeRange: {from: 600, to: 0}` 명시 |
| J | Prometheus 헬스체크 타임아웃 | Docker 기동 후 Grafana 준비 시간 부족 | 헬스체크 대기 20s → 60s 증가 |
| K | docker compose v2 없음 | AL2023 dnf repo에 `docker-compose-plugin` 없음 | GitHub Releases에서 바이너리 직접 다운로드 |
| L | Nginx `limit_req_zone` 적용 안 됨 | zone 선언을 `default.d/`에 넣으면 `server {}` 안 → `http {}` 레벨 위반 | `conf.d/fandrops-zones.conf`로 분리 |
| M | Nginx SSE 연결 중간 끊김 | 기본 `proxy_read_timeout 60s` | SSE location에 `proxy_read_timeout 3600s` 설정 |

---

## 8. 최종 DoD

### 완료

- [x] GitHub Actions CD (OIDC + SSM RunCommand, CI 통과 후 자동 배포)
- [x] Prometheus EC2 배포 (`infra/monitoring/prometheus/`, Docker Compose)
- [x] Grafana EC2 배포 (프로비저닝 파일 기반 — datasource·dashboard·alert 코드 관리)
- [x] Grafana SLO 대시보드 JSON (`fandrops-slo.json`)
- [x] Grafana Alert Rules P0/P1/P2 (`rules.yml`)
- [x] Grafana Gmail SMTP 알람 (`contact-points.yml`, `GMAIL_APP_PASSWORD` Secret)
- [x] CloudWatch Logs Agent (rsyslog → `/var/log/fandrops/app.log` → `/fandrops/prod`)
- [x] logrotate (7일 로컬) + CloudWatch 보관 30일
- [x] Nginx 설정 파일 레포 관리 (`nginx/`) + 자동 배포 워크플로우
- [x] Nginx Rate Limit (order 5r/s · queue 10r/s · payment 5r/s)
- [x] Nginx SSE 연결 수 제한 (IP당 3 연결)
- [x] HTTPS / certbot (Let's Encrypt, Nginx SSL 적용)

### 미완료

- [ ] **k6 부하 테스트 스크립트** — Issue #88, 마감 2026-06-07. Phase 4 SLO 검증의 핵심.
- [ ] **Redis AUTH Token** — `REDIS_PASSWORD` 환경변수 미설정 → AUTH 비활성화 상태. 보안 강화 필요 시 ElastiCache Modify + 환경파일 추가 필요.
- [ ] **Grafana 커스텀 메트릭 연동** — `fandrops_orders_status`, `fandrops_outbox_pending` 등은 도메인 오너가 Micrometer Gauge 등록 후 Alert 활성화됨.

---

## 9. Phase 3 TODO (지영재)

| 항목 | 비고 |
| --- | --- |
| **k6 부하 테스트 스크립트** | Issue #88. 주문 동시성·드롭 스파이크·Read P95 시나리오. 마감 2026-06-07 |
| Redis AUTH Token | `REDIS_PASSWORD` EC2 환경파일 추가 + ElastiCache AUTH 활성화 |
| S3 CORS 설정 | 도메인 `fandrops.site` 확정 완료. 파일 업로드 API 구현 시 버킷 CORS 규칙 추가 필요 |
| Grafana 커스텀 메트릭 알람 활성화 확인 | `fandrops_orders_status` (형성빈) · `fandrops_outbox_pending` (장성재) MeterRegistry 등록 PR 머지 후 Grafana NoData → Normal/Firing 전환 확인 |

---

## 10. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [aws-phase1-runbook.md](./aws-phase1-runbook.md) | VPC/EC2/RDS/Redis/Nginx 기초 구성 |
| [incident-response.md](./incident-response.md) | P0~P2 장애 대응 절차 |
| [failure-policy.md](./failure-policy.md) | Redis·DB·Outbox 장애 정책 |
| [observability-metrics.md](./observability-metrics.md) | SLO·메트릭·알람 기준 |
| [personas/jiyoungjae.md](../ai/personas/jiyoungjae.md) | SRE 담당 체크리스트 |
