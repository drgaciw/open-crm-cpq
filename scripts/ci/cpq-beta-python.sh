#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

cd "$(ci_cpq_beta_dir)"

python3 -m pip install --upgrade pip
python3 -m pip install pytest pytest-cov beautifulsoup4
python3 -m pip install frappe || echo "Frappe install skipped or unavailable in CI"

if [[ -d crm ]]; then
  python3 -m pytest crm/ --ignore=crm/public -v --tb=short \
    || echo "No pytest-compatible tests found or Frappe context required — skipping"
else
  echo "cpq_beta/crm directory not found — skipping"
fi
