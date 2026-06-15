# ADR-009: 배너 이미지 S3 Presigned URL 업로드 방식 채택

| 항목 | 내용 |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-06-13 |
| **Deciders** | 표지민 (user 오너) |
| **Issue** | #180 |
| **PR** | #271 |

---

## Context

어드민이 배너 이미지를 등록할 때 서버가 파일을 직접 수신·저장하는 방식과, S3에 클라이언트가 직접 업로드하도록 Presigned URL을 발급하는 방식 중 하나를 선택해야 했다.

추가로 다음 세 가지 세부 결정이 필요했다.

1. **업로드 크기 제한 강제 방식**: API 레벨 검증만 할 것인가, S3 서명에도 포함할 것인가.
2. **S3 버킷 자격증명 관리**: 로컬 개발 환경에서 prod 버킷 이름을 git에 노출하지 않는 방법.
3. **이미지 존재 검증**: 배너 등록 시 S3에 실제로 파일이 있는지 확인할 것인가.

---

## Decision 1 — Presigned URL 방식 채택

**결정**: 서버 직접 수신이 아닌 S3 Presigned PUT URL 발급 방식.

**이유**:
- 파일이 Spring 서버 메모리·네트워크를 통과하지 않아 서버 부하 없음.
- `.github/workflows/setup-s3-cors.yml`에 `AllowedMethods: ["PUT"]`이 이미 설정되어 있어 Presigned URL 방식이 초기 설계 의도임을 확인.
- EC2에 `fandrops-prod-ec2-role`이 붙어 있어 배포 환경은 자격증명 코드 없이 IAM Role 자동 인증.

**기각된 대안**: 서버 Multipart 수신 → S3 PutObject. 서버 메모리·커넥션 점유, 업로드 크기에 따른 타임아웃 관리 복잡도가 높아 기각.

---

## Decision 2 — contentLength S3 서명 포함 (이중 강제)

**결정**: `@Max(5_242_880)` API 레벨 검증과 함께 `contentLength`를 `PutObjectRequest`에 바인딩해 S3 서명에 포함.

**이유**:
- Presigned URL은 URL 자체가 공개되므로 서버 검증 이후 S3 PUT 단계에서도 제한이 필요.
- `contentLength`를 서명에 포함하면 선언값과 다른 크기 PUT 시 S3가 자체적으로 403 반환 → 서버 개입 없이 우회 차단.

**트레이드오프**: 클라이언트는 S3 PUT 요청에 `Content-Length` 헤더를 정확히 일치시켜야 함. `mvp-api-spec.md`에 명시하여 프론트 계약으로 관리.

---

## Decision 3 — prod 버킷 이름 git 분리

**결정**: `application-local.yml`에서 버킷 값 제거, `application-local.override.yml`(gitignore 대상)에 위임.

**이유**:
- prod 버킷 이름(`fandrops-prod-storage-*`)이 public 레포에 노출되면 S3 공개 리소스 열거 가능.
- `application-local.yml`에 `spring.config.import: optional:application-local.override.yml`이 이미 설정되어 있어 추가 인프라 변경 없이 분리 가능.
- `application-local.example.yml`에 템플릿 추가하여 신규 팀원 온보딩 지원.

---

## Decision 4 — 배너 등록 전 HeadObject 존재 검증

**결정**: `BannerService.createBanner()` 시 `S3ImageValidationPort.imageExists()` 호출 (HeadObject).

**이유**:
- Presigned URL 발급 후 실제 PUT 완료 없이 바로 배너 등록을 시도하는 "presign-and-forget" 방지.
- HeadObject는 파일 다운로드 없이 메타데이터만 확인하는 경량 API.
- PUT 미완료 URL로 배너가 등록되면 깨진 이미지가 메인에 노출되는 장애로 이어짐.

---

## Consequences

**긍정**:
- 서버 업로드 부하 없음.
- 5MB 제한이 API + S3 서명 이중으로 강제됨.
- prod 버킷이 git에 노출되지 않음.
- 깨진 이미지 배너 등록 차단.

**부정/주의**:
- 클라이언트가 S3 PUT 시 `Content-Length` 헤더를 정확히 전달해야 함 (프론트 계약 사항).
- HeadObject 호출 비용 발생 (배너 등록 시 추가 S3 API 1회).
- Presigned URL은 만료 시간(현재 10분) 이내 사용해야 함. 만료 후 재발급 필요.

---

## 연관 문서

- `docs/api/mvp-api-spec.md` — POST /admin/uploads 명세 (contentLength 바인딩 제약 포함)
- `.github/workflows/setup-s3-cors.yml` — S3 CORS AllowedMethods PUT 설정 근거
- `modules/user/user-infrastructure/src/main/java/com/fandrops/user/infrastructure/S3PresignedUrlAdapter.java`
- `modules/user/user-infrastructure/src/main/java/com/fandrops/user/infrastructure/S3ImageValidationAdapter.java`
