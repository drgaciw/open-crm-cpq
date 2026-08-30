---
status: done
id: e3-adr-accept
---

# e3-adr-accept

## Summary

Wave E **E3**: Accept `docs/backlog/ADR-configuration-api-reconciliation.md` and freeze S2B-11 open-question defaults. **Documentation only** — no product-service, gateway, or configuration-engine Java changes.

**Plan:** [`docs/forge/14-wave-e-architecture.md`](../14-wave-e-architecture.md) (E3 row)

## Intent

| Item | Outcome |
|------|---------|
| ADR Status | **Accepted** (2026-08-30) |
| Open questions | Frozen to Wave E defaults (table below) |
| S2B-11 implementation | **Deferred** — not scheduled in this PR |
| Runtime validate | Unchanged (`List<Long>` in / `List<String>` errors out per contract matrix) |

## Frozen open-question defaults

| # | Default |
|---|---------|
| 1 | Prefer `POST /internal/v1/configurations/evaluate` **stateless** on configuration-engine |
| 2 | Shared DTO in `backend/libs/common` |
| 3 | Sync REST rule fetch first (cache / event invalidation remains S2B-9) |
| 4 | Retain product-service validate until contract-test parity — **no removal date** until S2B-11 lands |

## Files

| Path | Change |
|------|--------|
| `docs/backlog/ADR-configuration-api-reconciliation.md` | Status → Accepted; freeze open questions; note S2B-11 deferred; diagram/migration notes aligned to defaults |
| `docs/forge/impl/e3-adr-accept.md` | This status note (`status: done`) |

## Out of scope (explicit)

- Gateway validate compatibility shim / route adapter
- `{ valid, violations[] }` response-shape rewrite
- `POST /internal/v1/configurations/evaluate` endpoint implementation
- Shared DTO Java types in `backend/libs/common`
- product-service validate removal or internal-only redirect
- Any other Wave E panes (E1 CSRF ADR, E2 SecurityConfig align, E4 GitHub hygiene)

## Acceptance

- [x] ADR **Status: Accepted**
- [x] Open questions answered with Wave E frozen defaults
- [x] S2B-11 implementation explicitly deferred
- [x] **No** product/gateway/configuration-engine Java changes
- [x] Contract matrix still lists current `List<String>` validate shape (unchanged this PR)
- [x] This forge status note `status: done`

## Verify

```bash
# Docs-only: no Java compile required
rg -n '^\*\*Status:\*\*' docs/backlog/ADR-configuration-api-reconciliation.md
# Expect: Status: Accepted

# Confirm no Java touched in this worktree commit
git diff --name-only origin/main...HEAD | rg '\.java$' || echo 'OK: no Java files'
```
