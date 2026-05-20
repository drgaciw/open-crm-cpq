#!/usr/bin/env python3
"""AI failure log triage — summarizes Jenkins console output on pipeline failure."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from ai_client import chat_completion, is_ai_enabled, sanitize_text, skip_report, write_report

SYSTEM_PROMPT = """You are a CI failure analyst for OpenCRM CPQ (Maven, Testcontainers, Nx/Vitest).
Given build logs, identify: root cause, failing stage, exact error lines, and fix steps.
Output markdown: Summary, Root Cause, Evidence (quoted log lines), Recommended Fixes, Prevention.
Do not guess — cite log evidence."""


def tail_log(path: Path, max_lines: int = 400) -> str:
    if not path.is_file():
        return f"(log file not found: {path})"
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return "\n".join(lines[-max_lines:])


def main() -> int:
    parser = argparse.ArgumentParser(description="AI log analysis for Jenkins CI failures")
    parser.add_argument("--log-file", default=os.environ.get("BUILD_LOG_FILE", ""))
    parser.add_argument("--stage", default=os.environ.get("STAGE_NAME", "unknown"))
    parser.add_argument("--output", default="ci-reports/ai-log-analysis.md")
    args = parser.parse_args()

    if not is_ai_enabled():
        return skip_report(args.output, "AI_CI_ENABLED is false or credentials missing")

    log_path = Path(args.log_file) if args.log_file else None
    if log_path and log_path.is_file():
        log_text = tail_log(log_path)
    else:
        # Read from stdin when Jenkins pipes console excerpt
        log_text = sys.stdin.read()[:100_000] if not sys.stdin.isatty() else "(no log input)"

    log_text = sanitize_text(log_text)

    user_prompt = f"""Pipeline stage `{args.stage}` failed. Analyze this log excerpt:

```
{log_text}
```
"""
    try:
        analysis = chat_completion(SYSTEM_PROMPT, user_prompt, max_tokens=2048)
    except RuntimeError as exc:
        write_report(args.output, f"# AI Log Analysis — Error\n\n{exc}\n")
        return 1

    report = (
        "---\n"
        f"stage: {args.stage}\n"
        "root_cause: see_summary_below\n"
        "ssot_script: scripts/ci/run-core.sh\n"
        "---\n\n"
        f"# AI Log Analysis — {args.stage}\n\n{analysis}\n"
    )
    write_report(args.output, report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
