#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

# Fast Docker-free unit path for the whole backend reactor: every lib and service, with
# integration- and docker-tagged tests excluded by -Punit-tests. Before 2026-09-22 this built
# only crm-service + gateway-service, so the other modules' unit tests ran only in the
# Docker integration stage.
#
# Docker-free is enforced, not assumed: a unit test that reaches a Testcontainers class
# (for example through a static helper on an integration base class) silently starts
# containers wherever Docker happens to be available. The stage fails if that happens.
LOG_DIR="$(ci_repo_root)/ci-reports"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/backend-unit.log"

ci_mvn -B -U verify -Punit-tests --no-transfer-progress 2>&1 | tee "${LOG_FILE}"

if grep -q "Creating container for image" "${LOG_FILE}"; then
  echo "backend-unit must be Docker-free, but Testcontainers started containers:" >&2
  grep -n "Creating container for image" "${LOG_FILE}" >&2
  echo "Tag the test @Tag(\"integration\") or move the helper off the Testcontainers base class." >&2
  exit 1
fi
