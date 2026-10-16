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

# Test every project that declares a test target. A hand-kept allowlist silently skipped every cpq-*
# project except cpq-quotes, and any new project (DRG-527); --all cannot drift.
TEST_PROJECTS="$(npx nx show projects --with-target=test | sort | paste -sd, -)"
echo "Running tests for $(echo "${TEST_PROJECTS}" | tr ',' '\n' | grep -c .) projects: ${TEST_PROJECTS}"
npx nx run-many --target=test --all --parallel=3
