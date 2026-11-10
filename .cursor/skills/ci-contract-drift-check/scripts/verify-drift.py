#!/usr/bin/env python3
"""Verify GHA/Jenkins CI parity against docs/ci-contract.yaml."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def find_repo_root(start: Path) -> Path:
    for parent in [start, *start.parents]:
        if (parent / "docs" / "ci-contract.yaml").is_file():
            return parent
    raise SystemExit("Could not find docs/ci-contract.yaml from cwd")


def load_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_contract_needs(contract_text: str) -> list[str]:
    """Extract backend-integration needs from github_actions.needs_graph."""
    in_graph = False
    in_integration = False
    needs: list[str] = []
    for line in contract_text.splitlines():
        if re.match(r"^\s+needs_graph:\s*$", line):
            in_graph = True
            continue
        if not in_graph:
            continue
        if re.match(r"^\s+backend-integration:\s*$", line):
            in_integration = True
            continue
        if in_integration:
            m = re.match(r"^\s+-\s+(\S+)\s*$", line)
            if m:
                needs.append(m.group(1))
                continue
            if line.strip() and not re.match(r"^\s+-\s+", line):
                break
    return needs


def parse_gha_integration_needs(gha_text: str) -> list[str]:
    m = re.search(
        r"backend-integration:.*?needs:\s*\[([^\]]+)\]",
        gha_text,
        re.DOTALL,
    )
    if not m:
        return []
    return [x.strip().strip("'\"") for x in m.group(1).split(",") if x.strip()]


def jenkins_has_run_core(jenkins_text: str, arg: str) -> bool:
    return f"run-core.sh {arg}" in jenkins_text


def gha_has_run_core(gha_text: str, arg: str) -> bool:
    return f"run-core.sh {arg}" in gha_text


def gha_has_ai_assist(gha_text: str) -> bool:
    return "ai-assist" in gha_text or "run-ai-stage" in gha_text


def pr_gate_errors(repo: Path, contract: str) -> list[str]:
    """Validate the pull-request gate workflow declared under github_pr_gate in the contract."""
    m = re.search(r"^github_pr_gate:\s*\n\s+workflow:\s*(\S+)", contract, re.MULTILINE)
    if not m:
        return []
    rel = m.group(1)
    path = repo / rel
    if not path.is_file():
        return [f"github_pr_gate workflow {rel} is missing"]
    text = load_text(path)
    errors: list[str] = []
    if "pull_request" not in text:
        errors.append(f"{rel} must trigger on pull_request")
    if re.search(r"^\s+push:", text, re.MULTILINE):
        errors.append(f"{rel} must not trigger on push")
    if not gha_has_run_core(text, "backend-unit"):
        errors.append(f"{rel} missing run-core.sh backend-unit")
    if "verify-drift.py" not in text:
        errors.append(f"{rel} must run verify-drift.py")
    if gha_has_ai_assist(text):
        errors.append(f"{rel} must not reference ai-assist (Jenkins-only per ADR-003)")
    return errors


def main_gate_errors(repo: Path, contract: str) -> list[str]:
    """Validate the post-merge gate declared under github_main_gate (ADR-003 section 8)."""
    m = re.search(r"^github_main_gate:\s*\n\s+workflow:\s*(\S+)", contract, re.MULTILINE)
    if not m:
        return []
    rel = m.group(1)
    path = repo / rel
    if not path.is_file():
        return [f"github_main_gate workflow {rel} is missing"]
    text = load_text(path)
    errors: list[str] = []
    if not re.search(r"^\s+push:\s*\n\s+branches:\s*\[\s*main\s*\]", text, re.MULTILINE):
        errors.append(f"{rel} must trigger on push to main")
    if re.search(r"^\s+pull_request:", text, re.MULTILINE):
        errors.append(f"{rel} must not trigger on pull_request (pr-check.yml owns that)")
    if not gha_has_run_core(text, "backend-unit"):
        errors.append(f"{rel} missing run-core.sh backend-unit")
    if "verify-drift.py" not in text:
        errors.append(f"{rel} must run verify-drift.py")
    if gha_has_ai_assist(text):
        errors.append(f"{rel} must not reference ai-assist (Jenkins-only per ADR-003)")
    if "report_script:" in contract:
        for wf in (rel, ".github/workflows/ci.yml"):
            if "report-main-status.sh" not in load_text(repo / wf):
                errors.append(f"{wf} must run scripts/ci/report-main-status.sh (main-red issue)")
    return errors


def mapping_body(text: str, key: str, indent: int = 0) -> str:
    """Read a block mapping in the repository's indented YAML format, without dependencies."""
    lines = text.splitlines()
    pattern = rf"^{' ' * indent}['\"]?{re.escape(key)}['\"]?:\s*(?:#.*)?$"
    for index, line in enumerate(lines):
        if not re.match(pattern, line):
            continue
        body: list[str] = []
        for child in lines[index + 1:]:
            if child.strip() and not child.lstrip().startswith("#"):
                if len(child) - len(child.lstrip(" ")) <= indent:
                    break
            body.append(child)
        return "\n".join(body)
    return ""


def nightly_schedule_errors(contract: str, gha: str) -> list[str]:
    """Require the declared nightly cron on the workflow's actual on.schedule trigger."""
    actions = mapping_body(contract, "github_actions")
    trigger = mapping_body(actions, "trigger", 2)
    declared = re.search(r"^    schedule_cron:\s*([^\n]+)", trigger, re.MULTILINE)
    if not declared:
        return []

    def cron_value(value: str) -> str:
        return value.split("#", 1)[0].strip().strip("'\"")

    expected = cron_value(declared.group(1))
    schedule = mapping_body(mapping_body(gha, "on"), "schedule", 2)
    actual = [cron_value(value) for value in re.findall(
        r"^    -\s+cron:\s*([^\n]+)", schedule, re.MULTILINE
    )]
    if not expected or actual != [expected]:
        return [f"GHA ci.yml schedule cron {actual} != contract {[expected]}"]
    return []


def gha_has_auto_triggers(gha_text: str) -> bool:
    """True if workflow runs on push or pull_request (disallowed for Option B)."""
    in_on = False
    for line in gha_text.splitlines():
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        if stripped == "on:":
            in_on = True
            continue
        if in_on:
            if stripped and not line.startswith(" ") and not line.startswith("\t"):
                if stripped.endswith(":") and stripped != "on:":
                    break
            if re.match(r"^\s+(push|pull_request):", line):
                return True
    return False


def jenkins_publishes_github_status(jenkins_text: str) -> bool:
    return "jenkins/opencrm-cpq-ci" in jenkins_text and "publishGitHubStatus" in jenkins_text


def jenkins_stage_names(jenkins_text: str) -> set[str]:
    names: set[str] = set()
    for m in re.finditer(r"stage\('([^']+)'\)", jenkins_text):
        names.add(m.group(1))
    return names


def main() -> int:
    repo = find_repo_root(Path.cwd())
    contract_path = repo / "docs" / "ci-contract.yaml"
    jenkins_path = repo / "Jenkinsfile"
    gha_path = repo / ".github" / "workflows" / "ci.yml"

    contract = load_text(contract_path)
    jenkins = load_text(jenkins_path)
    gha = load_text(gha_path)

    errors: list[str] = []
    warnings: list[str] = []

    expected_needs = sorted(parse_contract_needs(contract))
    actual_needs = sorted(parse_gha_integration_needs(gha))
    if expected_needs and actual_needs != expected_needs:
        errors.append(
            f"GHA backend-integration needs {actual_needs} != contract {expected_needs}"
        )

    run_core_stages = [
        "toolchain",
        "shared-libraries",
        "backend-unit",
        "backend-integration",
        "cpq-beta-python",
        "frontend",
        "security-audit",
    ]
    for stage in run_core_stages:
        if not jenkins_has_run_core(jenkins, stage):
            errors.append(f"Jenkinsfile missing run-core.sh {stage}")
        if stage != "toolchain" and not gha_has_run_core(gha, stage):
            if stage == "toolchain":
                continue
            errors.append(f"GHA ci.yml missing run-core.sh {stage}")

    if gha_has_auto_triggers(gha):
        errors.append(
            "GHA workflow must not auto-run on push/pull_request (Jenkins primary; use workflow_dispatch only)"
        )

    if "workflow_dispatch" not in gha:
        warnings.append("GHA manual fallback should include workflow_dispatch trigger")

    if not jenkins_publishes_github_status(jenkins):
        errors.append("Jenkinsfile must publish GitHub status context jenkins/opencrm-cpq-ci")

    if gha_has_ai_assist(gha):
        errors.append("GHA workflow must not reference ai-assist (Jenkins-only per ADR-003)")

    errors.extend(pr_gate_errors(repo, contract))
    errors.extend(main_gate_errors(repo, contract))
    errors.extend(nightly_schedule_errors(contract, gha))

    if "run-core.sh" not in jenkins:
        errors.append("Jenkinsfile must invoke scripts/ci/run-core.sh")
    if "run-core.sh" not in gha:
        errors.append("GHA ci.yml must invoke scripts/ci/run-core.sh")

    required_jenkins_stages = {
        "Toolchain",
        "Shared Libraries",
        "Backend Unit",
        "CPQ Beta Python",
        "Frontend",
        "Backend Integration",
        "Security Audit",
    }
    found = jenkins_stage_names(jenkins)
    missing = required_jenkins_stages - found
    if missing:
        errors.append(f"Jenkinsfile missing stages: {sorted(missing)}")

    # Integration must not depend on frontend/cpq in Jenkins (sequential stages)
    jenkins_lines = jenkins.splitlines()
    in_integration = False
    for line in jenkins_lines:
        if "stage('Backend Integration')" in line:
            in_integration = True
        if in_integration and "stage('Parallel" in line:
            break
        if in_integration and re.search(r"stage\('(Frontend|CPQ Beta)", line):
            errors.append("Backend Integration must not be nested after parallel frontend/cpq stages")

    if errors:
        print("CI contract drift check FAILED\n", file=sys.stderr)
        for e in errors:
            print(f"  ERROR: {e}", file=sys.stderr)
        for w in warnings:
            print(f"  WARN: {w}", file=sys.stderr)
        return 1

    print("CI contract drift check OK")
    print(f"  contract: {contract_path.relative_to(repo)}")
    print(f"  backend-integration needs: {actual_needs or expected_needs}")
    for w in warnings:
        print(f"  WARN: {w}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
