/**
 * Expanded chaos testing for Open CRM — Playwright + route/network faults.
 *
 * Adds: malformed JSON, 401/403/429, partial responses, flaky retries,
 * concurrent creates, history spam, viewport extremes, clipboard paste bombs,
 * CPU-like click storms, storage corruption, CSP-ish payloads, race cancel.
 *
 *   cd frontend && node e2e/chaos-crm.mjs
 */
import { writeFileSync } from 'node:fs';
import { launch, BASE } from './lib/browser.mjs';

const stamp = Date.now();
const findings = [];

function record(id, status, detail = '', severity = 'info') {
  findings.push({ id, status, detail, severity });
  const tag = status === 'PASS' ? 'PASS' : status === 'FAIL' ? 'FAIL' : 'INFO';
  console.log(`${tag}  [${severity}] ${id}${detail ? ` — ${detail}` : ''}`);
}

const CRM_PAGES = [
  ['/dashboard', 'Dashboard'],
  ['/organizations', 'Organizations'],
  ['/contacts', 'Contacts'],
  ['/leads', 'Leads'],
  ['/deals', 'Deals'],
];

const CPQ_PAGES = [
  ['/catalog', 'Product Catalog'],
  ['/quotes', 'Quotes'],
  ['/approvals', 'Approvals'],
  ['/price-books', 'Price Books'],
  ['/pricing-customers', 'Pricing customers'],
  ['/rule-sets', 'Rule sets'],
  ['/product-bundles', 'Product Bundles'],
  ['/pricing/simulator', 'Pricing Simulator'],
];

const XSS = `<img src=x onerror=alert(1)>`;
const UNICODE = '测试🚀 مرحبا';
const OVERSIZE = 'A'.repeat(8000);
const SQLISH = `'; DROP TABLE organizations; --`;
const NULL_BYTE = 'null\u0000byte';
const RTL = '\u202Eevil\u202C';

async function goto(page, path, opts = {}) {
  await page.goto(`${BASE}${path}`, {
    waitUntil: opts.waitUntil || 'domcontentloaded',
    timeout: opts.timeout || 45000,
  });
  await page.waitForLoadState('networkidle').catch(() => {});
}

async function alive(page) {
  return (await page.locator('app-root').count()) > 0;
}

async function createEnabled(page) {
  const btn = page.getByRole('button', { name: /^(Create|Save)$/ });
  return btn.isEnabled().catch(() => false);
}

async function uncaughtErrors(page) {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });
  return errors;
}

async function main() {
  const { browser, context, page } = await launch({
    timeoutMs: 20000,
    extraArgs: [
      '--headless=new',
      '--disable-gpu',
      '--disable-extensions',
      '--disable-background-networking',
      '--renderer-process-limit=2',
      '--disable-software-rasterizer',
    ],
    contextOptions: { ignoreHTTPSErrors: true },
  });
  page.on('crash', () => record('page-crash', 'FAIL', 'renderer crashed', 'critical'));
  const runtimeErrors = await uncaughtErrors(page);

  // Intercept alerts for XSS detection
  await page.addInitScript(() => {
    window.__chaosAlert = false;
    window.alert = () => {
      window.__chaosAlert = true;
    };
  });

  try {
    // --- 1. Rapid navigation storm (2x all pages + reverse commit) ---
    const navPaths = [...CRM_PAGES, ...CPQ_PAGES].map(([p]) => p);
    for (let i = 0; i < 2; i++) {
      for (const path of navPaths) {
        await goto(page, path, { timeout: 30000 });
      }
      for (const path of [...navPaths].reverse()) {
        await page.goto(`${BASE}${path}`, { waitUntil: 'commit', timeout: 20000 }).catch(() => {});
      }
    }
    record('rapid-nav-storm', (await alive(page)) ? 'PASS' : 'FAIL', `landed=${page.url()}`);

    // --- 2. Each page heading survives ---
    for (const [path, heading] of [...CRM_PAGES, ...CPQ_PAGES]) {
      await goto(page, path);
      try {
        await page.getByRole('heading', { name: heading }).first().waitFor({ timeout: 10000 });
        record(`page-shell:${path}`, 'PASS');
      } catch (e) {
        record(`page-shell:${path}`, 'FAIL', e.message.slice(0, 120), 'high');
      }
    }

    // --- 3. Empty required fields ---
    await goto(page, '/organizations/new');
    const createBtn = page.getByRole('button', { name: 'Create' });
    const disabledEmpty = await createBtn.isDisabled().catch(() => false);
    await createBtn.click({ force: true }).catch(() => {});
    await page.waitForTimeout(400);
    record(
      'empty-required-org',
      disabledEmpty || page.url().includes('/organizations/new') ? 'PASS' : 'FAIL',
      `disabled=${disabledEmpty} url=${page.url()}`,
    );

    // --- 4. XSS / unicode / oversized / SQL / null / RTL ---
    await goto(page, '/organizations/new');
    await page
      .getByRole('textbox', { name: 'Organization Name' })
      .fill(`Chaos ${XSS} ${UNICODE} ${SQLISH} ${NULL_BYTE} ${RTL}`);
    await page.getByRole('textbox', { name: 'Description' }).fill(OVERSIZE);
    await page.getByRole('textbox', { name: 'Website' }).fill('javascript:alert(1)');
    if (await createBtn.isEnabled()) {
      await createBtn.click();
      await page.waitForTimeout(2500);
      const alertFired = await page.evaluate(() => !!window.__chaosAlert);
      record(
        'xss-sql-null-org-create',
        !alertFired ? 'PASS' : 'FAIL',
        `url=${page.url()}`,
        alertFired ? 'critical' : 'info',
      );
    } else {
      record('xss-sql-null-org-create', 'INFO', 'Create stayed disabled');
    }

    // --- 5. Double-submit + triple-tab concurrent creates ---
    await goto(page, '/contacts/new');
    const fn = `Chaos${stamp}`;
    await page.getByRole('textbox', { name: 'First Name' }).fill(fn);
    await page.getByRole('textbox', { name: 'Last Name' }).fill('DoubleSubmit');
    await page.getByRole('textbox', { name: 'Email' }).fill(`chaos.ds${stamp}@example.com`);
    const btn = page.getByRole('button', { name: 'Create' });
    await Promise.all([btn.click().catch(() => {}), btn.click().catch(() => {}), btn.click().catch(() => {})]);
    await page.waitForTimeout(2500);

    const tabs = await Promise.all([context.newPage(), context.newPage()]);
    await Promise.all(
      tabs.map(async (p, i) => {
        await goto(p, '/contacts/new');
        await p.getByRole('textbox', { name: 'First Name' }).fill(`${fn}T${i}`);
        await p.getByRole('textbox', { name: 'Last Name' }).fill('Race');
        await p.getByRole('textbox', { name: 'Email' }).fill(`chaos.race${stamp}${i}@example.com`);
        await p.getByRole('button', { name: 'Create' }).click().catch(() => {});
      }),
    );
    await page.waitForTimeout(3000);
    for (const t of tabs) await t.close();
    await goto(page, '/contacts');
    const matches = await page.getByText(`${fn} DoubleSubmit`).count();
    record('double-submit-contact', matches <= 1 ? 'PASS' : 'FAIL', `rows=${matches}`, matches <= 1 ? 'info' : 'high');
    record('multi-tab-create-race', (await alive(page)) ? 'PASS' : 'FAIL');

    // --- 6. API abort / 500 / 401 / 403 / 429 / malformed JSON / truncated ---
    await page.route('**/api/v1/organizations**', (route) => route.abort('failed'));
    await goto(page, '/organizations');
    await page.waitForTimeout(1200);
    record(
      'api-abort-orgs',
      (await alive(page)) ? 'PASS' : 'FAIL',
      `errorUi=${(await page.getByText(/error|failed|unexpected|unable/i).count()) > 0}`,
    );
    await page.unroute('**/api/v1/organizations**');

    await page.route('**/api/v1/leads', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ success: false, message: 'Chaos 500', errorCode: 'INTERNAL_SERVER_ERROR' }),
        });
      } else await route.continue();
    });
    await goto(page, '/leads/new');
    await page.getByRole('textbox', { name: 'Title' }).fill(`Chaos Lead 500 ${stamp}`);
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForTimeout(2000);
    const leadCreateReenabled = await createEnabled(page);
    record(
      'api-500-lead-create-recover',
      (await alive(page)) && (page.url().includes('/leads/new') ? leadCreateReenabled : true) ? 'PASS' : 'FAIL',
      `url=${page.url()} createEnabled=${leadCreateReenabled}`,
      'high',
    );
    await page.unroute('**/api/v1/leads');

    for (const [code, name] of [
      [401, 'api-401-contacts'],
      [403, 'api-403-contacts'],
      [429, 'api-429-contacts'],
    ]) {
      await page.route('**/api/v1/contacts**', async (route) => {
        await route.fulfill({
          status: code,
          contentType: 'application/json',
          body: JSON.stringify({ success: false, message: `Chaos ${code}`, errorCode: String(code) }),
        });
      });
      await goto(page, '/contacts');
      await page.waitForTimeout(1000);
      record(name, (await alive(page)) ? 'PASS' : 'FAIL', `url=${page.url()}`);
      await page.unroute('**/api/v1/contacts**');
    }

    await page.route('**/api/v1/deals**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '{not-json!!!',
      });
    });
    await goto(page, '/deals');
    await page.waitForTimeout(1200);
    record('malformed-json-deals', (await alive(page)) ? 'PASS' : 'FAIL');
    await page.unroute('**/api/v1/deals**');

    await page.route('**/api/v1/organizations**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '{"success":true,"data":[{"id":1,"name":"Trunc',
      });
    });
    await goto(page, '/organizations');
    await page.waitForTimeout(1200);
    record('truncated-json-orgs', (await alive(page)) ? 'PASS' : 'FAIL');
    await page.unroute('**/api/v1/organizations**');

    // --- 7. Flaky API (fail twice then succeed) ---
    let orgHits = 0;
    await page.route('**/api/v1/organizations**', async (route) => {
      orgHits += 1;
      if (orgHits <= 2) {
        await route.abort('connectionfailed');
        return;
      }
      await route.continue();
    });
    await goto(page, '/organizations');
    await page.waitForTimeout(800);
    const retryBtn = page.getByRole('button', { name: /Retry/i });
    if ((await retryBtn.count()) > 0) {
      await retryBtn.first().click().catch(() => {});
      await page.waitForTimeout(500);
      await retryBtn.first().click().catch(() => {});
      await page.waitForTimeout(1500);
    } else {
      await page.reload({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await page.waitForTimeout(1500);
    }
    record('flaky-api-retry', (await alive(page)) ? 'PASS' : 'FAIL', `hits=${orgHits}`);
    await page.unroute('**/api/v1/organizations**');

    // --- 8. Slow API then navigate away / cancel ---
    await page.route('**/api/v1/deals**', async (route) => {
      await new Promise((r) => setTimeout(r, 4000));
      await route.continue().catch(() => {});
    });
    const slowNav = goto(page, '/deals');
    await page.waitForTimeout(300);
    await goto(page, '/dashboard');
    await slowNav.catch(() => {});
    record('slow-api-nav-away', (await alive(page)) ? 'PASS' : 'FAIL', `url=${page.url()}`);
    await page.unroute('**/api/v1/deals**').catch(() => {});

    // Create with delayed POST then Cancel (abort must not crash suite)
    await page.route('**/api/v1/organizations', async (route) => {
      try {
        if (route.request().method() === 'POST') {
          await new Promise((r) => setTimeout(r, 5000));
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, data: { id: 999999, name: 'Late' } }),
          });
        } else {
          await route.continue();
        }
      } catch {
        /* request aborted after Cancel / navigation */
      }
    });
    await goto(page, '/organizations/new');
    await page.getByRole('textbox', { name: 'Organization Name' }).fill(`LateCancel ${stamp}`);
    await page.getByRole('button', { name: 'Create' }).click().catch(() => {});
    await page.waitForTimeout(200);
    await page.getByRole('button', { name: 'Cancel' }).click().catch(() => {});
    await page.waitForTimeout(1500);
    record('create-then-cancel-race', (await alive(page)) ? 'PASS' : 'FAIL', `url=${page.url()}`);
    await page.unroute('**/api/v1/organizations').catch(() => {});

    // --- 9. Mid-form abandon + back/forward spam ---
    await goto(page, '/deals/new');
    await page.getByRole('textbox', { name: 'Deal Name' }).fill(`Abandon ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Amount ($)' }).fill('99999');
    await goto(page, '/dashboard');
    for (let i = 0; i < 8; i++) {
      await page.goBack().catch(() => {});
      await page.goForward().catch(() => {});
    }
    await page.waitForTimeout(800);
    record('history-spam', (await alive(page)) ? 'PASS' : 'FAIL', `url=${page.url()}`);

    // --- 10. Reload storm ---
    await goto(page, '/leads');
    for (let i = 0; i < 8; i++) {
      await page.reload({ waitUntil: 'domcontentloaded' });
    }
    await page
      .getByRole('heading', { name: 'Leads' })
      .first()
      .waitFor({ timeout: 15000 })
      .catch(() => {});
    record(
      'reload-storm-leads',
      (await page.getByRole('heading', { name: 'Leads' }).count()) > 0 ? 'PASS' : 'FAIL',
      `url=${page.url()}`,
    );

    // --- 11. Invalid email / negative / NaN / huge amounts ---
    await goto(page, '/contacts/new');
    await page.getByRole('textbox', { name: 'First Name' }).fill('Bad');
    await page.getByRole('textbox', { name: 'Last Name' }).fill('Email');
    await page.getByRole('textbox', { name: 'Email' }).fill('not-an-email');
    const contactCreate = page.getByRole('button', { name: 'Create' });
    const emailBlocked = await contactCreate.isDisabled().catch(() => false);
    if (!emailBlocked) {
      await contactCreate.click();
      await page.waitForTimeout(1200);
    }
    record(
      'invalid-email',
      emailBlocked || page.url().includes('/contacts/new') ? 'PASS' : 'INFO',
      `disabled=${emailBlocked}`,
    );

    await goto(page, '/deals/new');
    await page.getByRole('textbox', { name: 'Deal Name' }).fill(`NegAmt ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Amount ($)' }).fill('-50');
    await page.getByRole('spinbutton', { name: 'Probability (%)' }).fill('10');
    const dealCreate = page.getByRole('button', { name: 'Create' });
    const negBlocked = await dealCreate.isDisabled().catch(() => false);
    record(
      'invalid-amount-probability',
      negBlocked ? 'PASS' : 'FAIL',
      `negAmountDisabled=${negBlocked} url=${page.url()}`,
      negBlocked ? 'info' : 'high',
    );

    await goto(page, '/deals/new');
    await page.getByRole('textbox', { name: 'Deal Name' }).fill(`HugeAmt ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Amount ($)' }).fill('999999999999999');
    if (await page.getByRole('button', { name: 'Create' }).isEnabled()) {
      await page.getByRole('button', { name: 'Create' }).click();
      await page.waitForTimeout(1500);
    }
    record('huge-amount', (await alive(page)) ? 'PASS' : 'FAIL', `url=${page.url()}`);

    // --- 12. Viewport extremes + click storm ---
    await page.setViewportSize({ width: 320, height: 480 });
    await goto(page, '/organizations/new');
    for (let i = 0; i < 25; i++) {
      await page.getByRole('button', { name: 'Create' }).click({ force: true }).catch(() => {});
    }
    record('mobile-click-storm', (await alive(page)) ? 'PASS' : 'FAIL');
    await page.setViewportSize({ width: 2560, height: 1440 });
    await goto(page, '/dashboard');
    record('ultrawide-dashboard', (await alive(page)) ? 'PASS' : 'FAIL');
    await page.setViewportSize({ width: 1400, height: 900 });

    // --- 13. localStorage / sessionStorage corruption ---
    await page.evaluate(() => {
      try {
        localStorage.setItem('opencrm', '{broken');
        localStorage.setItem('auth', '<<<');
        sessionStorage.setItem('crm-state', 'null');
      } catch {
        /* ignore quota */
      }
    });
    await goto(page, '/dashboard');
    await goto(page, '/organizations');
    record('storage-corruption', (await alive(page)) ? 'PASS' : 'FAIL');

    // --- 14. Paste bomb into search / name ---
    await goto(page, '/contacts');
    const search = page.getByRole('textbox').first();
    if ((await search.count()) > 0) {
      await search.fill(OVERSIZE.slice(0, 4000)).catch(() => {});
      await page.waitForTimeout(500);
    }
    record('paste-bomb-list', (await alive(page)) ? 'PASS' : 'FAIL');

    // --- 15. Offline create recovery (Playwright context offline) ---
    await context.setOffline(true);
    await goto(page, '/organizations/new').catch(() => {});
    // may fail navigation; try fill if still there
    if (page.url().includes('/organizations/new')) {
      await page.getByRole('textbox', { name: 'Organization Name' }).fill(`Offline ${stamp}`).catch(() => {});
      await page.getByRole('button', { name: 'Create' }).click().catch(() => {});
      await page.waitForTimeout(1500);
      const enabledOffline = await createEnabled(page);
      record(
        'offline-create-recover',
        enabledOffline ? 'PASS' : 'FAIL',
        `createEnabled=${enabledOffline}`,
        enabledOffline ? 'info' : 'high',
      );
    } else {
      // navigate while offline may fail — still require app recover when back online
      record('offline-create-recover', 'INFO', 'could not reach form offline');
    }
    await context.setOffline(false);
    await goto(page, '/organizations/new');
    await page.getByRole('textbox', { name: 'Organization Name' }).fill(`OnlineAfter ${stamp}`);
    const onlineEnabled = await createEnabled(page);
    record('online-after-offline', onlineEnabled ? 'PASS' : 'FAIL', `createEnabled=${onlineEnabled}`);

    // --- 16. Keyboard / focus chaos ---
    await goto(page, '/leads/new');
    for (let i = 0; i < 40; i++) {
      await page.keyboard.press('Tab').catch(() => {});
    }
    await page.keyboard.press('Escape').catch(() => {});
    await page.keyboard.press('Enter').catch(() => {});
    record('keyboard-focus-storm', (await alive(page)) ? 'PASS' : 'FAIL');

    // --- 17. Concurrent tab race (nav) — distinct pages, tolerate abort races ---
    const page2 = await context.newPage();
    await Promise.all([
      goto(page, '/organizations').catch(() => {}),
      goto(page2, '/contacts').catch(() => {}),
    ]);
    await goto(page, '/leads').catch(() => {});
    record(
      'multi-tab-nav-race',
      (await alive(page)) && (await alive(page2)) ? 'PASS' : 'FAIL',
      `p1=${page.url()} p2=${page2.url()}`,
    );
    await page2.close();

    // --- Runtime errors summary ---
    const criticalJs = runtimeErrors.filter(
      (e) =>
        !e.includes('Failed to load resource') &&
        !e.includes('net::ERR_') &&
        !e.includes('favicon') &&
        !e.includes('NG0') &&
        !e.includes('Http failure') &&
        !e.includes('HttpErrorResponse'),
    );
    record(
      'uncaught-js-errors',
      criticalJs.length === 0 ? 'PASS' : 'FAIL',
      criticalJs.slice(0, 5).join(' | ') || 'none',
      criticalJs.length ? 'high' : 'info',
    );
  } catch (e) {
    record('fatal', 'FAIL', String(e.stack || e).slice(0, 600), 'critical');
  } finally {
    await browser.close();
  }

  const report = {
    stamp,
    base: BASE,
    summary: {
      pass: findings.filter((f) => f.status === 'PASS').length,
      fail: findings.filter((f) => f.status === 'FAIL').length,
      info: findings.filter((f) => f.status === 'INFO').length,
    },
    findings,
  };
  writeFileSync('/tmp/opencrm-chaos-report.json', JSON.stringify(report, null, 2));
  console.log(`\n${report.summary.pass} pass / ${report.summary.fail} fail / ${report.summary.info} info`);
  console.log('Report: /tmp/opencrm-chaos-report.json');
  process.exit(report.summary.fail ? 1 : 0);
}

main();
