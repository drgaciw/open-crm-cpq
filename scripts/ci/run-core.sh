#!/usr/bin/env bash
# CI SSOT entrypoint — dispatches to stage scripts (Jenkins + manual GHA fallback).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<'EOF' >&2
Usage: bash scripts/ci/run-core.sh <stage>

Stages:
  toolchain            Verify java, mvn, node, docker, python + CI contract drift
  shared-libraries     Maven verify for backend/libs/*
  backend-unit         Fast Maven unit profile (crm-service)
  backend-integration  Full Maven verify (Testcontainers)
  cpq-beta-python      cpq_beta pytest (best-effort)
  frontend             Nx build, lint, targeted tests
  security-audit       npm audit + OWASP dependency-check (non-blocking findings)
EOF
  exit 2
}

STAGE="${1:-}"
if [[ -z "${STAGE}" ]]; then
  usage
fi

case "${STAGE}" in
  toolchain) exec bash "${SCRIPT_DIR}/toolchain-check.sh" ;;
  shared-libraries) exec bash "${SCRIPT_DIR}/shared-libraries.sh" ;;
  backend-unit) exec bash "${SCRIPT_DIR}/backend-unit.sh" ;;
  backend-integration) exec bash "${SCRIPT_DIR}/backend-integration.sh" ;;
  cpq-beta-python) exec bash "${SCRIPT_DIR}/cpq-beta-python.sh" ;;
  frontend) exec bash "${SCRIPT_DIR}/frontend.sh" ;;
  security-audit) exec bash "${SCRIPT_DIR}/security-audit.sh" ;;
  *)
    echo "Unknown CI stage: ${STAGE}" >&2
    usage
    ;;
esac
