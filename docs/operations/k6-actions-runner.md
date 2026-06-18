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

## 8. 2026-06-18 첫 실행 검증 결과

Actions runner 전환 후 시나리오 02로 end-to-end 검증 완료.

| 지표 | 결과 |
|---|---|
| 실행 환경 | GitHub Actions ubuntu-latest |
| 시나리오 | 02 피드 조회 (50 VU, 2분) |
| P95 응답 시간 | **216.39ms** |
| 에러율 | **0.00%** |
| checks 통과 | **100%** (59,824 / 59,824) |
| EC2 로컬 이전 기록 | 231ms → Actions runner 분리로 ~7% 개선 |

**파이프라인 최종 상태**: S3 다운로드 ✅ · Nginx 정상 ✅ · JWT 인증 ✅ · 응답 정상 ✅  
**남은 과제**: p(95)=216ms > SLO 120ms — 최적화 후 재측정 필요 (담당: 정환철)

---

## 9. 최초 실행 트러블슈팅 이력 (2026-06-18)

Actions runner로 첫 실행 시 발생한 문제들. 순서대로 발생함.

### T-1. IAM S3 GetObject 권한 누락

**현상**: seed 파일 다운로드 단계에서 `An error occurred (403) when calling the GetObject operation` 발생.  
**원인**: `fandrops-github-actions-role`에 `k6/` prefix에 대한 `s3:GetObject` 권한 없음.  
**해결**: IAM 콘솔에서 `fandrops-k6-seed-policy` 인라인 정책 추가.
```json
{
  "Effect": "Allow",
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::<BUCKET>/k6/*"
}
```

---

### T-2. Nginx 502 Bad Gateway — upstream 포트 하드코딩

**현상**: k6 첫 요청 전체 502 반환.  
**원인**: `/etc/nginx/default.d/fandrops-location.conf`에 `proxy_pass http://localhost:8080` 하드코딩. 실제 active 슬롯은 green (:8082).  
**임시 해결**: EC2에서 `sed`로 포트 수정 후 nginx reload.  
**근본 해결**: CD 파이프라인이 `nginx/fandrops-location.conf`를 배포하도록 수정 (T-5 참고).

---

### T-3. Nginx `upstream not allowed here`

**현상**: `sudo nginx -t` 실패. `"upstream" directive is not allowed here`.  
**원인**: `fandrops-location.conf`가 `default.d/`(server context)에 있는데 `include /etc/nginx/fandrops-active.conf`를 통해 `upstream` 블록을 server context에서 정의하려 함. nginx는 `upstream`을 `http` context에서만 허용.  
**해결**: `/etc/nginx/conf.d/fandrops-upstream.conf` 신규 생성 — `include` + rate-limit zones를 http context에서 정의.  
`nginx/fandrops-location.conf`에서 `include` 줄 제거.

```nginx
# /etc/nginx/conf.d/fandrops-upstream.conf (http context)
include /etc/nginx/fandrops-active.conf;

limit_req_zone  $binary_remote_addr zone=fandrops_order:10m   rate=5r/s;
limit_req_zone  $binary_remote_addr zone=fandrops_queue:10m   rate=10r/s;
limit_req_zone  $binary_remote_addr zone=fandrops_payment:10m rate=5r/s;
limit_conn_zone $binary_remote_addr zone=fandrops_sse:10m;
```

---

### T-4. `zero size shared memory zone "fandrops_order"`

**현상**: nginx -t 시 `zero size shared memory zone "fandrops_order"`.  
**원인**: `nginx/fandrops-zones.conf` (repo)가 한 번도 EC2에 배포된 적 없음. `limit_req_zone` 정의 없이 `limit_req zone=fandrops_order` 지시어만 존재.  
**해결**: T-3에서 생성한 `conf.d/fandrops-upstream.conf`에 zone 정의 포함. 이후 nginx -t 통과.

---

### T-5. JWT 403 — tokens.csv 서명 불일치

**현상**: k6 100% 403 응답. curl로 직접 앱 서버 호출해도 403 반환 (응답 body 없음).  
**원인**: S3에 업로드된 `tokens.csv`의 JWT가 이전에 사용하던 테스트용 secret으로 서명됨. EC2 앱이 실제 운영 `JWT_SECRET`으로 검증하므로 서명 불일치 → Spring Security filter에서 403.  
**진단 과정**:
1. `GET /actuator/health` → 200 ✅ (앱 정상)
2. 인증 없는 요청 → 200 ✅ (Nginx 정상)
3. Bearer 토큰 포함 요청 → 403, body 없음 ✅ (JWT 검증 실패)
4. EC2 `/etc/fandrops/fandrops-prod.conf`에서 `JWT_SECRET` 확인
5. tokens.csv 서명 secret과 불일치 확인

**해결**: `JWT_SECRET` 값으로 fan_id 1~2100 JWT 재생성, S3 재업로드.

> ⚠️ **보안 주의**: `JWT_SECRET`은 `/etc/fandrops/fandrops-prod.conf`에만 존재. 코드·커밋에 절대 기록 금지.

**tokens.csv 만료 일정**: 7일 유효기간 → **2026-06-25까지** 재생성 필요.

---

### T-6. CD 파이프라인 nginx 설정 파일 drift

**현상**: 매 CD 배포 시 `fandrops-active.conf`(포트)만 갱신되고 `fandrops-location.conf` · `fandrops-upstream.conf`는 업데이트되지 않음. EC2와 repo 간 설정 파일 drift 상시 발생 가능.  
**원인**: `bluegreen-deploy.sh`가 `fandrops-active.conf`만 작성. 나머지 nginx 설정 파일을 배포하는 단계 없음.  
**해결**: 
- `nginx/fandrops-upstream.conf` 신규 추가 (include + zones)
- `nginx/fandrops-location.conf`에서 `include` 줄 제거 (server context 불가)
- `cd.yml`에 nginx 파일 S3 업로드 단계 추가
- `bluegreen-deploy.sh` step 5에 S3 → EC2 nginx 설정 동기화 추가

이후 배포마다 nginx 설정이 repo 기준으로 자동 동기화됨.

---

### T-7. k6 threshold 미달(exit 99) → GitHub Actions job 실패로 표시

**현상**: k6 p(95)=216ms > threshold 120ms → exit code 99 → Actions job 전체 빨간불. 인프라 오류와 구분 불가.  
**원인**: k6는 threshold 실패 시 exit 99 반환. Actions는 0 이외 종료코드를 job 실패로 처리.  
**해결**: `run-k6.yml` k6 실행 단계에 exit code 처리 추가.
- exit 0 → 성공 (threshold 통과)
- exit 99 → job 성공 + `::warning::` 배너 표시 (SLO 미달 경고)
- 그 외 → job 실패 (실제 인프라/실행 오류)

---

## 10. 테스트 완료 후 정리

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