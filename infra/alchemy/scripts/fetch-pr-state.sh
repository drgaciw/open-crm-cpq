#!/usr/bin/env bash
# Download the Alchemy local-state artifact for one PR stage.
# Fails closed when the artifact is missing: `alchemy destroy` with an empty
# store reports no resources and leaves the Sprite running.
set -euo pipefail

stage="${1:-}"
dest="${2:-}"

if [[ ! "${stage}" =~ ^pr-[1-9][0-9]*$ ]]; then
  echo "refusing stage ${stage}" >&2
  exit 1
fi
if [[ -z "${dest}" || "${dest}" == *..* || "${dest}" == /* ]]; then
  echo "refusing destination ${dest}" >&2
  exit 1
fi
if [[ "${HEAD_REPO:-}" != "${GITHUB_REPOSITORY:-}" ]]; then
  echo "refusing to fetch preview state for a fork checkout" >&2
  exit 1
fi

name="opencrm-alchemy-state-${stage}"
mkdir -p "${dest}"

artifact_id="$(
  gh api --paginate "/repos/${GITHUB_REPOSITORY}/actions/artifacts?name=${name}&per_page=100" \
    --jq ".artifacts[] | select(.name == \"${name}\" and .expired == false) | [.created_at, (.id | tostring)] | @tsv" \
    | sort | tail -n 1 | cut -f2
)"

if [[ ! "${artifact_id}" =~ ^[0-9]+$ ]]; then
  echo "no state artifact ${name}; refusing to destroy with an empty store" >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT
gh api "/repos/${GITHUB_REPOSITORY}/actions/artifacts/${artifact_id}/zip" > "${tmp}/state.zip"
unzip -q "${tmp}/state.zip" -d "${dest}"
echo "restored ${name} (${artifact_id}) into ${dest}"
