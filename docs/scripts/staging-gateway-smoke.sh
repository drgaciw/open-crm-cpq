#!/usr/bin/env bash
# Quick gateway smoke against CRM routes (staging or local).
# Linux/macOS twin of staging-gateway-smoke.ps1 — see CRM_CUTOVER_CHECKLIST.md.
#
# Usage:
#   ./staging-gateway-smoke.sh
#   GATEWAY_BASE='https://staging-gateway.example' ./staging-gateway-smoke.sh
#   GATEWAY_BASE='http://localhost:8080' TOKEN='eyJ…' ./staging-gateway-smoke.sh
#   CHECK_ERP=1 ./staging-gateway-smoke.sh   # optional: expect ERP stub HTTP 501 (not 404)
#
# Env:
#   GATEWAY_BASE  Base URL (default http://localhost:8080)
#   TOKEN         Optional Bearer token for secured staging gateways
#   CHECK_ERP     Set to 1 to assert GET /api/v1/erp/costs/bulk → 501
#   CURL_OPTS     Extra curl args (optional)
set -euo pipefail

GATEWAY_BASE="${GATEWAY_BASE:-http://localhost:8080}"
GATEWAY_BASE="${GATEWAY_BASE%/}"
TOKEN="${TOKEN:-}"
CHECK_ERP="${CHECK_ERP:-0}"
CURL_OPTS="${CURL_OPTS:-}"

fail=0
pass=0

auth_args=()
if [[ -n "$TOKEN" ]]; then
  auth_args=(-H "Authorization: Bearer ${TOKEN}")
fi

json_get() {
  local path="$1"
  local uri="${GATEWAY_BASE}${path}"
  local code body
  echo "GET ${uri}"
  # shellcheck disable=SC2086
  body="$(curl -sS -o /tmp/opencrm-smoke-body.$$ -w '%{http_code}' \
    -H 'Accept: application/json' \
    "${auth_args[@]}" \
    ${CURL_OPTS} \
    "$uri")" || {
    echo "FAIL  curl error for ${path}" >&2
    fail=$((fail + 1))
    return 0
  }
  code="$body"
  if [[ "$code" =~ ^2 ]]; then
    echo "  OK  HTTP ${code}"
    pass=$((pass + 1))
  else
    echo "FAIL  HTTP ${code} for ${path}" >&2
    head -c 400 /tmp/opencrm-smoke-body.$$ >&2 || true
    echo >&2
    fail=$((fail + 1))
  fi
  rm -f /tmp/opencrm-smoke-body.$$
}

expect_status() {
  local path="$1"
  local want="$2"
  local uri="${GATEWAY_BASE}${path}"
  local code
  echo "GET ${uri} (expect ${want})"
  # shellcheck disable=SC2086
  code="$(curl -sS -o /tmp/opencrm-smoke-body.$$ -w '%{http_code}' \
    -H 'Accept: application/json' \
    "${auth_args[@]}" \
    ${CURL_OPTS} \
    "$uri")" || {
    echo "FAIL  curl error for ${path}" >&2
    fail=$((fail + 1))
    return 0
  }
  if [[ "$code" == "$want" ]]; then
    echo "  OK  HTTP ${code}"
    pass=$((pass + 1))
  else
    echo "FAIL  HTTP ${code} (wanted ${want}) for ${path}" >&2
    head -c 400 /tmp/opencrm-smoke-body.$$ >&2 || true
    echo >&2
    fail=$((fail + 1))
  fi
  rm -f /tmp/opencrm-smoke-body.$$
}

echo "=== CRM gateway smoke (${GATEWAY_BASE}) ==="

# Paths used by frontend/libs/crm via gateway :8080 (not direct :8081)
json_get '/api/v1/deals/pipeline'
json_get '/api/v1/deals?page=0&size=5'
json_get '/api/v1/leads?page=0&size=5'
json_get '/api/v1/contacts?page=0&size=5'
json_get '/api/v1/organizations?page=0&size=5'

# Optional API-only reads (uncomment locally if useful):
# json_get '/api/v1/leads/stats'
# json_get '/api/v1/organizations/1/contacts?page=0&size=5'

if [[ "$CHECK_ERP" == "1" ]]; then
  echo "--- optional ERP stub ---"
  # Reserved agent-contract path; stub must be 501 Not Implemented, not 404
  expect_status '/api/v1/erp/costs/bulk' '501'
fi

echo
if [[ "$fail" -gt 0 ]]; then
  echo "FAILED — ${fail} check(s) failed, ${pass} passed." >&2
  echo "Quote-accept → deal WON + duplicate idempotency: docs/scripts/staging-quote-accept-idempotency.md" >&2
  exit 1
fi

echo "OK — basic CRM reads succeeded (${pass} checks)."
echo "Next: quote accept → deal WON + duplicate publish (docs/scripts/staging-quote-accept-idempotency.md)."
exit 0
