import importlib.util
from pathlib import Path
import pytest

# Load verify-drift.py dynamically due to hyphen in module name
SCRIPT_PATH = Path(__file__).parent / "verify-drift.py"
spec = importlib.util.spec_from_file_location("verify_drift", SCRIPT_PATH)
verify_drift = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify_drift)

toolchain_node_errors = verify_drift.toolchain_node_errors

CONTRACT_VALID = """
toolchain:
  java_version: "25"
  java_distribution: temurin
  node_version: "22.23.4"
"""

CONTRACT_MISSING_NODE = """
toolchain:
  java_version: "25"
"""


def test_positive_case_all_steps_pinned():
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22.23.4'
          cache: npm
  security-audit:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version: "22.23.4"
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == []


def test_defect_one_pinned_one_unpinned_step():
    """Defect: One step is pinned correctly, but another step lacks node-version pin.
    Previously returned [], must now report missing node-version pin error for unpinned step.
    """
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22.23.4'
  build-secondary:
    steps:
      - uses: actions/setup-node@v4
        with:
          cache: npm
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == [
        "GHA ci.yml setup-node step missing node-version pin (expected '22.23.4')"
    ]


def test_defect_unpinned_bare_setup_node():
    """Unpinned setup-node step with no 'with:' block."""
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version: '22.23.4'
  lint:
    steps:
      - uses: actions/setup-node@v4
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == [
        "GHA ci.yml setup-node step missing node-version pin (expected '22.23.4')"
    ]


def test_mismatch_wrong_node_version():
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version: '20.10.0'
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == [
        "GHA ci.yml setup-node node-version '20.10.0' != contract toolchain.node_version '22.23.4'"
    ]


def test_node_version_file_syntax_supported():
    """Valid node-version-file syntax (e.g. .nvmrc) should be recognized."""
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version-file: '.nvmrc'
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == []


def test_node_version_file_empty_fails():
    """Empty node-version-file lacks a valid pin."""
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version-file: ''
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == [
        "GHA ci.yml setup-node step missing node-version pin (expected '22.23.4')"
    ]


def test_step_with_name_before_uses():
    gha = """
jobs:
  frontend:
    steps:
      - name: Set up Node.js 22.23.4
        uses: actions/setup-node@v4
        with:
          node-version: '22.23.4'
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == []


def test_missing_setup_node_steps():
    gha = """
jobs:
  build:
    steps:
      - uses: actions/checkout@v4
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == [
        "GHA ci.yml missing setup-node steps with node-version to verify against toolchain.node_version"
    ]


def test_contract_missing_node_version():
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version: '22.23.4'
"""
    errors = toolchain_node_errors(CONTRACT_MISSING_NODE, gha)
    assert errors == ["docs/ci-contract.yaml missing toolchain.node_version"]


def test_multiple_errors_reported():
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version: '20.10.0'
  backend:
    steps:
      - uses: actions/setup-node@v4
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == [
        "GHA ci.yml setup-node node-version '20.10.0' != contract toolchain.node_version '22.23.4'",
        "GHA ci.yml setup-node step missing node-version pin (expected '22.23.4')",
    ]


def test_unquoted_version_and_comments():
    gha = """
jobs:
  frontend:
    steps:
      - uses: actions/setup-node@v4
        with:
          node-version: 22.23.4 # lts version
"""
    errors = toolchain_node_errors(CONTRACT_VALID, gha)
    assert errors == []


def test_repo_live_files_pass():
    repo = SCRIPT_PATH.parents[4]
    contract_path = repo / "docs" / "ci-contract.yaml"
    gha_path = repo / ".github" / "workflows" / "ci.yml"
    assert contract_path.is_file()
    assert gha_path.is_file()

    contract = contract_path.read_text(encoding="utf-8")
    gha = gha_path.read_text(encoding="utf-8")
    errors = toolchain_node_errors(contract, gha)
    assert errors == []
