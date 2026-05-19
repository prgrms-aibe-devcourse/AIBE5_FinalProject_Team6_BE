#!/usr/bin/env python3
"""
FANDROPS PostToolUse Hook — 동시성 파일 수정 감지

Claude Code가 Edit/Write/MultiEdit 도구를 실행한 직후 호출된다.
이벤트 JSON을 stdin으로 받아 수정된 파일 경로를 확인하고,
modules/order/ 또는 modules/inventory/ 하위일 경우 체크리스트를 출력한다.

훅 출력(stdout)은 Claude의 다음 응답 컨텍스트로 주입된다.
"""
import sys
import json
import re


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception:
        return

    file_path: str = data.get("tool_input", {}).get("file_path", "")
    normalized = file_path.replace("\\", "/")

    if not re.search(r"modules/(order|inventory)/", normalized):
        return

    border = "─" * 54
    print(f"\n{border}")
    print("  [FANDROPS] 동시성 파일 수정 감지")
    print(f"  파일: {file_path}")
    print()
    print("  체크리스트 (personas/hyungseongbin.md §동시성 수정 후 검증 절차)")
    print("  1. ./gradlew :modules:inventory:inventory-application:test")
    print("  2. reserved_quantity > stock_quantity = 0건 확인")
    print("  3. k6 run infra/k6/hotdeal.js  (파일 존재 시)")
    print(f"{border}\n")


if __name__ == "__main__":
    main()