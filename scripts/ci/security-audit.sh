#!/usr/bin/env bash
# Non-blocking security scans (Jenkins marks UNSTABLE via catchError; GHA uses continue-on-error).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

ROOT="$(ci_repo_root)"
AUDIT_FAILED=0

if [[ -f "${ROOT}/cpq_beta/package.json" ]]; then
  (
    cd "${ROOT}/cpq_beta"
    npm audit --audit-level=critical \
      || { echo "npm audit found critical issues — review output"; AUDIT_FAILED=1; }
  )
else
  echo "No cpq_beta/package.json found — skipping npm audit"
fi

(
  # Fully-qualified pin matches GHA nightly (DRG-673). No -DfailBuildOnCVSS override —
  # threshold comes from backend/pom.xml property dependency-check.failBuildOnCVSS.
  ci_mvn -B org.owasp:dependency-check-maven:12.1.3:check --no-transfer-progress \
    || { echo "OWASP check complete with findings or scanner issue"; AUDIT_FAILED=1; }
)

exit "${AUDIT_FAILED}"
