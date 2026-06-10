# AWS Phase 4 부하 테스트 및 SLO 검증

> **관련:** [aws-phase3-runbook.md](./aws-phase3-runbook.md) · [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) · [observability-metrics.md](./observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

Phase 3(고도화·FE 연동·배포 안정화)에서 Phase 4(부하 테스트·SLO 측정·분산 검증·튜닝)로 이행하는 작업 계획과 결과를 기록한다.
팀원이 부하 테스트 방법과 결과를 이해하거나, Phase 5 발표 자료를 준비할 때 이 문서를 참고한다.

---

## 1. 개요

| 항목 | 내용 |
| --- | --- |
| 기간 | 2026-06-12 ~ 2026-06-22 |
| 목표 | SLO 달성 여부 수치 증명 + 분산 설계 검증 |
| 핵심 질문 | "드롭스 오픈런 상황에서 오버셀 0건, Write P95 < 300ms를 달성하는가?" |

**SLO 목표:**

| 지표 | 목표 |
| --- | --- |
| Write P95 (주문·결제) | < 300ms |
| Read P95 (피드·행사) | < 120ms |
| 5xx 에러율 | < 0.1% |
| 오버셀 | 0건 |
| 중복 결제 | 0건 |

**Phase 4 작업 순서:**

```
① Blue/Green 배포 EC2 적용 → Phase 3 §8 참고 (Phase 3 완료 항목)
② k6 Baseline 실행 — 단일 EC2, 튜닝 전 기준선 수치 확보
③ D 단기 실험 — EC2-2 기동, 분산 설계 검증, terminate
④ SLO 미달 항목 튜닝
⑤ Grafana 커스텀 메트릭 알람 활성화 (#191)
⑥ 최종 SLO 수치 측정 및 기록
```

---

## 2. Blue/Green 배포 EC2 적용

### 2-1. 배경

Phase 4 부하 테스트 중 배포가 발생할 수 있다. k6 실행 중 `systemctl restart`로 배포하면 30~60초 다운타임이 생겨 5xx가 발생하고 SLO 측정에 영향을 준다.
Phase 4 시작 전 Blue/Green 배포를 EC2에 적용해 배포 중에도 5xx 0건을 유지해야 한다.

### 2-2. 적용 절차

**Blue/Green EC2 적용 절차는 [aws-phase3-runbook.md §8](./aws-phase3-runbook.md#8-bluegreen-배포-ec2-적용) 에 통합되어 있다.**

Phase 3 §8 Step 1~5를 순서대로 따른다 (systemd 유닛 생성 → Nginx active.conf 전환 → 기존 fandrops.service 비활성화 → 헬스체크).

### 2-3. cd.yml 수정

기존 SSM RunCommand 배포 스크립트를 Blue/Green 방식으로 교체한다.
스크립트 전문은 [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) §4-4 참고.

---

## 3. k6 Baseline 실행

### 3-1. 목적

튜닝 전 현재 상태의 SLO 수치를 확보한다.
Baseline 없이 튜닝하면 "얼마나 개선됐는가"를 증명할 수 없다.

### 3-2. 실행 전 사전 준비

| 항목 | 확인 내용 |
| --- | --- |
| k6 설치 | EC2에 k6 설치 여부 확인 (`k6 version`) |
| DB seed | 각 시나리오 주석의 seed 조건 확인 |
| 01·04 시나리오 | `product id=1 (inventory.total_qty=100)`, `fan id=1` |
| 02 시나리오 | `artist_profile id=1`, `artist_feed` 20개, `user_follow fan_id=1 artist_id=1` |
| 03 시나리오 | Wiremock 기동 + `TOSS_API_BASE_URL=http://wiremock:8080` 앱 재기동 |
| 05 시나리오 | Nginx `worker_connections ≥ 2048`, JVM `ulimit -n ≥ 8192` |
| Prometheus Remote Write | EC2 Prometheus 주소: `http://localhost:9090/api/v1/write` |

k6 설치:

```bash
sudo dnf install https://dl.k6.io/rpm/repo.rpm -y
sudo dnf install k6 -y
k6 version
```

### 3-3. 시나리오별 실행

```bash
cd /opt/fandrops/k6   # infra/k6/ 를 EC2로 복사 또는 git clone

# 01. 주문 동시성 (오버셀 0건 핵심)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8080 \
  scenarios/01_order_concurrency.js

# 02. 피드 Read P95
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8080 \
  scenarios/02_feed_read.js

# 04. 드롭스 스파이크 (1,000 VU 급상승)
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8080 \
  scenarios/04_drop_spike.js

# 05. SSE 대기열 연결 안정성
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8080 \
  scenarios/05_sse_queue.js
```

### 3-4. Baseline 수치 기록표

시나리오 실행 후 아래 표를 채워 Baseline으로 보관한다.

| 시나리오 | Write/Read P95 | 5xx 에러율 | 오버셀 건수 | SLO 통과 여부 |
| --- | --- | --- | --- | --- |
| 01 주문 동시성 | ms | % | 건 | ✅/❌ |
| 02 피드 Read | ms | % | — | ✅/❌ |
| 04 드롭스 스파이크 | ms | % | 건 | ✅/❌ |
| 05 SSE 대기열 | — | % | — | ✅/❌ |

> Grafana 대시보드(`api.fandrops.site:3000`) 패널 1(RPS), 2(P95), 3(5xx 에러율)에서 확인.

---

## 4. D 단기 실험 — 분산 설계 검증

### 4-1. 목적

D 실험은 **운영 안정성 향상이 아닌 stateless 설계 검증**이 목적이다.

ALB 없이 EC2-2를 추가해도 EC2-1(Nginx)이 SPOF로 남아 실제 고가용성은 개선되지 않는다.
실험의 핵심 질문은 "요청이 EC2-1과 EC2-2 중 어느 서버에 가더라도 오버셀·중복결제가 발생하지 않는가?"이다.

**사전 코드 검증 결과:**

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| in-memory Queue | ✅ 안전 | `LocalWaitQueueRepository` → `@Profile("local")`, prod는 Redis |
| JWT Stateless | ✅ 안전 | `SessionCreationPolicy.STATELESS` |
| 재고 오버셀 방지 | ✅ 안전 | `reserveAtomic` DB 단일 UPDATE + `WHERE availableQty >= qty` |
| `@Version` 낙관락 | ✅ 있음 | `InventoryJpaEntity` |
| payment_key unique | ✅ 있음 | DB unique index |
| idempotency_key unique | ✅ 있음 | DB unique index |
| Payment 비관락 | ✅ 있음 | `@Lock(PESSIMISTIC_WRITE)` |
| **SseEmitterRegistry** | ⚠️ 한계 | in-memory ConcurrentHashMap — EC2 2대 환경에서 SSE 메시지 유실 가능 |

`SseEmitterRegistry` 한계: 정합성(오버셀·중복결제)에는 영향 없고 대기열 SSE 상태 업데이트가 간헐적으로 누락되는 UX 문제. 개선 방향: Redis Pub/Sub 브로드캐스트.

### 4-2. EC2-2 기동 절차

**AWS 콘솔 → EC2 → Launch Instance:**

| 항목 | 값 |
| --- | --- |
| AMI | Amazon Linux 2023 |
| Instance type | t3.micro (1 vCPU, 1GB RAM) |
| VPC | fandrops-prod VPC (동일) |
| Subnet | Private Subnet (EC2-1과 동일 가용영역 권장) |
| Security Group | EC2-1 SG에서 `:8080` inbound 허용 규칙 추가 |
| IAM Role | `fandrops-prod-ec2-role` (기존 역할 재사용) |
| Key pair | 없음 (SSM Session Manager 사용) |

**EC2-2 초기 설정 (SSM Session Manager):**

```bash
# Java 21 설치
sudo dnf install java-21-amazon-corretto -y

# 앱 디렉터리
sudo mkdir -p /opt/fandrops
sudo useradd -r fandrops
sudo chown fandrops:fandrops /opt/fandrops

# 환경파일 (EC2-1과 동일 내용)
sudo mkdir -p /etc/fandrops
sudo tee /etc/fandrops/fandrops-prod.conf <<'EOF'
# EC2-1의 /etc/fandrops/fandrops-prod.conf 내용 복사
EOF
sudo chmod 600 /etc/fandrops/fandrops-prod.conf

# JAR 배포
aws s3 cp s3://${S3_BUCKET}/deploy/api-server.jar /opt/fandrops/app.jar
sudo chown fandrops:fandrops /opt/fandrops/app.jar

# systemd 등록
sudo tee /etc/systemd/system/fandrops.service <<'EOF'
[Unit]
Description=FANDROPS API Server
After=network.target

[Service]
Type=simple
User=fandrops
EnvironmentFile=/etc/fandrops/fandrops-prod.conf
ExecStart=/usr/bin/java -Xms256m -Xmx512m \
  -jar /opt/fandrops/app.jar \
  --server.port=8080 \
  --spring.profiles.active=prod
Restart=on-failure
StandardOutput=journal
StandardError=journal
SyslogIdentifier=fandrops

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable fandrops
sudo systemctl start fandrops

# 헬스체크
curl -s http://localhost:8080/actuator/health
```

### 4-3. EC2-1 Nginx upstream에 EC2-2 추가

```bash
EC2_2_PRIVATE_IP="<EC2-2 Private IP>"  # AWS 콘솔에서 확인

sudo tee /etc/nginx/fandrops-active.conf <<EOF
upstream fandrops_backend {
    server 127.0.0.1:8081;          # EC2-1 blue
    server ${EC2_2_PRIVATE_IP}:8080; # EC2-2
    keepalive 32;
}
EOF

sudo nginx -t && sudo systemctl reload nginx
```

### 4-4. k6 분산 환경 검증 실행

```bash
# Baseline과 동일 시나리오를 분산 환경에서 재실행
k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8080 \
  scenarios/01_order_concurrency.js

k6 run --out experimental-prometheus-rw \
  -e BASE_URL=http://localhost:8080 \
  scenarios/04_drop_spike.js
```

**확인 항목:**
- 오버셀 0건 — DB `SELECT COUNT(*) FROM orders WHERE status = 'RESERVED'` = 재고 수량
- 중복 결제 0건 — `payment_key` unique constraint violation 없음
- Grafana 패널 1(RPS)에서 두 서버에 요청 분산 확인
- SSE 메시지 간헐적 유실 현상 확인 (예상된 한계, 기록)

### 4-5. 분산 결과 기록표

| 항목 | 단일 EC2 Baseline | 분산 환경 (D 실험) | 차이 |
| --- | --- | --- | --- |
| 오버셀 건수 | 0건 | 0건 | — |
| 중복 결제 건수 | 0건 | 0건 | — |
| Write P95 | ms | ms | ms |
| 5xx 에러율 | % | % | % |
| SSE 메시지 유실 | 없음 | 간헐적 발생 | ⚠️ 예상된 한계 |

### 4-6. EC2-2 Terminate

실험 완료 후 즉시 EC2-2를 종료한다.

```bash
# EC2-1 Nginx upstream에서 EC2-2 제거
sudo tee /etc/nginx/fandrops-active.conf <<'EOF'
upstream fandrops_backend {
    server 127.0.0.1:8081;
    keepalive 32;
}
EOF
sudo nginx -t && sudo systemctl reload nginx
```

AWS 콘솔 → EC2-2 → Instance State → **Terminate**.

---

## 5. SLO 튜닝

### 5-1. 튜닝 우선순위

Baseline 결과에서 SLO 미달 항목을 식별하고 도메인 오너와 협의한다.

| SLO 미달 시 | 예상 원인 | 대응 |
| --- | --- | --- |
| Write P95 > 300ms | RDS 커넥션 풀 부족 | HikariCP `maximumPoolSize` 증가 |
| Write P95 > 300ms | 주문 TX 대기 시간 | 비관락 타임아웃 조정 (`lock.timeout`) |
| Read P95 > 120ms | N+1 쿼리 | fetch join 또는 캐시 적용 |
| Read P95 > 120ms | Redis 캐시 미적용 | 피드 조회 캐싱 레이어 추가 |
| 5xx > 0.1% | Rate Limit 설정 과도 | Nginx zone burst 값 상향 |
| 5xx > 0.1% | 커넥션 풀 고갈 | DB/Redis 풀 사이즈 조정 |

### 5-2. JVM 튜닝 파라미터 참고

```bash
# GC 로그 활성화 (튜닝 판단용)
-Xlog:gc*:file=/var/log/fandrops/gc.log:time,uptime:filecount=5,filesize=20m

# G1GC 힌트 (기본값이지만 명시적 설정)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
```

### 5-3. DB 커넥션 풀 튜닝

`application-prod.yml`에서 조정:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 기본 10 → 부하 테스트 결과 보고 조정
      connection-timeout: 3000   # 3초 대기 후 에러
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## 6. Grafana 커스텀 메트릭 알람 활성화 (#191)

### 6-1. 선행 조건

형성빈·표지민의 MeterRegistry Gauge 등록 PR이 develop에 머지돼야 한다.

| 담당 | 메트릭 | 등록 위치 |
| --- | --- | --- |
| 형성빈 | `fandrops_orders_status{status="FAILED"}` | order 도메인 서비스 |
| 표지민 | `fandrops_outbox_pending` | modules/notification/notification-infrastructure |

### 6-2. 활성화 절차

PR 머지 + 배포 완료 후:

```
1. Prometheus(api.fandrops.site:9090)에서 메트릭 수집 확인
   fandrops_orders_status
   fandrops_outbox_pending

2. Grafana(api.fandrops.site:3000) → Alerting → Alert Rules
   fandrops-failed-order-p0: NoData → Normal 확인
   fandrops-outbox-pending-p1: NoData → Normal 확인

3. P0 Alert firing 테스트
   DB에 status=FAILED 주문 1건 수동 INSERT → 알람 발화 → Gmail 수신 확인
   테스트 후 INSERT 행 삭제
```

---

## 7. Phase 4 타임라인

| 날짜 | 작업 |
| --- | --- |
| 6/12 | Blue/Green EC2 적용 + cd.yml 수정 + 배포 테스트 |
| 6/12~13 | k6 Baseline 실행 (시나리오 01·02·04·05) + 수치 기록 |
| 6/13~14 | D 단기 실험 — EC2-2 기동 → 분산 검증 → terminate |
| 6/14~18 | Baseline 미달 항목 튜닝 (도메인 오너 협의 포함) |
| 6/18~20 | #191 활성화 (형성빈·표지민 PR 머지 후) |
| 6/20~22 | 최종 SLO 수치 측정 + 결과 기록 |

---

## 8. 트러블슈팅 기록

*(Phase 4 진행 중 발생한 이슈를 여기에 추가)*

| # | 증상 | 원인 | 해결 |
| --- | --- | --- | --- |
| — | — | — | — |

---

## 9. 최종 DoD

- [ ] Blue/Green EC2 적용 완료 확인 (Phase 3 §8 DoD 참고)
- [ ] k6 Baseline 수치 기록 (시나리오 01·02·04·05)
- [ ] D 단기 실험 완료 — 오버셀 0건 확인 + SSE 한계 기록
- [ ] SLO 목표 달성 확인 (Write P95 < 300ms, Read P95 < 120ms, 5xx < 0.1%)
- [ ] Grafana 커스텀 메트릭 알람 활성화 (#191)
- [ ] 최종 SLO 수치 Grafana 스크린샷 보관

---

## 10. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [aws-phase3-runbook.md](./aws-phase3-runbook.md) | Redis 관측 · AUTH · S3 CORS · k6 스크립트 · Blue/Green 설계 |
| [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) | Blue/Green 아키텍처 의사결정 · 배포 스크립트 · 롤백 시나리오 · 분산 설계 검증 |
| [incident-response.md](./incident-response.md) | P0~P2 장애 대응 절차 |
| [observability-metrics.md](./observability-metrics.md) | SLO · 메트릭 · 알람 기준 |
| [personas/jiyoungjae.md](../ai/personas/jiyoungjae.md) | SRE 담당 체크리스트 |
