#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

# Fast Docker-free unit path: CRM domain + gateway route-contract checks.
# gateway-service CpqGatewayRoutesTest is @Tag("unit") / filesystem YAML only (no Testcontainers).
ci_mvn -B -U -pl services/crm-service,services/gateway-service -am verify -Punit-tests --no-transfer-progress
