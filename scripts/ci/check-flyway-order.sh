#!/usr/bin/env bash
# Reject a versioned Flyway migration the pull request adds at or below the
# highest version already on the base ref for that module, and reject any
# duplicate version in a module's db/migration directory.
#
# Versions follow Flyway ordering: underscores are dots (V1_10 is 1.10), each
# dot-separated part is compared as an integer, and a missing part is 0
# (1 equals 1.0). Repeatable (R__) and undo (U__) scripts are ignored.
set -euo pipefail

BASE_REF="${1:-${FLYWAY_BASE_REF:-origin/main}}"

if [[ ! "$BASE_REF" =~ ^[A-Za-z0-9._/-]+$ ]]; then
  echo "Refusing unsafe base ref" >&2
  exit 2
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if ! git rev-parse --verify --quiet "${BASE_REF}^{commit}" >/dev/null; then
  echo "Base ref not found: ${BASE_REF}" >&2
  exit 2
fi

canonicalize_version() {
  local ver="$1"
  local -a raw kept
  local part trimmed
  IFS='.' read -r -a raw <<< "$ver"
  kept=()
  for part in "${raw[@]}"; do
    if [[ "$part" =~ ^[0-9]+$ ]]; then
      trimmed=$((10#$part))
    else
      trimmed="$part"
    fi
    kept+=("$trimmed")
  done
  while ((${#kept[@]} > 1)) && [[ "${kept[-1]}" == "0" ]]; do
    unset 'kept[-1]'
  done
  (
    IFS='.'
    printf '%s\n' "${kept[*]}"
  )
}

# Print the canonical Flyway version, or return 1 when the file is not versioned.
flyway_version() {
  local base="$1"
  local raw
  if [[ "$base" =~ ^V([0-9]+([._][0-9]+)*)__ ]]; then
    raw="${BASH_REMATCH[1]//_/.}"
    canonicalize_version "$raw"
    return 0
  fi
  return 1
}

# Print -1, 0, or 1. Parts are already canonical integers.
version_cmp() {
  local a="$1"
  local b="$2"
  local -a pa pb
  local n i sa sb
  IFS='.' read -r -a pa <<< "$a"
  IFS='.' read -r -a pb <<< "$b"
  n=${#pa[@]}
  if ((${#pb[@]} > n)); then
    n=${#pb[@]}
  fi
  for ((i = 0; i < n; i++)); do
    sa="${pa[$i]:-0}"
    sb="${pb[$i]:-0}"
    if ((10#$sa < 10#$sb)); then
      printf '%s\n' -1
      return 0
    fi
    if ((10#$sa > 10#$sb)); then
      printf '%s\n' 1
      return 0
    fi
  done
  printf '%s\n' 0
}

is_migration_path() {
  local path="$1"
  [[ "$path" == */db/migration/* && "$path" != */target/* ]]
}

declare -A base_paths=()
declare -A base_max_version=()
declare -A base_max_file=()
declare -A head_version_file=()
declare -a failures=()

record_base() {
  local path="$1"
  local ver="$2"
  local mod cmp
  mod="$(dirname "$path")"
  base_paths["$path"]=1
  if [[ -z "${base_max_version[$mod]+x}" ]]; then
    base_max_version["$mod"]="$ver"
    base_max_file["$mod"]="$path"
    return 0
  fi
  cmp="$(version_cmp "$ver" "${base_max_version[$mod]}")"
  if [[ "$cmp" -eq 1 ]]; then
    base_max_version["$mod"]="$ver"
    base_max_file["$mod"]="$path"
  fi
}

while IFS= read -r -d '' path; do
  [[ -z "$path" ]] && continue
  is_migration_path "$path" || continue
  if ver="$(flyway_version "$(basename "$path")")"; then
    record_base "$path" "$ver"
  fi
done < <(git ls-tree -r -z --name-only "$BASE_REF")

while IFS= read -r -d '' path; do
  [[ -z "$path" ]] && continue
  is_migration_path "$path" || continue
  ver="$(flyway_version "$(basename "$path")")" || continue
  mod="$(dirname "$path")"
  key="${mod}|${ver}"
  if [[ -n "${head_version_file[$key]+x}" ]]; then
    failures+=("duplicate Flyway version ${ver} in ${mod}: ${head_version_file[$key]} and ${path}")
  else
    head_version_file["$key"]="$path"
  fi
  if [[ -z "${base_paths[$path]+x}" ]]; then
    if [[ -n "${base_max_version[$mod]+x}" ]]; then
      cmp="$(version_cmp "$ver" "${base_max_version[$mod]}")"
      if [[ "$cmp" -le 0 ]]; then
        failures+=("$(basename "$path") version ${ver} is at or below highest base version ${base_max_version[$mod]} ($(basename "${base_max_file[$mod]}")) in ${mod}")
      fi
    fi
  fi
done < <({ git ls-files -z; git ls-files -z --others --exclude-standard; } | sort -z)

if ((${#failures[@]} > 0)); then
  echo "Flyway order check failed against ${BASE_REF}:" >&2
  for failure in "${failures[@]}"; do
    echo "  ${failure}" >&2
  done
  exit 1
fi

echo "Flyway order check OK against ${BASE_REF}"
