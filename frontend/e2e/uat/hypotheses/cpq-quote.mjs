/**
 * Hypotheses exercised by the quotes and approvals suites. Static findings are claims, not
 * defects: the run fills in each verdict (CONFIRMED / REFUTED / BLOCKED / GAP).
 */
import { validateHypothesis } from '../contract.mjs';

const QUOTES = 'frontend/libs/cpq/quotes/src/lib';

export default [
  {
    id: 'H-CPQ-Q-001',
    claim: 'Quote create form cannot set dealType (every quote is created as NEW), yet the list offers a Deal type filter.',
    file: `${QUOTES}/quotes-create/quotes-create.component.ts`,
    triage: 'browserCheckable',
    checkId: 'quotes.create.deal-type-field',
    class: 'functional',
    note: 'Missing feature: the check records GAP while no Deal type control exists. The header-edit panel states deal type is not editable either.',
  },
  {
    id: 'H-CPQ-Q-002',
    claim: 'Quote status is spelled inconsistently: the list Status filter shows raw enums (PENDING_APPROVAL) while the status badge shows friendly labels (Pending Approval).',
    file: `${QUOTES}/quotes-list/quotes-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'quotes.list.status-vocabulary',
    class: 'consistency',
    note: 'Source refines the claim: the list *row* is raw too — DataTable has no @case for type:"badge" (shared/ui data-table.component.ts), so only the detail-page badge is friendly.',
  },
  {
    id: 'H-CPQ-Q-003',
    claim: 'Quotes list: every column is a sortable header, but (sortChange) is not bound, so clicking shows a sort arrow and nothing is sorted.',
    file: `${QUOTES}/quotes-list/quotes-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'quotes.list.sort-headers',
    class: 'functional',
  },
  {
    id: 'H-CPQ-Q-004',
    claim: 'quote-header-edit formats server errors as "message (CODE)" while every other quote surface uses "message [CODE]".',
    file: `${QUOTES}/quote-header-edit/quote-header-edit.component.ts`,
    triage: 'browserCheckable',
    checkId: 'quotes.header.error-format',
    class: 'consistency',
  },
  {
    id: 'H-CPQ-Q-005',
    claim: 'Reject flows collect the reason through native window.prompt; on the approvals inbox the prompt fires BEFORE the Material confirm dialog.',
    file: 'frontend/libs/cpq/approvals/src/lib/approvals-inbox/approvals-inbox.component.ts',
    triage: 'browserCheckable',
    checkId: 'approvals.row.reject.prompt-before-confirm',
    class: 'consistency',
    note: 'Confirmed by source (approvals-inbox onReject: prompt, then dialog.open; quotes-detail onReject: prompt only). Both browser checks need an IN_REVIEW quote / a pending approval, unreachable while configuration-engine and approval-routing are down. Second check: quotes.reject.uses-native-prompt.',
  },
  {
    id: 'H-CPQ-Q-006',
    claim: 'With configuration-engine down, Submit shows the user a raw upstream failure instead of a friendly "configuration check unavailable" message.',
    file: 'backend/services/quote-service/src/main/java/com/opencrm/quote/client/ProductServiceClient.java',
    triage: 'browserCheckable',
    checkId: 'quotes.submit.config-engine-down.user-message',
    class: 'functional',
  },
  {
    id: 'H-CPQ-Q-007',
    claim: 'quotes-list-webmcp.ts registers listQuotes / filterQuotes / pageQuotes through provideExperimentalWebMcpTools; Playwright Chromium has no navigator.modelContext, so the tools are unreachable from the browser UAT.',
    file: `${QUOTES}/quotes-list/quotes-list-webmcp.ts`,
    triage: 'unitOnly',
    class: 'functional',
    note: 'Not a defect. The suite records typeof navigator.modelContext as INFO (quotes.list.webmcp); tool behaviour is covered by quotes-list-webmcp.spec.ts.',
  },
  {
    id: 'H-CPQ-Q-008',
    claim: 'No success feedback (snackbar or inline) follows any quote mutation; only errors raise a toast.',
    file: `${QUOTES}/quotes-detail/quotes-detail.component.ts`,
    triage: 'browserCheckable',
    checkId: 'quotes.feedback.success-snackbar',
    class: 'consistency',
    note: 'Missing feature: recorded as GAP while a successful header Save stays silent.',
  },
  // ---- Found by the UAT run itself (not part of the static pass) ----
  {
    id: 'H-CPQ-Q-009',
    claim: 'Quote header "Subscription type" is a free-text input, but quotes.subscription_type has CHECK (SAAS | ONPREM | HYBRID): any other text fails Save with an opaque DATA_INTEGRITY_VIOLATION.',
    file: `${QUOTES}/quote-header-edit/quote-header-edit.component.ts`,
    triage: 'browserCheckable',
    checkId: 'quotes.header.subscription-type-free-text',
    class: 'functional',
    note: 'Constraint: backend/services/quote-service/src/main/resources/db/migration/V1__create_quote_schema.sql:59 (chk_quotes_subscription_type). QuoteUpdateRequest has no enum/pattern validation either.',
  },
  {
    id: 'H-CPQ-Q-010',
    claim: 'Adding a quote line item fails: POST /api/v1/quotes/{id}/line-items answers HTTP 500 INTERNAL_SERVER_ERROR for a valid payload, from the UI and from the API alike.',
    file: 'backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItemService.java',
    triage: 'browserCheckable',
    checkId: 'quotes.lines.add',
    class: 'functional',
    note: 'Blocks line-item edit/remove, Calculate, Clone-with-lines, Submit and the whole status lifecycle. Root cause needs the quote-service log (the 500 envelope carries no traceId); table quote.quote_line_items matches the entity.',
  },
  {
    id: 'H-CPQ-Q-011',
    claim: 'A quote created with a blank Currency is stored with currency=null: QuoteService.create defaults dealType and durationInMonths but not currency (the mapper overwrites the entity default "USD").',
    file: 'backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java',
    triage: 'browserCheckable',
    checkId: 'quotes.create.currency-defaults-when-blank',
    class: 'functional',
  },
].map(validateHypothesis);
