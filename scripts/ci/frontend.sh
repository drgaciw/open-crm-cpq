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

# Every gate below runs over the whole Nx project graph (--all), so a new project is gated
# automatically. The check fails first if any project lacks a gate target, because
# `run-many --all` would otherwise skip it without a word (P-5, DRG-527).
bash "${SCRIPT_DIR}/frontend-gate-check.sh"

npx nx run-many --target=build --all --parallel=3
npx nx run-many --target=lint --all --parallel=3
npx nx run-many --target=typecheck --all --parallel=3
npx nx run-many --target=test --all --parallel=3
