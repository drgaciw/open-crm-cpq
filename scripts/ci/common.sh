#!/usr/bin/env bash
# Shared helpers for CI stage scripts (Jenkins + manual GHA fallback).
set -euo pipefail

ci_repo_root() {
  local dir
  dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  printf '%s\n' "$dir"
}

ci_require_cmd() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

ci_backend_dir() {
  printf '%s/backend\n' "$(ci_repo_root)"
}

ci_frontend_dir() {
  printf '%s/frontend\n' "$(ci_repo_root)"
}

ci_cpq_beta_dir() {
  printf '%s/cpq_beta\n' "$(ci_repo_root)"
}

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
