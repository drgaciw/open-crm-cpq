/**
 * Dialog handling for the UAT runners.
 *
 * The app mixes three confirmation styles: native `window.confirm` (9 sites), native
 * `window.prompt` (2 sites) and the Material `ConfirmDialogComponent`.
 *
 * Native dialogs need care. With no listener registered Playwright auto-dismisses them, so a
 * delete behind `confirm()` silently does nothing and the test "passes" against a record that
 * was never removed. A permanent accept-everything listener is just as bad: it hides a dialog
 * that should not have appeared. `withNativeDialog` is therefore scoped to one action and
 * fails in both directions — no dialog when one was expected, or the wrong dialog.
 */

/** @param {string|RegExp|undefined} expected @param {string} actual */
const matches = (expected, actual) =>
  expected === undefined ||
  (expected instanceof RegExp ? expected.test(actual) : actual.includes(expected));

/**
 * Run `action`, expecting exactly one native dialog to fire during it.
 *
 * @param {import('playwright').Page} page
 * @param {object} expectation
 * @param {'confirm'|'prompt'|'alert'} expectation.type
 * @param {string|RegExp} [expectation.message]
 * @param {boolean} [expectation.accept]   Default true. false = dismiss (Cancel).
 * @param {string} [expectation.promptText] Text entered when accepting a prompt.
 * @param {() => Promise<unknown>} action
 * @param {{timeoutMs?: number}} [options]
 * @returns {Promise<{type:string, message:string}>}
 */
export async function withNativeDialog(page, expectation, action, { timeoutMs = 5000 } = {}) {
  const { type, message, accept = true, promptText } = expectation;
  let seen;
  let problem;

  const handler = async (dialog) => {
    seen = { type: dialog.type(), message: dialog.message() };
    if (seen.type !== type) problem = `expected a ${type} dialog, got ${seen.type}: "${seen.message}"`;
    else if (!matches(message, seen.message)) problem = `dialog message "${seen.message}" did not match ${message}`;
    // Always resolve the dialog, even a wrong one — leaving it open hangs the page.
    if (accept && !problem) await dialog.accept(promptText);
    else await dialog.dismiss();
  };

  page.once('dialog', handler);
  try {
    await action();
    const deadline = Date.now() + timeoutMs;
    while (!seen && Date.now() < deadline) await page.waitForTimeout(50);
  } finally {
    page.off('dialog', handler);
  }

  if (!seen) throw new Error(`expected a native ${type} dialog, but none appeared`);
  if (problem) throw new Error(problem);
  return seen;
}

/**
 * Assert that `action` raises no native dialog — for flows that are documented to use the
 * Material dialog instead.
 */
export async function expectNoNativeDialog(page, action, { settleMs = 400 } = {}) {
  let seen;
  const handler = async (dialog) => {
    seen = `${dialog.type()}: "${dialog.message()}"`;
    await dialog.dismiss();
  };
  page.once('dialog', handler);
  try {
    await action();
    await page.waitForTimeout(settleMs);
  } finally {
    page.off('dialog', handler);
  }
  if (seen) throw new Error(`unexpected native dialog — ${seen}`);
}

/**
 * Drive the shared Material ConfirmDialogComponent: assert its copy, then click a button.
 *
 * @param {import('playwright').Page} page
 * @param {object} spec
 * @param {string|RegExp} [spec.title]
 * @param {string|RegExp} [spec.message]
 * @param {string} spec.click  Exact button label to press (e.g. 'Delete', 'Cancel', 'Keep').
 */
export async function confirmMatDialog(page, { title, message, click }) {
  const dialog = page.getByRole('dialog');
  await dialog.waitFor({ state: 'visible' });
  const text = await dialog.innerText();
  if (title !== undefined && !matches(title, text)) {
    throw new Error(`confirm dialog title mismatch — wanted ${title}, dialog says "${text}"`);
  }
  if (message !== undefined && !matches(message, text)) {
    throw new Error(`confirm dialog message mismatch — wanted ${message}, dialog says "${text}"`);
  }
  await dialog.getByRole('button', { name: click, exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
}
