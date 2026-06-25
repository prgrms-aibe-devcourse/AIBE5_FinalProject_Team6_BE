# ADR-022: CD 파이프라인 — GitHub Actions OIDC + S3 artifact + SSM RunCommand

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-05 |
| **구현 완료일** | 2026-06-11 (PR #221, #222) |
| **선행 ADR** | [ADR-020 SSM 접근](./ADR-020-ssm-no-ssh-ec2-access.md) · [ADR-021 단일 EC2 인프라](./ADR-021-aws-single-ec2-infra-phase1.md) · [ADR-008 Blue/Green 배포](./ADR-008-nginx-bluegreen-deployment.md) |
| **관련** | [.github/workflows/cd.yml](../../.github/workflows/cd.yml) · [.github/scripts/bluegreen-deploy.sh](../../.github/scripts/bluegreen-deploy.sh) · [aws-phase3-runbook.md](../operations/aws/aws-phase3-runbook.md) |
| **담당** | 지영재 (SRE/Platform) |

---

## Title

GitHub Actions에서 AWS 자격증명을 장기 보관하지 않고 **OIDC 역할 위임**으로 인증하고, JAR 아티팩트를 S3에 업로드한 뒤 **SSM RunCommand**로 EC2에서 Blue/Green 배포 스크립트를 원격 실행한다.

---

## Context

### 현황

| 항목 | 값 |
| --- | --- |
| CD 트리거 | `develop` 브랜치 CI(`CI` workflow) 성공 시 자동 실행 (`workflow_run`) |
| EC2 접근 | SSM Session Manager 전용 — SSH 포트 22 미개방 (ADR-020) |
| 배포 방식 | Blue/Green 포트 스위칭 (ADR-008) |
| 아티팩트 크기 | Spring Boot fat JAR (~70MB) |

### 문제

Phase 1까지는 S3 수동 업로드 → SSM 수동 접속 → `systemctl restart` 방식으로 배포했다. Phase 2 이후 코드 변경이 빈번해지면서 수동 배포는 다음 문제를 유발한다.

- 배포마다 팀원이 SSM 접속 후 명령을 수작업으로 실행 → 실수 가능성
- `systemctl restart`는 30~60초 다운타임 → SLO 오염 (ADR-008에서 Blue/Green으로 해소)
- GitHub Actions에서 AWS API를 호출하려면 자격증명이 필요 → Access Key를 GitHub Secrets에 저장하면 장기 노출 위험

---

## 방안 비교

### A안 — Access Key를 GitHub Secrets에 저장

```
GitHub Actions
  AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY (Secrets)
    ↓
  aws s3 cp / aws ssm send-command
```

| 관점 | 평가 |
| --- | --- |
| 구현 난이도 | 낮음 — 단순 시크릿 등록 |
| 보안 | 장기 자격증명(Access Key)이 GitHub Secrets에 상주 — 키 노출 시 로테이션 불편 |
| 감사 | CloudTrail에 IAM User ARN으로 기록 |
| **기각 이유** | 장기 자격증명은 유출 시 즉시 악용 가능하다. 부트캠프 계정에서 IAM User 발급 자체가 제한적이고, OIDC가 지원되면 Access Key를 발급하지 않는 것이 우선 원칙이다. |

### B안 — GitHub Actions OIDC + IAM 역할 위임 ✅ 채택

```
GitHub Actions
  id-token: write (OIDC JWT 발급)
    ↓ AssumeRoleWithWebIdentity
  IAM Role (AWS_ROLE_ARN)
    ↓ 단기 임시 자격증명 (STS, 1시간 만료)
  aws s3 cp (JAR + deploy scripts → S3)
    ↓
  aws ssm send-command → EC2 Blue/Green 배포
```

| 관점 | 평가 |
| --- | --- |
| 보안 | 장기 Access Key 없음 — 임시 자격증명(1시간 만료) |
| 감사 | CloudTrail에 OIDC 발급자 + Actions 워크플로 정보 기록 |
| 구현 난이도 | 중간 — OIDC Provider, IAM Role trust policy 설정 필요 |
| **결론** | **채택 — Access Key를 GitHub Secrets에 저장하지 않음** |

### C안 — GitHub Actions Self-Hosted Runner (EC2 위에서 실행)

```
EC2에 GitHub Actions Runner 설치
Runner가 IAM Role을 직접 사용 → 자격증명 설정 불필요
```

| 관점 | 평가 |
| --- | --- |
| 구현 난이도 | 중간 — Runner 설치·등록·유지보수 |
| 비용 | GitHub-hosted runner 무료 → Self-hosted 전환 시 EC2 리소스 소비 증가 |
| **기각 이유** | Runner 유지보수 부담이 추가되고 EC2-1(App 서버)와 CPU를 공유하면 부하 테스트 중 결과 오염 위험이 있다. |

---

## Decision

**B안 채택 — GitHub Actions OIDC + S3 + SSM RunCommand.**

배포 흐름:

```
1. CI workflow 성공 (develop 브랜치)
2. CD workflow 자동 트리거 (workflow_run)
3. GitHub Actions OIDC → AssumeRoleWithWebIdentity
4. Gradle bootJar (테스트 생략 — CI에서 완료)
5. S3 업로드: JAR + bluegreen-deploy.sh + nginx 설정 파일
6. SSM RunCommand: EC2에서 bluegreen-deploy.sh 원격 실행
7. 폴링 (10초 간격, 최대 10분) → Success / Failed 확인
```

**핵심 구현 결정:**

| 결정 | 이유 |
| --- | --- |
| JAR를 S3 경유 전달 | SSM RunCommand의 `--parameters commands` 크기 제한(~3,000자) → JAR 직접 전달 불가. S3 경유 후 EC2에서 다운로드 |
| S3에 배포 스크립트도 업로드 | EC2 내 스크립트 버전 관리를 GitHub 레포와 동기화 — EC2에서 `git pull` 불필요 |
| SSM RunCommand + `file://` JSON 파라미터 | heredoc EOF 공백 문제 및 PowerShell 인코딩 우회 — `jq`로 파라미터 JSON 파일 생성 후 `file://` 참조 |
| 폴링 완료 대기 (10초 × 60회) | SSM RunCommand는 비동기 — 최대 10분 내 배포 완료 강제 |
| `permissions: id-token: write` 명시 | OIDC 토큰 발급에 필요한 최소 권한 — 불필요한 권한 제거 |

### develop 브랜치 자동 배포 (구현 완료)

`develop` 브랜치에서 CI 성공 시 자동으로 prod 환경에 배포된다.

### main 브랜치 릴리스 CD (미구현)

`main` 브랜치 또는 `release/*` 브랜치에 대한 별도 CD 워크플로는 현재 존재하지 않는다. 프로젝트 기간 내 `main` 브랜치 릴리스 전략(태그 기반 배포, 승인 게이트 등)은 구현하지 않았다.

---

## Consequences

### 긍정

- Access Key를 GitHub Secrets에 저장하지 않음 → 장기 자격증명 노출 위험 제거
- Blue/Green 배포 자동화 → 코드 푸시 후 인적 개입 없이 무중단 배포
- S3의 배포 스크립트가 GitHub 레포와 항상 동기화 → EC2 직접 수정 방지

### 부정 · 수용

| 한계 | 원인 | 수용 근거 |
| --- | --- | --- |
| develop 푸시 즉시 prod 배포 | 스테이징 환경 없음 (ADR-021) | CI(테스트) 통과 조건 + Blue/Green 헬스체크 실패 시 자동 차단 |
| main 브랜치 릴리스 CD 미구현 | 프로젝트 기간 내 우선순위 | develop 단일 브랜치 배포로 운영. 릴리스 전략은 Phase 5 이후 과제 |
| SSM RunCommand 최대 10분 타임아웃 | 폴링 방식의 한계 | Spring Boot 기동 시간(~30초) 대비 충분한 여유. 실패 시 stdout/stderr 로그 자동 출력 |

### 불변

| 규칙 | 내용 |
| --- | --- |
| OIDC 인증 | GitHub Actions → AWS 인증에 Access Key 사용 금지 |
| CI 성공 조건 | CD는 CI(`CI` workflow) 성공 후에만 실행 — 테스트 실패 시 배포 차단 |
| 배포 스크립트 위치 | `.github/scripts/bluegreen-deploy.sh` — EC2 직접 수정 금지, 레포에서만 관리 |

---

## 검증

- [x] `permissions: id-token: write` + `aws-actions/configure-aws-credentials@v4` OIDC 인증 확인
- [x] S3 업로드 (JAR + bluegreen-deploy.sh + nginx 설정) 동작 확인
- [x] SSM RunCommand `file://` 파라미터 방식으로 한글/특수문자 인코딩 오류 없이 동작
- [x] 폴링 루프 Success / Failed 분기 및 오류 로그 출력 확인
- [x] `develop` 브랜치 CI 성공 → CD 자동 트리거 확인
- [ ] main 브랜치 릴리스 CD 워크플로 — **미구현**

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| CD 워크플로 | [.github/workflows/cd.yml](../../.github/workflows/cd.yml) |
| Blue/Green 배포 스크립트 | [.github/scripts/bluegreen-deploy.sh](../../.github/scripts/bluegreen-deploy.sh) |
| Blue/Green 배포 전략 | [ADR-008](./ADR-008-nginx-bluegreen-deployment.md) |
| SSM 접근 방식 | [ADR-020](./ADR-020-ssm-no-ssh-ec2-access.md) |
| Phase 3 Blue/Green + CD 적용 | [aws-phase3-runbook.md](../operations/aws/aws-phase3-runbook.md) |
