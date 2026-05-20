#!/usr/bin/env python3
"""Advisory security-audit triage — summarizes npm/Maven audit output for Jenkins."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from ai_client import chat_completion, is_ai_enabled, sanitize_text, skip_report, write_report

SYSTEM_PROMPT = """You are a security analyst for OpenCRM CPQ CI.
Summarize dependency audit findings (npm audit, OWASP dependency-check) from build logs.
Output markdown: Summary, Critical/High findings, Recommended actions, False-positive notes.
Cite only evidence present in the input. Do not invent CVEs."""


def collect_audit_context(workspace: Path) -> str:
    parts: list[str] = []
    reports_dir = workspace / "backend" / "target"
    if reports_dir.is_dir():
        for path in sorted(reports_dir.rglob("dependency-check-report.*"))[:3]:
            try:
                text = path.read_text(encoding="utf-8", errors="replace")[:40_000]
                parts.append(f"## {path.relative_to(workspace)}\n```\n{text}\n```\n")
            except OSError:
                continue
    log_hint = workspace / "ci-reports" / "security-audit-console.txt"
    if log_hint.is_file():
        parts.append(
            f"## Console excerpt\n```\n{log_hint.read_text(encoding='utf-8', errors='replace')[:50_000]}\n```\n"
        )
    if not parts:
        return (
            "(No dependency-check report files found. "
            "Re-run security-audit stage or pipe console output to "
            "ci-reports/security-audit-console.txt)\n"
        )
    return "\n".join(parts)


def main() -> int:
    parser = argparse.ArgumentParser(description="AI security audit triage for Jenkins CI")
    parser.add_argument("--workspace", default=os.environ.get("WORKSPACE", "."))
    parser.add_argument("--output", default="ci-reports/ai-security-triage.md")
    args = parser.parse_args()

    workspace = Path(args.workspace).resolve()
    output = workspace / args.output

    if not is_ai_enabled():
        return skip_report(str(output), "AI_CI_ENABLED is false or credentials missing")

    context = sanitize_text(collect_audit_context(workspace))
    user_prompt = f"Summarize security audit results for this build:\n\n{context}"

    try:
        analysis = chat_completion(SYSTEM_PROMPT, user_prompt, max_tokens=2048)
    except RuntimeError as exc:
        write_report(str(output), f"# AI Security Triage — Error\n\n{exc}\n")
        return 1

    body = (
        "---\n"
        "stage: security-audit\n"
        "ssot_script: scripts/ci/security-audit.sh\n"
        "advisory: true\n"
        "---\n\n"
        f"# AI Security Triage\n\n{analysis}\n"
    )
    write_report(str(output), body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
