/**
 * Hypotheses exercised by the catalog and product-bundles suites. Static findings are claims,
 * not defects: each was checked against the component source first, and the run fills in the
 * verdict (CONFIRMED / REFUTED / GAP) for the browser-checkable ones.
 */
import { validateHypothesis } from '../contract.mjs';

const CATALOG = 'frontend/libs/cpq/catalog/src/lib';
const BUNDLES = 'frontend/libs/cpq/product-bundles/src/lib';

export default [
  {
    id: 'H-CPQ-P-001',
    claim: 'New Product dialog: List price has min(0) with the message "List price must be at least 0", but its mat-form-field has no mat-error block, so a negative price disables Create without telling the user why (FIX-UAT1-CATALOG-LISTPRICE-MIN-NOT-SHOWN).',
    file: `${CATALOG}/product-create-dialog/product-create-dialog.component.ts`,
    triage: 'falsePositive',
    note: 'Was true on the 2026-09 UAT branch. The list-price field now renders that mat-error, and catalog.field.List price.validator.min expects "List price must be at least 0".',
  },
  {
    id: 'H-CPQ-P-002',
    claim: 'Catalog table: every column renders an active mat-sort-header, but the list never binds (sortChange) and the rows come from the server, so clicking a header flips the arrow / aria-sort while the row order never changes.',
    file: `${CATALOG}/catalog-list/catalog-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'catalog.table.sort-header-sorts-rows',
    class: 'functional',
    note: 'Classed functional rather than gap: the control is rendered, focusable and announces a sorted state that is false. The same holds for the product-bundles table (same DataTableComponent, no sortChange binding).',
  },
  {
    id: 'H-CPQ-P-003',
    claim: "DataTable: column type 'badge' has no @case and falls through to @default, so the catalog (and bundle) Status column is plain text rather than a chip.",
    file: 'frontend/libs/shared/ui/src/lib/data-table/data-table.component.ts',
    triage: 'browserCheckable',
    checkId: 'catalog.table.status-renders-as-badge',
    class: 'cosmetic',
  },
  {
    id: 'H-CPQ-P-004',
    claim: 'Catalog list: while a search term is active, Category/Status are applied client-side to the current search page only (the search endpoint takes no category/status), so matches on later search pages are hidden and the paginator total stays unfiltered.',
    file: `${CATALOG}/catalog-list/catalog-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'catalog.filter.category-with-search-finds-all-matches',
    class: 'functional',
    note: 'The component documents this as intentional ("search API has no category/status params"); the check decides whether it is user-visible.',
  },
  {
    id: 'H-CPQ-P-005',
    claim: 'New Product dialog: Currency maxLength(3) — typing "USDD" either shows "Currency must be 3 characters" or is silently truncated by a maxlength attribute (previous UAT: inconclusive).',
    file: `${CATALOG}/product-create-dialog/product-create-dialog.component.ts`,
    triage: 'browserCheckable',
    checkId: 'catalog.field.Currency.maxlength-typing',
    class: 'consistency',
    note: 'The check types with real keystrokes; fill() bypasses maxlength and is what made the earlier run inconclusive. It only reports a defect when a 4-char value is accepted with no error.',
  },
  {
    id: 'H-CPQ-P-006',
    claim: 'Catalog list, filter selects and every product/option/rule select print raw enum constants (DRAFT, SUBSCRIPTION, SAAS_FLAT_RATE) while the detail status badge prints friendly labels (Draft); the *_LABELS maps in shared-models are never used by any component.',
    file: `${CATALOG}/catalog-list/catalog-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'catalog.consistency.status-wording',
    class: 'consistency',
  },
  {
    id: 'H-CPQ-P-007',
    claim: 'SELECTION_TYPE_LABELS omits REQUIRED_SINGLE.',
    file: 'frontend/libs/shared/models/src/lib/product-option.model.ts',
    triage: 'falsePositive',
    note: "The map is Record<SelectionType, string> and does contain REQUIRED_SINGLE: 'Required single' (line 18) — an omission would not compile. No component renders through the map anyway (only cpq-models.spec.ts imports it); the option-group dialog and card print the raw enum, which is covered by H-CPQ-P-006.",
  },
  {
    id: 'H-CPQ-P-008',
    claim: 'Product-bundle list: with the Product ID filter applied the paginator stays visible but onPageChange returns early, so paging does nothing.',
    file: `${BUNDLES}/product-bundle-list/product-bundle-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'product-bundles.filter.paginator-shows-all-rows',
    class: 'cosmetic',
    note: 'listByProductId() is unpaginated and sets size = totalElements = rows.length, totalPages = 1, so the no-op can only matter if rows were withheld. The check reports a defect only when the filtered table shows fewer rows than the API returns or offers an enabled "Next page".',
  },
  {
    id: 'H-CPQ-P-009',
    claim: 'No product Activate / Deprecate / Retire actions in the UI although ProductService exposes activate(), deprecate() and retire().',
    file: `${CATALOG}/catalog-detail/catalog-detail.component.ts`,
    triage: 'gap',
  },
  {
    id: 'H-CPQ-P-010',
    claim: 'Product bundles cannot be edited: the detail page offers only Delete and Back, and ProductBundleService has no update().',
    file: `${BUNDLES}/product-bundle-detail/product-bundle-detail.component.ts`,
    triage: 'gap',
  },
  {
    id: 'H-CPQ-P-011',
    claim: 'Bundle items cannot be edited (quantity / optional / sort order): the only row action is Remove.',
    file: `${BUNDLES}/product-bundle-detail/product-bundle-detail.component.ts`,
    triage: 'gap',
  },
  {
    id: 'H-CPQ-P-012',
    claim: 'New configuration rule dialog has no errorMessage or status field although ConfigRuleCreateRequest/ConfigurationRuleDto carry them; rules also cannot be edited, only deleted.',
    file: `${CATALOG}/config-rule-create-dialog/config-rule-create-dialog.component.ts`,
    triage: 'gap',
  },
  {
    id: 'H-CPQ-P-013',
    claim: 'No success feedback (snackbar) after product/bundle/group/option/rule create, update or delete — errors are toasted by errorInterceptor, successes are silent.',
    file: `${CATALOG}/catalog-detail/catalog-detail.component.ts`,
    triage: 'gap',
    class: 'consistency',
    note: 'Asserted as the documented behaviour with expectNoSnackbar in the CRUD steps of both suites; those checks start failing the day a success toast is added.',
  },
  {
    id: 'H-CPQ-P-014',
    claim: 'New Product dialog: signal-forms required() accepts a whitespace-only SKU/Name, so Create is enabled; the component trims to "" and the request is left for the server to reject.',
    file: `${CATALOG}/product-create-dialog/product-create-dialog.component.ts`,
    triage: 'browserCheckable',
    checkId: 'catalog.dialog.whitespace-sku-rejected-client-side',
    class: 'functional',
  },
  {
    id: 'H-CPQ-P-015',
    claim: 'New Product dialog: SKU/Name maxLength schema messages exist and mat-error blocks iterate errors(), but filling past the limit leaves Create disabled with no visible mat-error.',
    file: `${CATALOG}/product-create-dialog/product-create-dialog.component.ts`,
    triage: 'browserCheckable',
    checkId: 'catalog.field.SKU.validator.maxLength',
    class: 'functional',
  },
].map(validateHypothesis);
