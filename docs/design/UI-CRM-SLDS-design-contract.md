# UI-CRM-SLDS design contract

**Story:** [UI-CRM-SLDS](https://app.notion.com/p/416f7cb4e4df819893e2fd47a9ae8b07) (BC-12)  
**Plan SSOT:** [SLDS 2 Agentic UI → Open CRM Angular UX](https://app.notion.com/p/416f7cb4e4df81ec86aef44507fb5015)  
**Source artifact:** `slds2-agentic-ui-reference.html` (SLDS 2 Components for Web · Agentic UI Reference)  
**Decided:** 2026-11-12 via Cloudflare Clef — SLDS2-inspired Material Cosmos; no LWC; agent panel UI shell before Jev flags.

## Brief (filled)

| Field | Value |
| --- | --- |
| Container | Angular 22 CRM shell (`frontend/apps/crm`) |
| Theme | SLDS2-inspired Material Cosmos (semantic CSS variables + Angular Material) |
| Data model | CRM/CPQ REST via gateway (`/api/v1/...`) |
| Permissions | Seller OAuth / existing CRM roles; no new Salesforce FLS |
| Interaction | Keyboard + Material focus; writes stay confirm/reject where already required |
| Non-goals | LWC runtime, full SLDS CSS vendor, production `--slds-c-*` hooks, enabling `opencrm.jev.*.enabled` |

## Crosswalk (design need → Angular)

| SLDS2 / Figma need | Open CRM stand-in |
| --- | --- |
| Page Header / Global Header | `PageHeaderComponent` + shell toolbar/sidebar |
| Card | `mat-card` → shared record-section card (later) |
| Data Table | `DataTableComponent` (CPQ today; CRM migration) |
| Form Element / Input / Combobox | Material outline fields + shared form shell |
| Tabs | Material / shared record tabs |
| Modal / Prompt / Toast | `ConfirmDialogComponent`, Material dialog/snackbar |
| Spinner / Empty State | `LoadingService` + `EmptyStateComponent` |
| Path / Progress | Deal stage / approval progress (later) |
| Activity Timeline / Feed / Chat | Custom composition later (ADR-030) |
| Agent / advisory strip | `AdvisorySuggestionComponent` → agent panel shell (flag-off) |

## Do not use

- Hardcoded palette hex in new feature styles (use `--crm-*` tokens)
- Detached one-off detail headers when record `PageHeader` exists
- Unstyled native `<select>` / `<input>` as the long-term filter bar
- Salesforce proprietary tokens or LWC markup
- Enabling Jev runtime flags for Phase 3 shell work

## Tokens

Defined on `:root` in [`frontend/apps/crm/src/styles.scss`](../../frontend/apps/crm/src/styles.scss).

| Token | Role |
| --- | --- |
| `--crm-color-ink` | Primary text |
| `--crm-color-muted` | Secondary text |
| `--crm-color-line` | Borders / dividers |
| `--crm-color-canvas` | App background |
| `--crm-color-surface` | Cards / panels |
| `--crm-color-brand` | Links / primary accent |
| `--crm-color-info` / `--success` / `--warning` / `--danger` | Semantic status |
| `--crm-color-danger-container` / `--warning-container` | Soft alert backgrounds |
| `--crm-radius-sm` / `--md` | Corner radius |
| `--crm-space-*` | Spacing scale |
| `--crm-focus-ring` | Focus-visible outline |

Stage/status badge colors remain in shared-models maps until Phase 2 unifies them onto tokens.

## Phase 0 acceptance

- [x] This design note exists
- [x] `:root` semantic tokens in CRM `styles.scss`
- [x] Dashboard consumes tokens for chrome / KPI accents / alerts
- [x] No LWC or full SLDS CSS dependency added

## Phase 1 acceptance

- [x] Shell surfaces `LoadingService` as a global progress strip
- [x] `PageHeaderComponent` supports optional `[breadcrumb]` and `[highlights]` slots (additive)
- [x] Account (organization) detail uses shared record page header
- [x] Sidebar / shell chrome use `--crm-*` tokens; Accounts nav label aligns with page title
- [x] Focus-visible treatment on interactive controls (Phase 0 global styles)
