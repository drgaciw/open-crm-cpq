#!/usr/bin/env bash
# Lightweight per-checkout writer-lock guard (one writer per git checkout/worktree).
#
# Stores the lock owner in "$(git rev-parse --git-dir)/agent-writer.lock" (untracked,
# worktree-local). Intended for opt-in use via scripts/dev/pre-commit-writer-lock
# (git config core.hooksPath or symlink to .git/hooks/pre-commit).
#
# Usage:
#   scripts/dev/writer-lock.sh claim <agent>
#   scripts/dev/writer-lock.sh release
#   scripts/dev/writer-lock.sh check

set -euo pipefail

usage() {
  echo "Usage: $(basename "$0") {claim <agent>|release|check}" >&2
  exit 2
}

lock_path() {
  local git_dir
  git_dir="$(git rev-parse --git-dir)"
  printf '%s/agent-writer.lock\n' "${git_dir}"
}

cmd="${1:-}"
case "${cmd}" in
  claim)
    agent="${2:-}"
    if [[ -z "${agent}" ]]; then
      echo "writer-lock: claim requires an <agent> name" >&2
      usage
    fi
    path="$(lock_path)"
    printf '%s\n' "${agent}" > "${path}"
    echo "writer-lock: claimed by '${agent}' (${path})"
    ;;
  release)
    path="$(lock_path)"
    rm -f "${path}"
    echo "writer-lock: released (${path})"
    ;;
  check)
    path="$(lock_path)"
    if [[ ! -f "${path}" ]]; then
      exit 0
    fi
    owner="$(tr -d '\r\n' < "${path}")"
    current="${AGENT_NAME:-}"
    if [[ -z "${current}" || "${current}" != "${owner}" ]]; then
      echo "writer-lock: checkout is locked by '${owner}' (AGENT_NAME='${current:-<unset>}')" >&2
      exit 1
    fi
    exit 0
    ;;
  *)
    usage
    ;;
esac
