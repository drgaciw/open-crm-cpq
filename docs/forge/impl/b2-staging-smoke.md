---
status: done
id: b2-staging-smoke
wave: B3
branch: docs/forge-b2-staging-smokes
---

# b2-staging-smoke — CRM gateway bash smoke + quote-accept idempotency notes

## status

**done**

## Summary

Wave B3 docs/scripts deliverable: Linux/CI bash twin for CRM gateway read smokes, improved PowerShell twin, and a staging runbook + helper for quote accept → deal WON plus duplicate Redis publish idempotency (cutover checklist G1 / PR-C from track 03).

## Files

| Path | Change |
|------|--------|
| `docs/scripts/staging-gateway-smoke.sh` | **New** — curl CRM GETs via gateway; `GATEWAY_BASE`, `TOKEN`, optional `CHECK_ERP=1` → 501 |
| `docs/scripts/staging-gateway-smoke.ps1` | Improved — `-Token`, `-CheckErp`, fail counting, pointer to idempotency runbook |
| `docs/scripts/staging-quote-accept-idempotency.md` | **New** — accept → WON + duplicate `PUBLISH quote:accepted` runbook |
| `docs/scripts/staging-quote-accept-idempotency.sh` | **New** — helper (`DEAL_ID` / `QUOTE_ID` / `REDIS_CLI`) |
| `docs/CRM_CUTOVER_CHECKLIST.md` | Link bash/ps1 smokes + idempotency runbook in Verification + Commands |
| `docs/CRM_GATEWAY_ENV.md` | Staging smoke script table; INFO duplicate-skip log hint |
| `docs/forge/03-quote-deal-e2e.md` | G1 mitigated notes + script pointers |
| `docs/forge/06-gateway-ci.md` | Staging smoke row updated |
| `docs/forge/impl/b2-staging-smoke.md` | This status note |

## Acceptance

- [x] Bash CRM gateway smoke for leads/deals/contacts/organizations (+ pipeline)
- [x] Quote-accept duplicate idempotency documented (Redis `PUBLISH` path preferred over second HTTP accept)
- [x] Cutover checklist + gateway env link the scripts
- [x] `bash -n` clean on both shell scripts
- [ ] Live staging evidence (operator cutover) — checklist E2E box remains open until run green

## Verify locally (this worktree)

```bash
bash -n docs/scripts/staging-gateway-smoke.sh
bash -n docs/scripts/staging-quote-accept-idempotency.sh
# Against a down port (expects non-zero):
GATEWAY_BASE='http://127.0.0.1:9' ./docs/scripts/staging-gateway-smoke.sh || true
```

Against a live stack:

```bash
GATEWAY_BASE=http://localhost:8080 ./docs/scripts/staging-gateway-smoke.sh
# optional: CHECK_ERP=1 …
# then: DEAL_ID=… QUOTE_ID=… REDIS_CLI='… redis-cli' ./docs/scripts/staging-quote-accept-idempotency.sh
```

## Notes

- GitNexus MCP unavailable; docs/scripts only — no Java symbol edits; blast radius via grep of doc references only (**LOW**).
- Idempotency semantics already hardened in B0 (`b0-quote-idem`); this wave documents staging proof, does not change listener code.
- Kafka `QuoteAcceptedEvent` explicitly out of scope for CRM WON verification.
