#!/usr/bin/env bash
# Fails when an Nx project exists but would not be gated by scripts/ci/frontend.sh.
#
# frontend.sh runs `nx run-many --all` for every gate target, so a project is skipped only
# when it does not declare that target (nx.json has no plugins, so nothing is inferred).
# This check turns that silent skip into a hard failure. Usage: bash scripts/ci/frontend-gate-check.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

cd "$(ci_frontend_dir)"

GATE_TARGETS="${FRONTEND_GATE_TARGETS:-lint typecheck test}"

# `nx show projects` prints a JSON array when stdout is not a TTY (CI), one name per line
# otherwise; ask for JSON explicitly and normalise to one sorted name per line.
nx_projects() {
  npx nx show projects --json "$@" \
    | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const a=JSON.parse(s);if(!Array.isArray(a))process.exit(3);console.log(a.sort().join("\n"))})'
}

ALL_PROJECTS="$(nx_projects)"
if [[ -z "${ALL_PROJECTS}" ]]; then
  echo "frontend-gate-check: nx reported no projects; refusing to pass an empty gate" >&2
  exit 1
fi

status=0
for target in ${GATE_TARGETS}; do
  with_target="$(nx_projects --with-target="${target}")"
  missing="$(comm -23 <(printf '%s\n' "${ALL_PROJECTS}") <(printf '%s\n' "${with_target}"))"
  if [[ -n "${missing}" ]]; then
    status=1
    echo "frontend-gate-check: projects without a '${target}' target (they would be silently skipped):" >&2
    printf '  - %s\n' ${missing} >&2
  fi
done

if [[ "${status}" -ne 0 ]]; then
  echo "Declare the missing targets explicitly in each project.json (nx.json has no plugins)." >&2
  exit 1
fi

echo "frontend-gate-check: all $(printf '%s\n' "${ALL_PROJECTS}" | wc -l | tr -d ' ') Nx projects declare: ${GATE_TARGETS}"
printf '  %s\n' ${ALL_PROJECTS}
