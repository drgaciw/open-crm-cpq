/**
 * Hypotheses exercised by the contacts, leads, deals and dashboard suites. Static findings are
 * claims, not defects: the run fills in each verdict (CONFIRMED / REFUTED / BLOCKED).
 */
import { validateHypothesis } from '../contract.mjs';

const LIB = 'frontend/libs/crm';

export default [
  {
    id: 'H-CRM-014',
    claim: 'Contacts list: the row-actions cell lacks stopPropagation, so opening the row menu also triggers row-click navigation.',
    file: `${LIB}/contacts/src/lib/contact-list/contact-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'contacts.row.menu-does-not-navigate',
    class: 'functional',
  },
  {
    id: 'H-A11Y-002',
    claim: 'Contacts list: the icon-only row menu trigger has no aria-label.',
    file: `${LIB}/contacts/src/lib/contact-list/contact-list.component.ts`,
    triage: 'browserCheckable',
    checkId: 'contacts.a11y.row-menu-has-name',
    class: 'a11y',
  },
  {
    id: 'H-CRM-022',
    claim: 'Contact form: organization text that is typed but not picked from the autocomplete is silently dropped (organizationId stays null).',
    file: `${LIB}/contacts/src/lib/contact-form/contact-form.component.ts`,
    triage: 'browserCheckable',
    checkId: 'contacts.form.organization-typed-not-picked',
    class: 'functional',
  },
  {
    id: 'H-CRM-032',
    claim: 'Contacts search matches firstName OR lastName separately, so searching the full name shown in the Name column finds nothing.',
    file: 'backend/services/crm-service/src/main/java/com/opencrm/crm/contact/ContactRepository.java',
    triage: 'browserCheckable',
    checkId: 'contacts.search.finds-by-full-name',
    class: 'functional',
    note: 'New finding (not in the static list): ContactRepository.searchByName LIKEs each column against the whole query.',
  },
].map(validateHypothesis);
