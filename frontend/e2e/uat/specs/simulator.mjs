/**
 * Pricing simulator — one page, one reactive form, one result card.
 * Source: frontend/libs/cpq/pricing-simulator/src/lib/simulator-page/simulator-page.component.ts
 *
 * The form spec has no `submit`: "Simulate" lives in the page header, outside the card scope the
 * fields live in, and the template renders no mat-error at all — validator behaviour is asserted
 * by hand in the suite.
 */
import { validateSpec } from '../contract.mjs';

const opts = (values) => values.map((v) => ({ label: v, value: v }));

export const DEFAULTS = {
  customerExternalId: 'CUST-GOLD-001',
  productExternalId: 'PROD-001',
  quantity: 5,
  region: 'NA',
  currency: 'USD',
  durationInMonths: 12,
  subscriptionType: 'NEW',
};

export const page = validateSpec({
  id: 'simulator',
  path: '/pricing/simulator',
  heading: 'Pricing Simulator',
  subtitle: 'What-if sandbox — POST /api/v1/pricing/execute',
  backend: 'pricingExecute',
  sidebar: 'Pricing Simulator',
  actions: [{ name: 'Simulate', in: 'header' }],
  cards: [
    {
      title: 'Pricing context',
      labels: ['Customer external ID', 'Product external ID', 'Quantity', 'List price override', 'Region',
        'Currency', 'Duration (months)', 'Subscription type'],
    },
    { title: 'Result', labels: ['PricingResult from execute'] },
  ],
  forms: [{
    id: 'context',
    scope: { kind: 'card', title: 'Pricing context' },
    flavor: 'reactive',
    open: { path: '/pricing/simulator' },
    fields: [
      { label: 'Customer external ID', kind: 'input', required: true, default: DEFAULTS.customerExternalId },
      { label: 'Product external ID', kind: 'input', required: true, default: DEFAULTS.productExternalId },
      { label: 'Quantity', kind: 'number', required: true, default: DEFAULTS.quantity },
      { label: 'List price override', kind: 'number', default: '' },
      { label: 'Region', kind: 'select', required: true, default: DEFAULTS.region, options: opts(['NA', 'EMEA', 'APAC', 'LATAM']) },
      { label: 'Currency', kind: 'select', required: true, default: DEFAULTS.currency, options: opts(['USD', 'EUR', 'GBP']) },
      { label: 'Duration (months)', kind: 'number', required: true, default: DEFAULTS.durationInMonths },
      { label: 'Subscription type', kind: 'select', required: true, default: DEFAULTS.subscriptionType, options: opts(['NEW', 'AMENDMENT', 'RENEWAL']) },
    ],
  }],
});

/** Members of the frontend `PricingApprovalState` union (shared-models/pricing.model.ts). */
export const APPROVAL_STATES = ['COMPLIANT', 'DEAL_DESK_REQUIRED', 'CFO_REQUIRED', 'HARD_BLOCK'];

export default { page };
