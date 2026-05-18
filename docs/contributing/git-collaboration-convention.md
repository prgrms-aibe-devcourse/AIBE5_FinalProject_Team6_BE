# Git & 코드 협업 컨벤션

팀원이 동일한 브랜치 전략·커밋·PR·리뷰 기준으로 작업하기 위한 규칙입니다.

| 항목 | 위치 |
| --- | --- |
| **이슈 템플릿** (GitHub 자동 적용) | [`.github/ISSUE_TEMPLATE/task.md`](../../.github/ISSUE_TEMPLATE/task.md) |
| **PR 템플릿** (GitHub 자동 적용) | [`.github/pull_request_template.md`](../../.github/pull_request_template.md) |

> GitHub에서 **New issue** / **New pull request** 를 누르면 위 템플릿이 본문에 자동으로 채워집니다. (`main` 브랜치에 `.github` 폴더가 push되어 있어야 합니다.)

---

## 1. 브랜치 전략 (Simplified Git-Flow)

**Main → Develop → Feature** 중심의 브랜치 전략을 사용하며, 모든 브랜치는 **이슈 단위**로 생성하고 기능 단위 작업이 완료되면 삭제합니다.

| 브랜치명 | 설명 | 비고 |
| --- | --- | --- |
| `main` | 제품 출시 및 배포용 브랜치 | 상시 배포 가능 상태 및 안정성 최우선 유지 |
| `develop` | 다음 출시 버전을 위한 통합 개발 브랜치 | PR 합병의 대상 |
| `feat/#이슈번호` | 개별 이슈 해결 및 기능 구현용 브랜치 | 기능을 최대한 작게 쪼개어 작업 후 삭제 |
| `fix/#이슈번호` | 버그 수정용 브랜치 | 작업 완료 후 삭제 |
| `refactor/#이슈번호` | 코드 리팩토링 및 최적화용 브랜치 | 작업 완료 후 삭제 |

### 브랜치 생성 예시

```bash
git checkout develop
git pull origin develop
git checkout -b feat/23
```

---

## 2. 코드 및 네이밍 컨벤션

- **들여쓰기 & 포맷:** IDE 기본 설정을 따르며, `if`, `for` 문 등은 **한 줄이라도 반드시 중괄호(`{}`)를 사용**합니다.
- **클래스명:** `PascalCase` (예: `UserRepository`)
- **변수 / 함수명:** `camelCase` (예: `issueCoupon()`)
- **경로(URI):** `lowercase` 및 케밥 케이스 (예: `/api/v1/user-profiles`)
- **DB 테이블/컬럼:** `snake_case` (예: `order_id`, `created_at`)
- **DTO 명명:** 행위와 목적을 명확히 접미사로 표현 (예: `PostCreateRequest`, `PostCreateResponse`)
- **주석:** 꼭 필요한 경우에만 들여쓰기에 맞춰 작성하며, 가급적 명확한 코드로 의도를 드러냅니다.
- **디자인 패턴:** 객체 생성 시 안전성과 가독성을 위해 **`Builder` 패턴 사용을 적극 권장**합니다.
- **API 명세:** 테스트 코드 기반의 신뢰성을 위해 **Spring RestDocs**를 사용합니다.

---

## 3. 이슈(Issue) 작성 규칙

모든 작업은 **GitHub 이슈 생성**을 통해 시작합니다. 이슈는 작업의 최소 단위이자 기술적 대화의 출발점입니다.

### 작성 방법

1. GitHub 저장소 → **Issues** → **New issue**
2. **「작업 이슈」** 템플릿 선택 (빈 이슈는 비활성화되어 있음)
3. 요약 · Context · Tasks · DoD · 목표일 · Related 를 작성

### 템플릿 구성 (자동 채움 항목)

| 섹션 | 내용 |
| --- | --- |
| 🎯 이슈 요약 | 한 줄 목적 |
| Context | 왜 필요한가 (비즈니스·기술적 배경) |
| Tasks | 할 일 체크리스트 |
| Definition of Done | 완료 조건 |
| 😇 개발 완료 목표일 | `yyyy-mm-dd` |
| Related | 연관 이슈·문서 링크 |

템플릿 원본 수정: [`.github/ISSUE_TEMPLATE/task.md`](../../.github/ISSUE_TEMPLATE/task.md)

---

## 4. 커밋 메시지 컨벤션 (Conventional Commits)

커밋 메시지만 보고도 변경 사항의 성격과 영향을 즉시 파악할 수 있도록 **접두사(Prefix)를 필수**로 사용합니다.

- **형식:** `type: description (#IssueNumber)` (간단한 커밋은 이슈 번호 생략 가능)
- **예시:** `feat: Redisson 분산 락을 이용한 재고 차감 로직 구현 (#23)`

| Type | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩토링 (결과는 같으나 내부 구조·가독성 개선) |
| `perf` | 성능 개선 (인덱스 최적화, Redis 캐시 등) |
| `docs` | 문서 수정 (README, RestDocs, 주석) |
| `test` | 테스트 코드 추가·수정·삭제 |
| `style` | 포맷팅, 세미콜론 누락 등 (비즈니스 로직 변경 없음) |
| `chore` | 빌드, 패키지 설정, `.gitignore` 등 |
| `rename` | 파일·폴더명 변경 |
| `remove` | 파일·폴더 삭제 |

---

## 5. PR(Pull Request) 및 코드 리뷰 규칙

PR은 기술적 의사결정을 팀에 공유하고 승인을 받는 **Release Gate**입니다.

### PR 생성 방법

1. `develop` ← `feat/#이슈번호` (또는 `fix/`, `refactor/`) 로 PR 생성
2. GitHub가 [PR 템플릿](../../.github/pull_request_template.md)을 본문에 자동 삽입
3. 작업 내용 · 기술적 의사결정 · 변경 파일 · 연관 이슈 · 셀프 체크리스트를 작성

### 템플릿 구성 (자동 채움 항목)

| 섹션 | 내용 |
| --- | --- |
| 📝 작업 내용 | 구현 기능 체크리스트 |
| 🧪 기술적 의사결정 및 검증 | 기술 선택 배경, k6 등 검증, 트러블슈팅 (STAR) |
| 📌 주요 변경사항 | 추가·수정·삭제 파일 |
| 🔗 연관 이슈 | `Closes #번호`, `Related to #번호` |
| ✅ 셀프 체크리스트 | 테스트, N+1, RestDocs, 인프라, 시크릿 제외 |

템플릿 원본 수정: [`.github/pull_request_template.md`](../../.github/pull_request_template.md)

### 바람직한 PR 주기 및 규모 (Small PR 원칙)

리뷰어 피로도를 줄이고 꼼꼼한 리뷰를 받기 위해 아래 규모를 지향합니다.

| 항목 | 권장 |
| --- | --- |
| **생성 타이밍** | 기능 단위 완료 시, 하루 작업 마무리 시, 리뷰가 필요할 때 |
| **변경 파일 수** | 5~10개 이하 |
| **코드 라인** | 200~500줄 이하 |
| **리뷰 소요** | 30분 이내 |

### 머지 규칙

- 코드 리뷰 후 **30분~1시간 내** 머지 권장
- 리뷰가 없을 경우, **작성자가 직접 머지** 가능

---

## 6. 코드 리뷰 가이드

- 리뷰어 **1명의 승인**을 원칙으로 합니다.
- 건설적이고 **구체적인** 피드백을 지향합니다.
- 단순 오타·가독성을 넘어 **정합성 손실 가능성, 동시성 이슈, 쿼리 성능 병목**을 날카롭게 지적합니다.
- 가능하면 **대안 및 학습 자료**도 함께 제시합니다.

### 피드백 예시

| ❌ 비권장 | ✅ 권장 |
| --- | --- |
| "이상함" | "NPE가 발생할 수 있으니 `Optional` 사용을 고려해보세요" |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 설계 문서 인덱스 | [`../README.md`](../README.md) |
| ERD | [`../erd/erd-design.md`](../erd/erd-design.md) |
| 결제·주문 시퀀스 | [`../sequence/payment-flow-reason.md`](../sequence/payment-flow-reason.md) |
