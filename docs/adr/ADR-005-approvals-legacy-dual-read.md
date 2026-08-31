# ADR-005: Approvals legacy dual-read default remains `true`

**Status:** Proposed  
**Date:** 2026-08-31  
**Deciders:** (not accepted — docs freeze only)  
**Does not authorize:** flipping `approvals.legacy-local-read-enabled` YAML or env in this PR  
**Related:** Wave G4 BFF single-writer; H7 flag-off unit proof; J5 cutover checklist; Waves K–N holds

---

## Context

Quote-service BFF reads pending/history approvals. Canonical **write** path (G4) is single-writer to **approval-routing**. When `approvals.legacy-local-read-enabled` is `true`, reads **merge** leftover local `quote.approval_requests` rows (PENDING / history) with routing.

Runtime (`backend/services/quote-service/src/main/resources/application.yml`):

```yaml
approvals:
  legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

Flipping the **default** to `false` is a **HIGH** dual-store cutover: local-only PENDING rows become invisible; pre-cutover IDs can 404 on approve/reject. Forge waves J–N **held** this flip. Wave J5 documented the ops gate and **did not** change YAML.

FE approval inbox (`@frontend/cpq-approvals`, Wave K4) calls quote BFF only — it must not read or flip this flag.

---

## Decision (Proposed — not Accepted)

1. **Checked-in default stays `true`** until a **separate HIGH** ops PR is PM-authorized after the J5 checklist is evidenced.
2. **J5 is the gate**, not a forge wave: empty local PENDING → env-only `APPROVALS_LEGACY_LOCAL_READ=false` soak (YAML expression remains `:true`) → named rollback (`true` / unset) → then optionally change the YAML default.
3. This ADR **must not** be Accepted as “flip now.” Accepting it later would mean “cutover procedure is the contract,” not “default is already false.”

### Rejected

| Option | Why not |
|--------|---------|
| Flip YAML default in this review / Wave N | **HIGH**; J5 A–C not evidenced in prod/staging here |
| Delete `quote.approval_requests` | Out of scope; breaks rollback |
| FE toggle | Dual-read is server-side BFF only |

---

## J5 checklist (pointer — do not duplicate as complete)

Authoritative steps: [`docs/forge/impl/j5-dual-read-readiness.md`](../forge/impl/j5-dual-read-readiness.md).

| Gate | Meaning |
|------|---------|
| A. Empty local PENDING | `SELECT COUNT(*) … status = 'PENDING' AND is_deleted = FALSE` → **0** in target env |
| B. Env soak | `APPROVALS_LEGACY_LOCAL_READ=false` only; **no** YAML default change; H7 test remains green |
| C. Rollback | Re-enable `true` / unset; do not drop the table |
| D. Post-cutover | Separate ADR/PR to change `:true` → `:false` **after** PM HIGH gate |

---

## Consequences

- **Positive:** Prevents accidental HIGH cutover in forge PRs; single place to cite the hold.
- **Negative:** Dual-read remains on in production default until ops completes J5.
- **Residual:** GitNexus on the YAML key is not a Java symbol — confirm with `rg`. Do not treat empty FE callers as “safe to flip.”

## Explicit non-goals

- Do not edit `application.yml` in docs/ADR PRs.
- Do not implement Activities API, #238, #241, product-validate deletion, gateway domain validation, WhatsApp #430 undo, Kafka S2B-9, NgRx, contract-asset FE.

## References

- `docs/forge/impl/g4-approval-bff.md` — single-writer  
- `docs/forge/impl/h7-dual-read-prep.md` — flag-off unit proof  
- `docs/forge/impl/j5-dual-read-readiness.md` — cutover checklist  
- `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §6 — dual-read note  
