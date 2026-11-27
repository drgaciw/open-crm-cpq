# Angular Material CDK UX/UI Improvements Plan

> **Pane-map note (re-pinned 2026-11-27):** The `w3:p*` pane ids in this document are from the historical `w3` workspace, which no longer exists (`herdr workspace list` → only `w1`; `w3` → `workspace_not_found`). Live mapping: `pi-00` = `w1:p1`, `pi-01` = `w1:p7`, `agy` = `w1:p3`. `pi-decision` (was `w3:pA`), `architect` (was `w3:pB`) and `codex` (was `w3:p8`) have no live counterpart in this workspace — treat those references as historical. Do not resolve a `w3:p*` id against the current session.

**Status:** Complete
**Coordinator:** `pi-00` (`w3:p4`)  
**Peers:** `pi-01` (`w3:p5`), `pi-decision` (`w3:pA`)  
**Design Contract:** `docs/design/UI-CRM-SLDS-design-contract.md` (SLDS2-inspired Material Cosmos; `--crm-*` tokens; no LWC/SLDS vendor CSS; no `opencrm.jev.*.enabled` flips)

## Overview

`@angular/cdk@22.3.0` is installed in `frontend/`, but previously only `BreakpointObserver` (`ShellComponent`) and `CdkTrapFocus` (`ConfirmDialogComponent`) were used. This plan rolls out high-leverage Angular Material CDK capabilities across CRM and CPQ in 5 incremental, TDD-verified phases.

## Requirements

1. **Design Contract Compliance**: Use `--crm-*` semantic tokens from `frontend/apps/crm/src/styles.scss`; no hardcoded palette hex in new styles; no LWC/SLDS vendor CSS; no `opencrm.jev.*.enabled` flips.
2. **Preserve Contracts & UAT Selectors**: Keep all existing `data-testid` attributes (`data-table`, `quote-lines-table`, `agent-panel`, `ramp-stale-banner`, `ramp-governance-banner`, `quote-more-actions`, `unconverted-chip`, etc.) and Opportunities copy intact.
3. **Domain Rules Enforced**:
   - Kanban drag-and-drop respects `canAdvanceDeal` / `canCloseDeal`, opens `DealLoseReasonDialogComponent` for `LOST`, and handles `WON` errors (`OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE`) cleanly without moving the card on failure.
   - Keyboard parity preserved (existing `mat-menu` actions remain).
4. **Accessibility (WCAG 2.1 AA)**: `LiveAnnouncer` for async changes, `CdkTrapFocus` + `ActiveDescendantKeyManager` for overlay navigation, ARIA roles/states preserved.
5. **Additive Shared-UI APIs**: All changes in `frontend/libs/shared/ui` are strictly additive.
6. **Testing**: Vitest (`@nx/vitest:test`, `TestBed` + `vi.fn()`, no Jasmine spies) + `./scripts/ci/frontend.sh` green across all 20 Nx projects.

## Phases & Ownership

### Phase 1: Quick-Win Ergonomics (`TextField`, `Clipboard`, `Observers`, `A11y`)
- **1.1** `cdkTextareaAutosize` (`@angular/cdk/text-field`) on multi-line textareas across CRM & CPQ forms/dialogs and `BulkSimulatorComponent`.
- **1.2** `CopyButtonComponent` (`app-copy-button`, `@angular/cdk/clipboard` + `LiveAnnouncer`) in `frontend/libs/shared/ui/src/lib/copy-button/`, wired into Quote detail, Catalog detail, Contact detail, Organization detail, and Bulk Simulator.
- **1.3** `CdkObserveContent` (`@angular/cdk/observers`) in `PageHeaderComponent` for reactive `[breadcrumb]`/`[highlights]`/`[search]` slot visibility.
- **1.4** `LiveAnnouncer` integration in `NotificationService` (`frontend/libs/shared/data-access`).

### Phase 2: Interactive Pipeline (`Drag & Drop`)
- **2.1** `DealKanbanComponent` (`frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.ts`): `cdkDropListGroup`, per-stage `cdkDropList` + `cdkDropListEnterPredicate`, `cdkDrag` cards with `*cdkDragPlaceholder`, `cdkDropListDropped` handler respecting `canAdvanceDeal`/`canCloseDeal`, `DealLoseReasonDialogComponent` for `LOST`, `service.win` with error handling for `WON`, and `LiveAnnouncer` announcements.

### Phase 3: Data Table & Selection Modernization (`Collections`, `Table`, `Layout`)
- **3.1** Opt-in `SelectionModel<T>` (`@angular/cdk/collections`) in `DataTableComponent` (`[selectable]`, `[selectionKey]`, `(selectionChange)`).
- **3.2** `DataTableCellDefDirective` (`[appDataTableCell]`) for custom cell templates in `DataTableComponent`.
- **3.3** Responsive `hideBelow?: 'sm' | 'md' | 'lg'` column visibility via `BreakpointObserver` (`@angular/cdk/layout`).
- **3.4** Refactor `QuotesDetailComponent` bulk line selection to use `SelectionModel<number>`.

### Phase 4: Power-User Navigation (`Overlay`, `Portal`, `A11y`, `Platform`)
- **4.1** `CommandPaletteService` & `CommandPaletteComponent` in `frontend/libs/shared/ui/src/lib/command-palette/` using `Overlay`, `ComponentPortal`, `CdkTrapFocus`, `ActiveDescendantKeyManager`, and `Platform` (`⌘K` vs `Ctrl+K`).
- **4.2** Global `Ctrl/Cmd+K` shortcut in `ShellComponent` and trigger button in `ToolbarComponent`.

### Phase 5: Structural & High-Scale Components (`Stepper`, `Tree`, `Accordion`, `Scrolling`)
- **5.1** `StagePathComponent` (`app-stage-path`) extending `CdkStepper` (`@angular/cdk/stepper`) in `frontend/libs/shared/ui/src/lib/stage-path/`, wired into `DealDetailComponent`.
- **5.2** `CdkAccordion` / `CdkAccordionItem` (`@angular/cdk/accordion`) in `AgentPanelComponent` preserving `data-testid="agent-panel"` and `aria-expanded`/`aria-controls`.
- **5.3** `CdkTree` (`@angular/cdk/tree`) hierarchical option-group/item view in `ProductBundleDetailComponent`.
- **5.4** `CdkVirtualScrollViewport` (`@angular/cdk/scrolling`) in `BulkSimulatorComponent` results view.

## Peer-to-Peer Coordination Log

- DoD Rubric target file: `docs/plans/angular-cdk-ux-dod-rubric.md` (owned by `pi-decision` / `w3:pA`).
- File ownership (one writer per file/lib at a time):
  - `pi-00` (`w3:p4` + subagents): `frontend/libs/shared/ui`, `frontend/libs/shared/data-access`, `frontend/apps/crm`, `frontend/libs/cpq/quotes`
  - `pi-01` (`w3:p5`): `frontend/libs/crm/deals`, `frontend/libs/crm/leads`, `frontend/libs/crm/contacts`, `frontend/libs/crm/organizations`, `frontend/libs/cpq/catalog`, `frontend/libs/cpq/rule-sets`, `frontend/libs/cpq/product-bundles`, `frontend/libs/cpq/pricing-simulator`
  - `pi-decision` (`w3:pA`): DoD rubric creation (`docs/plans/angular-cdk-ux-dod-rubric.md`) and DoD evaluation/sign-off.
