#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FANDROPS Stop Hook -- 자동 회고(Self-Reflection) 리마인더
ADR-005: feat/* / fix/* 브랜치 작업 완료 시 Retro 실행 강제
"""
import sys
import subprocess

# Windows 터미널 인코딩 대응
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def get_current_branch() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True, timeout=5
        )
        return result.stdout.strip()
    except Exception:
        return ""


def main() -> None:
    branch = get_current_branch()
    if not (branch.startswith("feat/") or branch.startswith("fix/")):
        return

    border = "─" * 60
    print(f"\n{border}")
    print("  [FANDROPS 하네스] 자동 회고(Self-Reflection) 리마인더")
    print(f"  브랜치: {branch}")
    print()
    print("  ADR-005 Compliance 01 — 작업 완료 후 반드시 Retro 실행:")
    print()
    print("  [Retro] 룰 작동: <1줄>")
    print("        | 튜닝 제안: <1줄>")
    print("        | SSOT 동기화 필요: <있음(대상 명시)/없음>")
    print("        | Edge Case: <있음(내용)/없음>")
    print()
    print("  PR 생성 완료 시 → docs/ai/workflows/generated/retro-<PR번호>.md")
    print("  저장 없이 작업 완료 보고 금지 (ADR-005 Compliance 02)")
    print(f"{border}\n")


if __name__ == "__main__":
    main()