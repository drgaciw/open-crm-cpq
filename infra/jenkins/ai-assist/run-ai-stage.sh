#!/usr/bin/env bash
# Wrapper invoked from Jenkinsfile for AI stages.
set -euo pipefail

STAGE="${1:?Usage: run-ai-stage.sh <code_review|test_suggestions|log_analysis|drift_check|security_triage> [args...]}"
shift

AI_ASSIST_DIR="${AI_ASSIST_DIR:-/opt/ai-assist}"
REPO_AI_ASSIST="${WORKSPACE:-.}/infra/jenkins/ai-assist"

if [[ -d "$REPO_AI_ASSIST" ]]; then
  export PYTHONPATH="$REPO_AI_ASSIST:${PYTHONPATH:-}"
  SCRIPT_DIR="$REPO_AI_ASSIST"
elif [[ -d "$AI_ASSIST_DIR" ]]; then
  export PYTHONPATH="$AI_ASSIST_DIR:${PYTHONPATH:-}"
  SCRIPT_DIR="$AI_ASSIST_DIR"
else
  echo "AI assist scripts not found"
  exit 1
fi

mkdir -p "${WORKSPACE:-.}/ci-reports"

case "$STAGE" in
  code_review)
    python3 "$SCRIPT_DIR/code_review.py" --output "${WORKSPACE:-.}/ci-reports/ai-code-review.md" "$@"
    ;;
  test_suggestions)
    python3 "$SCRIPT_DIR/test_suggestions.py" --workspace "${WORKSPACE:-.}" "$@"
    ;;
  log_analysis)
    python3 "$SCRIPT_DIR/log_analysis.py" "$@"
    ;;
  drift_check)
    python3 "$SCRIPT_DIR/drift_sentinel.py" --workspace "${WORKSPACE:-.}" "$@"
    ;;
  security_triage)
    python3 "$SCRIPT_DIR/security_triage.py" --workspace "${WORKSPACE:-.}" "$@"
    ;;
  *)
    echo "Unknown AI stage: $STAGE"
    exit 2
    ;;
esac
