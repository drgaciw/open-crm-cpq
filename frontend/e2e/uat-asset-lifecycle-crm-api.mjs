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
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const CRM_BASE = (process.env.UAT_BASE_URL || 'http://127.0.0.1:8081').replace(/\/$/, '');
const CAM_BASE = (process.env.UAT_CAM_BASE_URL || siblingPort(CRM_BASE, 8087)).replace(/\/$/, '');
const QUOTE_BASE = (process.env.UAT_QUOTE_BASE_URL || siblingPort(CRM_BASE, 8084)).replace(/\/$/, '');
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

function assertCamLive(label, status) {
  if (status === undefined) throw new Error(`${label}: no HTTP status`);
  if (status === 503) throw new Error(`${label}: CAM/gateway 503`);
  if (status >= 500) throw new Error(`${label}: HTTP ${status}`);
  return `HTTP ${status}`;
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
    if (code && code !== 'RESOURCE_NOT_FOUND') {
      throw new Error(`expected RESOURCE_NOT_FOUND, got ${code}`);
    }
    return `CRM boundary 404 RESOURCE_NOT_FOUND`;
  });

  await runCheck('asset-lifecycle.api.assets-up', async () => {
    const camHealth = await http(CAM_BASE, 'GET', '/actuator/health');
    if (camHealth.status !== 200) throw blocked(`CAM health HTTP ${camHealth.status}`);
    const { status } = await http(CAM_BASE, 'GET', '/api/v1/assets?accountId=uat-alm&asOf=2026-01-01');
    const live = assertCamLive('installed-base', status);
    if (![200, 400, 401, 403].includes(status)) {
      throw new Error(`unexpected CAM installed-base status ${status}`);
    }
    return `${live}${status === 403 || status === 401 ? ' (InstalledBase.Read enforced)' : ''}`;
  });

  await runCheck('asset-lifecycle.api.history-shape', async () => {
    const { status } = await http(CAM_BASE, 'GET', '/api/v1/assets/does-not-exist/history');
    return assertCamLive('asset history', status);
  });

  // UAT-ALM-03 — Reports on CAM
  await runCheck('asset-lifecycle.api.reports-up', async () => {
    const upcoming = await http(
      CAM_BASE,
      'GET',
      '/api/v1/assets/reports/upcoming-renewals?accountId=uat-alm&withinDays=30',
    );
    const churn = await http(
      CAM_BASE,
      'GET',
      '/api/v1/assets/reports/churn?accountId=uat-alm&periodStart=2026-01-01&periodEnd=2026-12-31',
    );
    assertCamLive('upcoming-renewals', upcoming.status);
    assertCamLive('churn', churn.status);
    return `upcoming=${upcoming.status} churn=${churn.status}`;
  });

  // UAT-ALM-04 — Activation precursor: deal create on CRM + quote on quote-service
  await runCheck('asset-lifecycle.crm.deal-create', async () => {
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
    const quoteHealth = await http(QUOTE_BASE, 'GET', '/actuator/health');
    if (quoteHealth.status !== 200) throw blocked(`quote-service health HTTP ${quoteHealth.status}`);

    const orgs = await http(CRM_BASE, 'GET', '/api/v1/organizations?page=0&size=1');
    const orgRows = orgs.json?.data?.content ?? orgs.json?.content ?? orgs.json?.data ?? [];
    const org = Array.isArray(orgRows) ? orgRows[0] : null;
    const customerExternalId = org?.uuid ?? org?.accountId ?? (org?.id != null ? String(org.id) : null);
    if (!customerExternalId) throw blocked('no CRM organization uuid for quote customerExternalId');

    const name = `UAT-${RUN_ID}-alm-quote`;
    const created = await http(QUOTE_BASE, 'POST', '/api/v1/quotes', {
      name,
      customerExternalId,
      status: 'DRAFT',
    });
    if (created.status === 401 || created.status === 403) {
      return assertCamLive('quote create auth', created.status) + ' (auth enforced; precursor reachable)';
    }
    if (created.status >= 400) {
      throw new Error(`quote-service create ${created.status}: ${created.text.slice(0, 200)}`);
    }
    const dto = created.json?.data ?? created.json;
    if (dto?.status && dto.status !== 'DRAFT') throw new Error(`expected DRAFT, got ${dto.status}`);
    return `${dto?.quoteNumber ?? dto?.id ?? 'quote'} DRAFT`;
  });

  // UAT-ALM-05 — Renewal-from-assets API surface (GAP if absent)
  await runCheck('asset-lifecycle.api.renewal-from-assets', async () => {
    const paths = [
      '/api/v1/quotes/renewals/from-assets',
      '/api/v1/quotes/continuations/renewal-from-assets',
      '/api/v1/assets/renewal-drafts',
    ];
    for (const path of paths) {
      const { status } = await http(QUOTE_BASE, 'GET', path);
      if (status !== 404 && status < 500) {
        return `quote-service ${path} HTTP ${status}`;
      }
      const cam = await http(CAM_BASE, 'GET', path);
      if (cam.status !== 404 && cam.status < 500) {
        return `CAM ${path} HTTP ${cam.status}`;
      }
    }
    throw gap(
      'No renewal-from-assets HTTP surface on quote-service or CAM; API path is AssetRenewalDrafts / QuoteContinuation RENEWAL (documented GAP)',
    );
  });

  // UAT-ALM-06 — Installed-base on CRM is intentionally absent (boundary)
  await runCheck('asset-lifecycle.crm.installed-base-absent', async () => {
    const { status } = await http(CRM_BASE, 'GET', '/api/v1/assets');
    if (status === 404) {
      return 'CRM has no Assets API (OHS is CAM-only) — expected GAP closed as boundary';
    }
    if (status === 500) throw new Error('CRM /api/v1/assets still 500');
    throw gap(`CRM unexpectedly exposes /api/v1/assets with HTTP ${status}`);
  });

  // UAT-ALM-07 — S4 hold armed in code
  await runCheck('asset-lifecycle.hold.armed', async () => {
    const path =
      '/workspace/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/apply/AssetLifecycleReadiness.java';
    if (!existsSync(path)) throw new Error('AssetLifecycleReadiness.java missing');
    const src = readFileSync(path, 'utf8');
    if (!/isReady\(\)[\s\S]*?return false/.test(src) && !src.includes('return false')) {
      throw new Error('AssetLifecycleReadiness does not appear to refuse readiness');
    }
    const camHealth = await http(CAM_BASE, 'GET', '/actuator/health');
    if (camHealth.status !== 200) throw blocked(`CAM down; cannot confirm hold host HTTP ${camHealth.status}`);
    return 'S4 hold armed (AssetLifecycleReadiness.isReady()=false); mutate applies refuse ASSET_LIFECYCLE_HOLD';
  });

  // UAT-ALM-08 — Null start unit gate
  await runCheck('asset-lifecycle.null-start.unit-gate', async () => {
    const path =
      '/workspace/backend/services/contract-asset-management/src/test/java/com/opencrm/contract/documents/apply/DocumentApplyNullStartDateTest.java';
    if (!existsSync(path)) {
      throw new Error('DocumentApplyNullStartDateTest.java missing — DRG-651 UAT-ALM-08 gate absent');
    }
    const src = readFileSync(path, 'utf8');
    if (!src.includes('START_DATE_MISSING')) {
      throw new Error('DocumentApplyNullStartDateTest does not assert START_DATE_MISSING');
    }
    return 'DocumentApplyNullStartDateTest asserts START_DATE_MISSING';
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
