# ADR-021: Phase 1 AWS 단일 EC2 + private RDS/Redis 인프라 구조

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-27 |
| **구현 완료일** | 2026-05-27 (aws-phase1-runbook.md DoD 완료) |
| **선행 ADR** | [ADR-020 SSM 접근](./ADR-020-ssm-no-ssh-ec2-access.md) |
| **관련** | [aws-phase1-runbook.md](../operations/aws/aws-phase1-runbook.md) · [current-infra-state.md](../operations/aws/current-infra-state.md) · [ADR-008 Blue/Green 배포](./ADR-008-nginx-bluegreen-deployment.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

예산 90,000원/월 제약과 단일 prod 환경 원칙 하에, **공개 서브넷 EC2 1대 + 비공개 서브넷 RDS/Redis** 구조로 Phase 1 AWS 인프라를 구성한다.

---

## Context

### 제약 조건

| 항목 | 값 |
| --- | --- |
| 월 예산 | 90,000원 |
| 팀 AWS 계정 | 부트캠프 공용 (`495264909330`) — IAM User 추가 권한 제한 |
| 배포 환경 분리 | **단일 prod 환경** — stg/prod 물리 분리 없음 |
| 목표 | SLO 측정 가능한 운영 환경 구성 + 보안 기본 원칙 준수 |

### Phase 1 범위 정의

Phase 1에서 **포함**하는 항목:

- VPC / 서브넷 / 라우팅 테이블 / 인터넷 게이트웨이
- Security Group 3개 (EC2, RDS, Redis)
- IAM Role (`fandrops-prod-ec2-role`)
- S3 버킷 (애플리케이션 아티팩트 + 팬 업로드 통합)
- EC2-1 (Amazon Linux 2023, t3.medium, SSM 전용)
- RDS MySQL 8.0 (`db.t3.micro`, 단일 AZ)
- ElastiCache Redis OSS 7.1 (`cache.t3.micro`, 단일 노드)
- Nginx + Spring Boot 수동 배포 검증

Phase 1 **범위 외** (이후 Phase에서 도입): ALB, NAT Gateway, Docker 기반 모니터링, GitHub Actions CD, CloudWatch Agent, k6

### 문제

기능 개발 시작 전에 배포 가능한 AWS 환경이 필요하다. 예산 제약과 부트캠프 계정 권한 제한 내에서 다음 세 가지를 동시에 만족해야 한다.

1. 인터넷에서 HTTP(S) 요청을 수신할 수 있어야 한다
2. RDS / Redis는 인터넷에 직접 노출되지 않아야 한다
3. 향후 CD 자동화 / Blue-Green 배포 / k6 부하 테스트를 수용할 수 있는 구조여야 한다

---

## 방안 비교

### A안 — EC2 단일 서버 (RDS/Redis 포함, 단일 public 서브넷) — 기각

```
Public Subnet
  EC2-1 (App + Nginx)
  RDS MySQL           ← 공개 서브넷에 위치, Public Access 활성화 가능
  ElastiCache Redis   ← 공개 서브넷에 위치
```

| 관점 | 평가 |
| --- | --- |
| 구성 단순도 | 가장 단순 |
| 비용 | 최소 |
| 보안 | RDS/Redis가 공개 서브넷 → 인터넷 노출 위험 |
| **기각 이유** | 공개 서브넷의 RDS는 Security Group으로 막더라도 Public Access 설정 실수 시 데이터베이스가 인터넷에 노출된다. 비용 절감보다 보안 원칙 준수가 우선이다. |

### B안 — EC2 공개 서브넷 + RDS/Redis 비공개 서브넷 ✅ 채택

```
Internet → IGW

[Public Subnet 2a]          EC2-1 (App + Nginx) — SSM 아웃바운드 :443
                                 ↓ private IP
[Private Subnet 2a/2c]      RDS MySQL, ElastiCache Redis
                             (Internet 경로 없음 — EC2 SG 소스만 허용)
```

| 관점 | 평가 |
| --- | --- |
| 구성 단순도 | 중간 — 서브넷 4개, 라우팅 테이블 분리 필요 |
| 비용 | EC2-1 (~35,000원) + RDS (~15,000원) + Redis (~15,000원) + S3/CloudWatch = **~72,400원** |
| 보안 | RDS/Redis 인터넷 완전 차단 — EC2 SG 소스에서만 접근 가능 |
| 확장성 | EC2-2 추가, Blue/Green 슬롯 추가, k6 전용 인스턴스 추가 가능 |
| **결론** | **채택** |

### C안 — Staging + Production 이중 환경

```
Staging VPC:  EC2-s (t3.micro) + RDS-s (db.t3.micro) + Redis-s (cache.t3.micro)
Production VPC: EC2-p + RDS-p + Redis-p
```

| 관점 | 평가 |
| --- | --- |
| 비용 | Staging + Production = ~144,800원/월 → **예산 90,000원 대비 60% 초과** |
| **기각 이유** | 부트캠프 프로젝트 기간(5주)과 예산 90,000원 내에서 스테이징 환경을 별도 운영하는 것은 비용 효율이 없다. 단일 prod 환경에서 Blue/Green 배포 + 기능 플래그로 대체한다. |

---

## Decision

**B안 채택 — 공개 서브넷 EC2 + 비공개 서브넷 RDS/Redis.**

VPC를 `10.0.0.0/16`으로 생성하고 공개 서브넷 2개(2a/2c)와 비공개 서브넷 2개(2a/2c)로 분리한다. RDS와 Redis는 비공개 서브넷에만 위치하고 Security Group 소스를 EC2 SG로 제한한다. EC2는 NAT Gateway 없이 인터넷 게이트웨이를 통해 S3 / SSM 엔드포인트에 접근한다.

**핵심 구성 결정:**

| 결정 | 이유 |
| --- | --- |
| NAT Gateway 미도입 | 비용 ~45,000원/월 추가 → 예산 초과. EC2가 공개 서브넷에 위치하므로 불필요. RDS/Redis는 인터넷 아웃바운드 불필요 |
| ALB 미도입 | Phase 1 (Phase 2 이후 재검토). 비용 ~22,600원/월 — 예산 대비 과도. Blue/Green은 Nginx로 구현 (ADR-008) |
| 비공개 서브넷 2개 AZ (2a + 2c) | RDS 서브넷 그룹 최소 2개 AZ 요구사항 충족. 단일 AZ 장애 시 Multi-AZ 전환 용이성 |
| S3 단일 버킷 통합 | 배포 아티팩트(`deploy/`) + 팬 업로드(`uploads/`) 동일 버킷 분리 prefix로 관리 — 버킷 수 최소화 |
| RDS Deletion Protection 활성화 | 실수로 인한 데이터베이스 삭제 방지 |

---

## Consequences

### 긍정

- RDS/Redis 인터넷 완전 차단 — EC2 Security Group 소스 화이트리스트 적용
- 월 비용 ~72,400원 — 예산 90,000원 대비 17,600원 여유
- 공개 서브넷 예비(2c) 확보 — k6 전용 EC2-2 추가 시 즉시 활용 가능
- NAT Gateway 없는 구조가 CD 파이프라인 설계를 단순하게 유지

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| 스테이징 환경 없음 | 예산 제약 | Blue/Green 배포(ADR-008) + 로컬 Docker Compose로 대체 |
| EC2-1 SPOF | ALB 미도입 | Blue/Green은 다운타임 최소화 목적. HA는 ALB 도입 시 해결 가능한 구조적 한계로 발표에서 명시 |
| RDS 단일 AZ | Multi-AZ 비용 (~2배) | 부트캠프 프로젝트 기간 내 RDS 장애 복구 목표 미포함. Deletion Protection으로 실수 방지 |
| Redis 단일 노드 | Cluster mode 비용 | 대기열·캐시 데이터 휘발 시 재적재 절차 존재. fail-fast 정책(failure-policy.md)으로 수용 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| RDS/Redis 위치 | 비공개 서브넷 유지 — 공개 서브넷 이동 금지 |
| EC2 IAM Role 기반 인증 | Access Key를 EC2에 저장 금지 (ADR-020) |
| S3 Public Access Block | 전체 활성화 유지 (`BlockPublicAcls`, `IgnorePublicAcls`, `BlockPublicPolicy`, `RestrictPublicBuckets`) |

---

## 검증

- [x] VPC `vpc-0fef7cb616a5fd333` — DNS resolution / hostnames 활성화
- [x] 공개 서브넷 2개 (`10.0.1.0/24`, `10.0.2.0/24`) / 비공개 서브넷 2개 (`10.0.11.0/24`, `10.0.12.0/24`)
- [x] RDS `fandrops-prod-mysql` — Public Access No, 비공개 서브넷
- [x] ElastiCache `fandrops-prod-redis-001` — 비공개 서브넷, `fandrops-prod-sg-redis`
- [x] S3 `fandrops-prod-storage-495264909330-ap-northeast-2-an` — Public Access Block 전체 활성화
- [x] 외부 공인 IP 기준 `/actuator/health` → `{"status":"UP"}` 응답 확인
- [x] 월 비용 ~72,400원 (EC2 + RDS + Redis + S3/CloudWatch 기준)

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| Phase 1 구축 결과 상세 | [aws-phase1-runbook.md](../operations/aws/aws-phase1-runbook.md) |
| 현행 인프라 상태 (리소스 ID / 엔드포인트) | [current-infra-state.md](../operations/aws/current-infra-state.md) |
| Blue/Green 배포 전략 (ALB 미도입 근거) | [ADR-008](./ADR-008-nginx-bluegreen-deployment.md) |
| EC2 접근 방식 (SSM) | [ADR-020](./ADR-020-ssm-no-ssh-ec2-access.md) |
