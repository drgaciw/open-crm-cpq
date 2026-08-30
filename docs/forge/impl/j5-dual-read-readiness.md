---
status: done
id: j5-dual-read-readiness
---

# j5-dual-read-readiness

## Summary

Wave J5 (docs-only): dual-read **cutover readiness** checklist for quote-service legacy local `approval_requests` vs approval-routing. Builds on H7 flag-off proof.

**Production default remains `true`.** Wave J does **not** flip `approvals.legacy-local-read-enabled`. Do **not** change `application.yml`. Flip is a separate **HIGH** ops gate after this checklist is evidenced.

## Current state (runtime)

| Item | Value |
|------|-------|
| Config key | `approvals.legacy-local-read-enabled` |
| Env override | `APPROVALS_LEGACY_LOCAL_READ` |
| Default in `application.yml` | `${APPROVALS_LEGACY_LOCAL_READ:true}` — **must stay true** |
| Write path (G4) | Single-writer to approval-routing; no local `ApprovalRequest` insert on submit |
| Read path | Prefer routing; when flag `true`, merge legacy local PENDING / history |
| H7 evidence | `ApprovalServiceSingleWriterTest.getPendingWithLegacyFlagOffDoesNotReadLocal` |

## Cutover checklist (ops — future HIGH gate)

Use this list **before** any env or default flip to `false`. Owner + timestamp each gate.

### A. Empty local PENDING (hard gate)

Legacy local-only PENDING rows become invisible once the flag is off. Confirm the quote schema has **zero** live local PENDING approvals:

```sql
-- quote-service schema (default_schema: quote)
SELECT COUNT(*) AS local_pending
FROM quote.approval_requests
WHERE status = 'PENDING'
  AND is_deleted = FALSE;
-- Expect: 0
```

Optional inventory (do not flip while any row remains):

```sql
SELECT id, quote_id, tier, requested_by, requested_at
FROM quote.approval_requests
WHERE status = 'PENDING'
  AND is_deleted = FALSE
ORDER BY requested_at;
```

- [ ] Count is **0** in every target environment (staging, then prod)
- [ ] Any leftover local PENDING either completed via BFF approve/reject (legacy-on-404 path) or explicitly migrated / closed with DBA + product owner sign-off
- [ ] Snapshot of count + query time recorded in the change ticket

### B. Flag-off soak (env only — no YAML default change)

Prove routing-only reads in a non-prod / canary window **without** changing the checked-in default:

1. Set runtime env only: `APPROVALS_LEGACY_LOCAL_READ=false`
2. Redeploy / restart quote-service so the property binds
3. Confirm `application.yml` default expression is still `:true` (repo unchanged)
4. Smoke pending/approve/reject via quote BFF paths:
   - `GET /api/v1/quotes/approvals/pending`
   - `GET /api/v1/quotes/{quoteId}/approvals`
   - submit → approve/reject happy path against routing IDs
5. Soak window (suggested **24–48h** staging; longer if prod canary): watch for missing pending rows, 404 on approve/reject of pre-cutover IDs, agent 011 regressions
6. Keep H7 unit coverage green in CI (`getPendingWithLegacyFlagOffDoesNotReadLocal`)

- [ ] Staging soak complete with no local-only PENDING regressions
- [ ] Canary / prod soak plan approved (duration + rollback owner)
- [ ] **No** PR that changes `${APPROVALS_LEGACY_LOCAL_READ:true}` → `false` without PM HIGH gate

### C. Rollback

If pending queues shrink unexpectedly or pre-cutover IDs 404:

1. Re-enable legacy dual-read via env: `APPROVALS_LEGACY_LOCAL_READ=true` (or unset so YAML default `true` applies)
2. Restart quote-service; confirm dual-read merge resumes
3. Do **not** delete `quote.approval_requests` as part of rollback
4. File incident notes: env value before/after, time window, affected quote/approval IDs

- [ ] Rollback owner named before soak starts
- [ ] Rollback drill documented (staging: flip false → true and re-verify pending merge)

### D. Post-cutover (out of Wave J — do not run in forge)

Only after A–C succeed and PM authorizes the **HIGH** flip:

- [ ] Decide whether to change the YAML default (separate ADR/PR) or keep env-only forever
- [ ] Optionally deprecate / later drop local `approval_requests` table — **not** part of this readiness wave
- [ ] Matrix note updated that dual-read fallback is off in that environment

## Explicit non-goals (Wave J5)

- No Java / probe endpoint in quote-service
- No `application.yml` default flip
- No deletion of `approval_requests`
- No production env flip in this forge PR

## Matrix / ops pointers

- Contract matrix §6: dual-read + cutover gate → this note
- Prior prep: `docs/forge/impl/h7-dual-read-prep.md`
- BFF policy: `docs/forge/impl/g4-approval-bff.md`

## Acceptance (J5)

- [x] Checklist covers empty local PENDING, flag-off soak, rollback
- [x] Default `approvals.legacy-local-read-enabled` remains **true** (no YAML edit)
- [x] Docs-only; H7 tests unchanged / remain the flag-off proof
- [x] Matrix/ops notes point at this checklist
