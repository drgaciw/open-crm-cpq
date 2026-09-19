/**
 * Per-check isolation and browser-health monitoring.
 *
 * `runCheck` is the only way a runner records an outcome. It guarantees that:
 *  - one thrown error becomes one FAIL (or EXPECTED_FAIL) and the suite continues;
 *  - failure evidence (screenshot + URL) is captured while the page still shows the problem;
 *  - stray overlays are cleared afterwards so a stuck dialog cannot poison later checks;
 *  - uncaught page errors, console errors and API 5xx responses raised *during* the check fail
 *    it even when every explicit assertion passed — silent breakage is still breakage.
 */
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { closeStrayOverlays, dismissSnackbars } from './overlays.mjs';

/** Noise that is not a defect: dev-server chatter and the browser restating a failed request. */
const IGNORED_CONSOLE = [
  /Failed to load resource/i,
  /net::ERR_/i,
  /favicon/i,
  /\[vite\]/i,
  /Angular is running in development mode/i,
];

/**
 * Thrown by a check that has *observed the defect its hypothesis claims*. Only this confirms a
 * hypothesis. Any other error inside a hypothesis-linked check — a timeout, a strict-mode
 * violation, a harness bug — is an ordinary FAIL: a crash is not evidence that the claim is true.
 */
export class Defect extends Error {
  constructor(message) {
    super(message);
    this.name = 'Defect';
  }
}
export const defect = (message) => new Defect(message);

/**
 * Poll until `read()` satisfies `accept`, then return the value; throw with `describe(last)` on
 * timeout. The app is zoneless and ngModel writes to the DOM a microtask after the model
 * changes, so state must never be read once, synchronously, straight after an action.
 *
 * @template T
 * @param {() => Promise<T>} read
 * @param {(value:T) => boolean} accept
 * @param {{timeoutMs?:number, intervalMs?:number, describe?:(last:T)=>string}} [options]
 * @returns {Promise<T>}
 */
export async function waitUntil(read, accept, { timeoutMs = 3000, intervalMs = 50, describe } = {}) {
  const deadline = Date.now() + timeoutMs;
  let last;
  for (;;) {
    last = await read();
    if (accept(last)) return last;
    if (Date.now() >= deadline) {
      throw new Error(describe ? describe(last) : `condition not met within ${timeoutMs}ms (last value: ${JSON.stringify(last)})`);
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
}

/** Thrown by a check to end it with a non-FAIL outcome. */
export class Outcome extends Error {
  constructor(kind, detail, extra = {}) {
    super(detail);
    this.kind = kind;
    this.extra = extra;
  }
}
export const blocked = (reason, extra) => new Outcome('blocked', reason, extra);
export const gap = (detail, extra) => new Outcome('gap', detail, extra);
export const inconclusive = (detail, extra) => new Outcome('inconclusive', detail, extra);

/**
 * Attach listeners once per page. Returns a monitor whose `drain()` hands back and clears
 * everything seen since the previous drain.
 */
export function monitorPage(page) {
  let seen = [];
  page.on('pageerror', (error) => seen.push({ kind: 'pageerror', text: String(error?.message ?? error) }));
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    const text = message.text();
    if (!IGNORED_CONSOLE.some((pattern) => pattern.test(text))) seen.push({ kind: 'console', text });
  });
  page.on('response', (response) => {
    const url = response.url();
    if (response.status() >= 500 && url.includes('/api/')) {
      seen.push({ kind: 'http5xx', text: `${response.status()} ${response.request().method()} ${new URL(url).pathname}` });
    }
  });
  return {
    drain() {
      const out = seen;
      seen = [];
      return out;
    },
  };
}

const slug = (id) => id.replace(/[^a-z0-9.-]+/gi, '_').slice(0, 120);

/**
 * Run one check and record exactly one outcome.
 *
 * @param {object} ctx  Suite context: { page, report, monitor, evidenceDir, only? }
 * @param {string} id   Stable check id, e.g. 'organizations.form.name.required'.
 * @param {() => Promise<string|void>} fn  Returns optional PASS detail; throws to fail.
 * @param {object} [options]
 * @param {string} [options.hypothesis]      A failure here confirms this hypothesis (EXPECTED_FAIL).
 * @param {(string|RegExp)[]} [options.expectedErrors] Browser-health events tolerated in this check.
 * @returns {Promise<boolean>} true when the check passed.
 */
export async function runCheck(ctx, id, fn, { hypothesis, expectedErrors = [] } = {}) {
  const { page, report, monitor } = ctx;
  if (ctx.only && !ctx.only.some((prefix) => id.startsWith(prefix))) return true;

  monitor?.drain();
  await dismissSnackbars(page).catch(() => {});

  try {
    const detail = await fn();

    const tolerated = (event) =>
      expectedErrors.some((p) => (p instanceof RegExp ? p.test(event.text) : event.text.includes(p)));
    const unexpected = (monitor?.drain() ?? []).filter((event) => !tolerated(event));
    if (unexpected.length) {
      const summary = unexpected.slice(0, 3).map((e) => `${e.kind}: ${e.text}`).join(' | ');
      throw new Error(`browser health: ${summary}`);
    }

    report.pass(id, detail ?? '', hypothesis ? { hypothesis } : {});
    return true;
  } catch (error) {
    if (error instanceof Outcome) {
      report[error.kind](id, error.message, { ...(hypothesis ? { hypothesis } : {}), ...error.extra });
      return false;
    }

    const evidence = { url: page.url() };
    if (ctx.evidenceDir) {
      try {
        mkdirSync(ctx.evidenceDir, { recursive: true });
        const file = resolve(ctx.evidenceDir, `${slug(id)}.png`);
        await page.screenshot({ path: file, fullPage: true });
        evidence.screenshot = file;
      } catch {
        /* evidence is best-effort; never mask the real failure */
      }
    }
    const message = String(error?.message ?? error).split('\n')[0];
    const confirms = hypothesis && error instanceof Defect;
    report.fail(id, confirms || !hypothesis ? message : `not the claimed defect — ${message}`,
      { evidence, ...(confirms ? { hypothesis } : {}) });
    await closeStrayOverlays(page).catch(() => {});
    monitor?.drain();
    return false;
  }
}

/**
 * Run ordered workflow steps. After the first failed step the rest are recorded BLOCKED with
 * `blockedBy`, instead of cascading into FAILs that would pollute triage.
 *
 * @param {object} ctx
 * @param {{id:string, run:() => Promise<string|void>, hypothesis?:string}[]} steps
 */
export async function runSteps(ctx, steps) {
  let failedAt = null;
  for (const step of steps) {
    if (failedAt) {
      if (!ctx.only || ctx.only.some((prefix) => step.id.startsWith(prefix))) {
        ctx.report.blocked(step.id, `earlier step failed`, { blockedBy: failedAt });
      }
      continue;
    }
    const ok = await runCheck(ctx, step.id, step.run, { hypothesis: step.hypothesis, expectedErrors: step.expectedErrors });
    // An expected failure documents a known defect; the workflow may still be able to continue.
    if (!ok && !step.continueOnFail) failedAt = step.id;
  }
}
