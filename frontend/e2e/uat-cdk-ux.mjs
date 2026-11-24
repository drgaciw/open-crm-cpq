/**
 * Playwright browser UAT for Angular Material CDK UX/UI Improvements (Phases 1-5).
 * Prerequisites: frontend :4200, gateway :8080, crm-service :8081.
 *
 *   cd frontend && node e2e/uat-cdk-ux.mjs
 *
 * Scenarios:
 *   A) Kanban drag-and-drop (legal 1-step advance + reload persistence, illegal skip refused,
 *      LOST dialog cancel & confirm, WON without accepted quote clean error + rollback,
 *      no accidental navigation after drag, mat-menu stage actions parity).
 *   B) Command palette (Ctrl+K & toolbar button open, typing filter, ArrowDown/Up
 *      aria-activedescendant, Enter navigation, Escape & backdrop close with focus return,
 *      items match sidebar links).
 *   C) Deal detail read-only StagePath (shows stages, zero buttons/tab stops inside,
 *      exactly one aria-current="step").
 *   D) Smoke (Opportunities copy intact, data-testid="data-table" on CRM lists).
 */
import { launch, goto, BASE } from './lib/browser.mjs';
import { cdkDragTo } from './lib/kanban-drag.mjs';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const stamp = Date.now();
const results = [];
const artifactDir = process.env.UAT_ARTIFACT_DIR?.trim() || '';

function ok(name, detail = '') {
  results.push({ name, status: 'PASS', detail });
  console.log(`PASS  ${name}${detail ? ` — ${detail}` : ''}`);
}

async function fail(name, detail, page = null) {
  results.push({ name, status: 'FAIL', detail });
  console.error(`FAIL  ${name} — ${detail}`);
  if (artifactDir && page) {
    try {
      mkdirSync(artifactDir, { recursive: true });
      const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 60);
      await page.screenshot({ path: join(artifactDir, `fail-${slug}.png`), fullPage: true });
    } catch {
      // Best-effort failure screenshot
    }
  }
}

/**
 * Bootstrap CSRF token via gateway and return headers for mutating API requests.
 */
async function fetchCsrfToken(context) {
  const res = await context.request.get(`${BASE}/api/v1/csrf`, {
    headers: { Origin: BASE },
  });
  if (!res.ok()) {
    throw new Error(`GET /api/v1/csrf failed with HTTP ${res.status()}`);
  }
  const body = await res.json().catch(() => ({}));
  const cookies = await context.cookies(BASE);
  const xsrfCookie = cookies.find((c) => c.name === 'XSRF-TOKEN');
  const token = xsrfCookie?.value || body?.token;
  if (!token) {
    throw new Error('Could not obtain XSRF-TOKEN from /api/v1/csrf');
  }
  return token;
}

async function apiPost(context, csrfToken, path, payload) {
  const res = await context.request.post(`${BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      Origin: BASE,
      'X-XSRF-TOKEN': csrfToken,
    },
    data: payload,
  });
  if (!res.ok()) {
    const text = await res.text().catch(() => '');
    throw new Error(`POST ${path} failed (${res.status()}): ${text.slice(0, 200)}`);
  }
  const body = await res.json();
  return body?.data ?? body;
}

function stageList(page, stage) {
  return page.locator(`#kanban-stage-${stage}`);
}

function dealCardInStage(page, stage, dealName) {
  return stageList(page, stage).locator('mat-card.deal-card', { hasText: dealName }).first();
}

async function main() {
  // Wide viewport so sidebar + all 7 Kanban columns are simultaneously visible without horizontal scrolling
  const { browser, context, page } = await launch({
    timeoutMs: 45000,
    viewport: { width: 2560, height: 1000 },
  });

  try {
    // Seed mock CRM records via gateway API with CSRF token
    const csrfToken = await fetchCsrfToken(context);

    await apiPost(context, csrfToken, '/api/v1/organizations', {
      name: `UAT CDK Org ${stamp}`,
      city: 'Austin',
    });
    await apiPost(context, csrfToken, '/api/v1/contacts', {
      firstName: 'Cdk',
      lastName: `Uat${stamp}`,
      email: `cdk.uat.${stamp}@example.com`,
    });
    await apiPost(context, csrfToken, '/api/v1/leads', {
      title: `UAT CDK Lead ${stamp}`,
      estimatedValue: 15000,
    });

    const dealAdvanceName = `UAT CDK Drag ${stamp}`;
    const dealLoseName = `UAT CDK Lose ${stamp}`;
    const dealDetailName = `UAT CDK Path ${stamp}`;

    const dealAdvance = await apiPost(context, csrfToken, '/api/v1/deals', {
      name: dealAdvanceName,
      amount: 25000,
      currency: 'USD',
    });
    await apiPost(context, csrfToken, '/api/v1/deals', {
      name: dealLoseName,
      amount: 18000,
      currency: 'USD',
    });
    const dealDetail = await apiPost(context, csrfToken, '/api/v1/deals', {
      name: dealDetailName,
      amount: 32000,
      currency: 'USD',
    });

    // =========================================================================
    // A) Kanban drag-and-drop
    // =========================================================================
    await goto(page, '/deals/kanban');
    await stageList(page, 'PROSPECTING').waitFor({ state: 'visible', timeout: 15000 });
    await dealCardInStage(page, 'PROSPECTING', dealAdvanceName).waitFor({ state: 'visible' });

    // A1: Legal one-step advance (PROSPECTING -> QUALIFICATION) + no nav + persists after reload
    try {
      const advanceRes = page
        .waitForResponse(
          (r) =>
            r.url().includes(`/api/v1/deals/${dealAdvance.id}/advance`) &&
            r.request().method() === 'POST',
          { timeout: 10000 },
        )
        .catch(() => null);

      await cdkDragTo(
        page,
        dealCardInStage(page, 'PROSPECTING', dealAdvanceName),
        stageList(page, 'QUALIFICATION'),
      );
      await advanceRes;
      await page.waitForLoadState('networkidle').catch(() => {});

      // Must NOT navigate away from /deals/kanban
      if (!page.url().includes('/deals/kanban')) {
        throw new Error(`Unexpected navigation after drag: ${page.url()}`);
      }
      await dealCardInStage(page, 'QUALIFICATION', dealAdvanceName).waitFor({
        state: 'visible',
        timeout: 8000,
      });

      // Reload and confirm persistence
      await page.reload({ waitUntil: 'domcontentloaded' });
      await page.waitForLoadState('networkidle').catch(() => {});
      await dealCardInStage(page, 'QUALIFICATION', dealAdvanceName).waitFor({
        state: 'visible',
        timeout: 10000,
      });
      ok('A1 Kanban legal 1-step advance persists after reload & stays on board');
    } catch (e) {
      await fail('A1 Kanban legal 1-step advance persists after reload & stays on board', String(e.message || e), page);
      await goto(page, '/deals/kanban');
    }

    // A2: Illegal skip (QUALIFICATION -> PROPOSAL) is refused and card stays put
    try {
      let unexpectedAdvance = false;
      const listener = (req) => {
        if (req.url().includes('/advance') && req.method() === 'POST') {
          unexpectedAdvance = true;
        }
      };
      page.on('request', listener);
      await cdkDragTo(
        page,
        dealCardInStage(page, 'QUALIFICATION', dealAdvanceName),
        stageList(page, 'PROPOSAL'),
      );
      page.off('request', listener);

      if (!page.url().includes('/deals/kanban')) {
        throw new Error(`Unexpected navigation after refused drag: ${page.url()}`);
      }
      if (unexpectedAdvance) {
        throw new Error('Illegal stage skip triggered /advance API call');
      }
      await dealCardInStage(page, 'QUALIFICATION', dealAdvanceName).waitFor({
        state: 'visible',
        timeout: 5000,
      });
      ok('A2 Kanban illegal skip refused and card stays in QUALIFICATION');
    } catch (e) {
      await fail('A2 Kanban illegal skip refused and card stays in QUALIFICATION', String(e.message || e), page);
      await goto(page, '/deals/kanban');
    }

    // A3: Drop onto LOST opens lose-reason dialog; Cancel leaves card, Confirm moves it
    try {
      await cdkDragTo(
        page,
        dealCardInStage(page, 'PROSPECTING', dealLoseName),
        stageList(page, 'LOST'),
      );
      const dialog = page.getByRole('dialog');
      await dialog.waitFor({ state: 'visible', timeout: 8000 });

      // Cancel -> card stays in PROSPECTING
      await dialog.getByRole('button', { name: 'Cancel' }).click();
      await dialog.waitFor({ state: 'hidden', timeout: 5000 });
      await dealCardInStage(page, 'PROSPECTING', dealLoseName).waitFor({
        state: 'visible',
        timeout: 5000,
      });

      // Drag again -> fill reason & confirm -> card moves to LOST
      await cdkDragTo(
        page,
        dealCardInStage(page, 'PROSPECTING', dealLoseName),
        stageList(page, 'LOST'),
      );
      await dialog.waitFor({ state: 'visible', timeout: 8000 });
      await dialog.locator('textarea').fill('UAT budget frozen');
      const loseRes = page
        .waitForResponse(
          (r) => r.url().includes('/lose') && r.request().method() === 'POST',
          { timeout: 10000 },
        )
        .catch(() => null);
      await dialog.getByRole('button', { name: /Mark as Lost/i }).click();
      await loseRes;
      await dialog.waitFor({ state: 'hidden', timeout: 5000 });
      await page.waitForLoadState('networkidle').catch(() => {});

      await dealCardInStage(page, 'LOST', dealLoseName).waitFor({
        state: 'visible',
        timeout: 8000,
      });
      ok('A3 Kanban drop onto LOST opens dialog; Cancel keeps card, Confirm moves to LOST');
    } catch (e) {
      await fail('A3 Kanban drop onto LOST opens dialog; Cancel keeps card, Confirm moves to LOST', String(e.message || e), page);
      await goto(page, '/deals/kanban');
    }

    // A4: Drop onto WON with no accepted quote shows clean error & card stays put
    try {
      const winRes = page
        .waitForResponse(
          (r) =>
            r.url().includes(`/api/v1/deals/${dealAdvance.id}/win`) &&
            r.request().method() === 'POST',
          { timeout: 10000 },
        )
        .catch(() => null);

      await cdkDragTo(
        page,
        dealCardInStage(page, 'QUALIFICATION', dealAdvanceName),
        stageList(page, 'WON'),
      );
      await winRes;
      await page.waitForTimeout(300);

      if (!page.url().includes('/deals/kanban')) {
        throw new Error(`Unexpected navigation after WON drag: ${page.url()}`);
      }
      const alert = page.locator('.error-banner[role="alert"]').first();
      await alert.waitFor({ state: 'visible', timeout: 8000 });
      const alertText = (await alert.innerText()).replace(/\s+/g, ' ');
      if (!/OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE|Accept the quote|quote|Could not mark/i.test(alertText)) {
        throw new Error(`Unexpected WON error banner text: "${alertText}"`);
      }
      await dealCardInStage(page, 'QUALIFICATION', dealAdvanceName).waitFor({
        state: 'visible',
        timeout: 5000,
      });
      // Dismiss error banner
      await alert.getByRole('button', { name: 'Dismiss' }).click().catch(() => {});
      ok('A4 Kanban drop onto WON without accepted quote shows clean error & keeps card', alertText.slice(0, 80));
    } catch (e) {
      await fail('A4 Kanban drop onto WON without accepted quote shows clean error & keeps card', String(e.message || e), page);
      await goto(page, '/deals/kanban');
    }

    // A5: mat-menu stage actions still work (Advance Stage from QUALIFICATION -> NEEDS_ANALYSIS)
    try {
      const card = dealCardInStage(page, 'QUALIFICATION', dealAdvanceName);
      await card.getByRole('button', { name: 'Deal stage actions' }).click();
      const advanceMenuItem = page.getByRole('menuitem', { name: /Advance Stage/i });
      await advanceMenuItem.waitFor({ state: 'visible', timeout: 5000 });
      const advanceRes = page
        .waitForResponse(
          (r) =>
            r.url().includes(`/api/v1/deals/${dealAdvance.id}/advance`) &&
            r.request().method() === 'POST',
          { timeout: 10000 },
        )
        .catch(() => null);
      await advanceMenuItem.click();
      await advanceRes;
      await page.waitForLoadState('networkidle').catch(() => {});
      await dealCardInStage(page, 'NEEDS_ANALYSIS', dealAdvanceName).waitFor({
        state: 'visible',
        timeout: 8000,
      });
      ok('A5 Kanban mat-menu Advance Stage still works');
    } catch (e) {
      await fail('A5 Kanban mat-menu Advance Stage still works', String(e.message || e), page);
    }

    // =========================================================================
    // B) Command palette
    // =========================================================================
    await goto(page, '/dashboard');
    await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor();

    // B1: Ctrl+K opens palette, Escape closes it; toolbar button opens, backdrop & Escape return focus
    try {
      const palette = page.getByTestId('command-palette');
      const trigger = page.getByTestId('command-palette-trigger');

      await page.keyboard.press('Control+k');
      await palette.waitFor({ state: 'visible', timeout: 5000 });
      await page.waitForTimeout(150);
      await page.keyboard.press('Escape');
      await palette.waitFor({ state: 'hidden', timeout: 5000 });

      // Toolbar button open -> Escape -> focus returns to trigger
      await trigger.click();
      await palette.waitFor({ state: 'visible', timeout: 5000 });
      await page.waitForTimeout(150);
      await page.keyboard.press('Escape');
      await palette.waitFor({ state: 'hidden', timeout: 5000 });
      await page.waitForTimeout(100);
      const focusedAfterEsc = await page.evaluate(
        () => document.activeElement?.getAttribute('data-testid') || '',
      );
      if (focusedAfterEsc !== 'command-palette-trigger') {
        throw new Error(`Expected focus on command-palette-trigger after Escape, got "${focusedAfterEsc}"`);
      }

      // Toolbar button open -> backdrop click -> focus returns to trigger
      await trigger.click();
      await palette.waitFor({ state: 'visible', timeout: 5000 });
      await page.waitForTimeout(150);
      await page.locator('.cdk-overlay-backdrop').click({ position: { x: 10, y: 10 }, force: true });
      await palette.waitFor({ state: 'hidden', timeout: 5000 });
      await page.waitForTimeout(100);
      const focusedAfterBackdrop = await page.evaluate(
        () => document.activeElement?.getAttribute('data-testid') || '',
      );
      if (focusedAfterBackdrop !== 'command-palette-trigger') {
        throw new Error(
          `Expected focus on command-palette-trigger after backdrop click, got "${focusedAfterBackdrop}"`,
        );
      }
      ok('B1 Command palette Ctrl+K, toolbar button, Escape, backdrop close & focus return');
    } catch (e) {
      await fail('B1 Command palette Ctrl+K, toolbar button, Escape, backdrop close & focus return', String(e.message || e), page);
      await page.keyboard.press('Escape').catch(() => {});
    }

    // B2: Items match sidebar links (nav-items.ts is the shared source of truth; "Bundles" uses paletteLabel "Product Bundles")
    try {
      const sidebarLabels = await page
        .locator('app-sidebar a[mat-list-item] [matListItemTitle]')
        .allInnerTexts();
      const normalizedSidebar = sidebarLabels.map((s) => s.trim()).filter(Boolean);

      await page.getByTestId('command-palette-trigger').click();
      const palette = page.getByTestId('command-palette');
      await palette.waitFor({ state: 'visible', timeout: 5000 });
      await page.waitForTimeout(150);

      const paletteLabels = await palette
        .locator('[role="option"] .palette-option__label')
        .allInnerTexts();
      const normalizedPalette = paletteLabels.map((s) => s.trim()).filter(Boolean);

      const missingInPalette = normalizedSidebar.filter(
        (label) => !normalizedPalette.some((p) => p === label || p.endsWith(` ${label}`)),
      );
      await page.keyboard.press('Escape');
      await palette.waitFor({ state: 'hidden', timeout: 5000 });
      if (missingInPalette.length > 0) {
        throw new Error(`Sidebar links missing from command palette: ${missingInPalette.join(', ')}`);
      }
      ok('B2 Command palette items match sidebar links', `${normalizedSidebar.length} sidebar links matched`);
    } catch (e) {
      await fail('B2 Command palette items match sidebar links', String(e.message || e), page);
      await page.keyboard.press('Escape').catch(() => {});
    }

    // B3: Typing filters items, ArrowDown moves aria-activedescendant, Enter navigates
    try {
      await page.keyboard.press('Control+k');
      const palette = page.getByTestId('command-palette');
      await palette.waitFor({ state: 'visible', timeout: 5000 });
      const input = palette.locator('input[role="combobox"]');

      const beforeActive = await input.getAttribute('aria-activedescendant');
      await page.keyboard.press('ArrowDown');
      await page.waitForTimeout(80);
      const afterActive = await input.getAttribute('aria-activedescendant');
      if (!afterActive || afterActive === beforeActive) {
        throw new Error(
          `aria-activedescendant did not move on ArrowDown (before=${beforeActive}, after=${afterActive})`,
        );
      }

      await input.fill('Opportunities');
      await page.waitForTimeout(100);
      const filteredCount = await palette.locator('[role="option"]').count();
      if (filteredCount < 1) {
        throw new Error('Expected at least 1 filtered option for "Opportunities"');
      }
      await page.keyboard.press('Enter');
      await page.waitForURL(/\/deals(\?|$|\/)/, { timeout: 8000 });
      await palette.waitFor({ state: 'hidden', timeout: 5000 });
      ok('B3 Command palette filter, ArrowDown aria-activedescendant, Enter navigation');
    } catch (e) {
      await fail('B3 Command palette filter, ArrowDown aria-activedescendant, Enter navigation', String(e.message || e), page);
    }

    // =========================================================================
    // C) Deal detail stage path (read-only)
    // =========================================================================
    try {
      await goto(page, `/deals/${dealDetail.id}`);
      const stagePath = page.getByTestId('stage-path');
      await stagePath.waitFor({ state: 'visible', timeout: 10000 });

      const text = (await stagePath.innerText()).replace(/\s+/g, ' ');
      for (const expectedStage of ['Prospecting', 'Qualification', 'Proposal', 'Negotiation', 'Won', 'Lost']) {
        if (!text.includes(expectedStage)) {
          throw new Error(`StagePath missing stage label "${expectedStage}": "${text}"`);
        }
      }

      const buttonCount = await stagePath.locator('button').count();
      if (buttonCount !== 0) {
        throw new Error(`Expected 0 buttons inside read-only StagePath, found ${buttonCount}`);
      }

      const tabStopCount = await stagePath
        .locator('a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])')
        .count();
      if (tabStopCount !== 0) {
        throw new Error(`Expected 0 tab stops inside read-only StagePath, found ${tabStopCount}`);
      }

      const activeStepCount = await stagePath.locator('[aria-current="step"]').count();
      if (activeStepCount !== 1) {
        throw new Error(`Expected exactly 1 aria-current="step", found ${activeStepCount}`);
      }
      ok('C1 Deal detail StagePath shows stages, 0 buttons/tab stops, 1 aria-current="step"');
    } catch (e) {
      await fail('C1 Deal detail StagePath shows stages, 0 buttons/tab stops, 1 aria-current="step"', String(e.message || e), page);
    }

    // =========================================================================
    // D) Smoke: Opportunities copy & data-testid="data-table" on CRM lists
    // =========================================================================
    try {
      for (const [path, heading] of [
        ['/deals', 'Opportunities'],
        ['/organizations', 'Accounts'],
        ['/contacts', 'Contacts'],
        ['/leads', 'Leads'],
      ]) {
        await goto(page, path);
        await page.getByRole('heading', { name: heading, exact: true }).waitFor({ timeout: 10000 });
        await page.getByTestId('data-table').waitFor({ state: 'visible', timeout: 10000 });
      }
      ok('D1 Smoke: Opportunities copy intact & data-testid="data-table" on all 4 CRM lists');
    } catch (e) {
      await fail('D1 Smoke: Opportunities copy intact & data-testid="data-table" on all 4 CRM lists', String(e.message || e), page);
    }
  } catch (e) {
    await fail('fatal', String(e.stack || e), page);
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => r.status === 'FAIL');
  const passed = results.length - failed.length;
  console.log(`\n${passed}/${results.length} passed, ${failed.length} failed`);
  process.exit(failed.length ? 1 : 0);
}

main();
