/**
 * Read-only CAM acceptance against a loopback instance in JWT mode.
 * Supply UAT_CAM_BASE_URL, UAT_CAM_READER_TOKEN, UAT_CAM_SELLER_TOKEN, and
 * UAT_CAM_FIXTURE (path to the independently seeded fixture descriptor).
 * See docs/runbooks/asset-read-access.md for the capability boundary.
 */
import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const here = dirname(fileURLToPath(import.meta.url));
const artifact = process.env.UAT_CAM_ARTIFACT || resolve(here, '../.ci-tmp/uat-full/signed-cam.json');
const cases = [
  'asset-lifecycle.api.assets-up',
  'asset-lifecycle.api.history-shape',
  'asset-lifecycle.api.reports-up',
  'asset-lifecycle.type-safety.contract-parity',
];
const entries = [];
const samples = new Map();

function readConfig() {
  const base = new URL(process.env.UAT_CAM_BASE_URL ?? '');
  assert.equal(base.hostname, '127.0.0.1', 'A loopback CAM instance is required');
  assert.ok(base.port && /^https?:$/.test(base.protocol), 'CAM needs an explicit HTTP port');
  assert.ok(!base.username && !base.password && base.pathname === '/' && !base.search && !base.hash,
    'Use the CAM origin without credentials, path, or query');
  const readerToken = process.env.UAT_CAM_READER_TOKEN;
  const sellerToken = process.env.UAT_CAM_SELLER_TOKEN;
  assert.ok(readerToken && sellerToken && readerToken !== sellerToken, 'Separate reader and seller tokens are required');
  const fixture = JSON.parse(readFileSync(process.env.UAT_CAM_FIXTURE, 'utf8'));
  for (const field of ['accountId', 'activeAssetId', 'terminatedAssetId']) {
    assert.match(fixture[field], /^[0-9a-f-]{36}$/i, `Fixture ${field} must be a UUID`);
  }
  for (const field of ['asOf', 'effectiveEnd', 'periodStart', 'terminatedOn']) {
    assert.match(fixture[field], /^\d{4}-\d{2}-\d{2}$/, `Fixture ${field} must be a date`);
  }
  assert.ok(Number.isFinite(fixture.terminatedQuantity) && fixture.terminatedQuantity > 0,
    'Fixture termination must have a positive quantity');
  return { base: base.origin, readerToken, sellerToken, fixture };
}

function blocked(detail) {
  return Object.assign(new Error(detail), { blocked: true });
}

async function read(config, path, contractType) {
  const send = async (token) => {
    try {
      return await fetch(config.base + path, {
        headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
        redirect: 'error',
        signal: AbortSignal.timeout(10000),
      });
    } catch {
      throw blocked('CAM is unavailable at the configured loopback origin');
    }
  };
  const reader = await send(config.readerToken);
  if ([401, 403, 404, 502, 503, 504].includes(reader.status)) {
    throw blocked(`${path}: authorized read returned HTTP ${reader.status}`);
  }
  assert.equal(reader.status, 200, `${path}: reader must receive HTTP 200`);
  const { success, data } = await reader.json();
  assert.equal(success, true, `${path}: expected a successful API envelope`);
  const seller = await send(config.sellerToken);
  if (seller.status === 401) throw blocked(`${path}: seller fixture did not authenticate`);
  assert.equal(seller.status, 403, `${path}: authenticated seller must be forbidden`);
  samples.set(contractType, data);
  return data;
}

async function check(id, run) {
  try {
    entries.push({ id, status: 'PASS', detail: await run() });
  } catch (error) {
    entries.push({ id, status: error.blocked ? 'BLOCKED' : 'FAIL', detail: error.message });
  }
}

function checkContractTypes() {
  if (samples.size !== 5) throw blocked('Five successful signed payloads are required for parity');

  const samplePath = resolve(here, 'signed-cam-payloads.ts');
  const source = [
    "import type { AssetDto, InstalledAssetDto, AssetHistoryEntryDto, UpcomingRenewalsReport, AssetChurnReport } from '../libs/shared/models/src/lib/asset.model';",
    ...Array.from(samples, ([type, value], index) =>
      `const sample${index} = ${JSON.stringify(value)} satisfies ${type};`),
  ].join('\n');
  const options = {
    strict: true,
    noEmit: true,
    skipLibCheck: true,
    types: [],
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
  };
  const host = ts.createCompilerHost(options);
  const getSourceFile = host.getSourceFile.bind(host);
  host.getSourceFile = (name, languageVersion, ...rest) => name === samplePath
    ? ts.createSourceFile(name, source, ts.ScriptTarget.ES2022, true)
    : getSourceFile(name, languageVersion, ...rest);
  const program = ts.createProgram([samplePath], options, host);
  const diagnostics = ts.getPreEmitDiagnostics(program);
  assert.deepEqual(diagnostics.map(diagnostic => {
    const line = diagnostic.file?.getLineAndCharacterOfPosition(diagnostic.start ?? 0).line ?? 0;
    return `line ${line + 1}: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, ' ')}`;
  }), [], 'Live JSON does not match frontend asset models');
  return 'Five live JSON payloads satisfy the frontend TypeScript asset models under strict checking';
}

async function run(config) {
  const { fixture } = config;
  const account = encodeURIComponent(fixture.accountId);
  await check(cases[0], async () => {
    const assets = await read(config, `/api/v1/assets?accountId=${account}`, 'AssetDto[]');
    const installed = await read(config, `/api/v1/assets?accountId=${account}&asOf=${fixture.asOf}`, 'InstalledAssetDto[]');
    assert.equal(assets.find(asset => asset.uuid === fixture.activeAssetId)?.status, 'ACTIVE');
    assert.equal(assets.find(asset => asset.uuid === fixture.terminatedAssetId)?.status, 'TERMINATED');
    assert.deepEqual(installed.map(asset => asset.assetId), [fixture.activeAssetId]);
    assert.equal(installed[0].accountId, fixture.accountId);
    return 'Reader sees populated asset list and as-of installed base; seller receives 403 for both';
  });
  await check(cases[1], async () => {
    const history = await read(config, `/api/v1/assets/${encodeURIComponent(fixture.terminatedAssetId)}/history`,
      'AssetHistoryEntryDto[]');
    assert.deepEqual(history.map(event => event.eventType), ['CREATED', 'TERMINATED']);
    assert.equal(history[1].effectiveDate, fixture.terminatedOn);
    assert.ok(history.every(event => event.accountId === fixture.accountId));
    return 'Reader sees creation and termination history in order; seller receives 403';
  });
  await check(cases[2], async () => {
    const renewals = await read(config,
      `/api/v1/assets/reports/upcoming-renewals?accountId=${account}&windowStart=${fixture.asOf}&windowEnd=${fixture.effectiveEnd}`,
      'UpcomingRenewalsReport');
    const churn = await read(config,
      `/api/v1/assets/reports/churn?accountId=${account}&periodStart=${fixture.periodStart}&periodEnd=${fixture.asOf}`,
      'AssetChurnReport');
    assert.equal(renewals.assetCount, 1);
    assert.deepEqual(renewals.assets.map(asset => asset.assetId), [fixture.activeAssetId]);
    assert.equal(churn.eventCount, 1);
    assert.equal(churn.assetCount, 1);
    assert.equal(churn.events[0].assetId, fixture.terminatedAssetId);
    assert.equal(churn.events[0].churnKind, 'TERMINATED');
    assert.equal(churn.quantityChurned, fixture.terminatedQuantity);
    return 'Reader sees one renewal and one exact churn event; seller receives 403 for both reports';
  });
  await check(cases[3], checkContractTypes);
}

try {
  await run(readConfig());
} catch (error) {
  for (const id of cases) entries.push({ id, status: 'BLOCKED', detail: error.message });
}

mkdirSync(dirname(artifact), { recursive: true });
writeFileSync(artifact, JSON.stringify({ runner: 'signed-cam', entries }, null, 2) + '\n');
for (const entry of entries) console.log(`${entry.status} ${entry.id} — ${entry.detail}`);
if (entries.some(entry => entry.status !== 'PASS')) process.exitCode = 1;
