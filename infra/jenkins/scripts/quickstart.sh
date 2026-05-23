#!/usr/bin/env bash
# OpenCRM CPQ — local Jenkins quickstart (dev stack).
# Usage:
#   ./infra/jenkins/scripts/quickstart.sh              # env + compose up + health check + toolchain smoke
#   ./infra/jenkins/scripts/quickstart.sh --up-only    # skip host toolchain smoke
#   ./infra/jenkins/scripts/quickstart.sh --health-only
#   ./infra/jenkins/scripts/quickstart.sh --smoke-only # toolchain only (Jenkins may already be up)
#   ./infra/jenkins/scripts/quickstart.sh --down       # stop stack
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JENKINS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${JENKINS_DIR}/../.." && pwd)"
COMPOSE_FILE="${JENKINS_DIR}/docker-compose.jenkins.yml"
ENV_EXAMPLE="${JENKINS_DIR}/env/jenkins.env.example"
ENV_FILE="${JENKINS_DIR}/env/jenkins.env"

UP_ONLY=false
HEALTH_ONLY=false
SMOKE_ONLY=false
DOWN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --up-only) UP_ONLY=true; shift ;;
    --health-only) HEALTH_ONLY=true; shift ;;
    --smoke-only) SMOKE_ONLY=true; shift ;;
    --down) DOWN=true; shift ;;
    -h|--help)
      sed -n '2,9p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

read_env_var() {
  local key="$1"
  local default="$2"
  local val
  val="$(grep -E "^${key}=" "${ENV_FILE}" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r' || true)"
  if [[ -z "${val}" ]]; then
    echo "${default}"
  else
    echo "${val}"
  fi
}

load_env_port() {
  JENKINS_HTTP_PORT="$(read_env_var JENKINS_HTTP_PORT 8080)"
  JENKINS_URL="$(read_env_var JENKINS_URL "http://localhost:${JENKINS_HTTP_PORT}/")"
}

ensure_env_file() {
  if [[ -f "${ENV_FILE}" ]]; then
    echo "Using env file: ${ENV_FILE}"
    return
  fi
  if [[ ! -f "${ENV_EXAMPLE}" ]]; then
    echo "Missing ${ENV_EXAMPLE}" >&2
    exit 1
  fi
  cp "${ENV_EXAMPLE}" "${ENV_FILE}"
  tr -d '\r' < "${ENV_FILE}" > "${ENV_FILE}.tmp" && mv "${ENV_FILE}.tmp" "${ENV_FILE}"
  echo "Created ${ENV_FILE} from example."
  echo "Edit JENKINS_ADMIN_PASSWORD before shared environments."
}

warn_default_password() {
  if grep -qE '^JENKINS_ADMIN_PASSWORD=changeme\s*$' "${ENV_FILE}" 2>/dev/null; then
    echo "WARN: JENKINS_ADMIN_PASSWORD is still 'changeme' — change it in ${ENV_FILE}"
  fi
}

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

wait_for_health() {
  load_env_port
  local url="${JENKINS_URL%/}/login"
  local attempts="${QUICKSTART_HEALTH_ATTEMPTS:-60}"
  local delay="${QUICKSTART_HEALTH_DELAY_SEC:-5}"
  echo "Waiting for Jenkins at ${url} (up to $((attempts * delay))s)..."
  local i
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS -o /dev/null "${url}" 2>/dev/null; then
      echo "Jenkins is up (${url})"
      return 0
    fi
    sleep "${delay}"
  done
  echo "Jenkins did not become healthy in time." >&2
  echo "--- last 30 lines: docker logs opencrm-jenkins ---" >&2
  docker logs opencrm-jenkins --tail 30 2>&1 >&2 || true
  echo "Check: docker compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} logs jenkins" >&2
  return 1
}

run_toolchain_smoke() {
  echo "Running host toolchain smoke: scripts/ci/run-core.sh toolchain"
  (cd "${REPO_ROOT}" && bash scripts/ci/run-core.sh toolchain)
}

if [[ "${DOWN}" == true ]]; then
  require_cmd docker
  if [[ -f "${ENV_FILE}" ]]; then
    compose down
  else
    docker compose -f "${COMPOSE_FILE}" down
  fi
  echo "Jenkins stack stopped."
  exit 0
fi

if [[ "${SMOKE_ONLY}" == true ]]; then
  run_toolchain_smoke
  exit 0
fi

require_cmd docker
require_cmd curl

if [[ "${HEALTH_ONLY}" == true ]]; then
  [[ -f "${ENV_FILE}" ]] || { echo "Missing ${ENV_FILE}"; exit 1; }
  wait_for_health
  exit 0
fi

ensure_env_file
warn_default_password

echo "Starting Jenkins (compose build may take several minutes)..."
compose up -d --build

wait_for_health

load_env_port
echo ""
echo "Next steps (first green build):"
echo "  1. Open ${JENKINS_URL} (user from env: JENKINS_ADMIN_USER)"
echo "  2. Scan multibranch job 'opencrm-cpq' (JCasC) or configure GitHub webhook"
echo "  3. Keep AI_CI_ENABLED=false for the first run"
echo "  4. Trigger a build; expect Security Audit may be UNSTABLE"
echo ""

if [[ "${UP_ONLY}" == false ]]; then
  run_toolchain_smoke
fi

echo "Quickstart complete."
