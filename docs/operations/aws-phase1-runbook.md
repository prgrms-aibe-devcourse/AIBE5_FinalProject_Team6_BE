ㅁ# AWS Phase 1 인프라 구축 결과 및 수동 배포 검증 기록

> **관련:** [incident-response.md](./incident-response.md) · [failure-policy.md](./failure-policy.md) · [observability-metrics.md](./observability-metrics.md) · **담당:** 지영재 (SRE/Platform)

FANDROPS **단일 prod AWS 환경** Phase 1 구축 결과를 기록한다.
팀원이 현재 운영 환경을 이해하고, 동일 환경을 재현하거나, Phase 2 CD 자동화를 설계할 때 이 문서를 참고한다.

---

## 1. 개요

| 항목 | 내용 |
| --- | --- |
| 구축 환경 | AWS 단일 prod (stg/prod 물리 분리 없음) |
| Spring 프로필 | `SPRING_PROFILES_ACTIVE=prod` |
| 현재 상태 | **수동 배포 검증 완료** |
| CD 자동화 | **미완료 — Phase 2** (GitHub Actions + SSM RunCommand + OIDC Role) |
| AWS Budget 알람 | **미완료** — IAM User 권한 부족, 관리자/매니저에게 별도 설정 요청 필요 |

**Phase 1 범위에서 제외된 항목:** ALB, NAT Gateway, Docker, GitHub Actions CD, CloudWatch Agent, Prometheus / Grafana / k6

---

## 2. 최종 아키텍처 요약

```text
Internet
    |
[Internet Gateway]
    |
VPC  (DNS resolution ON / DNS hostnames ON)

  [Public Subnet 2a]               [Public Subnet 2c - 예비]
       [EC2 t3.small]
       Nginx :80 -> localhost:8080
       SSM outbound :443 -------------------------------->
       S3 outbound HTTPS -------------------------------->
              |
  [Private Subnet 2a]
       [RDS MySQL 8.0 db.t3.micro]

[Private Subnet Group: private-2a, private-2c]
     [ElastiCache Redis OSS 7.1 Single-node]
     Primary AZ: ap-northeast-2a
     Multi-AZ disabled
```

**핵심 원칙:**

- EC2 접속은 **SSM Session Manager** 전용 — SSH 22 미개방, Key Pair 미발급
- Spring Boot 8080은 **Nginx를 통해서만 외부 노출** — 8080 인바운드 미개방
- RDS/Redis는 **EC2 Security Group 소스에서만** 접근 가능

---

## 3. 실제 생성 리소스 목록

### 3-1. VPC / Network

| 리소스 | 설정값 |
| --- | --- |
| VPC | DNS resolution ON / DNS hostnames ON |
| Public Subnet | 2개 — Auto-assign public IPv4 **활성화** |
| Private Subnet | 2개 — Public IPv4 자동 할당 **비활성화** |
| Internet Gateway | VPC에 연결 완료 |
| Public Route Table | `0.0.0.0/0` → IGW |
| Private Route Table | IGW 경로 없음 (인터넷 단절) |

### 3-2. Security Groups

| SG 이름 | 방향 | 포트 | 소스 |
| --- | --- | --- | --- |
| `fandrops-prod-sg-ec2` | Inbound | HTTP 80 | `0.0.0.0/0` |
| `fandrops-prod-sg-ec2` | Inbound | SSH 22 | **없음** |
| `fandrops-prod-sg-ec2` | Inbound | TCP 8080 | **없음** |
| `fandrops-prod-sg-ec2` | Inbound | HTTPS 443 | **없음** |
| `fandrops-prod-sg-rds` | Inbound | MySQL 3306 | `fandrops-prod-sg-ec2` only |
| `fandrops-prod-sg-redis` | Inbound | Redis 6379 | `fandrops-prod-sg-ec2` only |

> HTTPS 443 인바운드는 도메인 확정 + Certbot/ACM 적용 시 Phase 2에서 추가.

### 3-3. IAM

| 항목 | 값 |
| --- | --- |
| Role name | `fandrops-prod-ec2-role` |
| 연결 정책 | `AmazonSSMManagedInstanceCore` (AWS 관리형) |
| 연결 정책 | `fandrops-prod-s3-policy` (고객 관리형 정책) |
| EC2 접속 방식 | SSM Session Manager (Key Pair 없이 생성, SSH 미사용) |

> `CloudWatchAgentServerPolicy`는 Phase 2에서 IAM 정책 추가 및 Agent 설치.

### 3-4. S3

| 항목 | 값 |
| --- | --- |
| **실제 버킷명** | `fandrops-prod-storage-495264909330-ap-northeast-2-an` |
| Public Access Block | 활성화 |
| Versioning | 활성화 |
| Lifecycle | 이전 버전 30일 정리 / 불완전 멀티파트 업로드 7일 정리 |
| CORS | 미설정 (Phase 2 — 프론트 도메인 확정 후) |
| 현재 배포 JAR 위치 | `deploy/api-server.jar` |

> **주의:** 계획서의 예시 버킷명 `fandrops-prod-storage`와 실제 버킷명이 다르다.
> 이후 모든 명령어·IAM ARN·스크립트에서 **실제 버킷명**을 사용해야 한다.

### 3-5. RDS

| 항목 | 값 |
| --- | --- |
| DB Identifier | `fandrops-prod-mysql` |
| Engine | MySQL 8.0.46 |
| Instance | db.t3.micro |
| 구성 | Single-AZ |
| Public access | No |
| Storage autoscaling | Disabled |
| Master username | `fandrops_admin` |
| Database name | `fandrops` |
| Deletion Protection | Enabled |

> RDS 삭제 시 Deletion Protection을 먼저 비활성화해야 한다.

### 3-6. ElastiCache Redis

| 항목 | 값 |
| --- | --- |
| Engine | Redis OSS 7.1 |
| 구성 | Single-node, Cluster mode disabled, Multi-AZ disabled |
| TLS (In-transit) | Enabled |
| At-rest encryption | Enabled |
| AUTH token | Disabled (Phase 1 초기) |
| Security Group | `fandrops-prod-sg-redis` |
| Subnet Group | `private-2a`, `private-2c` |
| Primary AZ | `ap-northeast-2a` |

### 3-7. EC2

| 항목 | 값 |
| --- | --- |
| AMI | Amazon Linux 2023 |
| Instance type | t3.small |
| Subnet | Public subnet |
| IAM Role | `fandrops-prod-ec2-role` |
| Key Pair | 없음 |
| 접속 방식 | SSM Session Manager |
| Java | Amazon Corretto 21 |
| AWS CLI | 설치 완료 (IAM Role 기반 인증, Access Key 저장 안 함) |

---

## 4. EC2 내부 파일 구조

### 4-1. 환경 파일

| 항목 | 값 |
| --- | --- |
| **최종 환경파일 경로** | `/etc/fandrops/fandrops-prod.conf` |
| 삭제된 파일 | `/etc/fandrops/app.env` (삭제 완료) |
| 권한 | `chmod 600` / `root:root` |
| 용도 | systemd `EnvironmentFile` — GitHub 레포 파일이 아닌 EC2 내부 파일 |

**환경 파일 예시** (비밀값은 placeholder 처리):

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/fandrops?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=fandrops_admin
DB_PASSWORD=<REDACTED>
REDIS_HOST=master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com
REDIS_PORT=6379
REDIS_SSL_ENABLED=true
```

> **주의:** `REDIS_HOST`에는 포트(`:6379`)를 포함하지 않는다. 포트는 `REDIS_PORT`에만 입력한다.
> 포트를 `REDIS_HOST`에 포함하면 Lettuce가 `REDIS_PORT`를 중복 추가해 `Host has a port` 오류가 발생한다. (트러블슈팅 E 참고)

### 4-2. JAR 파일

| 항목 | 값 |
| --- | --- |
| 위치 | `/opt/fandrops/app.jar` |
| 소유자 | `fandrops:fandrops` |
| 권한 | `755` |

---

## 5. systemd 설정

```ini
[Unit]
Description=FANDROPS API Server
After=network.target

[Service]
Type=simple
User=fandrops
Group=fandrops
EnvironmentFile=/etc/fandrops/fandrops-prod.conf
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/fandrops/app.jar --server.port=8080
ExecStop=/bin/kill -TERM $MAINPID
TimeoutStopSec=30
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=fandrops

[Install]
WantedBy=multi-user.target
```

| 항목 | 값 |
| --- | --- |
| 서비스명 | `fandrops.service` |
| 상태 | `enabled` / `active(running)` 확인 완료 |
| JVM 힙 | `-Xms512m -Xmx1024m` (t3.small 2GB 기준, OS·Nginx 버퍼 확보) |
| 포트 | `--server.port=8080` |

로그 확인:

```bash
sudo journalctl -u fandrops -f
sudo journalctl -u fandrops -n 100
sudo journalctl -u fandrops -p err
```

---

## 6. Nginx 설정

### 6-1. 초기 시도와 문제

초기에는 `/etc/nginx/conf.d/fandrops.conf`에 독립 `server` 블록을 생성했으나, Amazon Linux 2023 기본 Nginx `server` 블록과 **우선순위 충돌**로 `/actuator/health` 요청이 기본 Nginx 404 페이지를 반환했다.

### 6-2. 최종 적용 방식

- `/etc/nginx/conf.d/fandrops.conf` → `.bak` 처리 (비활성화)
- `/etc/nginx/default.d/fandrops-location.conf` 생성
- Amazon Linux 기본 `server` 블록의 `include /etc/nginx/default.d/*.conf;` 구문을 통해 `location` 블록만 주입

```nginx
# /etc/nginx/default.d/fandrops-location.conf

location / {
    proxy_pass http://localhost:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
    proxy_connect_timeout 10s;
}

location /actuator/health {
    proxy_pass http://localhost:8080/actuator/health;
    access_log off;
}
```

| 항목 | 값 |
| --- | --- |
| 도메인 | 미확정 (Phase 2) |
| HTTPS | 미적용 (Phase 2 — 도메인 확정 후 Certbot/ACM) |
| ALB | 없음 (Phase 2) |

---

## 7. 수동 배포 절차

### 7-1. 로컬 빌드

```bash
./gradlew.bat clean :apps:api-server:bootJar
# 산출물: apps/api-server/build/libs/api-server.jar
```

### 7-2. S3 업로드

S3 콘솔 → `fandrops-prod-storage-495264909330-ap-northeast-2-an` → `deploy/` → `api-server.jar` 업로드

> IAM Access Key 무분별 발급 금지. CLI 업로드는 팀 합의 후 사용.

### 7-3. EC2 배포 (SSM Session Manager)

```bash
# 1. S3에서 /tmp로 다운로드 (IAM Role 기반 인증 — Access Key 불필요)
aws s3 cp s3://fandrops-prod-storage-495264909330-ap-northeast-2-an/deploy/api-server.jar /tmp/app.jar

# 2. /opt/fandrops/로 이동 및 권한 설정
sudo mv /tmp/app.jar /opt/fandrops/app.jar
sudo chown fandrops:fandrops /opt/fandrops/app.jar
sudo chmod 755 /opt/fandrops/app.jar

# 3. 서비스 재시작
sudo systemctl restart fandrops

# 4. 기동 확인
sleep 30
sudo systemctl status fandrops
curl -s http://localhost:8080/actuator/health
```

> **왜 `/tmp` 경유인가:** `ssm-user`는 `/opt/fandrops/`에 직접 쓰기 권한이 없어 Permission denied가 발생한다.
> `/tmp`로 받은 뒤 `sudo mv`로 이동하는 방식을 사용한다.
> 이 흐름은 Phase 2 SSM RunCommand 스크립트로 그대로 전환 가능하다.

### 7-4. 헬스 체크

```bash
curl http://localhost:8080/actuator/health
curl http://localhost/actuator/health
# 예상 응답: {"status":"UP"}
# show-details: never 설정으로 db/redis 상세는 표시되지 않음
```

---

## 8. 검증 결과

- [x] `java -version` → Amazon Corretto 21 확인
- [x] `aws --version` 확인 / Access Key EC2에 미저장
- [x] SSM Session Manager 접속 확인
- [x] `aws s3 cp` IAM Role 기반 S3 다운로드 확인
- [x] `systemctl status fandrops` → `active(running)`
- [x] `systemctl status nginx` → `active(running)`
- [x] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [x] `curl http://localhost/actuator/health` → `{"status":"UP"}`
- [x] 외부 Public IP `/actuator/health` → `{"status":"UP"}`
- [x] RDS 연결 확인 — journalctl HikariPool 초기화 로그 / Database version 8.0.46 확인
- [x] Redis 연결 확인 — REDIS_HOST 포트 중복 수정 후 health UP 확인

---

## 9. 트러블슈팅 기록

| # | 증상 | 원인 | 해결 |
| --- | --- | --- | --- |
| A | `aws s3 cp` → `NoSuchBucket` | 계획서 예시 버킷명과 실제 버킷명 불일치 | 실제 버킷명 `fandrops-prod-storage-495264909330-ap-northeast-2-an`으로 명령어·IAM ARN 수정 |
| B | `aws s3 cp` → `AccessDenied` | EC2 IAM Role S3 정책 ARN에 실제 버킷명 미반영 또는 `s3:ListBucket` 권한 누락 | IAM 정책을 실제 버킷명 기준으로 수정, `s3:ListBucket` prefix 조건 추가 |
| C | `/opt/fandrops/app.jar` → `Permission denied` | `ssm-user`가 `/opt/fandrops/`에 직접 쓰기 권한 없음 | `/tmp/app.jar`로 다운로드 후 `sudo mv`로 이동 |
| D | systemd → `Failed to load environment files` | `EnvironmentFile`이 `fandrops-prod.conf`를 참조하는데 실제 파일은 `app.env`만 존재 | `fandrops-prod.conf` 생성 후 `app.env` 삭제 |
| E | `/actuator/health` → DOWN (Redis) | `REDIS_HOST`에 `:6379` 포트 포함 → Lettuce가 `REDIS_PORT`를 중복 추가 → `Host has a port` 오류 | `REDIS_HOST`에서 포트 제거, 포트는 `REDIS_PORT=6379`에만 입력 |
| F | Nginx → 기본 404 반환 | `/etc/nginx/conf.d/fandrops.conf` 독립 server 블록이 Amazon Linux 기본 server 블록과 우선순위 충돌 | `fandrops.conf` `.bak` 처리, `/etc/nginx/default.d/fandrops-location.conf`에 location 블록만 추가 |

---

## 10. 최종 DoD

### 완료

- [x] VPC / DNS resolution·hostnames 활성화
- [x] Public Subnet 2개 / Private Subnet 2개
- [x] Internet Gateway / Route Table
- [x] Security Group 3개 (EC2·RDS·Redis)
- [x] IAM Role (`fandrops-prod-ec2-role`)
- [x] S3 버킷 (`fandrops-prod-storage-495264909330-ap-northeast-2-an`)
- [x] RDS MySQL 8.0.46 (`fandrops-prod-mysql`)
- [x] ElastiCache Redis OSS 7.1
- [x] EC2 (Amazon Linux 2023, t3.small, SSM 접속 확인)
- [x] Java 21 / AWS CLI
- [x] `/etc/fandrops/fandrops-prod.conf` (chmod 600, root:root)
- [x] systemd `fandrops.service` (enabled / active)
- [x] Nginx (`/etc/nginx/default.d/fandrops-location.conf`)
- [x] JAR 빌드 및 S3 수동 배포
- [x] RDS 연결 확인 (HikariPool 로그)
- [x] Redis 연결 확인
- [x] 내부 / Nginx 경유 / 외부 health UP

### 미완료

- [ ] **AWS Budget 알람** — IAM User 권한 부족. 관리자/매니저에게 설정 요청 필요 ($50 경고 / $57 강경고 / Budget $60)

### 생략

- 실제 롤백 수행 — 첫 배포라 이전 JAR 없음. Phase 2 CD 자동화에서 정식 구성 예정

---

## 11. Phase 2 TODO

| 항목 | 비고 |
| --- | --- |
| GitHub Actions CD (workflow_dispatch) | S3 업로드 → SSM RunCommand 배포 자동화 |
| GitHub OIDC Role | Access Key 없이 GitHub Actions → AWS 인증 |
| SSM RunCommand 배포 스크립트 | `/tmp` 경유 배포 흐름 그대로 자동화 |
| 배포 artifact 버전/날짜 naming | `api-server-{sha}-{date}.jar` 등 |
| health check 실패 시 이전 JAR 복구 | 롤백 자동화 |
| AWS Budget 알람 설정 | 관리자 권한으로 별도 설정 |
| 도메인 확정 후 Nginx server_name / HTTPS / Certbot | — |
| S3 CORS 설정 | 프론트 도메인 확정 + 업로드 API 구현 시 |
| Redis AUTH Token 도입 여부 검토 | `REDIS_PASSWORD` 설정 + ElastiCache Modify |
| CloudWatch Agent | Phase 2에서 IAM 정책 추가 및 Agent 설치 |
| Prometheus / Grafana / k6 | 테스트/관측 단계에서 별도 검토 |

---

## 12. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [incident-response.md](./incident-response.md) | P0~P2 장애 대응 |
| [failure-policy.md](./failure-policy.md) | Redis·DB 장애 정책 |
| [observability-metrics.md](./observability-metrics.md) | SLO·메트릭·알람 |
| [personas/jiyoungjae.md](../ai/personas/jiyoungjae.md) | SRE 담당 체크리스트 |