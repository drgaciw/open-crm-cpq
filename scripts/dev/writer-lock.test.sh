#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCK_SCRIPT="${SCRIPT_DIR}/writer-lock.sh"
HOOK_SCRIPT="${SCRIPT_DIR}/pre-commit-writer-lock"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_ok() {
  local desc="$1"
  shift
  if ! "$@"; then
    fail "${desc} (expected exit 0)"
  fi
  echo "PASS: ${desc}"
}

assert_fail() {
  local desc="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    fail "${desc} (expected non-zero exit)"
  fi
  echo "PASS: ${desc}"
}

test -x "${LOCK_SCRIPT}" || fail "writer-lock.sh must exist and be executable"
test -x "${HOOK_SCRIPT}" || fail "pre-commit-writer-lock must exist and be executable"

cd "${TMP_DIR}"
git init -q
git config user.email "test@example.com"
git config user.name "Test"

# 1. No lock -> check succeeds even without AGENT_NAME
assert_ok "check passes when unlocked" env -u AGENT_NAME "${LOCK_SCRIPT}" check

# 2. claim without agent name fails
assert_fail "claim requires agent name" "${LOCK_SCRIPT}" claim

# 3. claim writes untracked lock under git-dir
assert_ok "claim pi-00 succeeds" "${LOCK_SCRIPT}" claim pi-00
LOCK_FILE="$(git rev-parse --git-dir)/agent-writer.lock"
test -f "${LOCK_FILE}" || fail "lock file not created at ${LOCK_FILE}"
OWNER="$(cat "${LOCK_FILE}")"
test "${OWNER}" = "pi-00" || fail "expected owner pi-00, got '${OWNER}'"
test -z "$(git status --porcelain)" || fail "lock file must remain untracked"
echo "PASS: lock file stored untracked at ${LOCK_FILE}"

# 4. check succeeds for matching owner
assert_ok "check passes when AGENT_NAME matches owner" env AGENT_NAME=pi-00 "${LOCK_SCRIPT}" check

# 5. check fails for foreign agent and for unset AGENT_NAME
assert_fail "check fails when AGENT_NAME differs" env AGENT_NAME=codex "${LOCK_SCRIPT}" check
assert_fail "check fails when AGENT_NAME is unset while locked" env -u AGENT_NAME "${LOCK_SCRIPT}" check

# 6. pre-commit hook delegates to check
assert_ok "pre-commit hook passes for owner" env AGENT_NAME=pi-00 "${HOOK_SCRIPT}"
assert_fail "pre-commit hook blocks foreign agent" env AGENT_NAME=codex "${HOOK_SCRIPT}"

# 7. release removes lock and restores unlocked check
assert_ok "release removes lock" "${LOCK_SCRIPT}" release
test ! -e "${LOCK_FILE}" || fail "lock file should be removed after release"
assert_ok "check passes after release" env AGENT_NAME=codex "${LOCK_SCRIPT}" check

echo "ALL WRITER-LOCK TESTS PASSED"
