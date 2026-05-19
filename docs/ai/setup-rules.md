# Claude Code 설정 가이드 (FANDROPS)

> 이 문서는 Claude Code의 **실제 설정 구조**를 설명한다.  
> 인터넷에 유통되는 잘못된 정보(존재하지 않는 훅 이벤트, 허구의 버전 기능)를 바로잡는 것이 목적이다.

---

## 실제 훅 이벤트 (4개)

Claude Code가 지원하는 훅 이벤트는 다음 **4가지**뿐이다.

| 이벤트 | 실행 시점 | 주요 용도 |
| --- | --- | --- |
| `PreToolUse` | 도구 실행 **직전** | 금지 조건 검사, 비정상 작업 차단 |
| `PostToolUse` | 도구 실행 **직후** | 파일 수정 후 테스트 알림, 검증 |
| `Notification` | Claude가 알림을 전송할 때 | 외부 알림 시스템 연동 |
| `Stop` | Claude 응답 완료 시 | 세션 종료 후 처리 |

**존재하지 않는 이벤트 (절대 작동하지 않음):**
`SessionStart`, `CwdChanged`, `FileChanged`, `AgentRoute` 등 — 소셜 미디어나 AI 생성 블로그에서 언급되지만 실제 구현되지 않은 이벤트들이다.

---

## 훅이 실제로 할 수 있는 것과 없는 것

| 가능 | 불가능 |
| --- | --- |
| 셸 명령 실행 (`bash`, `python3`, `pwsh`) | 파일을 Claude 컨텍스트에 자동 주입 |
| stdout 출력 → Claude 다음 응답 컨텍스트로 전달 | `@docs/ai/SHARED.md` 자동 로드 |
| PreToolUse에서 비정상 작업 차단 (exit 1) | 세션 시작 시 초기화 작업 |
| 파일 수정 후 테스트 자동 실행 | 도메인 감지 후 페르소나 자동 교체 |

**SHARED.md 자동 로드가 불가능한 이유:** 훅은 셸 명령을 실행할 뿐이며, Claude의 컨텍스트 창에 파일을 주입하는 API가 없다. CLAUDE.md만이 세션 시작 시 자동 로드된다. 핵심 규칙은 CLAUDE.md에 직접 포함하거나, 매 세션 `@docs/ai/SHARED.md`로 수동 로드해야 한다.

---

## 설정 파일 위치

| 범위 | 경로 | 적용 대상 |
| --- | --- | --- |
| 프로젝트 | `.claude/settings.json` | 이 레포만 |
| 글로벌 (모든 프로젝트) | `~/.claude/settings.json` | 모든 Claude Code 세션 |
| 프로젝트 컨텍스트 | `CLAUDE.md` (루트) | 세션 시작 시 자동 로드 |
| 글로벌 컨텍스트 | `~/.claude/CLAUDE.md` | 모든 세션 자동 로드 |

> **`~/.claude/rules/` 디렉터리는 존재하지 않는다.** 글로벌 규칙은 `~/.claude/CLAUDE.md`에 작성한다.

---

## 훅 설정 형식 (`.claude/settings.json`)

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "정규식 — 도구 이름 매칭",
        "hooks": [
          {
            "type": "command",
            "command": "실행할 셸 명령"
          }
        ]
      }
    ],
    "PreToolUse": [...],
    "Notification": [...],
    "Stop": [...]
  }
}
```

- `matcher`: 도구 이름에 대한 정규식. `"Edit|Write|MultiEdit"` 형식으로 OR 조건 가능.
- 훅 명령은 이벤트 JSON을 **stdin**으로 받는다.
- `PostToolUse` stdin JSON 구조:
  ```json
  {
    "tool_name": "Edit",
    "tool_input": { "file_path": "...", ... },
    "tool_response": "..."
  }
  ```
- `PreToolUse`에서 `exit 1`로 종료하면 해당 도구 실행이 **차단**된다.

---

## FANDROPS 현재 훅 구성

### PostToolUse — 동시성 파일 수정 감지

파일: `.claude/settings.json` + `.claude/hooks/concurrency-guard.py`

**동작:**
1. `Edit`, `Write`, `MultiEdit` 도구 실행 직후 트리거
2. 수정된 `file_path`가 `modules/order/` 또는 `modules/inventory/` 하위인지 확인
3. 해당 시 형성빈 체크리스트 출력 (테스트 명령·오버셀 확인·k6 안내)

**Windows 주의:** `.claude/settings.json`의 `python3`가 없는 경우 `python`으로 변경하거나 Python을 PATH에 추가한다.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write|MultiEdit",
        "hooks": [{ "type": "command", "command": "python3 .claude/hooks/concurrency-guard.py" }]
      }
    ]
  }
}
```

---

## 글로벌 규칙 설정 방법 (`~/.claude/CLAUDE.md`)

모든 프로젝트에 공통으로 적용할 규칙은 `~/.claude/CLAUDE.md`에 작성한다.

예시:
```markdown
# 글로벌 규칙

- 커밋·push는 사용자 요청 없이 하지 않는다.
- `--no-verify` 플래그를 사용하지 않는다.
- PII(이메일·토큰)를 로그에 출력하지 않는다.
```

이 파일은 모든 Claude Code 세션에서 자동으로 로드된다.

---

## 세션 시작 체크리스트 (수동)

훅으로 자동화할 수 없는 컨텍스트 로드는 세션 시작 시 직접 입력한다.

```
@docs/ai/SHARED.md
@docs/ai/personas/<본인>.md
```

또는 프롬프트 첫 줄에: `SHARED 읽고 시작` (CLAUDE.md 지시에 따라 클로드가 읽는다).