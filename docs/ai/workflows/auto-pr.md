# Auto-PR Agent Workflow

당신은 FANDROPS의 **Auto-PR 에이전트**입니다. 사용자가 기능을 요청하면 아래 워크플로를 **순서대로** 실행합니다.

> **금지:** `.github/ISSUE_TEMPLATE/feature.md` · `pull_request_template.md` 를 **채우지 않고** `gh --body-file`로 올리지 마세요.  
> (YAML frontmatter 포함·빈 칸 그대로 업로드됨 → GitHub UI 템플릿과 다른 본문이 됨)

**반드시 먼저 읽기:** `@docs/ai/SHARED.md` · `@docs/contributing/git-collaboration-convention.md` · 템플릿 원본:
- 이슈: `.github/ISSUE_TEMPLATE/feature.md` (또는 Bug → `bug.md`)
- PR: `.github/pull_request_template.md`
- 본문 골격(frontmatter 없음): `docs/ai/workflows/templates/issue-feature-body.md` · `pr-body.md`

---

### [Auto-PR 워크플로우]

1. **Plan:** Small PR(200~500줄) 단위로 쪼갠다. SSOT(`mvp-api-spec`, `invariants`, `erd-design`) 해당 섹션만 확인.

2. **이슈 본문 작성:** `docs/ai/workflows/templates/issue-feature-body.md` **섹션 구조**에 맞춰 내용을 채운 뒤  
   `docs/ai/workflows/generated/issue-<번호>-body.md` 에 저장한다.  
   - 섹션: 📝 작업 내용 · Definition of Done · 📅 마감 기한 · Related  
   - 제목: Feature 템플릿 규칙 → `Feat/be: <한 줄 요약>` ([git-collaboration-convention §3](../contributing/git-collaboration-convention.md))

3. **Issue 생성:**

   ```bash
   gh issue create --title "Feat/be: <요약>" --label feature --body-file docs/ai/workflows/generated/issue-<N>-body.md
   ```

   - `--body-file` 인자는 **채운 generated 파일**만 사용. `ISSUE_TEMPLATE/feature.md` 직접 사용 금지.
   - 생성된 **이슈 번호** 기록.

4. **브랜치:** `git checkout develop && git pull && git checkout -b feat/<이슈번호>` (`#` 없음)

5. **구현·테스트:** persona + SHARED. 담당 모듈만 수정.

6. **커밋:** `feat: <요약> (#<이슈번호>)`

7. **PR 본문 작성:** `docs/ai/workflows/templates/pr-body.md` 구조에 맞춰  
   `docs/ai/workflows/generated/pr-<PR예정>-body.md` 저장.  
   - 섹션: 📝 작업 내용(체크리스트) · 🧪 기술적 의사결정 · 📌 주요 변경사항 · 🔗 연관 이슈(`Closes #n`) · ✅ 셀프 체크리스트  
   - `Summary` / `Test plan` / `Generated with Claude Code` 등 **임의 섹션 추가 금지** (팀 PR 템플릿과 불일치)

8. **PR 생성:**

   ```bash
   git push -u origin HEAD
   gh pr create --base develop --title "feat: <요약>" --body-file docs/ai/workflows/generated/pr-<N>-body.md
   ```

   - `--body-file` 인자는 **채운 generated 파일**만. `pull_request_template.md` 직접 사용 금지.
   - GitHub 웹 UI에서 PR 열면 템플릿이 자동 삽입되므로, CLI 사용 시에도 **동일 섹션 제목(이모지 포함)** 을 유지한다.

9. **하네스 회고 및 보고:**

   PR 생성이 성공적으로 완료되면 아래 형식으로 `[AI 하네스 회고 리포트]`를 작성한다.  
   이번 PR 작업 중 겪었던 컨텍스트 부족, 룰 충돌, 개선이 필요한 페르소나 지침, 미명시 Edge Case가 있다면 구체적으로 명시하여 사용자가 프롬프트를 튜닝할 수 있도록 피드백을 제공한다.

   ```
   [AI 하네스 회고 리포트]
   - 룰 작동 분석: <SHARED·페르소나 지침이 명확하게 동작했는지 1~2줄>
   - 프롬프트 튜닝 제안: <비효율·모호·충돌이 있었던 지시와 개선안 1줄>
   - SSOT 동기화 필요: <있음 — 대상 문서 명시 / 없음>
   - 미명시 Edge Case: <발견된 예외 상황 / 없음>
   ```

   리포트를 `docs/ai/workflows/generated/retro-<PR번호>.md` 에 저장하여 다음 세션에서 참조할 수 있도록 한다.

---

### 체크 (리뷰 전)

| 항목 | 확인 |
| --- | --- |
| 이슈 라벨 | `feature` (Bug는 `bug`) |
| 브랜치 | `feat/<번호>` / `fix/<번호>` |
| PR base | `develop` |
| 본문 형식 | 템플릿 4·5섹션(이슈) / 5섹션(PR) |
| API 경로 | `mvp-api-spec` Base `/api/v1` 와 일치 |
