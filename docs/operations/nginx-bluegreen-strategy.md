# Nginx Blue/Green 배포 전략 및 분산 설계 검증

> **관련:** [aws-phase3-runbook.md](./aws-phase3-runbook.md) · [observability-metrics.md](./observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

ALB 없는 예산 제약 환경에서 Nginx를 활용해 무중단 배포와 분산 설계 검증을 구현한 의사결정 과정과 기술 상세를 기록한다.

---

## 1. 배경 및 제약 조건

### 1-1. 프로젝트 인프라 현황

| 항목 | 현재 구성 |
| --- | --- |
| EC2 | t3.small (2 vCPU, 2GB RAM), Amazon Linux 2023 |
| Nginx | :80 → upstream fandrops_backend (Blue/Green 포트 스위칭, 2026-06-11 적용 완료) |
| RDS | db.t3.micro MySQL 8.0, Private Subnet |
| ElastiCache | cache.t3.micro Redis 7.1, Private Subnet |
| 배포 방식 | GitHub Actions + S3 + SSM RunCommand (OIDC) |
| ALB | **없음** |
| NAT Gateway | **없음** |
| 월 비용 | **~72,000원** |
| 예산 | **90,000원/월** |

### 1-2. 핵심 제약 조건

**예산 제약으로 ALB 불가:**

| 구성 | 월 비용 | 예산 대비 |
| --- | --- | --- |
| 현재 (EC2 1대) | ~72,000원 | ✅ +18,000원 여유 |
| + EC2-2 t3.micro | ~82,500원 | ✅ +7,500원 여유 |
| + ALB만 추가 | ~100,000원 | ❌ 10,000원 초과 |
| + ALB + EC2-2 | ~110,000원 | ❌ 20,000원 초과 |

ALB 고정 요금만 $0.0225/hr × 720h = **$16.4/월(~22,600원)** 이므로 예산 초과. 따라서 ALB 없이 Nginx로 직접 배포 안정성과 분산 설계를 구현한다.

### 1-3. 핵심 목표

- **운영 목표:** 배포 중 다운타임 제거 (현재 `systemctl restart` 시 30~60초 중단)
- **SLO 목표:** Write P95 < 300ms, 5xx rate < 0.1%, 오버셀 0건
- **포트폴리오 목표:** "ALB 없이 Nginx로 직접 구현한 무중단 배포 + 분산 설계 검증"

---

## 2. 선택지 검토

### 2-1. 선택지 A — 단일 EC2 + Nginx Reverse Proxy 유지 (현재)

```
Internet → EC2 Nginx :80 → Spring Boot :8080 → RDS / Redis
```

| 항목 | 평가 |
| --- | --- |
| 비용 | 추가 없음 |
| 배포 다운타임 | 30~60초 발생 (`systemctl restart`) |
| 포트폴리오 가치 | 낮음 — 설명할 구조적 스토리 없음 |
| **결론** | Phase 4 이전까지 임시 유지, 이후 B로 전환 |

### 2-2. 선택지 B — 단일 EC2 Blue/Green 포트 스위칭 ✅ **채택**

```
EC2 1대
├── Nginx :80 → upstream fandrops_backend (active.conf)
├── Spring Boot blue  :8081  ← active (평상시)
└── Spring Boot green :8082  ← standby (배포 시만 기동)
```

배포 흐름:
1. 비활성 슬롯(green)에 새 JAR 기동
2. `/actuator/health` UP 확인
3. Nginx `active.conf` → 새 슬롯 포트로 교체
4. `nginx -t` 검증 → `systemctl reload nginx`
5. 기존 슬롯(blue) graceful shutdown

| 항목 | 평가 |
| --- | --- |
| 비용 | 추가 없음 |
| 배포 다운타임 | 0~2초 (nginx reload 순간) |
| 구현 난이도 | 중 |
| 포트폴리오 가치 | 높음 — "ALB 없이 Nginx로 무중단 배포 직접 구현" |
| **결론** | **Phase 4 전 구현** |

### 2-3. 선택지 C — 단일 EC2 Nginx upstream 상시 로드밸런싱

```
EC2 1대
├── Nginx :80
│     upstream { server :8081; server :8082; }
├── Spring Boot :8081 (상시 운영)
└── Spring Boot :8082 (상시 운영)
```

| 항목 | 평가 |
| --- | --- |
| 비용 | 추가 없음 |
| 실제 효과 | CPU/RAM 공유 → 진짜 분산 아님 |
| 메모리 | 2GB에서 두 프로세스 상시 운영 → GC pressure |
| 포트폴리오 가치 | 낮음 — "같은 서버에서 나눈 것"이라는 반론 |
| **결론** | **채택 안 함** |

### 2-4. 선택지 D — EC2 2대 + Nginx 로드밸런싱 (단기 실험) ✅ **Phase 4 한정 채택**

```
Internet
    ↓
EC2-1 t3.small (Nginx LB + App1)
    Nginx upstream {
        server 127.0.0.1:8080;      ← App1 (로컬)
        server EC2-2-Private:8080;  ← App2 (원격)
    }
    ↓
EC2-2 t3.micro (App2)
    ↓
RDS MySQL / ElastiCache Redis (공유)
```

| 항목 | 평가 |
| --- | --- |
| 추가 비용 | t3.micro ~10,500원/월 (단기 기동 시 ~700원/2일) |
| 고가용성 | EC2-1 SPOF → **실제 HA 아님** |
| 목적 | stateless 설계 검증 — "어느 서버에 가도 오버셀 0건" |
| 포트폴리오 가치 | 높음 — 분산 환경 직접 검증 |
| **결론** | **Phase 4 기간 단기 기동 후 terminate** |

### 2-5. 영구 D 전환을 하지 않는 이유

EC2 2대 구성에서 ALB가 없으면 EC2-1(Nginx)이 단일 장애점(SPOF)이 된다.
EC2-1이 다운되면 EC2-2가 살아있어도 전체 서비스가 중단된다.
**운영 안정성 측면에서 1대와 동일하면서 운영 복잡도만 증가**하므로 영구 전환은 비효율적이다.

```
ALB 없는 EC2 2대:
  EC2-1 사망 → Nginx 다운 → 전체 서비스 중단 (EC2-2 무의미)

ALB + EC2 2대:
  EC2-1 사망 → ALB가 EC2-2로만 라우팅 → 서비스 유지
```

실제 고가용성을 위해서는 ALB가 반드시 필요하며, 이는 발표에서 "한계와 개선 방향"으로 명시한다.

---

## 3. 최종 결정 및 로드맵

```
이번 주 (Phase 4 전) ✅ **2026-06-11 완료**
  └─ B 구현: 단일 EC2 Blue/Green 포트 스위칭 (PR #221, #222)
       ├── systemd 유닛 2개 (fandrops-blue, fandrops-green)
       ├── Nginx active.conf 구조 변경
       └── cd.yml 배포 스크립트 수정

Phase 4 (06-12~22)
  ├── 1~2일차: 단일 EC2 k6 Baseline 실행 → SLO 기준선 확보
  ├── 중반:    D 단기 실험 (EC2-2 t3.micro 기동)
  │             ├── k6 동일 시나리오 → 오버셀 0건 확인
  │             └── 검증 완료 후 EC2-2 terminate
  └── 후반:    SLO 미달 항목 튜닝

Phase 5 (06-22~25)
  └── 발표 자료: B + D 실험 결과 정리
```

---

## 4. Blue/Green 배포 기술 상세 (B)

### 4-1. 구성 요소

| 파일 | 역할 |
| --- | --- |
| `/etc/systemd/system/fandrops-blue.service` | Spring Boot blue 슬롯 (:8081) |
| `/etc/systemd/system/fandrops-green.service` | Spring Boot green 슬롯 (:8082) |
| `/etc/fandrops/active-slot` | 현재 active 슬롯 기록 (`blue` 또는 `green`) |
| `/etc/nginx/fandrops-active.conf` | Nginx upstream 포트 (배포 시 덮어씀) |
| `/opt/fandrops/blue.jar` | blue 슬롯 JAR |
| `/opt/fandrops/green.jar` | green 슬롯 JAR |

### 4-2. systemd 유닛 설정

```ini
# /etc/systemd/system/fandrops-blue.service
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
```

```ini
# /etc/systemd/system/fandrops-green.service
# 동일 구조, port=8082, jar=green.jar, SyslogIdentifier=fandrops-green
```

**왜 `-Xmx768m`인가:**
기존 단일 프로세스는 `-Xmx1024m`으로 운영했다. Blue/Green 전환 중 두 프로세스가 동시에 기동되는 순간이 있으며, 이 때 heap 합계가 2GB를 초과하면 OOM이 발생한다.
각 슬롯을 `-Xmx768m`으로 설정하면 동시 기동 시 최대 heap 1.5GB + OS/메타스페이스 ~300MB = 1.8GB로 t3.small 2GB 내에서 수용 가능하다.
전환이 완료되면 구 슬롯이 즉시 stop되므로 상시 운영 시에는 단일 프로세스만 동작한다.

### 4-3. Nginx 설정

```nginx
# /etc/nginx/conf.d/fandrops-zones.conf (기존 유지)
limit_req_zone $binary_remote_addr zone=fandrops_order:10m   rate=5r/s;
limit_req_zone $binary_remote_addr zone=fandrops_queue:10m   rate=10r/s;
limit_req_zone $binary_remote_addr zone=fandrops_payment:10m rate=5r/s;
limit_conn_zone $binary_remote_addr zone=fandrops_sse:10m;
```

```nginx
# /etc/nginx/fandrops-active.conf (배포 스크립트가 이 파일을 교체)
# 현재 active 슬롯 포트를 upstream으로 지정
upstream fandrops_backend {
    server 127.0.0.1:8081;  # blue active
    keepalive 32;
}
```

```nginx
# /etc/nginx/default.d/fandrops-location.conf
include /etc/nginx/fandrops-active.conf;

location /api/ {
    proxy_pass         http://fandrops_backend;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_connect_timeout 5s;
    proxy_read_timeout    60s;
    # 새 슬롯 전환 순간 일시적 502 시 자동 재시도
    proxy_next_upstream   error timeout http_502 http_503;

    limit_req zone=fandrops_order   burst=10 nodelay;
}

location /api/v1/queue/stream {
    include /etc/nginx/fandrops-active.conf;
    proxy_pass         http://fandrops_backend;
    proxy_buffering    off;
    proxy_cache        off;
    proxy_read_timeout 3600s;
    limit_conn         fandrops_sse 3;
}
```

**`proxy_next_upstream`의 역할:**
Nginx reload 순간 구 슬롯으로의 기존 연결이 끊기며 잠깐 502가 발생할 수 있다. `proxy_next_upstream error timeout http_502`를 설정하면 Nginx가 자동으로 새 upstream으로 재시도하여 클라이언트 입장에서는 오류를 인지하지 못한다.

### 4-4. 배포 스크립트 (`cd.yml` SSM RunCommand)

```bash
#!/bin/bash
set -euo pipefail

JAR_SRC="/tmp/api-server.jar"
JAR_DIR="/opt/fandrops"
ACTIVE_SLOT_FILE="/etc/fandrops/active-slot"
NGINX_ACTIVE_CONF="/etc/nginx/fandrops-active.conf"

# ── 1. 현재 슬롯 확인 ─────────────────────────────────────
ACTIVE=$(cat "$ACTIVE_SLOT_FILE" 2>/dev/null || echo "blue")
if [ "$ACTIVE" = "blue" ]; then
    NEW_SLOT="green";  NEW_PORT=8082
    OLD_SLOT="blue";   OLD_PORT=8081
else
    NEW_SLOT="blue";   NEW_PORT=8081
    OLD_SLOT="green";  OLD_PORT=8082
fi
echo "현재 active: $ACTIVE | 배포 대상: $NEW_SLOT (:$NEW_PORT)"

# ── 2. S3에서 새 JAR 다운로드 ─────────────────────────────
aws s3 cp "s3://${S3_BUCKET}/deploy/api-server.jar" "$JAR_SRC"
cp "$JAR_SRC" "$JAR_DIR/$NEW_SLOT.jar"
chown fandrops:fandrops "$JAR_DIR/$NEW_SLOT.jar"

# ── 3. 새 슬롯 기동 ───────────────────────────────────────
systemctl start "fandrops-$NEW_SLOT"

# ── 4. 헬스체크 (최대 60초 대기) ─────────────────────────
HEALTH="DOWN"
for i in $(seq 1 12); do
    HEALTH=$(curl -sf "http://127.0.0.1:$NEW_PORT/actuator/health" \
             | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" \
             2>/dev/null || echo "DOWN")
    echo "[$i/12] health=$HEALTH"
    [ "$HEALTH" = "UP" ] && break
    sleep 5
done

if [ "$HEALTH" != "UP" ]; then
    echo "❌ 헬스체크 실패 → 롤백: $NEW_SLOT 종료"
    systemctl stop "fandrops-$NEW_SLOT" || true
    exit 1
fi
echo "✅ 헬스체크 통과: $NEW_SLOT (:$NEW_PORT)"

# ── 5. Nginx upstream 전환 ────────────────────────────────
cat > "$NGINX_ACTIVE_CONF" <<EOF
upstream fandrops_backend {
    server 127.0.0.1:$NEW_PORT;
    keepalive 32;
}
EOF

nginx -t || {
    echo "❌ nginx 설정 오류 → 이전 설정 복구"
    cat > "$NGINX_ACTIVE_CONF" <<ROLLBACK
upstream fandrops_backend {
    server 127.0.0.1:$OLD_PORT;
    keepalive 32;
}
ROLLBACK
    exit 1
}
systemctl reload nginx
echo "✅ Nginx → :$NEW_PORT ($NEW_SLOT)"

# ── 6. 구 슬롯 Graceful Shutdown ─────────────────────────
# spring.lifecycle.timeout-per-shutdown-phase=30s 적용됨
systemctl stop "fandrops-$OLD_SLOT" || true
echo "✅ 구 슬롯 종료: $OLD_SLOT (:$OLD_PORT)"

# ── 7. 슬롯 기록 갱신 ─────────────────────────────────────
echo "$NEW_SLOT" > "$ACTIVE_SLOT_FILE"
echo "✅ 배포 완료. Active: $NEW_SLOT (:$NEW_PORT)"
```

**Graceful Shutdown이 중요한 이유:**
현재 `application-prod.yml`에 `server.shutdown: graceful`과 `lifecycle.timeout-per-shutdown-phase: 30s`가 설정되어 있다. 구 슬롯에 처리 중인 요청이 있을 때 `systemctl stop`을 호출하면 즉시 kill하지 않고 최대 30초간 기존 요청 완료를 기다린다. 이 덕분에 Nginx가 새 슬롯으로 전환된 이후에도 구 슬롯에 이미 전달된 요청(주문·결제)이 안전하게 완료된다.

---

## 5. 장애/롤백 시나리오

| 실패 상황 | 자동 대응 | 수동 조치 |
| --- | --- | --- |
| S3 다운로드 실패 | `exit 1` — Nginx 미변경, 구 슬롯 계속 서비스 | S3 재업로드 후 재실행 |
| `systemctl start` 실패 (JAR 오류) | `exit 1` — Nginx 미변경 | `journalctl -u fandrops-green -n 100` 확인 |
| 헬스체크 60초 타임아웃 | `systemctl stop fandrops-$NEW_SLOT` → `exit 1` | `dmesg \| grep oom` OOM 여부 확인, heap 조정 후 재시도 |
| `nginx -t` 실패 | 이전 `active.conf` 복구 → `exit 1` | `active.conf` 내용 수동 확인 |
| `systemctl reload nginx` 실패 | 기존 upstream 계속 서비스 중 | `nginx restart` (2~3초 중단 감수) |
| 전환 후 5xx 급증 | Grafana P0 알람 발화 → 수동 대응 | `echo "blue" > active-slot` + `active.conf` 이전 포트 복구 + `nginx reload` + 새 슬롯 `stop` |
| OOM (전환 중 두 프로세스 동시 기동) | 헬스체크 실패로 롤백 | 각 슬롯 heap을 `-Xmx512m`으로 낮추고 재시도 |

---

## 6. Phase 4 D 단기 실험 계획

### 6-1. 목적

D 실험은 **운영 안정성 향상이 목적이 아니다.**

ALB 없이 EC2 2대를 운영하면 EC2-1(Nginx)이 SPOF가 되어 실제 고가용성은 단일 EC2와 동일하다. D 실험의 유일한 목적은 **애플리케이션이 stateless하게 설계되어 어느 서버에서 요청을 처리해도 정합성이 보장되는지 검증하는 것**이다.

### 6-2. 검증 항목

| 항목 | 확인 방법 | 예상 결과 |
| --- | --- | --- |
| 재고 오버셀 | k6 `01_order_concurrency.js` 2대 동시 실행 | RESERVED = 재고 수량, 초과 없음 |
| 중복 주문 방지 | DB unique index `(fan_id, product_id, drop_event_id)` | 409 Conflict, 중복 없음 |
| 중복 결제 방지 | DB unique index `payment_key` | 409 Conflict, 중복 없음 |
| Redis 대기열 | `k6 05_sse_queue.js` | 두 서버 모두 동일 Redis 큐 참조 |
| 로컬 상태 없음 | static Map, synchronized 블록 없음 확인 | 어느 서버에 가도 동일 결과 |

### 6-3. 분산 설계 사전 검증 코드 체크리스트 (검증 완료)

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| in-memory Queue | ✅ 안전 | `LocalWaitQueueRepository` → `@Profile("local")`, prod는 `RedisWaitQueueRepository` 사용 |
| synchronized 블록 | ✅ 안전 | `LocalWaitQueueRepository`만 해당, local 프로파일에서만 활성화 |
| JWT Stateless | ✅ 안전 | `ApiSecurityConfig`: `SessionCreationPolicy.STATELESS` 확인 |
| 재고 오버셀 방지 | ✅ 안전 | `reserveAtomic` — DB 단일 UPDATE + `WHERE availableQty >= qty` 조건 → DB 레벨 원자적 처리 |
| `@Version` 낙관락 | ✅ 있음 | `InventoryJpaEntity`에 `@Version` 필드 |
| payment_key unique | ✅ 있음 | `PaymentJpaEntity`: `@Column(unique = true)` |
| idempotency_key unique | ✅ 있음 | `OrderEntity`: `@Column(unique = true)` |
| Payment 비관락 | ✅ 있음 | `PaymentJpaRepository`: `@Lock(PESSIMISTIC_WRITE)` |
| **SseEmitterRegistry** | ⚠️ **한계** | in-memory `ConcurrentHashMap<String, SseEmitter>` — 분산 환경에서 SSE 메시지 유실 가능 (아래 §6-4 참고) |

### 6-4. SseEmitterRegistry 분산 한계 및 대응 방향

**문제:**

```
Fan A가 EC2-1에 SSE 연결
  └─ EC2-1의 emitters Map에만 Fan A 등록

스케줄러가 EC2-2에서 실행 → sendToFan(Fan A) 호출
  └─ EC2-2의 emitters Map에 Fan A 없음 → 대기열 상태 업데이트 미전달
```

SSE 연결은 팬이 접속한 서버에만 emitter가 존재한다. 스케줄러가 다른 서버에서 실행되면 해당 팬에게 대기열 상태 메시지가 전달되지 않는다.

**영향 범위:**
- 오버셀·중복결제 발생 여부: **없음** (재고·결제 정합성은 DB 레벨에서 보장)
- 팬 UX: 대기열 순번 업데이트가 간헐적으로 누락될 수 있음

**D 실험 대응:** 이 한계를 **알고 진행**하며 실험 결과에서 SSE 메시지 유실 현상을 확인한다. 발표에서 "분산 환경에서 직접 발견한 한계"로 명시하고 개선 방향을 제시한다.

**개선 방향 (미구현):** Redis Pub/Sub으로 SSE push를 브로드캐스트하면 해소 가능.

```
스케줄러 → Redis Channel publish
  └─ EC2-1 구독 → emitters에 Fan A 있으면 전송
  └─ EC2-2 구독 → emitters에 Fan A 없으면 무시
```

### 6-4. EC2-2 세팅 절차

```
1. EC2-2 t3.micro 기동 (동일 VPC, Private Subnet)
   - AMI: Amazon Linux 2023
   - IAM Role: fandrops-prod-ec2-role (기존 역할 재사용)
   - Security Group: EC2-1 SG에서 :8080 inbound 허용 추가

2. SSM Session Manager로 접속 (SSH 키 불필요)

3. Java 21 설치
   sudo dnf install java-21-amazon-corretto -y

4. 앱 디렉터리 준비
   sudo mkdir -p /opt/fandrops
   sudo useradd -r fandrops
   sudo chown fandrops:fandrops /opt/fandrops

5. 환경파일 복사 (EC2-1과 동일)
   sudo mkdir -p /etc/fandrops
   sudo vi /etc/fandrops/fandrops-prod.conf
   # EC2-1과 동일한 환경변수 붙여넣기

6. JAR 배포
   aws s3 cp s3://${S3_BUCKET}/deploy/api-server.jar /opt/fandrops/app.jar
   sudo chown fandrops:fandrops /opt/fandrops/app.jar

7. systemd 등록
   # fandrops.service (EC2-1과 동일, port=8080)
   sudo systemctl enable fandrops
   sudo systemctl start fandrops

8. 헬스체크
   curl http://localhost:8080/actuator/health
```

### 6-5. EC2-1 Nginx upstream 수정 (D 실험 중)

```nginx
# /etc/nginx/fandrops-active.conf (D 실험 시)
upstream fandrops_backend {
    server 127.0.0.1:8081;            # EC2-1 App (blue)
    server EC2-2-PRIVATE-IP:8080;     # EC2-2 App
    keepalive 32;
}
```

실험 완료 후 EC2-2 항목 제거, EC2-2 인스턴스 terminate.

---

## 7. 비용 정리

### 월간 고정 비용 (현재)

| 리소스 | 스펙 | 월 비용 |
| --- | --- | --- |
| EC2 | t3.small | ~$15.0 (~20,700원) |
| RDS MySQL | db.t3.micro | ~$21.5 (~29,700원) |
| ElastiCache | cache.t3.micro | ~$13.0 (~17,900원) |
| CloudWatch + S3 | — | ~$3.0 (~4,100원) |
| **합계** | | **~$52.5 (~72,400원)** |

### D 실험 추가 비용 (단기 2일)

| 항목 | 계산 | 비용 |
| --- | --- | --- |
| EC2-2 t3.micro | $0.0104/hr × 48h | ~$0.50 (~700원) |

**2일 실험 총 추가 비용: 약 700원**

---

## 8. 아키텍처 비교

### Phase 3 현재 (A)

```
Internet
    │
    ▼
EC2 t3.small
┌─────────────────────────────┐
│  Nginx :80                  │
│    └─ proxy_pass :8080      │
│                             │
│  Spring Boot :8080          │
│    └─ Graceful Shutdown     │
└─────────────────────────────┘
    │                  │
    ▼                  ▼
RDS MySQL          ElastiCache Redis
(Private Subnet)   (Private Subnet)

배포: systemctl restart → 30~60초 다운타임 발생
```

### Phase 4 목표 (B)

```
Internet
    │
    ▼
EC2 t3.small
┌──────────────────────────────────────────┐
│  Nginx :80                               │
│    └─ upstream fandrops_backend          │
│         include fandrops-active.conf     │
│           → server 127.0.0.1:8081 (blue) │
│                                          │
│  Spring Boot blue  :8081  ← active       │
│  Spring Boot green :8082  ← 배포 시만 기동 │
└──────────────────────────────────────────┘
    │                  │
    ▼                  ▼
RDS MySQL          ElastiCache Redis

배포: Nginx reload → 0~2초 (proxy_next_upstream으로 재시도)
```

### Phase 4 D 실험 (단기)

```
Internet
    │
    ▼
EC2-1 t3.small (Nginx LB + App1)
┌──────────────────────────────────────┐
│  Nginx :80                           │
│    upstream fandrops_backend {        │
│      server 127.0.0.1:8081;          │  ← App1 blue
│      server EC2-2-PRIVATE:8080;      │  ← App2
│    }                                 │
│  Spring Boot blue :8081              │
└──────────────────────────────────────┘
    │
    ▼
EC2-2 t3.micro (App2) ← 실험 후 terminate
┌──────────────────────────────────────┐
│  Spring Boot :8080                   │
└──────────────────────────────────────┘
    │                       │
    ▼                       ▼
RDS MySQL (공유)    ElastiCache Redis (공유)

⚠️ EC2-1 SPOF — 실제 HA 아님, 분산 설계 검증 목적
```

---

## 9. 한계와 개선 방향

| 한계 | 원인 | 개선 방향 |
| --- | --- | --- |
| EC2-1 SPOF | ALB 없음 | ALB 도입 ($20/월 추가) |
| Blue/Green 전환 중 2~3초 불안정 | Nginx reload 방식 | ALB Target Group 교체 방식 |
| EC2-2 없을 때 단일 포인트 | 예산 제약 | 예산 확보 시 상시 2대 + ALB |
| 배포 중 메모리 압박 | t3.small 2GB | 인스턴스 업그레이드 또는 B-series 사용 |
| **SSE 메시지 유실 (분산 환경)** | `SseEmitterRegistry` in-memory | Redis Pub/Sub 브로드캐스트로 해소 가능 |

이 한계들은 발표에서 "현재 구조의 트레이드오프"로 명시하고 개선 방향을 함께 설명한다.

**SseEmitterRegistry 한계는 D 실험에서 직접 확인한다.** 오버셀·중복결제는 DB 레벨에서 보장되므로 정합성 문제는 없으며, SSE UX 한계만 발생한다는 점을 실험으로 증명한다.

---

## 10. 포트폴리오 스토리라인

> AWS 예산 90,000원 제약으로 ALB를 사용할 수 없는 환경에서, Nginx upstream과 systemd 이중 슬롯 구조를 직접 구현해 무중단 Blue/Green 배포를 달성했습니다.

> 배포 중 t3.small(2GB) 메모리에서 두 Spring Boot 프로세스가 동시 기동되는 구간의 OOM 위험을 `-Xmx768m` heap 제한과 Graceful Shutdown 30초 유예로 해소했으며, `proxy_next_upstream`으로 Nginx reload 순간 클라이언트 오류를 최소화했습니다.

> Phase 4에서 EC2-2를 단기 기동해 k6 부하 테스트를 분산 환경에서 실행했습니다. 재고는 DB 단일 UPDATE(`WHERE availableQty >= qty`) + 낙관락, 결제는 `PESSIMISTIC_WRITE` + unique index 조합으로 어느 서버에서 요청을 처리해도 오버셀·중복결제가 발생하지 않음을 수치로 검증했습니다.

> 코드 분석 과정에서 `SseEmitterRegistry`가 in-memory `ConcurrentHashMap`으로 SSE 연결을 관리해 분산 환경에서 대기열 상태 메시지가 유실될 수 있음을 직접 발견했습니다. 정합성(오버셀·중복결제)은 DB 레벨에서 보장되므로 비즈니스 무결성에는 영향이 없으나, Redis Pub/Sub 브로드캐스트로 해소할 수 있는 UX 한계로 명시했습니다.

> ALB 없는 구조의 SPOF 한계와 SSE 분산 문제를 직접 발견하고 개선 방향까지 제시한 것이 단순 구현을 넘어 운영 관점의 설계 사고를 보여주는 포인트입니다.

---

## 11. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [aws-phase2-runbook.md](./aws-phase2-runbook.md) | CI/CD · 모니터링 · CloudWatch · Rate Limit |
| [aws-phase3-runbook.md](./aws-phase3-runbook.md) | Redis 관측 · AUTH · S3 CORS · k6 스크립트 |
| [incident-response.md](./incident-response.md) | P0~P2 장애 대응 절차 |
| [observability-metrics.md](./observability-metrics.md) | SLO · 메트릭 · 알람 기준 |
| [personas/jiyoungjae.md](../ai/personas/jiyoungjae.md) | SRE 담당 체크리스트 |
