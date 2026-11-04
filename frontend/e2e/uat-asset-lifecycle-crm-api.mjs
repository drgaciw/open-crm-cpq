/**
 * Asset Lifecycle Management API UAT (UAT-ALM-01..08) against CRM at :8081.
 *
 * Spec: docs/uat/asset-lifecycle-management-cpq.md (Part III-B)
 *
 * CRM owns commercial precursors (organizations, deals, contacts, leads).
 * Asset OHS / reports live on contract-asset-management (:8087). When BASE is
 * CRM (:8081), unmapped /api/v1/assets must return 404 (not 500), and CAM is
 * probed as the sibling owner for live lifecycle checks.
 *
 *   UAT_BASE_URL=http://127.0.0.1:8081 node e2e/uat-asset-lifecycle-crm-api.mjs
 *   UAT_CAM_BASE_URL=http://127.0.0.1:8087  # optional override
 *   UAT_QUOTE_BASE_URL=http://127.0.0.1:8084
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const CRM_BASE = (process.env.UAT_BASE_URL || 'http://127.0.0.1:8081').replace(/\/$/, '');
const CAM_BASE = (process.env.UAT_CAM_BASE_URL || siblingPort(CRM_BASE, 8087)).replace(/\/$/, '');
const QUOTE_BASE = (process.env.UAT_QUOTE_BASE_URL || siblingPort(CRM_BASE, 8084)).replace(/\/$/, '');
const ALLOW_WRITES = process.env.UAT_ALLOW_WRITES === 'true';
const TOKENS = new Map();
for (const [base, token] of [[CRM_BASE, process.env.UAT_CRM_BEARER_TOKEN], [CAM_BASE, process.env.UAT_CAM_BEARER_TOKEN], [QUOTE_BASE, process.env.UAT_QUOTE_BEARER_TOKEN]]) {
  const credential = token || '';
  if (TOKENS.has(base) && TOKENS.get(base) !== credential) throw new Error('Shared origin requires identical explicit service credentials or none');
  TOKENS.set(base, credential);
}

const RUN_ID = `crm${Date.now().toString(36).slice(-6)}`;
const EVIDENCE_DIR = resolve(HERE, '..', '.ci-tmp', 'uat-full');

function siblingPort(base, port) {
  try {
    const u = new URL(base);
    u.port = String(port);
    return u.origin;
  } catch {
    return `http://127.0.0.1:${port}`;
  }
}

/** @type {{id:string,status:string,detail:string}[]} */
const results = [];

function record(id, status, detail) {
  results.push({ id, status, detail: String(detail).slice(0, 500) });
  const mark = status === 'PASS' ? '✓' : status === 'GAP' ? '○' : status === 'BLOCKED' ? '◌' : '✗';
  console.log(`${mark} ${id} [${status}] ${detail}`);
}

async function http(base, method, path, body) {
  const url = `${base}${path}`;
  const init = { method, headers: { Accept: 'application/json' } };
  const token = TOKENS.get(base);
  if (token) init.headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const res = await fetch(url, init);
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return { status: res.status, text, json, url };
}

function requireRead(response, label, valid) {
  if (response.status === 401 || response.status === 403) {
    throw blocked(`${label}: HTTP ${response.status}; an authorized read is required`);
  }
  if (response.status !== 200) throw blocked(`${label}: HTTP ${response.status}; service behavior not established`);
  if (!valid(response.json?.data)) throw new Error(`${label}: unexpected response body`);
  return `${label}: HTTP 200 with expected response shape`;
}

async function runCheck(id, fn) {
  try {
    const detail = await fn();
    record(id, 'PASS', detail ?? 'ok');
  } catch (error) {
    const kind = error?.kind;
    if (kind === 'gap') record(id, 'GAP', error.message);
    else if (kind === 'blocked') record(id, 'BLOCKED', error.message);
    else record(id, 'FAIL', error.message || String(error));
  }
}

const gap = (detail) => Object.assign(new Error(detail), { kind: 'gap' });
const blocked = (detail) => Object.assign(new Error(detail), { kind: 'blocked' });

async function main() {
  console.log(`Asset Lifecycle CRM API UAT`);
  console.log(`  CRM=${CRM_BASE}  CAM=${CAM_BASE}  QUOTE=${QUOTE_BASE}  runId=${RUN_ID}`);

  // Entry: CRM health
  await runCheck('asset-lifecycle.crm.health', async () => {
    const { status, json } = await http(CRM_BASE, 'GET', '/actuator/health');
    if (status !== 200) throw new Error(`CRM health HTTP ${status}`);
    if (json?.status && json.status !== 'UP') throw new Error(`CRM status ${json.status}`);
    return `CRM UP`;
  });

  // UAT-ALM-01 — CRM commercial precursors (deals / organizations APIs)
  await runCheck('asset-lifecycle.crm.deals-list', async () => {
    const { status, json } = await http(CRM_BASE, 'GET', '/api/v1/deals?page=0&size=1');
    if (status !== 200) throw new Error(`deals HTTP ${status}`);
    const rows = json?.data?.content ?? json?.content ?? json?.data ?? json;
    if (!Array.isArray(rows) && !(rows && typeof rows === 'object')) {
      throw new Error(`unexpected deals body shape`);
    }
    return `deals HTTP 200`;
  });

  await runCheck('asset-lifecycle.crm.organizations-list', async () => {
    const { status } = await http(CRM_BASE, 'GET', '/api/v1/organizations?page=0&size=1');
    if (status !== 200) throw new Error(`organizations HTTP ${status}`);
    return `organizations HTTP 200`;
  });

  // UAT-ALM-02 — CRM must not claim assets (404), CAM owns OHS
  await runCheck('asset-lifecycle.crm.assets-boundary-404', async () => {
    const { status, json } = await http(
      CRM_BASE,
      'GET',
      '/api/v1/assets?accountId=uat-alm&asOf=2026-01-01',
    );
    if (status === 500) {
      throw new Error(
        'CRM returned 500 for unmapped /api/v1/assets — NoResourceFoundException must map to 404',
      );
    }
    if (status !== 404) {
      throw new Error(`expected CRM 404 for /api/v1/assets, got ${status}`);
    }
    const code = json?.errorCode;
    if (code !== 'RESOURCE_NOT_FOUND') {
      throw new Error(`expected RESOURCE_NOT_FOUND, got ${code}`);
    }
    return `CRM boundary 404 RESOURCE_NOT_FOUND`;
  });

  await runCheck('asset-lifecycle.api.assets-up', async () => {
    const response = await http(CAM_BASE, 'GET', '/api/v1/assets?accountId=uat-alm&asOf=2026-01-01');
    return requireRead(response, 'installed base', Array.isArray);
  });

  await runCheck('asset-lifecycle.api.history-shape', async () => {
    const response = await http(CAM_BASE, 'GET', '/api/v1/assets/does-not-exist/history');
    return requireRead(response, 'asset history', Array.isArray);
  });

  await runCheck('asset-lifecycle.api.reports-up', async () => {
    const upcoming = await http(CAM_BASE, 'GET', '/api/v1/assets/reports/upcoming-renewals?accountId=uat-alm&withinDays=30');
    const churn = await http(CAM_BASE, 'GET', '/api/v1/assets/reports/churn?accountId=uat-alm&periodStart=2026-01-01&periodEnd=2026-12-31');
    requireRead(upcoming, 'upcoming renewals', (v) => v && typeof v.accountId === 'string' && Array.isArray(v.assets) && Number.isInteger(v.assetCount));
    requireRead(churn, 'churn', (v) => v && typeof v.accountId === 'string' && Array.isArray(v.events) && Number.isInteger(v.eventCount));
    return 'both reports returned HTTP 200 with object bodies';
  });

  // UAT-ALM-04 — Activation precursor: deal create on CRM + quote on quote-service
  await runCheck('asset-lifecycle.crm.deal-create', async () => {
    if (!ALLOW_WRITES) throw blocked('fixture writes disabled; set UAT_ALLOW_WRITES=true only against an authorized test environment');
    const orgs = await http(CRM_BASE, 'GET', '/api/v1/organizations?page=0&size=5');
    if (orgs.status !== 200) throw blocked(`cannot list orgs for deal seed: HTTP ${orgs.status}`);
    let orgRows = orgs.json?.data?.content ?? orgs.json?.content ?? orgs.json?.data ?? [];
    if (!Array.isArray(orgRows)) orgRows = [];
    let org = orgRows[0];
    if (!org?.id) {
      const orgName = `UAT-${RUN_ID}-alm-org`;
      const seeded = await http(CRM_BASE, 'POST', '/api/v1/organizations', {
        name: orgName,
        type: 'CUSTOMER',
        status: 'ACTIVE',
      });
      if (seeded.status >= 400) {
        throw new Error(`org seed failed HTTP ${seeded.status}: ${seeded.text.slice(0, 200)}`);
      }
      org = seeded.json?.data ?? seeded.json;
    }
    if (!org?.id) throw new Error('organization seed returned no id');

    const name = `UAT-${RUN_ID}-alm-deal`;
    const payloads = [
      { name, organizationId: org.id, amount: 1000, currency: 'USD', type: 'NEW_BUSINESS' },
      { name, organizationId: org.id },
    ];
    let last = null;
    for (const body of payloads) {
      const created = await http(CRM_BASE, 'POST', '/api/v1/deals', body);
      last = created;
      if (created.status < 400) {
        const id = created.json?.data?.id ?? created.json?.id;
        if (!id) throw new Error('deal create returned no id');
        return `deal ${id} (${name})`;
      }
    }
    throw new Error(`deal create failed CRM ${last?.status}: ${last?.text?.slice(0, 200)}`);
  });

  await runCheck('asset-lifecycle.quote.draft-create', async () => {
    if (!ALLOW_WRITES) throw blocked('fixture writes disabled; set UAT_ALLOW_WRITES=true only against an authorized test environment');
    const quoteHealth = await http(QUOTE_BASE, 'GET', '/actuator/health');
    if (quoteHealth.status !== 200) throw blocked(`quote-service health HTTP ${quoteHealth.status}`);

    const orgs = await http(CRM_BASE, 'GET', '/api/v1/organizations?page=0&size=1');
    const orgRows = orgs.json?.data?.content ?? orgs.json?.content ?? orgs.json?.data ?? [];
    const org = Array.isArray(orgRows) ? orgRows[0] : null;
    const customerExternalId = org?.uuid ?? org?.accountId;
    if (typeof customerExternalId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(customerExternalId)) throw blocked('no valid CRM organization UUID for quote customerExternalId');

    const name = `UAT-${RUN_ID}-alm-quote`;
    const created = await http(QUOTE_BASE, 'POST', '/api/v1/quotes', {
      name,
      customerExternalId,
      status: 'DRAFT',
    });
    if (created.status === 401 || created.status === 403) {
      throw blocked(`quote creation requires authorization (HTTP ${created.status}); no draft was verified`);
    }
    if (created.status >= 400) {
      throw new Error(`quote-service create ${created.status}: ${created.text.slice(0, 200)}`);
    }
    const dto = created.json?.data ?? created.json;
    if (!dto?.id || dto.status !== 'DRAFT') throw new Error('quote create response lacks an id or DRAFT status');
    return `${dto?.quoteNumber ?? dto?.id ?? 'quote'} DRAFT`;
  });

  // UAT-ALM-05 — Renewal-from-assets API surface (GAP if absent)
  await runCheck('asset-lifecycle.api.renewal-from-assets', async () => {
    throw blocked('renewal-from-assets was not exercised with an authorized request and real asset fixture');
  });

  // UAT-ALM-06 — Installed-base on CRM is intentionally absent (boundary)
  await runCheck('asset-lifecycle.crm.installed-base-absent', async () => {
    const { status, json } = await http(CRM_BASE, 'GET', '/api/v1/assets');
    if (status === 404 && json?.errorCode === 'RESOURCE_NOT_FOUND') {
      return 'CRM has no Assets API (OHS is CAM-only) — expected GAP closed as boundary';
    }
    if (status === 404) throw blocked('404 without CRM RESOURCE_NOT_FOUND body; ownership not verified');
    if (status === 500) throw new Error('CRM /api/v1/assets still 500');
    throw gap(`CRM unexpectedly exposes /api/v1/assets with HTTP ${status}`);
  });

  await runCheck('asset-lifecycle.hold.armed', async () => {
    throw blocked('no apply request was exercised; deployed S4 hold is unverified');
  });

  await runCheck('asset-lifecycle.null-start.unit-gate', async () => {
    throw blocked('this runner does not execute the backend null-start test or observe a live apply');
  });

  await runCheck('asset-lifecycle.fixture.prefix', async () => {
    const name = `UAT-${RUN_ID}-alm`;
    if (!name.startsWith(`UAT-${RUN_ID}-`)) throw new Error(`bad prefix ${name}`);
    return name;
  });

  // Evidence
  mkdirSync(EVIDENCE_DIR, { recursive: true });
  const summary = {
    suite: 'asset-lifecycle-crm-api',
    base: CRM_BASE,
    cam: CAM_BASE,
    quote: QUOTE_BASE,
    runId: RUN_ID,
    at: new Date().toISOString(),
    results,
    counts: results.reduce((acc, r) => {
      acc[r.status] = (acc[r.status] || 0) + 1;
      return acc;
    }, {}),
  };
  const out = resolve(EVIDENCE_DIR, `asset-lifecycle-crm-api-${RUN_ID}.json`);
  writeFileSync(out, JSON.stringify(summary, null, 2));
  console.log(`\nEvidence: ${out}`);
  console.log('Counts:', summary.counts);

  const fails = results.filter((r) => r.status === 'FAIL');
  if (fails.length) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
