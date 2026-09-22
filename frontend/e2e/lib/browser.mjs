/**
 * Shared browser bootstrap for the e2e scripts.
 *
 * Previously each script launched chromium itself, and uat-crm-smoke.mjs hardcoded
 * `executablePath: '/usr/bin/chromium'` — a Linux path that cannot resolve on a
 * Windows or macOS host. Playwright ships its own browser, so the executable is now
 * only overridden when CHROMIUM_PATH is explicitly set (CI images that supply one).
 */
import { chromium } from 'playwright';

/**
 * Base URL of the Angular dev server. Scripts must not hardcode this.
 *
 * Defaults to `localhost` rather than `127.0.0.1` deliberately: the Angular dev
 * server frequently binds IPv6-only (`::1`), which a literal IPv4 address cannot
 * reach. `localhost` resolves to whichever stack is actually listening.
 */
export const BASE = process.env.UAT_BASE_URL || 'http://localhost:4200';

/** Default per-action timeout; generous because the stack may be cold-starting. */
const DEFAULT_TIMEOUT_MS = Number(process.env.UAT_TIMEOUT_MS) || 30000;

/**
 * Launch chromium and open one page.
 * @param {object} [options]
 * @param {boolean} [options.headless]
 * @param {number} [options.timeoutMs]
 * @param {{width:number,height:number}} [options.viewport]
 * @param {string[]} [options.extraArgs] Appended to the baseline chromium flags.
 * @param {object} [options.contextOptions] Passed to newContext (e.g. ignoreHTTPSErrors).
 * @returns {Promise<{browser:import('playwright').Browser, context:import('playwright').BrowserContext, page:import('playwright').Page}>}
 */
export async function launch({
  headless = process.env.UAT_HEADED !== '1',
  timeoutMs = DEFAULT_TIMEOUT_MS,
  viewport = { width: 1400, height: 900 },
  extraArgs = [],
  contextOptions = {},
} = {}) {
  const executablePath = process.env.CHROMIUM_PATH?.trim() || undefined;
  const browser = await chromium.launch({
    headless,
    // Only set when explicitly provided — otherwise use Playwright's bundled build.
    ...(executablePath ? { executablePath } : {}),
    args: ['--no-sandbox', '--disable-dev-shm-usage', ...extraArgs],
  });
  const context = await browser.newContext({ viewport, ...contextOptions });
  const page = await context.newPage();
  page.setDefaultTimeout(timeoutMs);
  return { browser, context, page };
}

/**
 * Navigate to an app-relative path and settle. Network-idle is best-effort: the
 * CPQ services are often down during CRM-only UAT, so a pending XHR is expected.
 */
export async function goto(page, path, { waitUntil = 'domcontentloaded' } = {}) {
  await page.goto(`${BASE}${path}`, { waitUntil });
  await page.waitForLoadState('networkidle').catch(() => {});
}
