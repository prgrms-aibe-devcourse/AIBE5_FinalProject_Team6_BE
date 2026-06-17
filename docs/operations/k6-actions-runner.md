# k6 GitHub Actions Runner 실행 가이드

**작성일:** 2026-06-17  
**담당:** 지영재  
**관련 파일:** `.github/workflows/run-k6.yml`

---

## 1. 왜 Actions Runner로 전환했나

### 기존 방식의 문제

기존 워크플로우는 SSM SendCommand로 **EC2에서 k6를 직접 실행**했다.  
EC2(t3.small, 2vCPU/2GB)에서 다음이 동시에 실행되는 구조였다.

```
EC2 t3.small
├── Spring Boot (blue 또는 green) — ~400MB
├── Spring Boot (inactive 슬롯) — ~400MB  
├── Prometheus + Grafana — ~300MB
└── k6 (부하 생성) — VU 수에 비례해 수백 MB
```

**실제 발생한 장애:**

| 시나리오 | 증상 | 원인 |
|---|---|---|
| 05 SSE 대기열 | EC2 전체 OOM 크래시, 결과 전부 유실 | k6 2,100 VU + Spring Boot × 2 + 모니터링 → 메모리 2GB 초과 |
| 06 통합 워크로드 | 피드 0% 성공, 전면 타임아웃 | 혼합 150 VU에서 CPU 포화 |

**결론:** k6와 앱 서버를 같은 EC2에서 실행하면 부하 테스트가 부하 테스트 대상에게 영향을 줘서 측정값 자체가 의미 없어진다.

### 전환 후 구조

```
GitHub Actions Runner (ubuntu-latest, 2vCPU/7GB)
└── k6 (부하 생성) — EC2와 완전 분리

EC2 t3.small
├── Spring Boot (active 슬롯) — 측정 대상만 실행
├── Prometheus + Grafana
└── Wiremock Docker (시나리오 03·06만)
```

- Runner는 `https://api.fandrops.site` 를 통해 EC2 앱에 부하를 가한다.
- k6 VU가 Runner 메모리를 사용하므로 EC2 OOM 재발 없음.
- EC2는 Spring Boot 응답에만 집중 → 측정 정확도 향상.

---

## 2. 아키텍처

```
┌─────────────────────────────────────────────┐
│         GitHub Actions Runner                │
│                                              │
│  1. git checkout                             │
│  2. k6 설치                                  │
│  3. S3 → tokens.csv, orders.json 다운로드    │
│  4. k6 run -e BASE_URL=https://api.fandrops.site │
└──────────────────────┬──────────────────────┘
                       │ HTTPS 부하
                       ▼
┌─────────────────────────────────────────────┐
│         EC2 t3.small (ap-northeast-2)        │
│                                              │
│  Nginx (:443) → Spring Boot (:808x)          │
│  Wiremock Docker (:8090) ← Spring Boot만     │
│  Prometheus + Grafana                        │
└─────────────────────────────────────────────┘
```

**Wiremock 접근 경로:**  
k6(Runner) → `POST /api/v1/payments/toss/confirm` → Spring Boot → `http://localhost:8090` (Wiremock)  
Runner가 Wiremock에 직접 접근하지 않으므로 포트 개방 불필요.

---

## 3. 필요 GitHub Secrets

| Secret | 값 | 비고 |
|---|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::...` | 기존 (OIDC) |
| `S3_BUCKET` | `fandrops-...` | 기존 |
| `EC2_INSTANCE_ID` | `i-...` | 기존 (Wiremock 확인용) |
| `APP_BASE_URL` | `https://api.fandrops.site` | **신규 추가 필요** |

> Settings → Secrets and variables → Actions → New repository secret

---

## 4. S3 사전 업로드

워크플로우 실행 전 seed 파일을 S3에 올려야 한다.

```bash
# tokens.csv — 로컬에서 JwtGeneratorTest 실행 후 생성 (k6-execution-plan.md §1 참고)
aws s3 cp infra/k6/seed/tokens.csv s3://<BUCKET>/k6/tokens.csv

# orders.json — 시나리오 03·06만 필요, EC2에서 생성 후 업로드
# EC2 SSM 세션에서:
mysql -u fandrops_admin -p<PW> -h <RDS_HOST> fandrops \
  -e "SELECT id, fan_id, total_price, order_payment_key FROM orders WHERE status='RESERVED' LIMIT 50;" \
  --batch --skip-column-names \
  | python3 -c "
import sys, json
rows = [l.split('\t') for l in sys.stdin.read().strip().split('\n')]
print(json.dumps([{'orderId':int(r[0]),'fanId':int(r[1]),'amount':float(r[2]),'orderPaymentKey':r[3]} for r in rows]))
" > /tmp/orders.json
aws s3 cp /tmp/orders.json s3://<BUCKET>/k6/orders.json
```

> `tokens.csv`와 `orders.json`은 유효한 JWT·주문 정보를 포함하므로 테스트 완료 후 S3에서 즉시 삭제.

---

## 5. 시나리오별 EC2 사전 준비 (워크플로우가 자동화하지 않는 부분)

워크플로우는 k6 실행만 담당한다. EC2 상태 초기화는 수동으로 진행.

| 시나리오 | EC2 사전 작업 |
|---|---|
| 01 주문 동시성 | `UPDATE inventory SET available_qty=100, reserved_qty=0, total_qty=100, version=0 WHERE product_id=1;` + Redis 티켓 재적재 |
| 02 피드 조회 | 없음 |
| 03 결제 확인 | Wiremock Docker 기동 확인 + RESERVED 주문 50건 + orders.json S3 업로드 |
| 04 드롭스 스파이크 | `UPDATE inventory SET available_qty=100, reserved_qty=0, total_qty=100, version=0 WHERE product_id=1;` + Redis 티켓 재적재 (05 먼저 실행 금지) |
| 05 SSE 대기열 | Redis 티켓 재적재 |
| 06 통합 워크로드 | `UPDATE inventory SET available_qty=200, reserved_qty=0, total_qty=200, version=0 WHERE product_id=1;` + Redis 티켓 재적재 + Wiremock 기동 확인 |

**Redis 티켓 재적재 (공통 명령):**
```bash
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

---

## 6. 워크플로우 실행 절차

1. GitHub → Actions → **Run k6 Load Test** → **Run workflow**
2. `scenario` 선택 (02 → 01 → 04 → 03 → 05 → 06 순서 권장)
3. `confirm` 입력란에 `yes` 입력
4. Actions 로그에서 k6 터미널 출력 확인
5. 결과를 `k6-baseline-results.md` 해당 시나리오 섹션에 기록

---

## 7. Prometheus 출력 없음

Runner에서 EC2 Prometheus(`http://localhost:9090/api/v1/write`)에 접근할 수 없으므로  
`--out experimental-prometheus-rw` 플래그를 사용하지 않는다.

> Grafana 실시간 모니터링이 필요하면 EC2 SSM 세션에서 직접 k6를 실행하는 구버전 명령어를 사용한다.  
> 단, t3.small CPU 경합으로 tail latency가 부풀어 있음을 감안해야 한다. (`k6-baseline-results.md` 참고)

---

## 8. 테스트 완료 후 정리

```bash
# S3 seed 파일 삭제 (JWT·주문 정보 노출 방지)
aws s3 rm s3://<BUCKET>/k6/tokens.csv
aws s3 rm s3://<BUCKET>/k6/orders.json

# Redis AccessTicket 테스트용 키 삭제
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls del "access:ticket:1:$i"
done
```