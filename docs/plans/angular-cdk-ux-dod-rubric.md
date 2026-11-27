# Angular Material CDK UX/UI Improvements — Definition of Done (DoD) Rubric

> **Pane-map note (re-pinned 2026-11-27):** The `w3:p*` pane ids in this document are from the historical `w3` workspace, which no longer exists (`herdr workspace list` → only `w1`; `w3` → `workspace_not_found`). Live mapping: `pi-00` = `w1:p1`, `pi-01` = `w1:p7`, `agy` = `w1:p3`. `pi-decision` (was `w3:pA`), `architect` (was `w3:pB`) and `codex` (was `w3:p8`) have no live counterpart in this workspace — treat those references as historical. Do not resolve a `w3:p*` id against the current session.

**Owner:** `pi-decision` (`w3:pA`)  
**Plan:** `docs/plans/angular-cdk-ux-improvements-plan.md`  
**Decision Engine:** TypeSafe Jev (`typesafe/jev-1.13` via `frontend/e2e/lib/decide.mjs` + `.env` `OPENROUTER_API_KEY`)  
**Design Contract:** `docs/design/UI-CRM-SLDS-design-contract.md`

---

## Jev-Calibrated Gate Policy

Calibrated via TypeSafe Jev (`typesafe/jev-1.13`) on the plan constraints:

| Gate Policy | Jev Signal | Enforcement |
|---|---|---|
| Full Frontend CI (`./scripts/ci/frontend.sh`, all 20 Nx projects) | `hard_blocking` (`p=0.98, conf=0.96`) | **Blocking** for final DoD sign-off; targeted `npx nx test/lint` allowed per-phase |
| Accessibility (WCAG 2.1 AA: `LiveAnnouncer`, `CdkTrapFocus`, `ActiveDescendantKeyManager`, ARIA) | `noul=0.82` | **Blocking** per phase and final |
| Token Purity (`--crm-*` tokens only; zero new hardcoded palette hex in touched/new SCSS) | `noul=0.93` | **Blocking** per phase and final |
| Regression & Selector Preservation (`data-testid`, Vitest + `vi.fn()`, no Jasmine spies) | `score=2.64/3.0` (Critical 64% / High 36%) | **Blocking** per phase and final |
| Jev Decision Confidence Threshold | `0.99` combined mass on `>=0.85` | `>= 0.85` for per-phase sign-off; `>= 0.90` for final release sign-off |

---

## Global Blocking Gates (Apply to Every Phase)

- **G1 — Design Contract & Token Purity:** All new/modified component styles use `--crm-*` semantic tokens from `frontend/apps/crm/src/styles.scss`. Zero new hardcoded hex colors (`#...`), zero LWC/SLDS vendor CSS imports.
- **G2 — Feature Flag Hold:** No `opencrm.jev.*.enabled` flag is flipped to `true` anywhere in frontend or backend config.
- **G3 — Contract & Selector Preservation:** All existing `data-testid` attributes (`data-table`, `quote-lines-table`, `agent-panel`, `ramp-stale-banner`, `ramp-governance-banner`, `quote-more-actions`, `unconverted-chip`, etc.) and user-facing **Opportunities** copy (not Deals) remain intact.
- **G4 — Additive Shared-UI APIs:** All changes in `frontend/libs/shared/ui` and `frontend/libs/shared/data-access` are strictly additive with backward-compatible defaults.
- **G5 — Vitest & Lint Clean:** Touched Nx projects pass `npx nx test <proj>` and `npx nx lint <proj>` using `TestBed` + `vi.fn()` (zero Jasmine `createSpyObj`/`spyOn`).
- **G6 — Final CI Gate:** Before final DoD sign-off, `./scripts/ci/frontend.sh` exits `0` across all 20 Nx projects (build, lint, test) and `git diff --check` is clean.

---

## Phase-Specific Acceptance Criteria

### Phase 1: Quick-Win Ergonomics (`TextField`, `Clipboard`, `Observers`, `A11y`)
- **P1.1 (`cdkTextareaAutosize`):** Multi-line textareas across CRM & CPQ forms/dialogs and `BulkSimulatorComponent` use `cdkTextareaAutosize` from `@angular/cdk/text-field` with sensible `cdkAutosizeMinRows` / `cdkAutosizeMaxRows`; verified by unit specs.
- **P1.2 (`CopyButtonComponent`):** `app-copy-button` in `frontend/libs/shared/ui/src/lib/copy-button/` uses `@angular/cdk/clipboard` (`Clipboard`) + `@angular/cdk/a11y` (`LiveAnnouncer`), provides accessible `aria-label` and visual copied feedback, and is wired into Quote detail, Catalog detail, Contact detail, Organization detail, and Bulk Simulator.
- **P1.3 (`CdkObserveContent`):** `PageHeaderComponent` uses `cdkObserveContent` (`@angular/cdk/observers`) for reactive `[breadcrumb]`/`[highlights]`/`[search]` slot visibility without stale empty wrappers.
- **P1.4 (`LiveAnnouncer` in `NotificationService`):** `NotificationService` (`frontend/libs/shared/data-access`) announces notifications via `LiveAnnouncer` (`polite` / `assertive` appropriately) alongside snackbar display, covered by Vitest specs.

### Phase 2: Interactive Pipeline (`Drag & Drop`)
- **P2.1 (`DealKanbanComponent`):** Uses `@angular/cdk/drag-drop` (`cdkDropListGroup`, per-stage `cdkDropList`, `cdkDropListEnterPredicate`, `cdkDrag`, `*cdkDragPlaceholder`).
- **P2.2 (Domain Guards & Error Isolation):** `cdkDropListDropped` respects `canAdvanceDeal` / `canCloseDeal`; dropping into `LOST` opens `DealLoseReasonDialogComponent`; dropping into `WON` calls `service.win` and handles `OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE` (or other errors) cleanly **without moving the card on failure**.
- **P2.3 (Keyboard & A11y Parity):** Existing `mat-menu` stage-move actions remain intact; drag-drop stage transitions announce outcome via `LiveAnnouncer`.

### Phase 3: Data Table & Selection Modernization (`Collections`, `Table`, `Layout`)
- **P3.1 (`SelectionModel<T>` in `DataTableComponent`):** Opt-in `[selectable]`, `[selectionKey]`, and `(selectionChange)` powered by `@angular/cdk/collections` `SelectionModel<T>`, preserving `data-testid="data-table"` and non-selectable callers unchanged.
- **P3.2 (`DataTableCellDefDirective`):** `[appDataTableCell]` custom cell template directive works cleanly in `DataTableComponent` with fallback to default cell rendering.
- **P3.3 (`BreakpointObserver` Column Hiding):** `hideBelow?: 'sm' | 'md' | 'lg'` column visibility uses `@angular/cdk/layout` `BreakpointObserver` and updates displayed columns reactively.
- **P3.4 (`QuotesDetailComponent` Refactor):** Bulk quote-line selection in `QuotesDetailComponent` uses `SelectionModel<number>`, preserving `data-testid="quote-lines-table"` and all existing bulk/ramp actions.

### Phase 4: Power-User Navigation (`Overlay`, `Portal`, `A11y`, `Platform`)
- **P4.1 (`CommandPaletteService` & `CommandPaletteComponent`):** Located in `frontend/libs/shared/ui/src/lib/command-palette/`, uses `@angular/cdk/overlay` (`Overlay`), `@angular/cdk/portal` (`ComponentPortal`), `@angular/cdk/a11y` (`CdkTrapFocus`, `ActiveDescendantKeyManager`), and `@angular/cdk/platform` (`Platform` for `⌘K` vs `Ctrl+K` hint).
- **P4.2 (Shell & Toolbar Integration):** Global `Ctrl+K` / `Cmd+K` shortcut registered in `ShellComponent`; trigger button exposed in `ToolbarComponent`; Escape/backdrop closes cleanly and restores focus.

### Phase 5: Structural & High-Scale Components (`Stepper`, `Tree`, `Accordion`, `Scrolling`)
- **P5.1 (`StagePathComponent`):** `app-stage-path` in `frontend/libs/shared/ui/src/lib/stage-path/` extends `CdkStepper` (`@angular/cdk/stepper`) and is wired into `DealDetailComponent` with keyboard navigation and ARIA step attributes.
- **P5.2 (`AgentPanelComponent`):** Uses `@angular/cdk/accordion` (`CdkAccordion` / `CdkAccordionItem`) while preserving `data-testid="agent-panel"` and `aria-expanded` / `aria-controls`.
- **P5.3 (`ProductBundleDetailComponent`):** Uses `@angular/cdk/tree` (`CdkTree` / `cdk-nested-tree-node`) for hierarchical option-group/item view while preserving existing actions (`Add item`, `Remove`, `Delete`, and existing selectors).
- **P5.4 (`BulkSimulatorComponent`):** Uses `@angular/cdk/scrolling` (`CdkVirtualScrollViewport`) in the Bulk Simulator results view while preserving per-context failure isolation and existing UAT selectors.

---

## Peer-to-Peer Sign-Off Protocol

1. **Requesting Evaluation:** `pi-00` (`w3:p4`) or `pi-01` (`w3:p5`) sends a concise evidence packet to `pi-decision` (`w3:pA`) listing:
   - Phase ID(s) (e.g., `P1`, `P2`, `P5.3-5.4`, or `FINAL`)
   - Modified/created files
   - Test & lint command results (`npx nx test ...`, `npx nx lint ...`, or `./scripts/ci/frontend.sh`)
2. **Decision Execution:** `pi-decision` verifies deterministic evidence (files, selectors, token check, test output) and runs TypeSafe Jev (`typesafe/jev-1.13`) to issue a verdict:
   - **PASS** (`confidence >= 0.85` per-phase, `>= 0.90` final, all blocking gates green)
   - **REVISE** (specific blocking gap(s) identified; implementer fixes and re-submits)
   - **HOLD** (design contract or flag-hold violation; requires immediate revert/fix)
