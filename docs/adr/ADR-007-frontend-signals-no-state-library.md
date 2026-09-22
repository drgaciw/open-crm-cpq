# ADR-007: Frontend state — Angular signals + HttpClient services (no state library)

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — the decision was implemented first, this record documents it; it was never separately minuted)*
**Date (decision embedded):** ~2026-08 (forge waves G–P) · **Documented:** 2026-09-19
**Deciders:** forge waves (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR — describes what `main` already does. Not a new direction; does **not** un-hold NgRx for new work.

---

## Context

The Angular CRM/CPQ SPA (`frontend/apps/crm`, Nx workspace) needed a state strategy for CPQ feature libraries (catalog, quotes, approvals, price-books, pricing-customers, rule-sets, product-bundles, simulator). The original modernization plan (Phase 2, `AGENT.md` role tables, `MODERNIZATION_PLAN.md`) called for **NgRx 19+** store management.

The landed code diverged from that plan. Verified on the tree (2026-09-19):

- `frontend/package.json` has **no `@ngrx/*` dependency** (any version).
- Feature state is implemented as **injectable HttpClient services holding Angular `signal()` state** (the "Deal-style" pattern), with `computed()` derivations in components.
- `apps/crm/src/app/layout/shell.component.ts:104` — `readonly sidenavOpened = signal(true);` (signals in the shell).
- AGENT.md now codifies: "**Signals + HttpClient services** (Deal-style) for CPQ — do not introduce NgRx until an Accepted ADR (forge **holds** NgRx)."

## Decision

**Use Angular signals + stateful HttpClient service classes as the only state-management pattern. No NgRx (or any external state library) in the workspace.**

1. Smart (container) components call injectable data services; services own `signal()` state, expose `computed()`/readonly views, and perform `HttpClient` calls against the **gateway only** (`/api/**`).
2. Presentational (dumb) components receive inputs/outputs; OnPush everywhere; standalone components (no NgModules).
3. Lazy-loaded feature libraries via `loadChildren` in `app.routes.ts` — at most one new portal per authorized wave may own routes/sidebar.
4. Testing: Vitest + `TestBed` + `vi.fn()` (Analog `setupTestBed`) — not Jasmine spies.
5. Introducing NgRx requires a **new, explicitly Accepted** ADR and remains on the holds list.

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Signals + HttpClient services (landed)** | Zero new dependencies; fine-grained reactivity native to Angular 22; small API surface per CPQ lib; matches team's strict-TS conventions. Con: no devtools/store middleware; cross-lib shared state must be designed deliberately (`libs/shared/data-access`). |
| B. NgRx 19+ store (original plan) | Powerful for large shared state, but heavy boilerplate for list/detail CPQ screens and a second mental model alongside signals; plan-stage choice, never landed. **Held.** |
| C. Component-rxjs services only (`async` pipe) | Landed convention still prefers `async` pipe *within* RxJS flows, but as sole state strategy it loses signal-based change tracking. Superseded by A. |

## Consequences

- **Positive:** No state-library upgrade churn across Angular majors; bundle stays lean; signals map 1:1 to template bindings; strict typing survives (no `any` store actions).
- **Negative / residual:** No time-travel devtools; state ownership conventions rely on review discipline; `libs/shared/data-access` must stay the single home for cross-feature DTOs/clients.
- **Governance:** NgRx stays on the holds list in [docs/ARCHITECTURE.md §17](../ARCHITECTURE.md). This ADR documents the landed state; it does **not** accept NgRx or authorize its introduction.

## Verification evidence (2026-09-19)

- `frontend/package.json` — dependencies contain `@angular/*` 22.1.6, rxjs; **no `@ngrx/*`**.
- `frontend/apps/crm/src/app/layout/shell.component.ts:104` — `signal(true)`.
- `frontend/package.json` scripts `test`, `test:crm`, `test:cpq` enumerate the landed Nx projects (10 CPQ + 7 CRM/shared).
- `AGENT.md` "Patterns to follow (landed)" — signals + HttpClient; NgRx held.

## References

- [docs/ARCHITECTURE.md §11](../ARCHITECTURE.md) — Frontend
- [docs/adr/ADR-006-entities-never-at-api-boundary.md](ADR-006-entities-never-at-api-boundary.md) — DTO contracts consumed by these services
- `AGENT.md` — Development conventions (Angular/TypeScript)
