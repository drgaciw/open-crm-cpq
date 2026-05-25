#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

cd "$(ci_backend_dir)"
mvn -B -U -pl services/crm-service -am verify -Punit-tests --no-transfer-progress
