#!/usr/bin/env python3
"""Advisory CI contract drift report for Jenkins (compare contract vs Jenkinsfile/GHA)."""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

from ai_client import write_report


def find_repo_root(start: Path) -> Path:
    for parent in [start, *start.parents]:
        if (parent / "docs" / "ci-contract.yaml").is_file():
            return parent
    raise RuntimeError("Could not find docs/ci-contract.yaml")


def parse_contract_needs(text: str) -> list[str]:
    in_block = False
    needs: list[str] = []
    for line in text.splitlines():
        if re.match(r"^\s+backend-integration:\s*$", line):
            in_block = True
            continue
        if in_block:
            m = re.match(r"^\s+-\s+(\S+)\s*$", line)
            if m:
                needs.append(m.group(1))
                continue
            if line.strip() and not line.startswith(" " * 4 + "-"):
                break
    return needs


def parse_gha_needs(gha: str) -> list[str]:
    m = re.search(
        r"backend-integration:.*?needs:\s*\[([^\]]+)\]",
        gha,
        re.DOTALL,
    )
    if not m:
        return []
    return [x.strip().strip("'\"") for x in m.group(1).split(",") if x.strip()]


def check_drift(repo: Path) -> tuple[list[str], list[str]]:
    contract = (repo / "docs" / "ci-contract.yaml").read_text(encoding="utf-8")
    jenkins = (repo / "Jenkinsfile").read_text(encoding="utf-8")
    gha = (repo / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")

    errors: list[str] = []
    warnings: list[str] = []

    expected = sorted(parse_contract_needs(contract))
    actual = sorted(parse_gha_needs(gha))
    if expected and actual != expected:
        errors.append(f"GHA backend-integration needs {actual} != contract {expected}")

    stages = [
        "toolchain",
        "shared-libraries",
        "backend-unit",
        "backend-integration",
        "cpq-beta-python",
        "frontend",
        "security-audit",
    ]
    for s in stages:
        if f"run-core.sh {s}" not in jenkins:
            errors.append(f"Jenkinsfile missing run-core.sh {s}")
        if s != "toolchain" and f"run-core.sh {s}" not in gha:
            errors.append(f"GHA missing run-core.sh {s}")

    if "ai-assist" in gha or "run-ai-stage" in gha:
        errors.append("GHA must not reference ai-assist (Jenkins-only)")

    return errors, warnings


def main() -> int:
    parser = argparse.ArgumentParser(description="CI contract drift sentinel (Jenkins)")
    parser.add_argument(
        "--workspace",
        default=os.environ.get("WORKSPACE", "."),
        help="Repository workspace root",
    )
    parser.add_argument(
        "--output",
        default="ci-reports/ai-drift-check.md",
        help="Report path relative to workspace",
    )
    args = parser.parse_args()

    workspace = Path(args.workspace).resolve()
    output = workspace / args.output

    try:
        repo = find_repo_root(workspace)
    except RuntimeError as exc:
        write_report(str(output), f"# CI Drift Check — Error\n\n{exc}\n")
        return 1

    errors, warnings = check_drift(repo)
    status = "PASS" if not errors else "FAIL"

    lines = [
        "---",
        f"status: {status}",
        f"ssot_script: scripts/ci/run-core.sh",
        f"contract: docs/ci-contract.yaml",
        "---",
        "",
        f"# CI Drift Check — {status}",
        "",
    ]
    if errors:
        lines.append("## Errors\n")
        for e in errors:
            lines.append(f"- {e}\n")
    else:
        lines.append("No drift detected against contract.\n")
    if warnings:
        lines.append("\n## Warnings\n")
        for w in warnings:
            lines.append(f"- {w}\n")
    lines.append(
        "\nVerify locally: `python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py`\n"
    )

    write_report(str(output), "".join(lines))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
