#!/usr/bin/env bash
# CI SSOT stage: frontend-uat
# Boots isolated Postgres 16 + Redis 7 (unique compose project), crm-service,
# gateway-service (dev profile, Java 25), and Angular `nx serve crm`, waits for
# health, runs Playwright CDK UX UAT (frontend/e2e/uat-cdk-ux.mjs), and tears
# down all processes and containers in an EXIT trap.
#
# All ports are env-overridable (defaults match standard local/CI ports):
#   UAT_UI_PORT   (default 4200)
#   GATEWAY_PORT  (default 8080)
#   CRM_PORT      (default 8081)
#   PG_PORT       (default 5432)
#   REDIS_PORT    (default 6379)
#
# Set UAT_DRY_RUN=1 to print resolved port/env wiring and exit 0 without booting.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/ci/common.sh
source "${SCRIPT_DIR}/common.sh"

REPO_ROOT="$(ci_repo_root)"
BACKEND_DIR="$(ci_backend_dir)"
FRONTEND_DIR="$(ci_frontend_dir)"

UAT_UI_PORT="${UAT_UI_PORT:-4200}"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
CRM_PORT="${CRM_PORT:-8081}"
PG_PORT="${PG_PORT:-5432}"
REDIS_PORT="${REDIS_PORT:-6379}"

UAT_BASE_URL="${UAT_BASE_URL:-http://localhost:${UAT_UI_PORT}}"
CRM_SERVICE_URI="${CRM_SERVICE_URI:-http://localhost:${CRM_PORT}}"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:${UAT_UI_PORT},http://127.0.0.1:${UAT_UI_PORT}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${UAT_UI_PORT}}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${PG_PORT}"
DB_NAME="${DB_NAME:-opencrm}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=crm"
UAT_ARTIFACT_DIR="${UAT_ARTIFACT_DIR:-${REPO_ROOT}/ci-reports/frontend-uat}"
UAT_RUN_ID="${UAT_RUN_ID:-$$-$(date +%s)}"
COMPOSE_PROJECT="opencrm-uat-${UAT_RUN_ID}"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/opencrm-frontend-uat.XXXXXX")"
COMPOSE_FILE="${TMP_DIR}/docker-compose.uat.yml"
PROXY_FILE="${TMP_DIR}/proxy.conf.json"
GATEWAY_CFG_FILE="${TMP_DIR}/gateway-uat.yml"

CRM_PID=""
GATEWAY_PID=""
UI_PID=""
COMPOSE_UP=0

cat >"${PROXY_FILE}" <<EOF
{
  "/api": {
    "target": "http://localhost:${GATEWAY_PORT}",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "warn"
  },
  "/oauth2": {
    "target": "http://localhost:${GATEWAY_PORT}",
    "secure": false,
    "changeOrigin": false,
    "logLevel": "warn"
  },
  "/login/oauth2": {
    "target": "http://localhost:${GATEWAY_PORT}",
    "secure": false,
    "changeOrigin": false,
    "logLevel": "warn"
  },
  "/logout": {
    "target": "http://localhost:${GATEWAY_PORT}",
    "secure": false,
    "changeOrigin": false,
    "logLevel": "warn"
  }
}
EOF

if [[ "${UAT_DRY_RUN:-0}" == "1" ]]; then
  echo "UAT_UI_PORT=${UAT_UI_PORT}"
  echo "GATEWAY_PORT=${GATEWAY_PORT}"
  echo "CRM_PORT=${CRM_PORT}"
  echo "PG_PORT=${PG_PORT}"
  echo "REDIS_PORT=${REDIS_PORT}"
  echo "UAT_BASE_URL=${UAT_BASE_URL}"
  echo "CRM_SERVICE_URI=${CRM_SERVICE_URI}"
  echo "CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS}"
  echo "FRONTEND_URL=${FRONTEND_URL}"
  echo "DB_HOST=${DB_HOST}"
  echo "DB_PORT=${DB_PORT}"
  echo "SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}"
  echo "COMPOSE_PROJECT=${COMPOSE_PROJECT}"
  echo "PROXY_CONFIG_JSON=$(tr -d '\n' <"${PROXY_FILE}")"
  rm -rf "${TMP_DIR}"
  exit 0
fi

kill_tree() {
  local pid="$1"
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then
    return 0
  fi
  local children
  children="$(pgrep -P "${pid}" 2>/dev/null || true)"
  for child in ${children}; do
    kill_tree "${child}"
  done
  kill -TERM "${pid}" 2>/dev/null || true
}

wait_dead() {
  local pid="$1"
  if [[ -z "${pid}" ]]; then
    return 0
  fi
  for _ in $(seq 1 20); do
    if ! kill -0 "${pid}" 2>/dev/null; then
      return 0
    fi
    sleep 0.25
  done
  local children
  children="$(pgrep -P "${pid}" 2>/dev/null || true)"
  for child in ${children}; do
    kill -KILL "${child}" 2>/dev/null || true
  done
  kill -KILL "${pid}" 2>/dev/null || true
}

cleanup() {
  local exit_code=$?
  set +e
  echo "=== frontend-uat: tearing down isolated stack (${COMPOSE_PROJECT}) ==="
  kill_tree "${UI_PID}"
  kill_tree "${GATEWAY_PID}"
  kill_tree "${CRM_PID}"
  wait_dead "${UI_PID}"
  wait_dead "${GATEWAY_PID}"
  wait_dead "${CRM_PID}"
  if [[ "${COMPOSE_UP}" == "1" ]]; then
    docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -rf "${TMP_DIR}"
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

wait_http() {
  local name="$1"
  local url="$2"
  local max_wait="${3:-120}"
  local log_file="${4:-}"
  local pid="${5:-}"
  local elapsed=0
  while (( elapsed < max_wait )); do
    if curl -fsS --max-time 3 "${url}" >/dev/null 2>&1; then
      echo "=== frontend-uat: ${name} ready (${url}) after ${elapsed}s ==="
      return 0
    fi
    if [[ -n "${pid}" ]] && ! kill -0 "${pid}" 2>/dev/null; then
      echo "ERROR: ${name} (pid ${pid}) exited before becoming ready at ${url}" >&2
      if [[ -n "${log_file}" && -f "${log_file}" ]]; then
        echo "--- tail ${log_file} ---" >&2
        tail -n 60 "${log_file}" >&2 || true
      fi
      return 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "ERROR: ${name} did not become ready at ${url} within ${max_wait}s" >&2
  if [[ -n "${log_file}" && -f "${log_file}" ]]; then
    echo "--- tail ${log_file} ---" >&2
    tail -n 60 "${log_file}" >&2 || true
  fi
  return 1
}

# Ensure Java 25 (Temurin) is selected when available (Cursor Cloud + local mise).
if ! java -version 2>&1 | grep -q '"25\.'; then
  for candidate in \
    "/usr/lib/jvm/temurin-25-jdk-amd64" \
    "${HOME}/.local/share/mise/installs/java/25"; do
    if [[ -x "${candidate}/bin/java" ]]; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      break
    fi
  done
fi

ci_require_cmd java
ci_require_cmd node
ci_require_cmd npm
ci_require_cmd docker
ci_require_cmd curl

mkdir -p "${UAT_ARTIFACT_DIR}"

CRM_JAR="${BACKEND_DIR}/services/crm-service/target/crm-service-1.0.0-SNAPSHOT.jar"
GATEWAY_JAR="${BACKEND_DIR}/services/gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar"

if [[ "${UAT_SKIP_BACKEND_BUILD:-0}" != "1" ]] && {
  [[ "${UAT_REBUILD_BACKEND:-0}" == "1" ]] ||
  [[ "${CI:-}" == "true" ]] ||
  [[ ! -f "${CRM_JAR}" ]] ||
  [[ ! -f "${GATEWAY_JAR}" ]]
}; then
  echo "=== frontend-uat: building crm-service + gateway-service jars ==="
  ci_mvn -B -DskipTests -Djacoco.skip=true install -pl services/crm-service,services/gateway-service -am
fi

cat >"${COMPOSE_FILE}" <<EOF
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: ${DB_NAME}
    ports:
      - "127.0.0.1:${PG_PORT}:5432"
    volumes:
      - "${BACKEND_DIR}/scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME} -d ${DB_NAME}"]
      interval: 2s
      timeout: 3s
      retries: 30

  redis:
    image: redis:7-alpine
    ports:
      - "127.0.0.1:${REDIS_PORT}:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 2s
      timeout: 3s
      retries: 30
EOF

echo "=== frontend-uat: starting isolated Postgres (${PG_PORT}) + Redis (${REDIS_PORT}) ==="
docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" up -d --wait
COMPOSE_UP=1

echo "=== frontend-uat: starting crm-service on :${CRM_PORT} ==="
DB_HOST="${DB_HOST}" \
DB_PORT="${DB_PORT}" \
DB_NAME="${DB_NAME}" \
DB_USERNAME="${DB_USERNAME}" \
DB_PASSWORD="${DB_PASSWORD}" \
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}" \
java -Xmx512m -jar "${CRM_JAR}" \
  --spring.profiles.active=dev \
  --server.port="${CRM_PORT}" \
  --spring.data.redis.host=127.0.0.1 \
  --spring.data.redis.port="${REDIS_PORT}" \
  >"${UAT_ARTIFACT_DIR}/crm-service.log" 2>&1 </dev/null &
CRM_PID=$!

cat >"${GATEWAY_CFG_FILE}" <<EOF
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: crm-service
              uri: ${CRM_SERVICE_URI}
              predicates:
                - Path=/api/v1/leads/**,/api/v1/deals/**,/api/v1/contacts/**,/api/v1/organizations/**,/api/v1/playbooks/**,/api/v1/guided-sessions/**
            - id: pricing-service
              uri: \${PRICING_SERVICE_URI:http://localhost:8082}
              predicates:
                - Path=/api/v1/pricing/**,/api/v1/pricing-customers/**,/api/v1/rule-sets/**,/api/v1/rules/**
            - id: product-service
              uri: \${PRODUCT_SERVICE_URI:http://localhost:8083}
              predicates:
                - Path=/api/v1/products/**,/api/v1/product-bundles/**,/api/v1/product-options/**,/api/v1/product-attributes/**,/api/v1/configuration/**
            - id: quote-service
              uri: \${QUOTE_SERVICE_URI:http://localhost:8084}
              predicates:
                - Path=/api/v1/quotes/**
            - id: approval-routing
              uri: \${APPROVAL_ROUTING_URI:http://localhost:8085}
              predicates:
                - Path=/api/v1/approvals/**
            - id: pricebook-service
              uri: \${PRICEBOOK_SERVICE_URI:http://localhost:8089}
              predicates:
                - Path=/api/v1/price-books/**
EOF

echo "=== frontend-uat: starting gateway-service on :${GATEWAY_PORT} ==="
CRM_SERVICE_URI="${CRM_SERVICE_URI}" \
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS}" \
FRONTEND_URL="${FRONTEND_URL}" \
REDIS_HOST="127.0.0.1" \
REDIS_PORT="${REDIS_PORT}" \
java -Xmx512m -jar "${GATEWAY_JAR}" \
  --spring.profiles.active=dev \
  --server.port="${GATEWAY_PORT}" \
  --spring.data.redis.host=127.0.0.1 \
  --spring.data.redis.port="${REDIS_PORT}" \
  --spring.config.additional-location="file:${GATEWAY_CFG_FILE}" \
  >"${UAT_ARTIFACT_DIR}/gateway-service.log" 2>&1 </dev/null &
GATEWAY_PID=$!

wait_http "crm-service" "http://localhost:${CRM_PORT}/actuator/health" 120 "${UAT_ARTIFACT_DIR}/crm-service.log" "${CRM_PID}"
wait_http "gateway-service" "http://localhost:${GATEWAY_PORT}/actuator/health" 120 "${UAT_ARTIFACT_DIR}/gateway-service.log" "${GATEWAY_PID}"

echo "=== frontend-uat: starting Angular dev server on :${UAT_UI_PORT} ==="
mkdir -p "${TMP_DIR}/nx-cache/terminalOutputs" "${TMP_DIR}/nx-data"
(
  cd "${FRONTEND_DIR}"
  if [[ ! -d node_modules ]]; then
    npm ci
  fi
  exec env \
    NX_DAEMON=false \
    NX_CACHE_DIRECTORY="${TMP_DIR}/nx-cache" \
    NX_WORKSPACE_DATA_DIRECTORY="${TMP_DIR}/nx-data" \
    npx nx serve crm \
      --host=127.0.0.1 \
      --port="${UAT_UI_PORT}" \
      --proxyConfig="${PROXY_FILE}" \
      --skip-nx-cache
) >"${UAT_ARTIFACT_DIR}/nx-serve.log" 2>&1 </dev/null &
UI_PID=$!

wait_http "Angular UI" "${UAT_BASE_URL}" 180 "${UAT_ARTIFACT_DIR}/nx-serve.log" "${UI_PID}"
wait_http "UI /api/v1/csrf proxy" "${UAT_BASE_URL}/api/v1/csrf" 60 "${UAT_ARTIFACT_DIR}/gateway-service.log" "${GATEWAY_PID}"

echo "=== frontend-uat: running Playwright CDK UX UAT against ${UAT_BASE_URL} ==="
(
  cd "${FRONTEND_DIR}"
  UAT_BASE_URL="${UAT_BASE_URL}" \
  UAT_ARTIFACT_DIR="${UAT_ARTIFACT_DIR}" \
  node e2e/uat-cdk-ux.mjs
)
