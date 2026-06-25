# ADR-020: SSM Session Manager 기반 SSH 없는 EC2 접근

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-27 |
| **구현 완료일** | 2026-05-27 (Phase 1 인프라 구축, aws-phase1-runbook.md) |
| **선행 ADR** | — |
| **관련** | [current-infra-state.md §5](../operations/aws/current-infra-state.md) · [aws-phase1-runbook.md §3-3](../operations/aws/aws-phase1-runbook.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

EC2 접근 방식으로 SSH Key Pair 대신 **AWS SSM Session Manager**를 채택한다. SSH 포트 22를 열지 않고 Key Pair를 발급하지 않는다.

---

## Context

### 인프라 현황

| 항목 | 값 |
| --- | --- |
| EC2 인스턴스 | `team06-fandrops` (`i-07d1c60d175cdb8ca`), t3.medium, Amazon Linux 2023 |
| IAM Role | `fandrops-prod-ec2-role` |
| VPC | `vpc-0fef7cb616a5fd333` (단일 prod 환경, stg/prod 물리 분리 없음) |
| 팀 규모 | 5인, 학습용 부트캠프 계정 (IAM User 추가 권한 제한) |

### 문제

EC2 운영에 SSH 키 페어를 사용할 경우 다음 문제가 발생한다.

- **키 관리 부담:** 5인 팀원 모두 동일 키 페어를 공유하거나, 팀원마다 별도 키를 발급해야 함. 퇴팀·키 노출 시 로테이션 절차가 복잡해짐
- **포트 22 노출:** 인터넷에 22번 포트를 열면 무차별 대입 공격 대상이 됨. IP 제한 적용 시 팀원 고정 IP가 없으면 운영 불가
- **부트캠프 계정 제약:** 관리자 권한 없이 IAM User 추가 발급 불가 → Access Key를 EC2에 저장하는 방식은 자격증명 유출 위험

---

## 방안 비교

### A안 — SSH Key Pair + 포트 22 개방

```
팀원 로컬 → SSH -i key.pem ec2-user@<Public IP>:22
```

| 관점 | 평가 |
| --- | --- |
| 구현 난이도 | 낮음 — 기존 익숙한 방식 |
| 보안 | 포트 22 공개 → 무차별 대입 공격 노출 |
| 키 관리 | 키 페어 공유 또는 1인 1키 → 팀 협업 시 관리 부담 |
| 감사 로그 | SSH 접속 로그만 남음, 세션 내 작업 기록 없음 |
| **기각 이유** | 포트 22 개방과 키 배포 관리 복잡도가 보안 이점보다 위험이 크다 |

### B안 — SSM Session Manager (IAM Role 기반) ✅ 채택

```
팀원 로컬 (aws ssm start-session --target i-xxx)
    ↓ HTTPS :443 outbound
AWS SSM Endpoint (VPC 외부 도달 가능, IAM 인증)
    ↓
EC2 SSM Agent
```

| 관점 | 평가 |
| --- | --- |
| 구현 난이도 | 중간 — IAM Role 설정, SSM Agent 활성화 필요 |
| 보안 | 포트 22 미개방, Key Pair 없음 — 공격 표면 최소화 |
| 인증 | AWS IAM (MFA 연동 가능) — 키 분실·노출 위험 없음 |
| 감사 로그 | CloudTrail에 세션 전체 기록 가능 |
| 비용 | SSM Session Manager 자체 무료 (CloudWatch Logs 추가 시 소액) |
| **결론** | **채택 — SSH 포트 노출 없이 IAM 기반 접근 통제** |

### C안 — Bastion Host + Private EC2

```
팀원 로컬 → SSH → Bastion EC2 (Public) → SSH → App EC2 (Private)
```

| 관점 | 평가 |
| --- | --- |
| 보안 향상 | App EC2가 Private 서브넷 → 외부 직접 노출 없음 |
| 비용 추가 | Bastion EC2 추가 (~10,500원/월 t3.micro) — 예산 90,000원 대비 부담 |
| 관리 복잡도 | Bastion 유지보수, 이중 SSH 설정 필요 |
| **기각 이유** | SSM이 Bastion 역할을 AWS 관리형으로 대체하므로 추가 EC2 비용과 관리 부담이 불필요하다 |

---

## Decision

**B안(SSM Session Manager) 채택.**

`fandrops-prod-ec2-role`에 `AmazonSSMManagedInstanceCore` 정책을 부착하고 EC2 생성 시 Key Pair를 발급하지 않는다. Security Group에서 SSH 22번 인바운드 규칙을 추가하지 않는다.

**핵심 구현 결정:**

| 결정 | 이유 |
| --- | --- |
| Key Pair 없이 EC2 생성 | 키 분실·공유 위험 원천 차단 |
| 포트 22 인바운드 미개방 | 공격 표면 최소화 |
| `AmazonSSMManagedInstanceCore` 관리형 정책 사용 | 최소 권한으로 SSM 기능 전체 활성화 |
| `ssm-user` 홈 디렉터리 `/tmp` 경유 배포 | `ssm-user`의 `/opt/fandrops/` 직접 쓰기 권한 없음 → `/tmp` 경유 후 `sudo mv` |

SSM은 CD 파이프라인(ADR-022)에서도 SSM RunCommand로 재활용되어 GitHub Actions가 EC2에 직접 SSH 없이 명령을 실행할 수 있는 기반이 된다.

---

## Consequences

### 긍정

- 포트 22 완전 미개방 → 인터넷 스캐너 대상 제거
- Key Pair 불필요 → 키 분실·공유 문제 없음
- IAM 기반 접근 제어 → CloudTrail 감사 로그 자동 수집
- SSM RunCommand 재활용 → CD 파이프라인 설계 단순화 (ADR-022)

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| AWS CLI 설치 필요 | 접속 방식 변경 | 팀원 전원 설치 1회 — 이후 동일 UX |
| 인터넷 단절 환경에서 접속 불가 | SSM이 AWS 엔드포인트와 HTTPS 통신 필요 | VPC Endpoint(PrivateLink) 도입으로 해결 가능. 현재 인터넷 게이트웨이 존재하므로 무관 |
| Windows PowerShell 인코딩 우회 필요 | ssm-user 셸 환경 | `PYTHONUTF8=1 --cli-input-json file://` 조합으로 해소 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| 포트 22 | `fandrops-prod-sg-ec2`에 SSH 22 인바운드 추가 금지 |
| Key Pair | EC2 생성 시 Key Pair 발급 금지 |
| IAM 자격증명 | Access Key를 EC2 인스턴스에 저장 금지 — IAM Role 기반 인증만 허용 |

---

## 검증

- [x] EC2 `i-07d1c60d175cdb8ca` — Key Pair 없음 확인
- [x] `fandrops-prod-sg-ec2` — SSH 22 인바운드 규칙 없음 확인 (aws-phase1-runbook.md §3-2)
- [x] SSM Session Manager 접속 성공 확인
- [x] `aws s3 cp` IAM Role 기반 인증 동작 확인 (Access Key EC2에 미저장)
- [x] SSM RunCommand를 통한 CD 파이프라인 동작 확인 (ADR-022)

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| Phase 1 인프라 구축 결과 | [aws-phase1-runbook.md](../operations/aws/aws-phase1-runbook.md) |
| 현행 인프라 상태 | [current-infra-state.md](../operations/aws/current-infra-state.md) |
| CD 파이프라인 (SSM RunCommand 활용) | [ADR-022](./ADR-022-cd-pipeline-gha-s3-ssm.md) |
| Phase 1 AWS 단일 EC2 인프라 | [ADR-021](./ADR-021-aws-single-ec2-infra-phase1.md) |
