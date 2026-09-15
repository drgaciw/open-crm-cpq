#!/usr/bin/env python3
"""Automated AI code review for Jenkins — analyzes git diff of changed files."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys

from ai_client import chat_completion, is_ai_enabled, sanitize_text, skip_report, write_report

SYSTEM_PROMPT = """You are a senior code reviewer for the OpenCRM CPQ monorepo.
Stack: Java 25 Spring Boot 4.0.6 microservices (backend/), Angular 22 Nx (frontend/), optional Frappe cpq_beta/.
Focus on: correctness, security (OWASP), concurrency, test gaps, API contract breaks, and maintainability.
Output markdown with sections: Summary, Critical, Warnings, Suggestions, Test Recommendations.
Be concise. Do not invent issues not supported by the diff."""


def git_diff(base_ref: str) -> str:
    for ref in (base_ref, "origin/main", "main", "HEAD~1"):
        if not ref:
            continue
        result = subprocess.run(
            ["git", "diff", f"{ref}...HEAD", "--", "backend/", "frontend/"],
            capture_output=True,
            text=True,
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout[:120_000]
    result = subprocess.run(
        ["git", "diff", "HEAD~1", "--", "backend/", "frontend/"],
        capture_output=True,
        text=True,
    )
    return (result.stdout or "")[:120_000]


def main() -> int:
    parser = argparse.ArgumentParser(description="AI code review for Jenkins CI")
    parser.add_argument("--base-ref", default=os.environ.get("GIT_BASE_REF", "origin/main"))
    parser.add_argument(
        "--output",
        default="ci-reports/ai-code-review.md",
        help="Report path relative to workspace",
    )
    args = parser.parse_args()

    if not is_ai_enabled():
        return skip_report(args.output, "AI_CI_ENABLED is false or credentials missing")

    diff = sanitize_text(git_diff(args.base_ref))
    if not diff.strip():
        write_report(args.output, "# AI Code Review\n\nNo backend/frontend changes detected in diff.\n")
        return 0

    user_prompt = f"""Review the following git diff for a pull request.
Flag only issues you can justify from the diff.

```diff
{diff}
```
"""
    try:
        review = chat_completion(SYSTEM_PROMPT, user_prompt)
    except RuntimeError as exc:
        write_report(args.output, f"# AI Code Review — Error\n\n{exc}\n")
        return 1

    write_report(args.output, f"# AI Code Review\n\n{review}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
