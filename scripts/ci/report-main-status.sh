#!/usr/bin/env bash
# Keeps one open GitHub issue per workflow while main is red (ADR-003 section 8, amendment 2026-11-10).
#
#   report-main-status.sh failure   open the issue, or comment on the open one
#   report-main-status.sh success   comment on the open issue and close it
#
# Needs gh and jq on PATH, GH_TOKEN with issues:write, and the GITHUB_* variables Actions sets.
set -euo pipefail

status="${1:?usage: report-main-status.sh success|failure}"
label="main-red"
title="main is red: ${GITHUB_WORKFLOW}"
run_url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
commit="${GITHUB_SHA:0:8}"

existing="$(gh issue list --repo "${GITHUB_REPOSITORY}" --state open --label "${label}" \
  --json number,title --limit 100 | jq -r --arg t "${title}" 'map(select(.title == $t)) | .[0].number // empty')"

case "${status}" in
  failure)
    body="**${GITHUB_WORKFLOW}** failed on \`main\` at \`${commit}\`: ${run_url}

The next merge to \`main\` is the fix or a revert (ADR-003 section 8). This issue closes itself on the next green run."
    if [[ -n "${existing}" ]]; then
      gh issue comment "${existing}" --repo "${GITHUB_REPOSITORY}" --body "Still red at \`${commit}\`: ${run_url}"
    else
      gh label create "${label}" --repo "${GITHUB_REPOSITORY}" --color B60205 \
        --description "main is failing CI" --force
      gh issue create --repo "${GITHUB_REPOSITORY}" --title "${title}" --label "${label}" --body "${body}"
    fi
    ;;
  success)
    if [[ -n "${existing}" ]]; then
      gh issue close "${existing}" --repo "${GITHUB_REPOSITORY}" --comment "Green again at \`${commit}\`: ${run_url}"
    fi
    ;;
  *)
    echo "report-main-status.sh: unknown status '${status}'" >&2
    exit 2
    ;;
esac
