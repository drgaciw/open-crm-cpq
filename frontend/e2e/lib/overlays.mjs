/**
 * Angular Material overlay handling.
 *
 * The app is zoneless with animations enabled, so an overlay that has been dismissed is still in
 * the DOM while it animates out. Reading or clicking during that window is the main source of
 * flakiness: a closing mat-select bleeds its option list into `innerText`, and a snackbar
 * (top / end, 7000 ms for errors) sits directly over the page-header action buttons and
 * intercepts the next click on New / More / Submit.
 */

/** Transient panels that must be gone before the page is observed or clicked. */
const TRANSIENT_PANELS = [
  '.cdk-overlay-pane .mat-mdc-select-panel',
  '.cdk-overlay-pane .mat-mdc-autocomplete-panel',
  '.cdk-overlay-pane .mat-mdc-menu-panel',
  '.cdk-overlay-pane .mat-datepicker-content',
];

const SNACKBAR = 'mat-snack-bar-container';

/**
 * Wait for select / autocomplete / menu / datepicker panels to finish closing.
 * Best-effort: a panel that is legitimately open is the caller's business, so this never throws.
 */
export async function settleOverlays(page, { timeoutMs = 2000 } = {}) {
  await Promise.all(
    TRANSIENT_PANELS.map((selector) =>
      page.locator(selector).first().waitFor({ state: 'detached', timeout: timeoutMs }).catch(() => {}),
    ),
  );
}

/**
 * Close every visible snackbar via its "Close" action and wait for it to detach.
 * Call at the start of a check so a leftover error toast cannot swallow the first click.
 */
export async function dismissSnackbars(page, { timeoutMs = 3000 } = {}) {
  const bars = page.locator(SNACKBAR);
  for (let i = 0; i < 5 && (await bars.count()) > 0; i++) {
    const close = bars.first().getByRole('button', { name: 'Close', exact: true });
    if (await close.isVisible().catch(() => false)) await close.click().catch(() => {});
    await bars.first().waitFor({ state: 'detached', timeout: timeoutMs }).catch(() => {});
  }
}

/**
 * Assert a snackbar appears whose text starts with / matches `expected`, then dismiss it.
 *
 * Matching is by prefix or RegExp, never equality: errorInterceptor appends
 * " (Trace ID: …)" and, for ApiError envelopes, a newline plus the joined field errors.
 *
 * @param {string|RegExp} expected
 * @returns {Promise<string>} The full snackbar text.
 */
export async function expectSnackbar(page, expected, { timeoutMs = 8000 } = {}) {
  const bar = page.locator(SNACKBAR).first();
  await bar.waitFor({ state: 'visible', timeout: timeoutMs });
  const text = (await bar.innerText()).replace(/\s*Close\s*$/, '').trim();
  const ok = expected instanceof RegExp ? expected.test(text) : text.startsWith(expected);
  await dismissSnackbars(page);
  if (!ok) throw new Error(`snackbar said "${text}", expected ${expected instanceof RegExp ? expected : `prefix "${expected}"`}`);
  return text;
}

/** Assert no snackbar shows up within `windowMs` — the app has no success toasts by design. */
export async function expectNoSnackbar(page, { windowMs = 1200 } = {}) {
  const appeared = await page
    .locator(SNACKBAR)
    .first()
    .waitFor({ state: 'visible', timeout: windowMs })
    .then(() => true)
    .catch(() => false);
  if (appeared) {
    const text = await page.locator(SNACKBAR).first().innerText().catch(() => '');
    await dismissSnackbars(page);
    throw new Error(`unexpected snackbar: "${text.trim()}"`);
  }
}

/**
 * Recovery after a failed check: Escape twice closes a stray menu / dialog / select so one stuck
 * overlay cannot cascade into every later check. Dialogs opened with disableClose ignore Escape;
 * those are closed through their Cancel button when present.
 */
export async function closeStrayOverlays(page) {
  await dismissSnackbars(page).catch(() => {});
  for (let i = 0; i < 2; i++) await page.keyboard.press('Escape').catch(() => {});
  const dialog = page.getByRole('dialog');
  if (await dialog.count().catch(() => 0)) {
    const cancel = dialog.first().getByRole('button', { name: /^(Cancel|Close|Keep)$/ });
    if (await cancel.first().isVisible().catch(() => false)) await cancel.first().click().catch(() => {});
  }
  await settleOverlays(page, { timeoutMs: 1000 });
}
