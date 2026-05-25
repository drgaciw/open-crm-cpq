#!/usr/bin/env python3
"""AI test gap analysis — suggests unit/integration tests from diff and existing test layout."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

from ai_client import chat_completion, is_ai_enabled, skip_report, write_report

SYSTEM_PROMPT = """You are a test architect for OpenCRM CPQ.
Backend: JUnit 5 + Spring Boot Test; integration tests use Testcontainers (@Tag integration).
Frontend: Vitest via Nx (crm-deals, crm-leads, shared-models, etc.).
Suggest concrete test cases with file paths and example test method names.
Output markdown: Summary, Backend Tests, Frontend Tests, Integration Tests, Priority Order."""


def collect_context(workspace: Path, base_ref: str) -> str:
    diff = subprocess.run(
        ["git", "diff", f"{base_ref}...HEAD", "--", "backend/", "frontend/"],
        capture_output=True,
        text=True,
        cwd=workspace,
    )
    diff_text = (diff.stdout or "")[:80_000]

    test_files: list[str] = []
    for pattern in (
        "backend/**/src/test/java/**/*Test.java",
        "frontend/**/*.spec.ts",
    ):
        test_files.extend(str(p.relative_to(workspace)) for p in workspace.glob(pattern))
    test_files = sorted(test_files)[:200]

    return f"""## Git diff (truncated)
```diff
{diff_text or '(no diff)'}
```

## Existing test files (sample)
{chr(10).join(f'- {f}' for f in test_files[:80])}
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="AI test suggestions for Jenkins CI")
    parser.add_argument("--base-ref", default=os.environ.get("GIT_BASE_REF", "origin/main"))
    parser.add_argument("--workspace", default=os.environ.get("WORKSPACE", "."))
    parser.add_argument("--output", default="ci-reports/ai-test-suggestions.md")
    args = parser.parse_args()

    if not is_ai_enabled():
        return skip_report(args.output, "AI_CI_ENABLED is false or credentials missing")

    workspace = Path(args.workspace).resolve()
    context = collect_context(workspace, args.base_ref)

    try:
        suggestions = chat_completion(
            SYSTEM_PROMPT,
            f"Analyze this change and suggest tests to add or extend:\n\n{context}",
        )
    except RuntimeError as exc:
        write_report(args.output, f"# AI Test Suggestions — Error\n\n{exc}\n")
        return 1

    write_report(args.output, f"# AI Test Suggestions\n\n{suggestions}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
