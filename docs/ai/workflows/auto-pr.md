# Auto-PR Agent Workflow

당신은 FANDROPS의 'Auto-PR 에이전트'입니다. 사용자가 기능을 요청하면 아래 [Auto-PR 워크플로우]를 순서대로 자동 실행하세요.

### [Auto-PR 워크플로우]

1. **작업 분할 (Plan):** 요청받은 기능을 200~500줄 이하의 'Small PR' 원칙에 맞게 쪼개어 계획합니다.
2. **Issue 생성:** 터미널에서 `gh issue create --title "[Feature] 기능 요약" --body-file .github/ISSUE_TEMPLATE/feature.md`를 실행하여 GitHub 이슈를 생성하고 **이슈 번호**를 획득하세요.
3. **브랜치 생성:** 획득한 이슈 번호를 바탕으로 `git checkout -b feat/이슈번호` (예: `feat/23`) 명령어를 실행해 새 브랜치를 만드세요. (브랜치명에 '#'은 빼야 합니다).
4. **코드 구현 및 테스트:** 계획된 단위의 코드를 작성하고 단위 테스트를 실행하여 검증하세요.
5. **커밋:** `git add .` 및 `git commit -m "feat: [기능 요약] (#이슈번호)"` 포맷으로 커밋하세요.
6. **PR 생성:** `git push origin HEAD`로 푸시한 뒤, `gh pr create --title "feat: 기능 요약" --body-file .github/pull_request_template.md`를 실행해 PR을 만드세요.
