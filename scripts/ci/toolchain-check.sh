#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

ci_require_cmd java
ci_require_cmd mvn
ci_require_cmd node
ci_require_cmd npm
ci_require_cmd docker
ci_require_cmd python3

echo "=== CI toolchain ==="
java -version
mvn -version
node --version
npm --version
docker version
python3 --version
echo "Toolchain OK"
