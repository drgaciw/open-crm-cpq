/**
 * Page observation helpers for the browser-use agent.
 *
 * Everything returned here is *untrusted input* — it is scraped from a rendered page
 * and handed to a model. Callers must keep framing it as data, never as instruction.
 */
import { BASE } from './browser.mjs';

/**
 * Visible, same-origin links on the current page.
 *
 * Only app-relative hrefs survive: `/foo` is kept, `//evil.example` and absolute
 * URLs are dropped, so a model choice can never navigate the agent off-origin.
 *
 * @returns {Promise<Array<{text:string, href:string}>>}
 */
export async function safeLinks(page) {
  const links = await page.locator('a[href]').evaluateAll((elements) =>
    elements
      .filter((el) => el.getClientRects().length > 0)
      .map((el) => ({ text: el.innerText.trim(), href: el.getAttribute('href') })),
  );
  return links.filter((l) => l.href?.startsWith('/') && !l.href.startsWith('//'));
}

/**
 * Turn a link list into choice criteria plus a bail-out option.
 * Keys are positional (`nav_0`…) so the model never echoes raw page text back as a key.
 *
 * @returns {{criteria:Record<string,string>, resolve:(key:string)=>{text:string,href:string}|undefined}}
 */
export function linkCriteria(links, { stopKey = 'stop' } = {}) {
  const criteria = Object.fromEntries(
    links.map((link, i) => [`nav_${i}`, `${link.text} ${link.href}`]),
  );
  criteria[stopKey] = 'No available action accomplishes the goal';
  return {
    criteria,
    resolve: (key) => (key === stopKey ? undefined : links[Number(key.slice('nav_'.length))]),
  };
}

/**
 * Snapshot of a dialog's observable state, used both to decide the next action and
 * to detect that the page changed underneath us between deciding and acting.
 */
export async function observeDialog(dialog, { submitLabel = 'Create', settleMs = 2000 } = {}) {
  // Settle first. A just-dismissed <mat-select> leaves its overlay panel in the DOM
  // while it animates out, and the panel's option list bleeds into dialog.innerText
  // ("SUBSCRIPTION LICENSE SUPPORT ..."). Observing mid-animation yields a snapshot
  // that differs from one taken moments later, which trips the staleness guard and
  // feeds the model text that does not describe the form.
  await dialog
    .page()
    .locator('.cdk-overlay-pane .mat-mdc-select-panel')
    .waitFor({ state: 'detached', timeout: settleMs })
    .catch(() => {});

  // Fields are reported as label/value pairs, not a bare value array. Two reasons:
  // an unlabelled array gives the model no way to tell which value belongs to which
  // field, and Angular Material renders selects as <mat-select>, not <input> — so a
  // plain `input` query silently omits every dropdown on the form.
  const fields = await dialog.locator('mat-form-field').evaluateAll((elements) =>
    elements.map((el) => {
      const label = (el.querySelector('mat-label, label')?.textContent ?? '').trim();
      const input = el.querySelector('input, textarea');
      // Read only the select's rendered trigger text. Falling back to the whole
      // <mat-select> concatenates its mounted option list into the value
      // ("SUBSCRIPTION" + "SUBSCRIPTIONLICENSESUPPORT..."), which reads to the model
      // as a corrupt field and makes it refuse to submit a correctly filled form.
      const select = el.querySelector('.mat-mdc-select-value');
      const raw = input ? input.value : (select?.textContent ?? '');
      return { label, value: raw.replace(/\s+/g, ' ').trim() };
    }),
  );

  return {
    text: await dialog.innerText(),
    fields,
    emptyFields: fields.filter((f) => !f.value).map((f) => f.label),
    submitEnabled: await dialog
      .getByRole('button', { name: submitLabel, exact: true })
      .isEnabled()
      .catch(() => false),
  };
}

/**
 * Re-observe and compare. A browser agent decides against a snapshot, so if the page
 * moved between observation and action the decision is stale and must not be applied.
 *
 * @throws {Error} when the observation changed.
 */
export async function assertUnchanged(before, observeFn) {
  const after = await observeFn();
  if (JSON.stringify(before) !== JSON.stringify(after)) {
    throw new Error('Stale observation — page changed between decision and action');
  }
  return after;
}

/** Absolute URL for an app-relative path, for waitForURL comparisons. */
export const appUrl = (path) => `${BASE}${path}`;
