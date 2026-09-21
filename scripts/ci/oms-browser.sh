#!/usr/bin/env bash
# Required fixture-browser + live PostgreSQL-backed OMS acceptance stage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
ci_require_cmd docker
ci_require_cmd node
ci_require_cmd npm
ci_require_cmd python3
docker info >/dev/null

REPO_ROOT="$(ci_repo_root)"
ARTIFACT_DIR="$(ci_backend_dir)/services/order-management/target"
mkdir -p "${ARTIFACT_DIR}"
# tee preserves useful diagnostics on failure; pipefail keeps this stage blocking.
exec > >(tee "${ARTIFACT_DIR}/oms-browser-ci.log") 2>&1

# The reactor permits upstream modules without this named test; never let that hide
# an accidentally missing live acceptance class in the service itself.
LIVE_TEST_SOURCE="$(find "$(ci_backend_dir)/services/order-management/src/test/java" -name OrderLiveBrowserUat.java -print -quit)"
if [[ -z "${LIVE_TEST_SOURCE}" ]]; then
  echo 'Required OrderLiveBrowserUat.java is missing' >&2
  exit 1
fi

cd "$(ci_frontend_dir)"
npm ci --legacy-peer-deps
# Linux agents need browser libraries; run with the agent's approved package-install capability.
npx --no-install playwright install --with-deps chromium
cd "${REPO_ROOT}"
node scripts/ci/oms-browser.mjs --fixtures

REPORT_DIR="${ARTIFACT_DIR}/surefire-reports"
mkdir -p "${REPORT_DIR}"
find "${REPORT_DIR}" -maxdepth 1 -type f -name 'TEST-*OrderLiveBrowserUat.xml' -delete
ci_mvn -B --no-transfer-progress -pl services/order-management -am test \
  '-Dtest=OrderLiveBrowserUat' '-Dsurefire.failIfNoSpecifiedTests=false' \
  '-Dgroups=integration' '-DexcludedGroups='
python3 - "${REPORT_DIR}" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

reports = list(Path(sys.argv[1]).glob('TEST-*OrderLiveBrowserUat.xml'))
if not reports:
    raise SystemExit('Live browser acceptance produced no Surefire report')
tests = 0
for report in reports:
    suite = ET.parse(report).getroot()
    tests += int(suite.get('tests', '0'))
    if any(int(suite.get(key, '0')) for key in ('failures', 'errors', 'skipped')):
        raise SystemExit(f'Live browser acceptance failed or skipped tests: {report}')
if tests < 1:
    raise SystemExit('Live browser acceptance executed zero tests')
print(f'Live browser acceptance executed {tests} tests with zero failures/errors/skips')
PY
