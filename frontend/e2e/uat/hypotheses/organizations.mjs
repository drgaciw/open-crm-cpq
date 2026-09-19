/**
 * Hypotheses exercised by the organizations suite. Static findings are claims, not defects:
 * the run fills in each verdict (CONFIRMED / REFUTED / BLOCKED).
 */
import { validateHypothesis } from '../contract.mjs';

export default [
  {
    id: 'H-CRM-007',
    claim: 'Organization list: the row-actions cell lacks stopPropagation, so opening the menu also triggers row-click navigation.',
    file: 'frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.ts',
    triage: 'browserCheckable',
    checkId: 'organizations.row.menu-does-not-navigate',
    class: 'functional',
  },
  {
    id: 'H-A11Y-001',
    claim: 'Organization list: the icon-only row menu trigger has no aria-label.',
    file: 'frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.ts',
    triage: 'browserCheckable',
    checkId: 'organizations.a11y.row-menu-has-name',
    class: 'a11y',
  },
].map(validateHypothesis);
