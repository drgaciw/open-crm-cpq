/**
 * Full UAT of Open CRM/CPQ — every page, field, card and workflow.
 *
 *   cd frontend
 *   npm run e2e:uat                          # everything (Jev suites need an API key in ../.env)
 *   npm run e2e:uat -- --suite organizations # one or more suites (comma separated)
 *   npm run e2e:uat -- --only organizations.crud   # only checks whose id starts with a prefix
 *   npm run e2e:uat -- --no-jev              # deterministic suites only; no API key needed
 *   npm run e2e:uat -- --strict              # known-defect tolerance off: expected FAILs count
 *   npm run e2e:uat -- --sweep               # delete UAT-* leftovers from older runs, then exit
 *
 * Suites and hypotheses are auto-discovered from uat/suites and uat/hypotheses, so adding one
 * never means editing this file.
 */
import { readdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { launch } from './lib/browser.mjs';
import { createReport } from './lib/report.mjs';
import { createApi } from './lib/api.mjs';
import { runPreflight, blockedReason } from './lib/preflight.mjs';
import { monitorPage } from './lib/health.mjs';
import { newRunId, ENTITY_TYPES, entityPath } from './uat/fixtures.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));

// A Playwright promise left pending when a check throws rejects once the browser closes. That
// must never take the process down before the evidence is written.
process.on('unhandledRejection', (reason) => {
  console.error(`(ignored unhandled rejection: ${String(reason?.message ?? reason).split(String.fromCharCode(10))[0]})`);
});
const argv = process.argv.slice(2);
const flag = (name) => argv.includes(`--${name}`);
const option = (name) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1].split(',') : null;
};

const onlySuites = option('suite');
const onlyChecks = option('only');
const useJev = !flag('no-jev');
const runId = newRunId();

async function discover(folder) {
  const dir = resolve(HERE, 'uat', folder);
  const files = readdirSync(dir).filter((f) => f.endsWith('.mjs') && !f.endsWith('.test.mjs')).sort();
  const modules = [];
  for (const file of files) {
    const loaded = await import(pathToFileURL(resolve(dir, file)).href);
    modules.push(loaded.default);
  }
  return modules;
}

/** Search every entity for leftovers of older runs and delete them. */
async function sweep(api, report) {
  for (const type of ENTITY_TYPES) {
    const base = entityPath(type);
    let removed = 0;
    try {
      const found = await api.get(`${base}/search?q=UAT-&page=0&size=200`).catch(() => api.get(`${base}?page=0&size=200`));
      const rows = Array.isArray(found) ? found : (found?.content ?? []);
      for (const row of rows) {
        const label = row.name ?? row.title ?? row.sku ?? row.lastName ?? row.externalId ?? '';
        if (!String(label).startsWith('UAT-')) continue;
        await api.del(`${base}/${row.id}`).then(() => removed++).catch((e) => report.residue({ type, id: row.id, reason: e.message }));
      }
      report.info(`sweep.${type}`, `removed ${removed}`);
    } catch (error) {
      report.info(`sweep.${type}`, `skipped: ${error.message}`, 'low');
    }
  }
}

const report = createReport('uat-full', { strict: flag('strict'), dir: resolve(HERE, '..', '.ci-tmp', 'uat-full') });
const evidenceDir = resolve(HERE, '..', '.ci-tmp', 'uat-full', 'evidence');
let browser;
let preflight = {};

try {
  // UTC + en-US pin date parsing and formatting; passed here so the older scripts are unaffected.
  const session = await launch({ contextOptions: { locale: 'en-US', timezoneId: 'UTC' } });
  browser = session.browser;
  const { page } = session;
  const api = createApi(page.request);
  const monitor = monitorPage(page);

  if (flag('sweep')) {
    await sweep(api, report);
  } else {
    preflight = await runPreflight(page.request);
    for (const [key, result] of Object.entries(preflight)) {
      report.info(`preflight.${key}`, `${result.state}${result.status ? ` (HTTP ${result.status})` : ''} — gates: ${result.gates}`);
    }

    const hypotheses = (await discover('hypotheses')).flat();
    const suites = (await discover('suites')).filter((s) => !onlySuites || onlySuites.includes(s.id));
    if (onlySuites && !suites.length) throw new Error(`no suite matches --suite ${onlySuites.join(',')}`);

    /** Lazily built so deterministic suites never need an API key. */
    let decider;
    const ctx = {
      page, api, monitor, report, preflight, runId, evidenceDir, launch,
      only: onlyChecks,
      useJev,
      async jev() {
        if (!useJev) return null;
        if (!decider) {
          const { createDecider } = await import('./lib/decide.mjs');
          decider = createDecider({ minConfidence: Number(process.env.UAT_MIN_CONFIDENCE) || 0.9 });
        }
        return decider;
      },
    };

    for (const suite of suites) {
      if (suite.jev && !useJev) {
        report.info(`${suite.id}`, 'skipped (--no-jev)');
        continue;
      }
      const reason = suite.backend ? blockedReason(preflight, suite.backend) : null;
      if (reason) {
        report.blocked(suite.id, reason);
        continue;
      }
      console.log(`\n── suite: ${suite.id} ──`);
      try {
        await suite.run(ctx);
      } catch (error) {
        // A suite-level throw is a harness bug or an unrecoverable page; record and move on.
        report.fail(`${suite.id}.suite-crashed`, String(error?.message ?? error).split('\n')[0]);
      }
    }

    for (const item of await api.teardown()) report.residue(item);

    // GAP / unitOnly / falsePositive hypotheses have no browser check; list them so the report
    // accounts for every claim the static pass produced.
    for (const h of hypotheses) {
      if (h.triage === 'gap') report.gap(h.id, h.claim, { hypothesis: h.id });
      else if (h.triage === 'unitOnly') report.info(h.id, `unit-test only: ${h.claim}`);
      else if (h.triage === 'falsePositive') report.info(h.id, `false positive: ${h.claim}${h.note ? ` — ${h.note}` : ''}`);
    }
  }
} catch (error) {
  report.fail('uat-full.fatal', String(error?.stack ?? error).split('\n').slice(0, 3).join(' | '));
} finally {
  await browser?.close();
}

const failures = report.finish({ runId, preflight, jev: useJev, suites: onlySuites ?? 'all', only: onlyChecks });
process.exit(failures ? 1 : 0);
