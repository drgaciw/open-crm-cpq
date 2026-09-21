/**
 * Price-book immutability workflow.
 *
 * Backend rule (PriceBookService.ensureNotPublished): effectiveDate present AND <= today means
 * "published" — the header, the entries and the book itself can no longer be changed or deleted.
 * The detail page mirrors that rule client-side (isPriceBookPublished) and disables Edit / Delete /
 * Add entry. This workflow checks the UI mirror, both editable variants (future date, no date) and
 * what the backend does when the UI is bypassed.
 *
 * RESIDUE: a published book cannot be deleted through any API. The workflow therefore keeps ONE
 * long-lived fixture (`UAT-PUBLISHED-FIXTURE`, region `UAT-PUBLISHED`, effective 2020-01-01) and
 * reuses it on every later run; only the run that first creates it reports it as residue. The
 * region is a value no pricing context uses, so the fixture never takes part in price resolution.
 *
 * Deliberately NOT tested: effectiveDate == today. The UI compares against the browser's local
 * date, the backend against the server's `LocalDate.now()`; around midnight UTC the two disagree,
 * so such a check would flip its verdict with the time of day.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, waitUntil } from '../../lib/health.mjs';
import { dismissSnackbars } from '../../lib/overlays.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { ensure } from '../fixtures.mjs';
import { PUBLISHED_NOTE } from '../specs/price-books.mjs';

export const API = '/api/v1/price-books';
export const PUBLISHED_NAME = 'UAT-PUBLISHED-FIXTURE';
export const PUBLISHED_REGION = 'UAT-PUBLISHED';

/** GET …/entries answers 500 on every detail load (H-CPQ-PR-013); tolerated where it is not the subject. */
export const ENTRIES_5XX = /\/api\/v1\/price-books\/\d+\/entries/;

/** A monitor that hides the known entries 5xx, for generic runners that take no expectedErrors. */
export const tolerantCtx = (ctx, patterns = [ENTRIES_5XX]) => ({
  ...ctx,
  monitor: { drain: () => (ctx.monitor?.drain() ?? []).filter((e) => !patterns.some((p) => p.test(e.text))) },
});

/** Region values must be unique per price book (DUPLICATE_REGION_PRICE_BOOK) — never reuse 'NA'. */
let regionSeq = 0;
export const uniqueRegion = (ctx) => `R-${ctx.runId}-${++regionSeq}`;

/**
 * Open a book's detail page and clear the error toast its failing entries request raises — the
 * toast sits on top of the header buttons for seven seconds.
 */
export async function openDetail(page, id, name) {
  await goto(page, `/price-books/${id}`);
  const h1 = page.getByRole('heading', { level: 1 });
  await (name ? h1.filter({ hasText: name }) : h1).first().waitFor({ state: 'visible' });
  await page.locator('mat-snack-bar-container').first().waitFor({ state: 'visible', timeout: 700 }).catch(() => {});
  await dismissSnackbars(page);
}

/** Value of one label/value pair on the details card. */
export const detailValue = (page, label) =>
  page.locator('.info-item').filter({ has: page.locator('.label', { hasText: new RegExp(`^${label}$`) }) }).locator('.value');

const squash = (text) => (text ?? '').replace(/\s+/g, ' ').trim();

/** Expect a 4xx carrying `code`; anything else (2xx, 5xx, other code) is a failure. */
async function expectRejected(call, code) {
  try {
    await call();
  } catch (error) {
    if (!(error instanceof ApiCallError)) throw error;
    if (error.status >= 500) throw new Error(`backend answered ${error.status} instead of a business error: ${error.message}`);
    if (!String(error.body).includes(code)) throw new Error(`rejected with HTTP ${error.status} but without ${code}: ${String(error.body).slice(0, 160)}`);
    return `HTTP ${error.status} ${code}`;
  }
  throw new Error(`the backend ACCEPTED the call — expected ${code}`);
}

async function findPublishedFixture(api) {
  const page = await api.get(`${API}?page=0&size=50&region=${encodeURIComponent(PUBLISHED_REGION)}`);
  return (page?.content ?? []).find((b) => b.name === PUBLISHED_NAME) ?? null;
}

async function assertEditable(page, book, expectedEffective) {
  await openDetail(page, book.id, book.name);
  const status = squash(await detailValue(page, 'Status').innerText());
  if (status !== 'Draft (editable)') throw new Error(`Status reads "${status}", expected "Draft (editable)"`);
  const effective = squash(await detailValue(page, 'Effective').innerText());
  if (effective !== expectedEffective) throw new Error(`Effective reads "${effective}", expected "${expectedEffective}"`);
  for (const css of ['.edit-book-btn', '.delete-book-btn', '.add-entry-btn']) {
    if (!(await page.locator(css).isEnabled())) throw new Error(`${css} is disabled on an editable book`);
  }
  if (await page.locator('.published-note').count()) throw new Error('the "published" note is shown on an editable book');
  return `${status}; Edit / Delete / Add entry enabled`;
}

/**
 * @param {object} ctx
 */
export async function runPriceBookImmutability(ctx) {
  const { page, api } = ctx;
  const base = 'price-books.immutability';
  const tolerate = { expectedErrors: [ENTRIES_5XX] };
  const state = { published: null };

  await runSteps(ctx, [
    {
      id: `${base}.published.fixture`,
      run: async () => {
        const existing = await findPublishedFixture(api);
        if (existing) {
          state.published = existing;
          return `reusing book ${existing.id} (effective ${existing.effectiveDate}) — no new residue`;
        }
        const created = await api.post(API, {
          name: PUBLISHED_NAME, region: PUBLISHED_REGION, currency: 'USD', isDefault: false, effectiveDate: '2020-01-01',
        });
        api.track('priceBook', created.id, `${API}/${created.id}`); // undeletable by design -> reported as residue once
        state.published = created;
        return `created book ${created.id}; it can never be deleted and is reused by later runs`;
      },
    },
    {
      id: `${base}.published.ui-read-only`,
      ...tolerate,
      run: async () => {
        const book = state.published;
        await openDetail(page, book.id, book.name);
        const status = squash(await detailValue(page, 'Status').innerText());
        if (status !== 'Published (read-only)') throw new Error(`Status reads "${status}", expected "Published (read-only)"`);
        for (const css of ['.edit-book-btn', '.delete-book-btn', '.add-entry-btn']) {
          if (await page.locator(css).isEnabled()) throw new Error(`${css} is ENABLED on a published book`);
        }
        const note = page.locator('[role="status"].published-note');
        await note.waitFor({ state: 'visible' });
        const text = squash(await note.innerText());
        if (text !== PUBLISHED_NOTE) throw new Error(`published note reads "${text}"`);
        return 'Edit, Delete, Add entry disabled; role=status note shown';
      },
    },
    {
      id: `${base}.published.api.put-rejected`,
      run: async () => {
        const book = state.published;
        const verdict = await expectRejected(
          () => api.put(`${API}/${book.id}`, { name: `${PUBLISHED_NAME}-tampered`, isDefault: false }),
          'PRICE_BOOK_PUBLISHED_IMMUTABLE',
        );
        const after = await api.get(`${API}/${book.id}`);
        if (after.name !== PUBLISHED_NAME) throw new Error(`name changed to "${after.name}" although the PUT was rejected`);
        return verdict;
      },
    },
    {
      id: `${base}.published.api.delete-rejected`,
      run: async () => {
        const book = state.published;
        const verdict = await expectRejected(() => api.del(`${API}/${book.id}`), 'PRICE_BOOK_PUBLISHED_IMMUTABLE');
        await api.get(`${API}/${book.id}`); // still readable
        return verdict;
      },
    },
    {
      id: `${base}.published.api.entry-create-rejected`,
      run: async () => expectRejected(
        () => api.post(`${API}/${state.published.id}/entries`, { productExternalId: 'UAT-TAMPER', listPrice: 1 }),
        'PRICE_BOOK_PUBLISHED_IMMUTABLE',
      ),
    },
  ].map((step) => ({ ...step, continueOnFail: step.id !== `${base}.published.fixture` })));

  await runCheck(ctx, `${base}.draft.future-date-is-editable`, async () => {
    const book = await ensure(ctx, 'priceBook', { region: uniqueRegion(ctx), effectiveDate: '2099-01-01' });
    return assertEditable(page, book, '2099-01-01');
  }, tolerate);

  await runCheck(ctx, `${base}.draft.no-date-is-editable`, async () => {
    const book = await ensure(ctx, 'priceBook', { region: uniqueRegion(ctx) });
    return assertEditable(page, book, '—');
  }, tolerate);

  // The list offers no hint that a book is locked; record what it does show for the fixture.
  await runCheck(ctx, `${base}.published.listed-with-effective-date`, async () => {
    if (!state.published) throw new Error('no published fixture');
    await goto(page, '/price-books');
    await page.getByRole('textbox', { name: 'Filter by region', exact: true }).fill(PUBLISHED_REGION);
    await page.getByRole('button', { name: 'Apply region filter', exact: true }).click();
    const row = page.getByRole('row').filter({ hasText: PUBLISHED_NAME }).first();
    await row.waitFor({ state: 'visible' });
    const text = await waitUntil(async () => squash(await row.innerText()), (t) => t.includes('2020-01-01'), {
      describe: (t) => `row reads "${t}"`,
    });
    return text;
  });
}
