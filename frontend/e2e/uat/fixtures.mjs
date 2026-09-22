/**
 * API-seeded fixtures for the UAT.
 *
 * The UI create path of each entity is tested exactly once, in that entity's own CRUD suite.
 * Everything else seeds through the API, so no suite depends on records left by another and
 * suites can run in any order (or in parallel under different run ids).
 *
 * Every record name carries `UAT-<runId>-`, which (a) lets assertions search by run id before
 * looking at a list — accumulated rows push new records off page 1 — and (b) lets `--sweep`
 * find leftovers from older runs.
 */

/** Short, sortable, collision-resistant run id. */
export const newRunId = () => `U${Date.now().toString(36).toUpperCase()}`;

/** Prefix shared by every record a run creates. */
export const runPrefix = (runId) => `UAT-${runId}-`;

let sequence = 0;
/** Unique, human-readable record name: `UAT-<runId>-<label>-<n>`. */
export const uniqueName = (runId, label) => `${runPrefix(runId)}${label}-${++sequence}`;

/** Endpoint + default payload per entity. Payload shapes mirror what the app's own forms send. */
const ENTITIES = {
  organization: {
    path: '/api/v1/organizations',
    body: (name) => ({ name, industry: 'Technology', city: 'Austin' }),
  },
  contact: {
    path: '/api/v1/contacts',
    body: (name) => ({ firstName: 'Uat', lastName: name, email: `${name.toLowerCase()}@example.com`, primary: false }),
  },
  lead: {
    path: '/api/v1/leads',
    body: (name) => ({ title: name, source: 'UAT', priority: 'MEDIUM', estimatedValue: 1000 }),
  },
  deal: {
    path: '/api/v1/deals',
    body: (name) => ({ name, amount: 5000 }),
  },
  product: {
    path: '/api/v1/products',
    body: (name) => ({ sku: name, name, productType: 'SAAS_FLAT_RATE', category: 'SUBSCRIPTION', listPrice: 25, currency: 'USD' }),
  },
  quote: {
    path: '/api/v1/quotes',
    body: (name) => ({ name, customerExternalId: 'CUST-GOLD-001', currency: 'USD' }),
  },
  priceBook: {
    path: '/api/v1/price-books',
    body: (name) => ({ name, region: 'NA', currency: 'USD', isDefault: false }),
  },
  pricingCustomer: {
    path: '/api/v1/pricing-customers',
    body: (name) => ({ externalId: name, name, segment: 'SMB', region: 'NA' }),
  },
  ruleSet: {
    path: '/api/v1/rule-sets',
    body: (name) => ({ name, description: 'UAT fixture', engineType: 'DROOLS' }),
  },
};

export const ENTITY_TYPES = Object.keys(ENTITIES);

/**
 * Create one record through the API and ledger it for teardown.
 *
 * @param {{api: ReturnType<import('../lib/api.mjs').createApi>, runId: string}} ctx
 * @param {keyof typeof ENTITIES} type
 * @param {object} [overrides] Merged over the default payload.
 * @returns {Promise<object>} The created DTO (always has `id`).
 */
export async function ensure(ctx, type, overrides = {}) {
  const entity = ENTITIES[type];
  if (!entity) throw new Error(`unknown fixture type '${type}' (known: ${ENTITY_TYPES.join(', ')})`);
  const name = uniqueName(ctx.runId, type);
  const created = await ctx.api.post(entity.path, { ...entity.body(name), ...overrides });
  if (created?.id === undefined) throw new Error(`fixture '${type}' was created without an id`);
  ctx.api.track(type, created.id, `${entity.path}/${created.id}`);
  return created;
}

/** REST collection path for an entity type — used by persistence checks and the sweep. */
export const entityPath = (type) => {
  const entity = ENTITIES[type];
  if (!entity) throw new Error(`unknown fixture type '${type}'`);
  return entity.path;
};
