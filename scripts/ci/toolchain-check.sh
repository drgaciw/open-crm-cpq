#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

ci_require_cmd java
if ! command -v mvn >/dev/null 2>&1 && [[ ! -x "$(ci_backend_dir)/mvnw" ]]; then
  echo "Required: mvn on PATH or backend/mvnw" >&2
  exit 1
fi
ci_require_cmd node
ci_require_cmd npm
ci_require_cmd docker
ci_require_cmd python3

echo "=== CI toolchain ==="
java -version
if command -v mvn >/dev/null 2>&1; then
  mvn -version
else
  "$(ci_backend_dir)/mvnw" -version
fi
node --version
npm --version
docker version
python3 --version
echo "Toolchain OK"
