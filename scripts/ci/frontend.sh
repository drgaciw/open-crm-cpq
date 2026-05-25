#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

cd "$(ci_frontend_dir)"

npm ci --legacy-peer-deps

node --version
npm --version
npx nx --version

npx nx run-many --target=build --all --parallel=3
npx nx run-many --target=lint --all --parallel=3

TARGET_PROJECTS="crm-deals crm-leads crm-contacts crm-organizations crm-dashboard shared-models shared-data-access"
AVAILABLE_PROJECTS="$(
  npx nx show projects --with-target=test \
    | { grep -E "^($(echo "$TARGET_PROJECTS" | tr ' ' '|'))$" || true; } \
    | paste -sd, -
)"

if [[ -n "${AVAILABLE_PROJECTS}" ]]; then
  echo "Running tests for: ${AVAILABLE_PROJECTS}"
  npx nx run-many --target=test --projects="${AVAILABLE_PROJECTS}" --parallel=3
else
  echo "No targeted CRM/shared projects expose a test target — skipping frontend tests"
fi
