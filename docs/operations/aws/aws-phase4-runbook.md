# AWS Phase 4 부하 테스트 및 SLO 검증

> **관련:** [aws-phase3-runbook.md](./aws-phase3-runbook.md) · [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) · [observability-metrics.md](../observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

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
② k6 예비 측정 — 단일 EC2에서 실행, 코드 버그 탐색·오버셀 검증 (공식 SLO 기준선 아님)
③ EC2-2 t3.small 기동 → k6 설치 → 공식 SLO 베이스라인 측정 (EC2-1 단일 서버 기준)
④ D 단기 실험 — EC2-2에 Spring Boot 추가, 분산 설계 검증 → Spring Boot 종료
⑤ SLO 미달 항목 튜닝 (도메인 오너)
⑥ EC2-2 k6로 최적화 후 재측정 → 동일 환경 비교로 개선폭 확인
⑦ Grafana 커스텀 메트릭 알람 활성화 (#191)
⑧ 최종 SLO 수치 기록 + EC2-2 terminate
```

> **측정 환경 원칙**: 최적화 전·후 비교는 반드시 EC2-2 동일 환경에서 측정해야 유효하다.  
> 공식 베이스라인(③)은 단일 서버(EC2-1) 기준이며, 분산 실험(④)과 구분된다.  
> 예비 측정(②)과 공식 베이스라인(③)은 환경이 다르므로 수치를 직접 비교하지 않는다.

---

## 2. Blue/Green 배포 EC2 적용

### 2-1. 배경

Phase 4 부하 테스트 중 배포가 발생할 수 있다. k6 실행 중 `systemctl restart`로 배포하면 30~60초 다운타임이 생겨 5xx가 발생하고 SLO 측정에 영향을 준다.
Phase 4 시작 전 Blue/Green 배포를 EC2에 적용해 배포 중에도 5xx 0건을 유지해야 한다.

### 2-2. 적용 절차

> **✅ 완료** — 2026-06-11 사전 완료 (PR #221, #222). Phase 4 시작 전 적용 완료됨.

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
| k6 설치 | ~~EC2 직접 설치~~ → **Actions runner에서 자동 설치** (`k6-actions-runner.md` 참고) |
| DB seed | 각 시나리오 주석의 seed 조건 확인 |
| 01·04 시나리오 | `product id=1 (inventory.total_qty=100)`, fan id 1~2100 (seed.sql) |
| 02 시나리오 | `artist_profile id=1`, `artist_feed` 20개, `user_follow` fan_id 1~N & artist_id=1 |
| 03 시나리오 | Wiremock 기동 + `TOSS_API_BASE_URL=http://localhost:8090` 앱 재기동 |
| 05 시나리오 | Nginx `worker_connections ≥ 2048`, JVM `ulimit -n ≥ 8192` |
| **JWT tokens.csv** | 로컬에서 JwtGeneratorTest 실행 → `infra/k6/seed/tokens.csv` 생성 → S3 업로드 → EC2 다운로드 |
| **Redis AccessTicket** | `access:ticket:1:{fanId}=test-ticket-token` fan_id 1~2100 일괄 적재 (아래 참고) |
| Prometheus Remote Write | EC2 Prometheus 주소: `http://localhost:9090/api/v1/write` |

k6 설치:

```bash
sudo dnf install https://dl.k6.io/rpm/repo.rpm -y
sudo dnf install k6 -y
k6 version
```

**Wiremock Docker 기동 (03·결제 시나리오 실행 전):**

```bash
# EC2에 Docker가 없으면 설치
sudo dnf install docker -y
sudo systemctl start docker

# Wiremock 컨테이너 기동 (infra/k6/wiremock/ 기준)
docker run -d --name wiremock \
  -p 8090:8080 \
  -v /opt/fandrops/k6/wiremock:/home/wiremock \
  wiremock/wiremock:3.3.1 \
  --root-dir /home/wiremock

# 앱 서버 TOSS_API_BASE_URL 변경 후 재기동 (active 슬롯 확인)
ACTIVE=$(cat /etc/fandrops/active-slot)
sudo sed -i 's|TOSS_API_BASE_URL=.*|TOSS_API_BASE_URL=http://localhost:8090|' /etc/fandrops/fandrops-prod.conf
sudo systemctl restart "fandrops-$ACTIVE"

# 결제 시나리오 종료 후 원복
# sudo sed -i 's|TOSS_API_BASE_URL=.*|TOSS_API_BASE_URL=https://api.tosspayments.com|' /etc/fandrops/fandrops-prod.conf
# sudo systemctl restart "fandrops-$ACTIVE"
```

### 3-3. JWT 생성 및 Redis 사전 적재

> **인증 방식:** prod 프로파일 유지 — JWT 사전 생성(CSV) + Redis AccessTicket 사전 적재 방식으로 운영 코드 수정 없이 실행.
> 상세 배경 및 결정 이유: [`k6-execution-plan.md §2`](../k6/k6-execution-plan.md)

**JWT tokens.csv 생성 (로컬):**

```bash
# 로컬에서 JwtGeneratorTest 실행 (JWT_SECRET 환경변수 주입)
# 출력: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT, .gitignore 대상)

# S3 업로드
aws s3 cp infra/k6/seed/tokens.csv s3://<버킷명>/k6/tokens.csv

# EC2 SSM 세션에서 다운로드
aws s3 cp s3://<버킷명>/k6/tokens.csv /opt/fandrops/k6/seed/tokens.csv
```

**Redis AccessTicket 사전 적재 (EC2 SSM 세션):**

```bash
# productId=1, fan_id 1~2100 일괄 적재 (TTL 86400초)
for i in {1..2100}; do
  redis-cli -h <REDIS_ENDPOINT> setex "access:ticket:1:$i" 86400 "test-ticket-token"
done

# 적재 확인
redis-cli -h <REDIS_ENDPOINT> get "access:ticket:1:1"    # → "test-ticket-token"
redis-cli -h <REDIS_ENDPOINT> get "access:ticket:1:2100" # → "test-ticket-token"
```

### 3-4. 시나리오별 실행

> **2026-06-17 변경:** k6를 EC2에서 직접 실행하면 t3.small CPU 경합으로 측정값이 왜곡된다.  
> (시나리오 05 OOM 크래시, 시나리오 06 피드 0% 실패 실제 발생)  
> **현재는 GitHub Actions runner에서 k6를 실행한다.** 상세: [`k6-actions-runner.md`](../k6/k6-actions-runner.md)

**실행 절차 요약:**

1. GitHub → Actions → **Run k6 Load Test** → **Run workflow**
2. `scenario` 선택 (02 → 01 → 04 → 03 → 05 → 06 권장 순서)
3. `confirm` 입력란에 `yes` 입력
4. Actions 로그에서 k6 터미널 출력 확인

**시나리오별 EC2 사전 준비 (Actions 실행 전 수동):**

```bash
# 01·04: inventory 리셋 + Redis 티켓 재적재
mysql -u fandrops_admin -p<PW> -h <RDS> fandrops \
  -e "UPDATE inventory SET available_qty=100, reserved_qty=0, total_qty=100, version=0 WHERE product_id=1;"

REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done

# 03·06: Wiremock Docker 기동 확인
docker ps --filter name=wiremock  # Up 상태인지 확인
# 미실행 시: docker start wiremock

# 06: inventory 200으로 리셋 (01·04와 다름)
mysql -u fandrops_admin -p<PW> -h <RDS> fandrops \
  -e "UPDATE inventory SET available_qty=200, reserved_qty=0, total_qty=200, version=0 WHERE product_id=1;"
```

> **주의:** `inventory` 리셋 시 `total_qty`를 반드시 포함할 것.  
> `available_qty > total_qty` 상태가 되면 주문 서비스 invariant 위반으로 50건 이후 모두 실패.

**테스트 완료 후 정리:**

```bash
# S3 seed 파일 삭제 (JWT·주문 정보 노출 방지)
aws s3 rm s3://<버킷명>/k6/tokens.csv
aws s3 rm s3://<버킷명>/k6/orders.json

# Redis AccessTicket 테스트용 키 삭제
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls del "access:ticket:1:$i"
done
```

### 3-4. Baseline 수치 기록표

시나리오 실행 후 아래 표를 채워 Baseline으로 보관한다.

| 시나리오 | Write/Read P95 | 5xx 에러율 | 오버셀 건수 | SLO 통과 여부 |
| --- | --- | --- | --- | --- |
| 01 주문 동시성 | — | — | — | 미실행 |
| 02 피드 Read | **231ms** | 0.00% | — | ❌ (목표 120ms) |
| 03 결제 확인 (Wiremock) | — | — | — | 미실행 |
| 04 드롭스 스파이크 | — | — | — | 미실행 |
| 05 SSE 대기열 | — | — | — | 미실행 |
| 06 통합 워크로드 | — | — | — | 미실행 |

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

**§ 4 전체 실행 순서:**

```
1. EC2-2 기동 (§ 4-2)
2. k6 설치 + 공식 SLO 베이스라인 측정 (§ 4-7 Step 1)  ← Spring Boot 설치 전에 먼저
3. Spring Boot 설치 (§ 4-2 나머지)
4. EC2-1 Nginx upstream에 EC2-2 추가 (§ 4-3)
5. 분산 실험 실행 (§ 4-4)
6. 결과 기록 (§ 4-5)
7. Spring Boot 종료 + Nginx 원복 (§ 4-6)
8. 팀원 최적화 완료 후 EC2-2 k6로 재측정 (§ 4-7 Step 2)
9. EC2-2 terminate (§ 4-8)
```

### 4-2. EC2-2 기동 절차

**AWS 콘솔 → EC2 → Launch Instance:**

| 항목 | 값 |
| --- | --- |
| AMI | Amazon Linux 2023 |
| Instance type | t3.small (2 vCPU, 2GB RAM) |
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
ExecStart=/usr/bin/java -Xms256m -Xmx768m \
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

> **실행 방식:** k6는 **GitHub Actions runner**에서 실행한다 (`k6-actions-runner.md` 참고).  
> EC2-1 Nginx upstream에 EC2-2가 추가되어 있으므로 `BASE_URL=https://api.fandrops.site`로 요청하면 두 서버로 자동 분산된다.  
> EC2-2에서 직접 k6를 실행하지 않는다 — Spring Boot와 k6를 같은 인스턴스에서 실행하면 측정값이 오염된다.

**EC2-1 SSM 세션에서 사전 준비 (Actions 실행 전):**

```bash
# Redis AccessTicket 재적재 (분산 환경 실행 전)
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done

# inventory 리셋
mysql -u fandrops_admin -p<PW> -h <RDS> fandrops \
  -e "UPDATE inventory SET available_qty=100, reserved_qty=0, total_qty=100, version=0 WHERE product_id=1;"
```

**GitHub Actions → Run k6 Load Test → 시나리오 01 실행:**

```
scenario: 01_order_concurrency
confirm: yes
```

**01 완료 후 Redis 재적재, 시나리오 04 실행:**

```bash
# EC2-1 SSM 세션
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

```
scenario: 04_drop_spike
confirm: yes
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

### 4-6. EC2-2 Spring Boot 종료 (분산 실험 완료 후)

분산 실험이 끝나면 EC2-2의 Spring Boot를 종료하고 Nginx upstream에서 제거한다.

```bash
# EC2-2 SSM 세션 — Spring Boot 종료
sudo systemctl stop fandrops

# EC2-1 SSM 세션 — Nginx upstream에서 EC2-2 제거
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)

sudo tee /etc/nginx/fandrops-active.conf <<EOF
upstream fandrops_backend {
    server 127.0.0.1:$PORT;
    keepalive 32;
}
EOF
sudo nginx -t && sudo systemctl reload nginx
```

---

### 4-7. EC2-2 k6 runner 전환 — 공식 SLO 베이스라인 및 재검증

> **공식 SLO 베이스라인**: Spring Boot 종료 직후, **최적화 전**에 먼저 측정한다.  
> 예비 측정(§ 3)은 EC2 로컬에서 CPU 경합이 있는 환경이므로 공식 기준선으로 사용하지 않는다.  
> EC2-2에서 최적화 전·후를 동일 환경으로 측정해야 개선폭이 유효하다.

**Step 1 — 공식 베이스라인 측정 (Spring Boot 설치 전, 최적화 전)**

> ⚠️ **이 단계는 반드시 Spring Boot 설치(§ 4-2 나머지) 전에 먼저 실행한다.**  
> EC2-1 단일 서버를 대상으로 측정해야 공식 SLO 기준선이 된다.  
> Spring Boot가 EC2-2에 올라간 뒤 측정하면 분산 환경 수치가 되어 단일 서버 베이스라인으로 쓸 수 없다.

k6 설치 완료 후 전 시나리오를 실행해 단일 서버 공식 기준선을 확보한다.

**Step 2 — 팀원 최적화 작업 완료 후 재측정**

> **전제 조건:** 팀원 최적화 작업(s02 N+1 쿼리, s03 TossConfirmBody #318 등) 완료 후 진행.

EC2-2에 k6를 설치하고 EC2-1(앱 서버)을 대상으로 SLO 재검증을 실행한다.  
EC2-2(서울 리전)에서 실행하므로 Actions runner 방식의 150ms 네트워크 오버헤드 없이 정확한 레이턴시 측정이 가능하다.

**k6 설치 (EC2-2 SSM 세션):**

```bash
sudo dnf install https://dl.k6.io/rpm/repo.rpm -y
sudo dnf install k6 -y
k6 version

# k6 시나리오 디렉터리 구성
sudo mkdir -p /opt/fandrops/k6
cd /opt/fandrops/k6

# seed 파일 S3 다운로드
S3_BUCKET="fandrops-prod-storage-495264909330-ap-northeast-2-an"
aws s3 cp s3://$S3_BUCKET/k6/tokens.csv /opt/fandrops/k6/seed/tokens.csv
```

**시나리오 파일 복사 (EC2-1 → EC2-2):**  
EC2-1 SSM 세션에서 k6 시나리오 파일을 S3 경유로 EC2-2에 전달한다.

```bash
# EC2-1 SSM 세션
aws s3 sync /opt/fandrops/k6/ s3://$S3_BUCKET/k6-scenarios/ --exclude "seed/*"

# EC2-2 SSM 세션
aws s3 sync s3://$S3_BUCKET/k6-scenarios/ /opt/fandrops/k6/
```

**EC2-1 Private IP 확인 (EC2-2에서 직접 타깃):**

```bash
# EC2-2 SSM 세션 — EC2-1 Private IP를 BASE_URL로 사용
EC2_1_PRIVATE_IP="<EC2-1 Private IP>"  # AWS 콘솔에서 확인

# 또는 Nginx 경유 (권장 — 실제 트래픽 경로와 동일)
BASE_URL="https://api.fandrops.site"
```

**시나리오별 실행:**

| 시나리오 | 실행 위치 | 이유 |
|---|---|---|
| s01 주문 동시성 (200VU) | EC2-2 k6 | 서울 리전, 레이턴시 SLO 검증 |
| s02 피드 조회 (50VU) | EC2-2 k6 | 서울 리전, Read P95 < 120ms 검증 |
| s03 결제 확인 (50VU) | EC2-2 k6 | 서울 리전, Write P95 < 300ms 검증 |
| s04 드롭스 스파이크 (1000VU) | EC2-2 k6 | 서울 리전, 메모리 모니터링 필수 |
| s05 SSE 대기열 (2100VU) | Actions runner | ec2-2 t3.small(2GB) k6 runner 측 2,100 VU SSE 연결 생성 부담. ec2-1 앱 서버는 t3.medium으로 수용 능력 개선됨 |
| s06 통합 워크로드 (150VU) | EC2-2 k6 | 서울 리전, 종합 검증 |

```bash
# EC2-2 SSM 세션 — 실행 예시 (s02)
cd /opt/fandrops/k6
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)" k6 run \
  -e BASE_URL=https://api.fandrops.site \
  scenarios/02_feed_read.js
```

> **s04 메모리 모니터링:** 1000VU 실행 중 `free -h`로 메모리 여유 확인. 300MB 미만 시 중단.

---

### 4-8. EC2-2 Terminate

모든 재검증 완료 후 EC2-2를 종료한다.

```bash
# S3 k6-scenarios 임시 파일 정리
S3_BUCKET="fandrops-prod-storage-495264909330-ap-northeast-2-an"
aws s3 rm s3://$S3_BUCKET/k6-scenarios/ --recursive
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

> **✅ 완료** — 2026-06-18 (#235)

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

### 6-3. 모니터링 스택 재기동 절차

> `compose.monitoring.yml`에 `restart: unless-stopped` 정책이 설정되어 있어 **EC2 재시작 및 일반 크래시는 자동 복구**된다.  
> OOM 크래시 후 자동 재시작된 경우 `GMAIL_APP_PASSWORD`가 비어 있어 Gmail 알림만 동작하지 않는다 → `start-monitoring.sh` 수동 실행 필요.

**자동 재기동 적용 범위:**

| 상황 | 동작 |
| --- | --- |
| EC2 재시작 | ✅ 자동 재기동 (restart policy) |
| 일반 컨테이너 크래시 | ✅ 자동 재기동 (restart policy) |
| OOM 크래시 | ⚠️ 컨테이너는 자동 재시작되나 Gmail 알림 비활성 → 수동 스크립트 실행 필요 |

> **OOM(Out Of Memory) 크래시란?**  
> EC2 메모리(RAM)가 부족해지면 Linux OS가 강제로 프로세스를 종료한다. k6 부하 테스트처럼 메모리를 많이 쓰는 작업 중 Prometheus·Grafana가 종료 대상이 될 수 있다.  
> OOM 크래시 후 `restart: unless-stopped` 정책으로 컨테이너는 자동 재시작되지만, 환경변수 `GMAIL_APP_PASSWORD`가 빈 값으로 올라와 Gmail 알림만 동작하지 않는다. Grafana 대시보드는 정상.

**OOM 크래시 여부 확인:**

```bash
# 재시작 횟수가 1 이상이면 OOM 크래시가 있었던 것
docker inspect fandrops-monitoring-grafana-1 --format '{{.RestartCount}}'
```

**OOM 크래시 후 수동 재기동 스크립트 (`/opt/fandrops-monitoring/start-monitoring.sh`):**

```bash
#!/bin/bash
export GMAIL_APP_PASSWORD=$(aws ssm get-parameter \
  --name "/fandrops/prod/gmail-app-password" \
  --with-decryption \
  --region ap-northeast-2 \
  --query "Parameter.Value" \
  --output text)

docker compose -f /opt/fandrops-monitoring/compose.monitoring.yml up -d
```

- `GMAIL_APP_PASSWORD`는 SSM Parameter Store `/fandrops/prod/gmail-app-password` (SecureString)에 저장
- EC2 IAM 역할(`fandrops-prod-ec2-role`)에 `ssm:GetParameter` + KMS Decrypt 권한 있음
- 스크립트 실행: `sudo /opt/fandrops-monitoring/start-monitoring.sh`

**컨테이너 상태 확인:**

```bash
docker ps | grep -E "prometheus|grafana"
curl -s http://localhost:9090/-/healthy
curl -s "http://admin:admin@localhost:3000/api/health"
```

---

## 7. Phase 4 타임라인

| 날짜 | 작업 |
| --- | --- |
| 6/11 ✅ | Blue/Green EC2 적용 + cd.yml 수정 + 배포 테스트 완료 (PR #221, #222) |
| 6/12~13 | EC2 k6 설치 + Wiremock Docker 기동 환경 구성 (#230) |
| 6/12~13 | k6 Baseline 실행 (시나리오 01·02·03·04·05·06) + 수치 기록 (#231·#232) |
| 6/13~17 | k6 Baseline 전 시나리오 완료 (01~06) |
| 6/18~20 | Baseline 미달 항목 튜닝 + 도메인 오너 피드백 전달 (#233) |
| 6/18~20 | #191 Grafana 커스텀 알람 활성화 + P0 Alert firing 실전 테스트 (#235·#236) |
| 6/19~ | D 단기 실험 — EC2-2 t3.small 기동 → 분산 검증(Phase 1) → k6 runner 전환(Phase 2) → terminate (#234) |
| 6/20~22 | EC2-2 k6 runner로 최적화 후 SLO 재검증 (s01~s04·s06 EC2-2, s05 Actions runner) |
| 6/22~25 | 최종 SLO 수치 측정 + Grafana 스크린샷 보관 (#237) |
| 6/22~ | Phase 5 이행 — STAR 리포트 · 발표 자료 준비 (aws-phase5-runbook.md) |

---

## 8. 트러블슈팅 기록

| # | 날짜 | 증상 | 원인 | 해결 |
| --- | --- | --- | --- | --- |
| 1 | 2026-06-15 | Flyway V25 FAILED → CD 배포 중단 | `CREATE INDEX IF NOT EXISTS` MySQL 8.0.46 미지원 | PR #279 머지 후 flyway_schema_history FAILED 레코드 SSM으로 수동 삭제 → CD 재실행 |
| 2 | 2026-06-17 | EC2 재기동 후 Spring Boot 크래시 | RDS도 중지 상태 — EC2만 시작하면 HikariPool 커넥션 실패 | AWS 콘솔에서 RDS 별도 시작 후 앱 재기동 |
| 3 | 2026-06-17 | curl 요청 400 HTML 응답 | TOKEN 변수에 `\r\n` 포함 → `Authorization` 헤더 두 줄로 분리 | `tr -d '\r\n'` 추가: `TOKEN=$(... \| tr -d '\r\n')` |
| 4 | 2026-06-17 | 시나리오 04 전체 403 | 시나리오 05를 04보다 먼저 실행 → `RedisAccessTicketRepository.issue()`가 티켓 UUID로 덮어씀 | 04 실행 전 Redis 재적재 필수. 실행 순서: 04 → 05 (절대 역순 금지) |
| 5 | 2026-06-17 | 시나리오 03 전체 99% 실패 | `TossConfirmBody` inner private record Jackson 직렬화 불가 → Wiremock 빈 body 수신 → 404 → payment FAILED → 이후 전부 409 DUPLICATE_PAYMENT | 이슈 [#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318) 수정(담당: 장성재) 후 재측정 |
| 6 | 2026-06-17 | DB reset 후 k6 실행해도 전부 409 | `updated_at` 미갱신 → OrderRecoveryScheduler(60초 주기, 30분 타임아웃)가 즉시 CANCEL | `UPDATE orders SET status='RESERVED', updated_at=NOW() WHERE ...` |
| 7 | 2026-06-17 | 시나리오 05 OOM 크래시, 시나리오 06 피드 0% | k6와 앱 서버가 같은 t3.small에서 실행 → CPU/메모리 경합 | k6를 GitHub Actions runner로 이관 ([k6-actions-runner.md](../k6/k6-actions-runner.md)) |
| 8 | 2026-06-17 | inventory 리셋 후 50건 초과 시 주문 전부 실패 | `available_qty=200`으로 리셋했으나 `total_qty=50` 그대로 → invariant 위반 | 리셋 SQL에 `total_qty`도 포함: `SET available_qty=200, reserved_qty=0, total_qty=200` |
| 9 | 2026-06-18 | Prometheus/Grafana 컨테이너 Exited (255) — 메트릭 수집 중단 | 시나리오 05 OOM 크래시 이후 19시간 미재기동 — docker compose가 restart policy 없이 기동됐음 | `start-monitoring.sh` 스크립트로 재기동 (§6-3). `GMAIL_APP_PASSWORD`는 SSM `/fandrops/prod/gmail-app-password`에 저장, EC2 역할로 읽어 주입 |

---

## 9. 최종 DoD

- [x] Blue/Green EC2 적용 완료 확인 (Phase 3 §8 DoD 참고) — 2026-06-11 완료, PR #221·#222
- [x] k6 Baseline 수치 기록 — 시나리오 01~06 전체 완료 (2026-06-15~17)
  - 01 주문 동시성: P95=2.85s, 오버셀 0건 ✅
  - 02 피드 조회: P95=231ms, 에러율 0%
  - 03 결제 확인: TossConfirmBody 버그([#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318)) — 재측정 필요
  - 04 드롭스 스파이크: P95(성공)=508ms, 오버셀 0건 ✅
  - 05 SSE 대기열: OOM 크래시 — k6 runner 이관 후 재측정 필요
  - 06 통합 워크로드: t3.small 과부하 + 버그 — 재측정 필요
- [x] k6 GitHub Actions runner 이관 완료 — EC2 CPU 경합 제거 (`k6-actions-runner.md`)
- [ ] D 단기 실험 완료 — EC2-2 t3.small 분산 검증(오버셀 0건·중복결제 0건) (#234)
- [ ] EC2-2 공식 SLO 베이스라인 측정 완료 — 최적화 전, s01·s02·s03·s04·s05·s06
- [ ] 팀원 최적화 완료 — s02 N+1 쿼리(정환철), s03 TossConfirmBody(#318, 장성재) 등
- [ ] EC2-2에서 최적화 후 재측정 완료 — 동일 환경 비교로 개선폭 확인
- [ ] SLO 목표 달성 확인 (Write P95 < 300ms, Read P95 < 120ms, 5xx < 0.1%)
- [x] Grafana 커스텀 메트릭 알람 활성화 (#191·#235) — 2026-06-18 완료 (fandrops-failed-order-p0, fandrops-outbox-pending-p1 정상 수집·Gmail 수신 확인)
- [ ] 최종 SLO 수치 Grafana 스크린샷 보관

---

## 10. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [k6-actions-runner.md](../k6/k6-actions-runner.md) | k6 GitHub Actions runner 실행 가이드 — 전환 이유, 아키텍처, Secrets, S3 업로드, EC2 사전 준비 |
| [aws-phase3-runbook.md](./aws-phase3-runbook.md) | Redis 관측 · AUTH · S3 CORS · k6 스크립트 · Blue/Green 설계 |
| [aws-phase5-runbook.md](./aws-phase5-runbook.md) | STAR 리포트 · 발표 자료 · 최종 SLO 수치 기록 |
| [nginx-bluegreen-strategy.md](./nginx-bluegreen-strategy.md) | Blue/Green 아키텍처 의사결정 · 배포 스크립트 · 롤백 시나리오 · 분산 설계 검증 |
| [incident-response.md](../incident-response.md) | P0~P2 장애 대응 절차 · 실전 테스트 절차 |
| [observability-metrics.md](../observability-metrics.md) | SLO · 메트릭 · 알람 기준 |
| [personas/jiyoungjae.md](../../ai/personas/jiyoungjae.md) | SRE 담당 체크리스트 |

