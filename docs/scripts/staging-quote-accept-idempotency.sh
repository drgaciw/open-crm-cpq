#!/usr/bin/env bash
# Staging helper: quote accept → deal WON, then duplicate Redis publish idempotency check.
# See staging-quote-accept-idempotency.md for full runbook and semantics.
#
# Usage (local compose example):
#   GATEWAY_BASE=http://localhost:8080 \
#   DEAL_ID=1 QUOTE_ID=42 \
#   REDIS_CLI='docker compose -f backend/docker-compose.yml exec -T redis redis-cli' \
#   ./docs/scripts/staging-quote-accept-idempotency.sh
#
# Env (required unless noted):
#   GATEWAY_BASE   default http://localhost:8080
#   DEAL_ID        CRM deal numeric id (for GET/assert via gateway)
#   QUOTE_ID       Quote numeric id to POST …/accept (skip accept with SKIP_ACCEPT=1)
#   TOKEN          optional Bearer
#   REDIS_CLI      command prefix that speaks redis-cli (default: redis-cli)
#   SKIP_ACCEPT=1  only run duplicate republish + deal assert (quote already accepted)
#   PAYLOAD_FILE   optional path to exact QuoteAcceptedCrmMessage JSON for PUBLISH
#   QUOTE_UUID     used when building PAYLOAD (preferred dedupe key)
#   QUOTE_NUMBER   fallback dedupe / logging
#   TOTAL_AMOUNT  default 25000.50
#   CURRENCY       default EUR
#   MUTATE_AMOUNT  if set (e.g. 999.99), PUT deal amount after first win before duplicate
set -euo pipefail

GATEWAY_BASE="${GATEWAY_BASE:-http://localhost:8080}"
GATEWAY_BASE="${GATEWAY_BASE%/}"
DEAL_ID="${DEAL_ID:?Set DEAL_ID to the CRM deal numeric id}"
QUOTE_ID="${QUOTE_ID:-}"
TOKEN="${TOKEN:-}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
SKIP_ACCEPT="${SKIP_ACCEPT:-0}"
PAYLOAD_FILE="${PAYLOAD_FILE:-}"
QUOTE_UUID="${QUOTE_UUID:-}"
QUOTE_NUMBER="${QUOTE_NUMBER:-Q-SMOKE}"
TOTAL_AMOUNT="${TOTAL_AMOUNT:-25000.50}"
CURRENCY="${CURRENCY:-EUR}"
MUTATE_AMOUNT="${MUTATE_AMOUNT:-}"
ACCEPTED_AT="${ACCEPTED_AT:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

auth_args=()
if [[ -n "$TOKEN" ]]; then
  auth_args=(-H "Authorization: Bearer ${TOKEN}")
fi

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

need_cmd curl
need_cmd jq

http_json() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local uri="${GATEWAY_BASE}${path}"
  local args=(-sS -X "$method" -H 'Accept: application/json' -H 'Content-Type: application/json')
  args+=("${auth_args[@]}")
  if [[ -n "$data" ]]; then
    args+=(-d "$data")
  fi
  curl "${args[@]}" "$uri"
}

echo "=== Quote accept → WON + idempotency (${GATEWAY_BASE}) ==="

deal_before="$(http_json GET "/api/v1/deals/${DEAL_ID}")"
deal_uuid="$(echo "$deal_before" | jq -r '.data.uuid // empty')"
if [[ -z "$deal_uuid" || "$deal_uuid" == "null" ]]; then
  echo "FAIL: could not read deal uuid from GET /api/v1/deals/${DEAL_ID}" >&2
  echo "$deal_before" | head -c 500 >&2
  exit 1
fi
echo "Deal id=${DEAL_ID} uuid=${deal_uuid} stage=$(echo "$deal_before" | jq -r '.data.stage')"

if [[ "$SKIP_ACCEPT" != "1" ]]; then
  if [[ -z "$QUOTE_ID" ]]; then
    echo "Set QUOTE_ID or SKIP_ACCEPT=1" >&2
    exit 1
  fi
  echo "POST /api/v1/quotes/${QUOTE_ID}/accept"
  accept_body="$(http_json POST "/api/v1/quotes/${QUOTE_ID}/accept")"
  echo "$accept_body" | jq -c '{success: .success, message: .message, status: .data.status, quoteUuid: .data.uuid, dealExternalId: .data.dealExternalId}' 2>/dev/null \
    || echo "$accept_body" | head -c 400
  # Prefer quote uuid from accept response when present
  if [[ -z "$QUOTE_UUID" ]]; then
    QUOTE_UUID="$(echo "$accept_body" | jq -r '.data.uuid // empty')"
  fi
  sleep 2
fi

await_won() {
  local i stage
  for i in $(seq 1 20); do
    stage="$(http_json GET "/api/v1/deals/${DEAL_ID}" | jq -r '.data.stage // empty')"
    if [[ "$stage" == "WON" ]]; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

if ! await_won; then
  echo "FAIL: deal ${DEAL_ID} did not reach WON after accept (is Redis + crm listener up? dealExternalId set?)" >&2
  http_json GET "/api/v1/deals/${DEAL_ID}" | jq . >&2 || true
  exit 1
fi

won_json="$(http_json GET "/api/v1/deals/${DEAL_ID}")"
amount_after_win="$(echo "$won_json" | jq -r '.data.amount')"
currency_after_win="$(echo "$won_json" | jq -r '.data.currency')"
version_after_win="$(echo "$won_json" | jq -r '.data.version // empty')"
echo "WON ok — amount=${amount_after_win} currency=${currency_after_win} version=${version_after_win}"

echo "--- Redis processed keys ---"
# shellcheck disable=SC2086
eval $REDIS_CLI KEYS 'crm:quote-accepted:processed:*' || {
  echo "WARN: REDIS_CLI failed; continue with PUBLISH attempt" >&2
}

if [[ -n "$MUTATE_AMOUNT" ]]; then
  echo "Mutating deal amount to ${MUTATE_AMOUNT} so a non-idempotent re-apply would be visible"
  # Minimal PUT body — staging may require fuller DTO; adjust if API rejects.
  put_body="$(echo "$won_json" | jq --argjson amt "$MUTATE_AMOUNT" '.data | {name, stage, amount: $amt, currency, probability, expectedCloseDate, organizationId, contactId, leadId}')"
  http_json PUT "/api/v1/deals/${DEAL_ID}" "$put_body" | jq -c '{stage: .data.stage, amount: .data.amount}' || true
  amount_after_win="$MUTATE_AMOUNT"
  sleep 1
fi

if [[ -n "$PAYLOAD_FILE" ]]; then
  payload="$(cat "$PAYLOAD_FILE")"
else
  if [[ -z "$QUOTE_UUID" ]]; then
    QUOTE_UUID="smoke-$(date +%s)"
    echo "WARN: QUOTE_UUID unset — using ${QUOTE_UUID}; prefer real quote uuid for key match" >&2
  fi
  payload="$(jq -nc \
    --arg deal "$deal_uuid" \
    --arg qu "$QUOTE_UUID" \
    --arg qn "$QUOTE_NUMBER" \
    --arg cur "$CURRENCY" \
    --arg at "$ACCEPTED_AT" \
    --argjson amt "$TOTAL_AMOUNT" \
    '{schemaVersion:"QuoteAccepted.v1",dealExternalId:$deal,quoteUuid:$qu,quoteNumber:$qn,totalAmount:$amt,currency:$cur,acceptedAt:$at}')"
fi

echo "PUBLISH quote:accepted (duplicate delivery)"
# redis-cli -x reads the last argument from stdin (safe for JSON)
# shellcheck disable=SC2086
if ! printf '%s' "$payload" | eval $REDIS_CLI -x PUBLISH quote:accepted; then
  echo "FAIL: REDIS_CLI PUBLISH failed (check REDIS_CLI / redis reachability)" >&2
  exit 1
fi

# Give listener time; non-idempotent apply would overwrite amount
sleep 2
after_dup="$(http_json GET "/api/v1/deals/${DEAL_ID}")"
amount_dup="$(echo "$after_dup" | jq -r '.data.amount')"
currency_dup="$(echo "$after_dup" | jq -r '.data.currency')"
stage_dup="$(echo "$after_dup" | jq -r '.data.stage')"

echo "After duplicate — stage=${stage_dup} amount=${amount_dup} currency=${currency_dup}"

ok=1
if [[ "$stage_dup" != "WON" ]]; then
  echo "FAIL: stage is ${stage_dup}, expected WON" >&2
  ok=0
fi
# Numeric compare (API may return amount as number or string)
if ! jq -ne --arg a "$amount_after_win" --arg b "$amount_dup" '($a | tonumber) == ($b | tonumber)' >/dev/null 2>&1; then
  echo "FAIL: amount changed after duplicate (${amount_after_win} → ${amount_dup}) — not idempotent" >&2
  ok=0
fi
if [[ "$currency_dup" != "$currency_after_win" ]]; then
  echo "FAIL: currency changed after duplicate (${currency_after_win} → ${currency_dup})" >&2
  ok=0
fi

echo
if [[ "$ok" -ne 1 ]]; then
  echo "FAILED — see docs/scripts/staging-quote-accept-idempotency.md" >&2
  exit 1
fi

echo "OK — deal WON and duplicate Redis publish did not re-apply totals."
echo "Confirm INFO log: Duplicate quote acceptance for key …, skipping"
echo "Confirm key: crm:quote-accepted:processed:<quoteUuid>"
exit 0
