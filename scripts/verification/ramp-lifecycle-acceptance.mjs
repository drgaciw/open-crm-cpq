import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createHash, generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, realpath, readdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import path from 'node:path';

// Synthetic identities only. Real gateway/domain JWT validation, token relay, service-token
// calls, PostgreSQL, Kafka outbox/inbox and all lifecycle backends remain in the path.
// This does not exercise an Entra browser login, customer notification or downstream CRM delivery.
const root = path.resolve(fileURLToPath(new URL('../..', import.meta.url)));
const artifacts = path.join(root, '.acceptance-artifacts', 'lifecycle');
const launcher = path.join(root, 'scripts', 'verification', 'ramp-lifecycle-processes.ps1');
const manifestPath = path.join(artifacts, 'processes.json');
const origin = 'http://127.0.0.1:19080';
const issuer = 'http://127.0.0.1:19090/synthetic-tenant/v2.0';
const fixtureSecret = randomBytes(32).toString('base64url');
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'synthetic-lifecycle', use: 'sig', alg: 'RS256' };
const report = {
  startedUtc: new Date().toISOString(), sourceHead: null, database: null, passed: false,
  assertions: [], http: [], fixtureTokenClients: [], fixtureJwksReads: 0,
  knownGaps: ['Entra browser authorization-code/PKCE/session login', 'actual customer notification',
    'downstream QuoteAccepted CRM/subscription consumption', 'rollback fault injection',
    'historical distinct-event replay monotonic ordering', 'broader browser UAT/chaos'],
};
const jwt = (claims = {}) => {
  const now = Math.floor(Date.now() / 1000);
  const parts = [
    Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: jwk.kid })).toString('base64url'),
    Buffer.from(JSON.stringify({ iss: issuer, aud: 'api://opencrm-api', iat: now, nbf: now - 5, exp: now + 3600, ...claims })).toString('base64url'),
  ];
  return `${parts.join('.')}.${sign('RSA-SHA256', Buffer.from(parts.join('.')), privateKey).toString('base64url')}`;
};
const authorId = 'synthetic-ramp-author';
const vpId = 'synthetic-ramp-sales-vp';
const author = () => jwt({ sub: authorId, oid: authorId, scp: 'cpq', roles: ['CPQ.User'] });
const vp = () => jwt({ sub: vpId, oid: vpId, scp: 'cpq', roles: ['SalesVP'] });
const runtimeEnv = {
  ...process.env,
  OPENCRM_SECURITY_JWT_ISSUERURI: issuer,
  OPENCRM_SECURITY_JWT_JWKSETURI: 'http://127.0.0.1:19090/jwks',
  OPENCRM_SECURITY_JWT_AUDIENCES: 'api://opencrm-api',
  OPENCRM_SECURITY_CLIENT_TOKENURI: 'http://127.0.0.1:19090/token',
  OPENCRM_SECURITY_CLIENT_CLIENTSECRET: fixtureSecret,
  OPENCRM_SECURITY_CLIENT_SCOPE: 'api://opencrm-api/.default',
};
// The fixture owns both authentication lanes. Inherited workstation/cloud credentials
// must neither take precedence over it nor enable a real browser login/advisory call.
for (const key of ['AZURE_FEDERATED_TOKEN_FILE', 'AZURE_CLIENT_ID', 'AZURE_TENANT_ID', 'AZURE_AUTHORITY_HOST',
  'OPENCRM_SECURITY_CLIENT_FEDERATEDTOKENFILE', 'OPENCRM_GATEWAY_LOGIN_CLIENTID', 'OPENCRM_GATEWAY_LOGIN_CLIENTSECRET',
  'OPENCRM_ENV', 'JEV_API_KEY', 'JEV_BASE_URL']) delete runtimeEnv[key];
for (const key of Object.keys(runtimeEnv)) {
  if (/^AZURE_(FEDERATED_TOKEN_FILE|CLIENT_ID|TENANT_ID|AUTHORITY_HOST)$/i.test(key)
    || /^OPENCRM_SECURITY_CLIENT_FEDERATED/i.test(key) || /^OPENCRM_GATEWAY_LOGIN_/i.test(key)
    || /^JEV_(API_KEY|BASE_URL)$/i.test(key) || /^OPENCRM_ENV$/i.test(key)) delete runtimeEnv[key];
}
const fixture = createServer(async (request, response) => {
  response.setHeader('Content-Type', 'application/json');
  if (request.url === '/jwks' && request.method === 'GET') {
    report.fixtureJwksReads++;
    response.end(JSON.stringify({ keys: [jwk] }));
    return;
  }
  if (request.url === '/token' && request.method === 'POST') {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const form = new URLSearchParams(Buffer.concat(chunks).toString());
    const id = form.get('client_id');
    const allowedClients = ['quote-service', 'pricing-service', 'pricebook-service', 'configuration-engine', 'product-service', 'approval-routing', 'gateway-service']
      .map((name) => `ramp-acceptance-${name}`);
    if (form.get('grant_type') !== 'client_credentials' || form.get('client_secret') !== fixtureSecret || !allowedClients.includes(id)) {
      response.statusCode = 401;
      response.end(JSON.stringify({ error: 'invalid_client' }));
      return;
    }
    report.fixtureTokenClients.push(id);
    response.end(JSON.stringify({ access_token: jwt({ sub: id, azp: id, idtyp: 'app', roles: ['Internal.Invoke'] }), token_type: 'Bearer', expires_in: 3600 }));
    return;
  }
  response.statusCode = 404;
  response.end(JSON.stringify({ error: 'not_found' }));
});
const invoke = (file, args, { echo = true } = {}) => new Promise((resolve, reject) => {
  const child = spawn(file, args, { cwd: root, env: runtimeEnv, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk; if (echo) process.stdout.write(chunk); });
  child.stderr.on('data', (chunk) => { stderr += chunk; if (echo) process.stderr.write(chunk); });
  child.on('error', reject);
  // Windows background JVMs can inherit pipe handles after PowerShell itself exits.
  // Waiting for close would wait for those intentionally long-lived descendants.
  child.on(path.basename(file).toLowerCase() === 'pwsh.exe' ? 'exit' : 'close', (code) => {
    if (path.basename(file).toLowerCase() === 'pwsh.exe') {
      child.stdout.destroy();
      child.stderr.destroy();
    }
    code === 0 ? resolve(stdout) : reject(new Error(`${path.basename(file)} exited ${code}: ${stderr.slice(-1500)}`));
  });
});
const manage = (action, services, disabled = false) => invoke('pwsh.exe', [
  '-NoProfile', '-File', launcher, '-Action', action, '-IntegrationRoot', root,
  ...(services ? ['-Services', services.join(',')] : []), ...(disabled ? ['-RampDisabled'] : []),
]);
const mark = (name) => { report.assertions.push(name); console.log(`PASS ${name}`); };
const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));
const request = async (route, { method = 'GET', body, token = author(), status, code } = {}) => {
  const response = await fetch(`${origin}${route}`, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30000),
  });
  const text = await response.text();
  const envelope = text ? JSON.parse(text) : {};
  report.http.push({ route, method, status: response.status, errorCode: envelope.errorCode ?? null });
  if (status !== undefined) {
    assert.equal(response.status, status, `${method} ${route}: ${envelope.errorCode ?? envelope.message ?? ''}`);
    if (code) assert.equal(envelope.errorCode, code, route);
    return envelope;
  }
  assert.ok(response.ok && envelope.success !== false, `${method} ${route}: HTTP${response.status} ${envelope.errorCode ?? envelope.message ?? ''}`);
  return envelope.data;
};
const poll = async (description, action, predicate) => {
  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    const value = await action();
    if (predicate(value)) return value;
    await wait(1000);
  }
  throw new Error(`Timed out waiting for ${description}`);
};
const sql = async (statement) => {
  assert.match(report.database, /^ramp_next_acceptance_[0-9]{14}$/);
  return (await invoke('docker', ['exec', 'postgres', 'psql', '-U', 'postgres', '-d', report.database, '-v', 'ON_ERROR_STOP=1', '-tAc', statement], { echo: false })).trim();
};
const metricSnapshot = (quote) => Object.fromEntries(['tcv', 'acv', 'arr', 'exitArr', 'totalAmount'].map((key) => [key, Number(quote[key])]));
async function runPendingBrowserAcceptance(quoteId, mode) {
  const artifactSource = await realpath(process.env.RAMP_BROWSER_ARTIFACTSOURCE);
  const allowedSource = path.resolve('C:/Users/dgorn/.codex-worktrees/open-crm-cpq/quality-audit-20261009');
  assert.equal(artifactSource.toLowerCase(), allowedSource.toLowerCase(), 'use only the independently verified audit frontend build');
  const auditHead = (await invoke('git', ['-C', artifactSource, 'rev-parse', 'HEAD'], { echo: false })).trim();
  assert.equal((await invoke('git', ['diff', '--name-only', 'HEAD', auditHead, '--', 'frontend'], { echo: false })).trim(), '', 'frontend build source must match current committed tree');
  await invoke('git', ['-C', artifactSource, 'diff', '--quiet', 'HEAD', '--', 'frontend'], { echo: false });
  const frontendFiles = (await invoke('git', ['ls-files', 'frontend'], { echo: false })).trim().split(/\r?\n/);
  for (const file of frontendFiles) {
    const hash = (bytes) => createHash('sha256').update(bytes).digest('hex');
    assert.equal(hash(await readFile(path.join(root, file))), hash(await readFile(path.join(artifactSource, file))), `frontend source mismatch: ${file}`);
  }
  const freeGB = Number((await invoke('pwsh.exe', ['-NoProfile', '-Command', '(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB'], { echo: false })).trim());
  assert.ok(freeGB >= 1, `Need1GB free before owned browser; measured ${freeGB}`);
  const staticRoot = await realpath(path.join(artifactSource, 'frontend', 'dist', 'apps', 'crm', 'browser'));
  const buildHashes = [];
  const hashBuild = async (directory) => {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) await hashBuild(file);
      else if (entry.isFile()) buildHashes.push({ path: path.relative(staticRoot, file), sha256: createHash('sha256').update(await readFile(file)).digest('hex') });
      else throw new Error('Unexpected non-file build artifact');
    }
  };
  await hashBuild(staticRoot);
  report.browser ??= { artifactSource, sourceHead: auditHead, sourceFilesVerified: frontendFiles.length, buildHashes, checks: [], pageErrors: [], failedQuoteResponses: [] };
  const before = await request(`/api/v1/quotes/${quoteId}`);
  assert.equal(before.status, 'PENDING_APPROVAL');
  assert.match(before.name, /^Synthetic RAMP-NEXT-/);
  const browserOrigin = 'http://127.0.0.1:19091';
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.png': 'image/png' };
  const server = createServer(async (incoming, outgoing) => {
    try {
      const url = new URL(incoming.url, browserOrigin);
      if (url.pathname.startsWith('/api/')) {
        const chunks = [];
        for await (const chunk of incoming) chunks.push(chunk);
        const bytes = Buffer.concat(chunks);
        const response = await fetch(`${origin}${url.pathname}${url.search}`, {
          method: incoming.method, headers: { 'Content-Type': incoming.headers['content-type'] ?? 'application/json', Authorization: incoming.headers.authorization ?? '' },
          ...(bytes.length ? { body: bytes } : {}), signal: AbortSignal.timeout(30000),
        });
        outgoing.writeHead(response.status, { 'Content-Type': response.headers.get('content-type') ?? 'application/json' });
        outgoing.end(Buffer.from(await response.arrayBuffer()));
        return;
      }
      const requested = path.resolve(staticRoot, `.${decodeURIComponent(url.pathname)}`);
      assert.ok(requested === staticRoot || requested.startsWith(staticRoot + path.sep));
      let file;
      try { file = await realpath(requested); } catch { file = path.join(staticRoot, 'index.html'); }
      assert.ok(file.startsWith(staticRoot + path.sep));
      let bytes;
      try { bytes = await readFile(file); } catch { file = path.join(staticRoot, 'index.html'); bytes = await readFile(file); }
      outgoing.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' });
      outgoing.end(bytes);
    } catch (error) { outgoing.writeHead(502); outgoing.end(error.message); }
  });
  let browser;
  try {
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(19091, '127.0.0.1', resolve); });
    const require = createRequire(path.join(artifactSource, 'frontend', 'package.json'));
    browser = await require('playwright').chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.setDefaultTimeout(15000);
    page.on('pageerror', (error) => report.browser.pageErrors.push(error.message));
    page.on('response', (response) => {
      if (response.status() >= 400 && /\/api\/v1\/quotes(?:\/|$)/.test(new URL(response.url()).pathname)) {
        report.browser.failedQuoteResponses.push({ status: response.status(), path: new URL(response.url()).pathname });
      }
    });
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.origin !== browserOrigin) return route.abort();
      // Credentials go to the local API proxy only, never to assets or an external origin.
      return url.pathname.startsWith('/api/')
        ? route.continue({ headers: { ...route.request().headers(), authorization: `Bearer ${author()}` } }) : route.continue();
    });
    await page.goto(`${browserOrigin}/quotes/${quoteId}`);
    const banner = page.getByTestId('ramp-governance-banner');
    await banner.waitFor();
    assert.match(await banner.innerText(), /PENDING/);
    assert.equal(await page.getByTestId('save-schedule').isDisabled(), false, 'actual PENDING_APPROVAL must permit bound schedule edits');
    if (mode === 'edit') await page.getByTestId('period-unit-1').fill('40');
    const response = page.waitForResponse((result) => result.url().includes('/line-items/') && result.request().method() === 'PUT');
    await page.getByTestId('save-schedule').click();
    assert.ok((await response).ok());
    await page.reload();
    if (mode === 'identical') {
      await banner.waitFor();
      assert.equal(await page.getByTestId('ramp-stale-banner').count(), 0);
      assert.match(await page.getByTestId('preview-net-2').innerText(), /45\.00/);
      report.browser.checks.push('actual PENDING_APPROVAL identical schedule UI save preserves priced binding after reload');
    } else {
      await page.getByTestId('ramp-stale-banner').waitFor();
      for (const name of ['Submit', 'Send', 'Accept']) assert.equal(await page.getByRole('button', { name, exact: true }).isDisabled(), true);
      assert.equal(await banner.count(), 0);
      assert.match(await page.getByTestId('preview-net-2').innerText(), /—/);
      report.browser.checks.push('actual PENDING_APPROVAL UI edit persists DRAFT stale state, hides old rates and blocks downstream actions');
    }
    await page.screenshot({ path: path.join(artifacts, `pending-${mode}.png`), fullPage: true });
    assert.deepEqual(report.browser.pageErrors, []);
    assert.deepEqual(report.browser.failedQuoteResponses, []);
  } finally {
    if (browser) await browser.close();
    if (server.listening) { server.closeAllConnections(); await new Promise((resolve) => server.close(resolve)); }
  }
}
async function runLifecycleAcceptance() {
  if (!process.env.DB_PASSWORD) throw new Error('Supply DB_PASSWORD in the secure process environment; it is never written to evidence.');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  assert.equal(path.resolve(manifest.worktree), root);
  assert.equal(manifest.databaseCreatedByWave, false, 'start only from a prepared, never-run lifecycle manifest');
  const provenance = JSON.parse(await readFile(path.join(artifacts, 'artifact-provenance.json'), 'utf8'));
  report.sourceHead = provenance.targetHead;
  report.artifactSourceHead = provenance.sourceHead;
  report.artifactHashes = provenance.jars;
  report.database = manifest.database;
  await new Promise((resolve, reject) => { fixture.once('error', reject); fixture.listen(19090, '127.0.0.1', resolve); });
  await manage('start-broker');
  await manage('start', ['gateway-service', 'product-service']);
  await request('/api/v1/products', { token: null, status: 401 });
  await request('/api/v1/products', { token: jwt({ sub: authorId, scp: 'cpq', aud: 'api://wrong-audience' }), status: 401 });
  await request('/api/v1/products', { token: jwt({ sub: authorId, scp: 'cpq', exp: Math.floor(Date.now() / 1000) - 120 }), status: 401 });
  mark('gateway rejects absent, wrong-audience and expired bearer identities');
  const runId = `RAMP-NEXT-${Date.now()}`;
  const product = await request('/api/v1/products', { method: 'POST', body: {
    sku: `${runId}-SEAT`, name: `Synthetic ${runId} seats`, productType: 'SAAS_SEAT_NAMED', category: 'SUBSCRIPTION',
    listPrice: 50, currency: 'USD', billingFrequency: 'MONTHLY',
  } });
  assert.equal(product.createdBy, authorId, 'verified user identity survives gateway relay');
  report.productId = product.id;
  await manage('stop', ['product-service']);
  await manage('start', ['pricing-service', 'pricebook-service', 'quote-service']);
  const customerExternalId = `${runId}-CUSTOMER`;
  const region = `RN-${Date.now()}`;
  await request('/api/v1/pricing-customers', { method: 'POST', body: { externalId: customerExternalId, name: `Synthetic ${runId}`, segment: 'SMB', region: 'NA' } });
  const book = await request('/api/v1/price-books', { method: 'POST', body: { name: `Synthetic ${runId}`, region, currency: 'USD', isDefault: false, effectiveDate: null } });
  await request(`/api/v1/price-books/${book.id}/entries`, { method: 'POST', body: { productExternalId: String(product.id), listPrice: 50, currency: 'USD' } });
  await request(`/api/v1/price-books/${book.id}`, { method: 'PUT', body: { effectiveDate: '2020-01-01' } });
  const baseQuote = { customerExternalId, currency: 'USD', region, durationInMonths: 36, startDate: '2027-01-01', endDate: '2029-12-31' };
  const line = { productExternalId: String(product.id), productName: product.name, productType: 'SAAS_SEAT_NAMED', lineItemType: 'SAAS_SEAT_NAMED',
    quantity: 50, sortOrder: 0, listPrice: 50, billingFrequency: 'MONTHLY', schedule: { steps: [40, 45, 50].map((unitPrice, index) => ({
      sequence: index + 1, startMonth: index * 12 + 1, months: 12, quantity: 50, priceBasis: 'ABSOLUTE', unitPrice,
    })) } };
  const numericalQuote = await request('/api/v1/quotes', { method: 'POST', body: { ...baseQuote, name: `Synthetic ${runId} AC01` } });
  await request(`/api/v1/quotes/${numericalQuote.id}/line-items`, { method: 'POST', body: line });
  const ac01 = await request(`/api/v1/quotes/${numericalQuote.id}/calculate`, { method: 'POST', body: {} });
  assert.deepEqual(Object.fromEntries(['tcv', 'acv', 'arr', 'exitArr'].map((key) => [key, Number(ac01[key])])), { tcv: 81000, acv: 27000, arr: 24000, exitArr: 30000 });
  assert.equal(ac01.createdBy, authorId);
  mark('real gateway/auth quote→pricing→pricebook AC01 totals and verified author persist');
  const quote = await request('/api/v1/quotes', { method: 'POST', body: { ...baseQuote, name: `Synthetic ${runId} governance` } });
  const governedLine = structuredClone(line);
  governedLine.schedule.steps[0].unitPrice = 34;
  await request(`/api/v1/quotes/${quote.id}/line-items`, { method: 'POST', body: governedLine });
  const governed = await request(`/api/v1/quotes/${quote.id}/calculate`, { method: 'POST', body: {} });
  assert.equal(governed.approvalTier, 'SALES_VP');
  assert.equal(governed.rampGovernance.governingPeriod, 1);
  assert.equal(Number(governed.rampGovernance.discountPercent), 32);
  report.quoteId = quote.id;
  report.lineId = governed.lineItems[0].id;
  await manage('stop', ['pricing-service', 'pricebook-service']);
  await manage('start', ['product-service', 'configuration-engine', 'approval-routing']);
  const history = () => request(`/api/v1/approvals/quote/${quote.id}`);
  const pending = (rows) => rows.filter((row) => row.status === 'PENDING');
  const originalRows = await poll('real quote outbox→Kafka→canonical pending approval', history, (rows) => pending(rows).length === 1);
  const originalApproval = pending(originalRows)[0];
  assert.equal(originalApproval.approvalTier, 'SALES_VP');
  assert.equal(originalApproval.governingPeriod, 1);
  assert.equal(originalApproval.requesterId, 'service:ramp-acceptance-approval-routing');
  assert.equal(await sql(`SELECT count(*) FROM quote.outbox_events WHERE aggregate_id='${quote.id}' AND topic='cpq.quote.ramp.approval.binding.changed' AND published=true`), '1');
  mark('current ramp binding arrives through real transactional outbox, broker and canonical inbox');
  const inReview = await request(`/api/v1/quotes/${quote.id}/submit`, { method: 'POST', body: {} });
  assert.equal(inReview.status, 'IN_REVIEW');
  assert.ok(report.fixtureTokenClients.includes('ramp-acceptance-quote-service'), 'submit must obtain a service token for real CE preflight');
  const submitted = await request(`/api/v1/quotes/${quote.id}/approvals`, { method: 'POST', body: { tier: 'SALES_VP' } });
  assert.equal(submitted.id, originalApproval.id);
  assert.equal((await request(`/api/v1/quotes/${quote.id}`)).status, 'PENDING_APPROVAL');
  mark('actual submit uses authenticated configuration-engine/product and enters PENDING_APPROVAL');
  const beforeIdentical = await sql(`SELECT count(*) FROM quote.outbox_events WHERE aggregate_id='${quote.id}' AND topic='cpq.quote.ramp.approval.binding.changed'`);
  if (process.env.RAMP_BROWSER_ARTIFACTSOURCE) await runPendingBrowserAcceptance(quote.id, 'identical');
  else await request(`/api/v1/quotes/${quote.id}/line-items/${report.lineId}`, { method: 'PUT', body: governedLine });
  const unchanged = await request(`/api/v1/quotes/${quote.id}`);
  assert.equal(unchanged.status, 'PENDING_APPROVAL');
  assert.equal(unchanged.rampScheduleStale, false);
  assert.deepEqual(unchanged.lineItems[0].pricedPeriods, governed.lineItems[0].pricedPeriods);
  assert.equal(await sql(`SELECT count(*) FROM quote.outbox_events WHERE aggregate_id='${quote.id}' AND topic='cpq.quote.ramp.approval.binding.changed'`), beforeIdentical);
  assert.deepEqual(pending(await history()).map((row) => row.id), [originalApproval.id]);
  mark('identical full-PUT during actual pending approval preserves snapshot and emits no retirement');
  governedLine.schedule.steps[1].unitPrice = 40;
  if (process.env.RAMP_BROWSER_ARTIFACTSOURCE) await runPendingBrowserAcceptance(quote.id, 'edit');
  else await request(`/api/v1/quotes/${quote.id}/line-items/${report.lineId}`, { method: 'PUT', body: governedLine });
  const stale = await request(`/api/v1/quotes/${quote.id}`);
  assert.equal(stale.status, 'DRAFT');
  assert.equal(stale.rampScheduleStale, true);
  await poll('canonical retirement after pending edit', history, (rows) => rows.some((row) => row.id === originalApproval.id && row.status === 'SUPERSEDED'));
  await request(`/api/v1/quotes/approvals/${originalApproval.id}/approve`, { method: 'POST', body: {}, token: vp(), status: 409, code: 'APPROVAL_SUPERSEDED' });
  await request(`/api/v1/approvals/${originalApproval.id}/approve`, { method: 'POST', body: {}, token: vp(), status: 409, code: 'APPROVAL_SUPERSEDED' });
  await request(`/api/v1/quotes/${quote.id}/submit`, { method: 'POST', body: {}, status: 422, code: 'RAMP_SCHEDULE_STALE' });
  mark('changed pending edit invalidates quote and canonically supersedes; stale submit and late decisions refused');
  await manage('stop', ['product-service', 'configuration-engine', 'approval-routing']);
  await manage('start', ['pricing-service', 'pricebook-service']);
  const repriced = await request(`/api/v1/quotes/${quote.id}/calculate`, { method: 'POST', body: {} });
  assert.equal(repriced.rampScheduleStale, false);
  assert.equal(Number(repriced.tcv), 74400);
  assert.equal(Number(repriced.rampGovernance.discountPercent), 32, 'max unchanged despite second-period commercial edit');
  await manage('stop', ['pricing-service', 'pricebook-service']);
  await manage('start', ['product-service', 'configuration-engine', 'approval-routing']);
  const replacementRows = await poll('replacement canonical binding', history, (rows) => pending(rows).length === 1 && pending(rows)[0].id !== originalApproval.id);
  const replacement = pending(replacementRows)[0];
  assert.notEqual(replacement.pricingFingerprint, originalApproval.pricingFingerprint);
  await request(`/api/v1/quotes/${quote.id}/submit`, { method: 'POST', body: {} });
  const resubmitted = await request(`/api/v1/quotes/${quote.id}/approvals`, { method: 'POST', body: { tier: 'SALES_VP' } });
  assert.equal(resubmitted.id, replacement.id);
  await request(`/api/v1/quotes/approvals/${replacement.id}/approve`, { method: 'POST', body: {}, status: 403, code: 'APPROVER_ROLE_REQUIRED' });
  const approval = await request(`/api/v1/quotes/approvals/${replacement.id}/approve`, { method: 'POST', body: {}, token: vp() });
  assert.equal(approval.approver, vpId);
  assert.equal(approval.status, 'APPROVED');
  const approvedQuote = await request(`/api/v1/quotes/${quote.id}`);
  assert.equal(approvedQuote.status, 'APPROVED');
  mark('same-max edited pricing creates new fingerprint; token-relayed tier authorization403 then verified VP approval');
  const sent = await request(`/api/v1/quotes/${quote.id}/send`, { method: 'POST', body: {} });
  assert.equal(sent.status, 'SENT');
  const accepted = await request(`/api/v1/quotes/${quote.id}/accept`, { method: 'POST', body: {} });
  assert.equal(accepted.status, 'ACCEPTED');
  assert.equal(await sql(`SELECT (accepted_at IS NOT NULL)::text FROM quote.quotes WHERE id=${quote.id}`), 'true');
  assert.deepEqual(metricSnapshot(accepted), metricSnapshot(approvedQuote));
  assert.deepEqual(accepted.lineItems[0].pricedPeriods, repriced.lineItems[0].pricedPeriods);
  await request(`/api/v1/quotes/${quote.id}/line-items/${report.lineId}`, { method: 'PUT', body: governedLine, status: 400, code: 'QUOTE_NOT_EDITABLE' });
  await request(`/api/v1/quotes/${quote.id}/calculate`, { method: 'POST', body: {}, status: 409, code: 'QUOTE_NOT_EDITABLE' });
  await poll('accepted outbox acknowledgement', () => sql(`SELECT count(*) FROM quote.outbox_events WHERE aggregate_type='Quote' AND aggregate_id='${accepted.quoteNumber}' AND topic='cpq.quote.status.accepted' AND published=true`), (count) => count === '1');
  report.acceptedMetrics = metricSnapshot(accepted);
  report.originalApprovalId = originalApproval.id;
  report.replacementApprovalId = replacement.id;
  mark('gateway/auth full submit→canonical approval→send→accept preserves priced periods/totals and freezes accepted authoring');
  await manage('stop', ['product-service', 'configuration-engine', 'approval-routing', 'quote-service']);
  await manage('start', ['pricing-service', 'pricebook-service', 'quote-service'], true);
  const flagOffQuote = await request('/api/v1/quotes', { method: 'POST', body: { ...baseQuote, name: `Synthetic ${runId} flag off` } });
  await request(`/api/v1/quotes/${flagOffQuote.id}/line-items`, { method: 'POST', body: line, status: 422, code: 'RAMP_NOT_SUPPORTED' });
  assert.equal((await request(`/api/v1/quotes/${flagOffQuote.id}`)).lineItems.length, 0, 'disabled ramp authoring must not write a row');
  const flat = structuredClone(line);
  delete flat.schedule;
  await request(`/api/v1/quotes/${flagOffQuote.id}/line-items`, { method: 'POST', body: flat });
  const flatCalculated = await request(`/api/v1/quotes/${flagOffQuote.id}/calculate`, { method: 'POST', body: {} });
  assert.equal(flatCalculated.lineItems.length, 1);
  assert.ok(Number(flatCalculated.totalAmount) > 0);
  assert.equal(flatCalculated.rampScheduleStale, false);
  mark('process-only flag off refuses schedule with422/no write and preserves real flat quote calculate');
  report.passed = true;
}
await mkdir(artifacts, { recursive: true });
try {
  await runLifecycleAcceptance();
} catch (error) {
  report.failure = error.message;
  process.exitCode = 1;
  console.error(error.message);
} finally {
  report.cleanupErrors = [];
  try { await manage('stop'); } catch (error) { report.cleanupErrors.push(error.message); process.exitCode = 1; }
  try { await manage('stop-broker'); } catch (error) { report.cleanupErrors.push(error.message); process.exitCode = 1; }
  if (fixture.listening) await new Promise((resolve) => fixture.close(resolve));
  report.finishedUtc = new Date().toISOString();
  if (report.cleanupErrors.length) report.passed = false;
  await writeFile(path.join(artifacts, 'result.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify({ passed: report.passed, assertions: report.assertions.length, failure: report.failure ?? null, cleanupErrors: report.cleanupErrors }));
}
