/**
 * Environment preflight: classify each backend before any suite runs, so a service that is down
 * produces one BLOCKED line instead of seventeen misleading FAILs.
 *
 * Probes go through the dev-server origin, i.e. the same path the app uses (proxy -> gateway).
 */
import { BASE } from './browser.mjs';

/**
 * One probe per backend the UI depends on. `gates` documents what a non-'up' result blocks.
 * Two probes guard specific quote workflow steps rather than whole pages:
 *  - configValidate: quote Submit runs a configuration preflight routed to configuration-engine.
 *  - pricingExecute: quote Calculate and the simulator both call pricing execute.
 */
export const PROBES = [
  { key: 'crm', method: 'GET', path: '/api/v1/organizations?page=0&size=1', gates: 'organizations, contacts, leads, deals, dashboard' },
  { key: 'product', method: 'GET', path: '/api/v1/products?page=0&size=1', gates: 'catalog, product bundles' },
  { key: 'quote', method: 'GET', path: '/api/v1/quotes?page=0&size=1', gates: 'quotes, approvals inbox' },
  { key: 'pricing', method: 'GET', path: '/api/v1/rule-sets?page=0&size=1', gates: 'rule sets, pricing customers' },
  { key: 'pricebook', method: 'GET', path: '/api/v1/price-books?page=0&size=1', gates: 'price books' },
  { key: 'approvalsBff', method: 'GET', path: '/api/v1/quotes/approvals/pending', gates: 'approvals inbox list' },
  { key: 'configValidate', method: 'POST', path: '/api/v1/configuration/validate', body: [], gates: 'quote Submit and everything after it' },
  { key: 'pricingExecute', method: 'POST', path: '/api/v1/pricing/execute', body: {}, gates: 'quote Calculate, pricing simulator' },
];

/**
 * @param {{status?:number, error?:string}} probe
 * @returns {'up'|'forbidden'|'fallback'|'down'}
 */
export function classify({ status, error }) {
  if (error || status === undefined) return 'down';
  if (status === 401 || status === 403) return 'forbidden';
  // The gateway circuit-breaker fallback answers 503 on behalf of a dead service.
  if (status === 503) return 'fallback';
  if (status >= 500) return 'down';
  // 2xx, and 4xx validation answers (400/404/422 to an empty probe body), all prove a live service.
  return 'up';
}

/**
 * @param {import('playwright').APIRequestContext} request
 * @returns {Promise<Record<string, {state:string, status?:number, error?:string, gates:string}>>}
 */
export async function runPreflight(request, { base = BASE, timeoutMs = 8000 } = {}) {
  const results = {};
  for (const probe of PROBES) {
    let observed;
    try {
      const options = { timeout: timeoutMs, failOnStatusCode: false };
      const response =
        probe.method === 'POST'
          ? await request.post(`${base}${probe.path}`, { ...options, data: probe.body })
          : await request.get(`${base}${probe.path}`, options);
      observed = { status: response.status() };
    } catch (cause) {
      observed = { error: String(cause?.message ?? cause).split('\n')[0] };
    }
    results[probe.key] = { state: classify(observed), ...observed, gates: probe.gates };
  }
  return results;
}

/** Reason string for a BLOCKED entry, or null when the backend is usable. */
export function blockedReason(preflight, key) {
  const result = preflight?.[key];
  if (!result) return `no preflight probe named '${key}'`;
  if (result.state === 'up') return null;
  const detail = result.status ? `HTTP ${result.status}` : result.error;
  return `backend '${key}' is ${result.state} (${detail})`;
}
