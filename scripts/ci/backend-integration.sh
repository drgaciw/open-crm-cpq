#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

ci_require_cmd docker

cd "$(ci_backend_dir)"
docker version
docker info
mvn -B -U verify --no-transfer-progress
