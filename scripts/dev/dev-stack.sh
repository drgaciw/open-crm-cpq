#!/usr/bin/env bash
# Local dev backend stack launcher: gateway + the services the CRM/CPQ frontend (nx serve crm, :4200) uses.
#
# Usage:
#   scripts/dev/dev-stack.sh status            # who owns each dev port, and health
#   scripts/dev/dev-stack.sh start [svc...]    # start (skips services already healthy on their port)
#   scripts/dev/dev-stack.sh stop  [svc...]    # stop dev-stack java processes on the dev ports
#   scripts/dev/dev-stack.sh restart [svc...]
#   scripts/dev/dev-stack.sh build [svc...]    # opt-in: mvn package -DskipTests for the selected services
#
# Services run from backend/services/<svc>/target/<svc>-1.0.0-SNAPSHOT.jar against the dev containers
# opencrm-dev-postgres (:15432) and opencrm-dev-redis (:16379). The DB password is read from the
# Postgres container unless DB_PASSWORD is already set, so no secret lives in this file.
#
# crm-service runs on 18081 because :8081 is commonly taken by another local tool; the gateway config is
# generated from the repo's gateway application.yml with only the CRM port remapped, so routes stay current.
#
# Runtime state (logs, generated gateway config) goes to ${OPENCRM_DEV_STATE:-~/.local/state/opencrm-dev}.
# Processes are detached with setsid, so they outlive the terminal, but not a reboot.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="${REPO_ROOT}/backend"
STATE_DIR="${OPENCRM_DEV_STATE:-${XDG_STATE_HOME:-$HOME/.local/state}/opencrm-dev}"
LOG_DIR="${STATE_DIR}/logs"
GATEWAY_CONFIG="${STATE_DIR}/gateway.yml"

PG_CONTAINER="${OPENCRM_DEV_PG_CONTAINER:-opencrm-dev-postgres}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-15432}"
DB_NAME="${DB_NAME:-opencrm}"
DB_USERNAME="${DB_USERNAME:-postgres}"
REDIS_PORT="${OPENCRM_DEV_REDIS_PORT:-16379}"
CRM_PORT="${OPENCRM_DEV_CRM_PORT:-18081}"
HEALTH_TIMEOUT="${OPENCRM_DEV_HEALTH_TIMEOUT:-180}"

# The backend targets Java 25; `java` on PATH may be a newer JDK, so pin it.
resolve_java() {
  if [[ -n "${OPENCRM_DEV_JAVA:-}" ]]; then echo "${OPENCRM_DEV_JAVA}"; return; fi
  local home; home="$(mise where java@25 2>/dev/null || true)"
  if [[ -n "${home}" && -x "${home}/bin/java" ]]; then echo "${home}/bin/java"; return; fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then echo "${JAVA_HOME}/bin/java"; return; fi
  command -v java
}
JAVA_BIN="$(resolve_java)"

# Start order matters: backends first, gateway last.
ALL_SERVICES=(crm-service pricing-service product-service quote-service contract-asset-management pricebook-service gateway-service)

port_of() {
  case "$1" in
    crm-service) echo "${CRM_PORT}" ;;
    pricing-service) echo 8082 ;;
    product-service) echo 8083 ;;
    quote-service) echo 8084 ;;
    contract-asset-management) echo 8087 ;;
    pricebook-service) echo 8089 ;;
    gateway-service) echo 8080 ;;
    *) echo "dev-stack: unknown service '$1'" >&2; return 1 ;;
  esac
}

extra_args_of() {
  case "$1" in
    crm-service) echo "--server.port=${CRM_PORT}" ;;
    # The dev database carries pricing migrations the current branch may not ship.
    pricing-service) echo "--spring.flyway.ignore-migration-patterns=*:missing" ;;
    gateway-service) echo "--spring.config.additional-location=file:${GATEWAY_CONFIG}" ;;
    *) echo "" ;;
  esac
}

jar_of() { echo "${BACKEND}/services/$1/target/$1-1.0.0-SNAPSHOT.jar"; }

health_code() {
  curl -s -m 3 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$1/actuator/health" 2>/dev/null || true
}

listener_pid() {
  ss -ltnpH "sport = :$1" 2>/dev/null | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1
}

# A process is ours only if it is `java -jar <...>/<svc>...jar`; never touch anything else on a port.
is_dev_service_process() {
  local pid="$1" svc="$2" cmd
  [[ -r "/proc/${pid}/cmdline" ]] || return 1
  cmd="$(tr '\0' ' ' < "/proc/${pid}/cmdline")"
  [[ "${cmd}" == *java* && "${cmd}" == *" -jar "* ]] || return 1
  [[ "${cmd}" == *"/${svc}"*".jar"* || "${cmd}" == *"${svc}-1.0.0-SNAPSHOT.jar"* || "${cmd}" == *"/${svc}.jar"* ]]
}

resolve_db_password() {
  if [[ -n "${DB_PASSWORD:-}" ]]; then return; fi
  DB_PASSWORD="$(docker inspect "${PG_CONTAINER}" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | sed -n 's/^POSTGRES_PASSWORD=//p')"
  if [[ -z "${DB_PASSWORD}" ]]; then
    echo "dev-stack: DB_PASSWORD not set and could not be read from container '${PG_CONTAINER}'" >&2
    exit 1
  fi
}

generate_gateway_config() {
  mkdir -p "${STATE_DIR}"
  sed "s#localhost:8081#localhost:${CRM_PORT}#g" \
    "${BACKEND}/services/gateway-service/src/main/resources/application.yml" > "${GATEWAY_CONFIG}.tmp"
  mv "${GATEWAY_CONFIG}.tmp" "${GATEWAY_CONFIG}"
}

wait_healthy() {
  local svc="$1" port="$2" pid="$3" waited=0
  while (( waited < HEALTH_TIMEOUT )); do
    if [[ "$(health_code "${port}")" == 200 ]]; then
      echo "  ${svc}: healthy on :${port} (pid ${pid}, ${waited}s)"
      return 0
    fi
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "  ${svc}: process exited during startup; last log lines:" >&2
      tail -n 25 "${LOG_DIR}/${svc}.log" >&2 || true
      return 1
    fi
    sleep 2; waited=$((waited + 2))
  done
  echo "  ${svc}: not healthy after ${HEALTH_TIMEOUT}s; see ${LOG_DIR}/${svc}.log" >&2
  return 1
}

start_one() {
  local svc="$1" port jar pid existing
  port="$(port_of "${svc}")"
  jar="$(jar_of "${svc}")"
  existing="$(listener_pid "${port}")"
  if [[ -n "${existing}" ]]; then
    if is_dev_service_process "${existing}" "${svc}" && [[ "$(health_code "${port}")" == 200 ]]; then
      echo "  ${svc}: already running on :${port} (pid ${existing})"
      return 0
    fi
    echo "dev-stack: :${port} is held by pid ${existing} ($(ps -o comm= -p "${existing}")); not starting ${svc}" >&2
    return 1
  fi
  [[ -f "${jar}" ]] || { echo "dev-stack: missing ${jar} (run: $0 build ${svc})" >&2; return 1; }
  [[ "${svc}" == gateway-service ]] && generate_gateway_config

  mkdir -p "${LOG_DIR}"
  # shellcheck disable=SC2046 # extra args are intentionally word-split
  ( cd "${BACKEND}" && \
    DB_HOST="${DB_HOST}" DB_PORT="${DB_PORT}" DB_NAME="${DB_NAME}" DB_USERNAME="${DB_USERNAME}" DB_PASSWORD="${DB_PASSWORD}" \
    exec setsid nohup "${JAVA_BIN}" -jar "${jar}" --spring.profiles.active=dev --spring.data.redis.port="${REDIS_PORT}" \
      $(extra_args_of "${svc}") > "${LOG_DIR}/${svc}.log" 2>&1 < /dev/null ) &
  pid=$!
  wait_healthy "${svc}" "${port}" "${pid}"
}

stop_one() {
  local svc="$1" port pid
  port="$(port_of "${svc}")"
  pid="$(listener_pid "${port}")"
  if [[ -z "${pid}" ]]; then
    echo "  ${svc}: not running on :${port}"
    return 0
  fi
  if ! is_dev_service_process "${pid}" "${svc}"; then
    echo "  ${svc}: :${port} is held by pid ${pid} ($(ps -o comm= -p "${pid}")), which is not ${svc}; leaving it alone" >&2
    return 0
  fi
  kill "${pid}"
  for _ in $(seq 1 30); do kill -0 "${pid}" 2>/dev/null || { echo "  ${svc}: stopped (pid ${pid})"; return 0; }; sleep 1; done
  echo "  ${svc}: pid ${pid} ignored SIGTERM for 30s; sending SIGKILL" >&2
  kill -9 "${pid}" 2>/dev/null || true
}

status() {
  local svc port pid
  printf '%-27s %-6s %-8s %-7s %s\n' SERVICE PORT PID HEALTH JAR
  for svc in "${ALL_SERVICES[@]}"; do
    port="$(port_of "${svc}")"; pid="$(listener_pid "${port}")"
    printf '%-27s %-6s %-8s %-7s %s\n' "${svc}" "${port}" "${pid:--}" "$(health_code "${port}")" \
      "$( [[ -n "${pid}" ]] && tr '\0' '\n' < "/proc/${pid}/cmdline" | grep -m1 '\.jar$' || echo -)"
  done
}

build() {
  local list; list="$(printf 'services/%s,' "$@")"
  # Pin the build JDK too: compiling under a newer javac than 25 yields broken MapStruct/Lombok
  # output (e.g. mapper impls that no longer implement their interface). `clean` drops classes
  # compiled by a different JDK, which incremental compilation would otherwise keep.
  local java_home; java_home="$(dirname "$(dirname "${JAVA_BIN}")")"
  ( cd "${BACKEND}" && JAVA_HOME="${java_home}" PATH="${java_home}/bin:${PATH}" \
      ./mvnw -B -DskipTests -pl "${list%,}" -am clean package )
}

reverse() { local i; for (( i=$#; i>0; i-- )); do echo "${!i}"; done; }

main() {
  local cmd="${1:-status}"; shift || true
  local svcs=("$@"); (( ${#svcs[@]} )) || svcs=("${ALL_SERVICES[@]}")
  for s in "${svcs[@]}"; do port_of "${s}" > /dev/null; done
  case "${cmd}" in
    status) status ;;
    build) build "${svcs[@]}" ;;
    start) resolve_db_password; echo "Starting:"; for s in "${svcs[@]}"; do start_one "${s}"; done ;;
    stop) echo "Stopping:"; while read -r s; do stop_one "${s}"; done < <(reverse "${svcs[@]}") ;;
    restart)
      resolve_db_password
      echo "Stopping:"; while read -r s; do stop_one "${s}"; done < <(reverse "${svcs[@]}")
      echo "Starting:"; for s in "${svcs[@]}"; do start_one "${s}"; done ;;
    *) echo "Usage: $0 {status|start|stop|restart|build} [service...]" >&2; exit 2 ;;
  esac
}

main "$@"
