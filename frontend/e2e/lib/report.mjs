/**
 * Result recording and evidence output, shared by the smoke, chaos, agent and full-UAT scripts.
 *
 * Every check ends in exactly one outcome:
 *   PASS / FAIL          deterministic assertion held / did not
 *   EXPECTED_FAIL        a FAIL linked to a registered hypothesis — tolerated until --strict
 *   BLOCKED              environment (backend down, or an earlier workflow step failed)
 *   INCONCLUSIVE         a model judgement fell below the confidence gate; never a PASS
 *   GAP                  missing feature — reported, never "fixed"
 *   INFO                 observation only
 *
 * `pass` / `fail` / `info` / `finish` keep their original signatures so the three
 * pre-existing scripts are unaffected.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** Where evidence JSON lands; .ci-tmp is gitignored. */
const EVIDENCE_DIR = resolve(process.cwd(), '.ci-tmp');

/** How a run's observation of a check translates into a verdict on its hypothesis. */
const HYPOTHESIS_VERDICT = {
  EXPECTED_FAIL: 'CONFIRMED',
  FAIL: 'CONFIRMED',
  PASS: 'REFUTED',
  BLOCKED: 'BLOCKED',
  INCONCLUSIVE: 'INCONCLUSIVE',
  GAP: 'GAP',
};

/**
 * @param {string} name Evidence file stem.
 * @param {object} [options]
 * @param {boolean} [options.strict] Count expected failures as failures.
 * @param {boolean} [options.write]  Write the JSON document on finish (default true).
 * @param {boolean} [options.log]    Print each entry as it is recorded (default true).
 * @param {string}  [options.dir]    Evidence directory override.
 */
export function createReport(name, { strict = false, write = true, log = true, dir = EVIDENCE_DIR } = {}) {
  const entries = [];
  const residue = [];
  const started = Date.now();

  const push = (status, id, detail = '', extra = {}) => {
    const entry = { id, status, detail };
    for (const key of ['severity', 'hypothesis', 'evidence', 'blockedBy']) {
      if (extra[key] !== undefined) entry[key] = extra[key];
    }
    entries.push(entry);
    if (log) {
      const tag = entry.hypothesis ? ` {${entry.hypothesis}}` : '';
      const sev = entry.severity ? `[${entry.severity}] ` : '';
      const line = `${status.padEnd(13)} ${sev}${id}${tag}${detail ? ` — ${detail}` : ''}`;
      (status === 'FAIL' ? console.error : console.log)(line);
    }
    return entry;
  };

  const isFailure = (e) => e.status === 'FAIL' || (strict && e.status === 'EXPECTED_FAIL');

  function document(extra = {}) {
    const summary = {};
    const hypothesisVerdicts = {};
    for (const e of entries) {
      summary[e.status] = (summary[e.status] ?? 0) + 1;
      if (e.hypothesis && HYPOTHESIS_VERDICT[e.status]) {
        // A confirmed defect outranks any other observation of the same hypothesis.
        if (hypothesisVerdicts[e.hypothesis] !== 'CONFIRMED') {
          hypothesisVerdicts[e.hypothesis] = HYPOTHESIS_VERDICT[e.status];
        }
      }
    }
    return {
      name,
      strict,
      durationMs: Date.now() - started,
      passed: summary.PASS ?? 0,
      failed: entries.filter(isFailure).length,
      summary,
      hypothesisVerdicts,
      residue,
      entries,
      ...extra,
    };
  }

  return {
    pass: (id, detail = '', extra = {}) => push('PASS', id, detail, extra),
    /** A failure linked to a hypothesis is recorded as EXPECTED_FAIL. */
    fail: (id, detail = '', extra = {}) => push(extra.hypothesis ? 'EXPECTED_FAIL' : 'FAIL', id, detail, extra),
    info: (id, detail = '', severity = 'info') => push('INFO', id, detail, { severity }),
    blocked: (id, reason = '', extra = {}) => push('BLOCKED', id, reason, extra),
    inconclusive: (id, detail = '', extra = {}) => push('INCONCLUSIVE', id, detail, extra),
    gap: (id, detail = '', extra = {}) => push('GAP', id, detail, extra),
    /** Records that could not be cleaned up (e.g. CONVERTED leads, ACCEPTED quotes). */
    residue: (item) => residue.push(item),
    entries,
    document,

    /**
     * Write evidence JSON and return the failure count, so callers can set an exit code.
     * @param {object} [extra] Merged into the written document.
     */
    finish(extra = {}) {
      const doc = document(extra);
      if (write) {
        const file = resolve(dir, `${name}.json`);
        mkdirSync(dirname(file), { recursive: true });
        writeFileSync(file, JSON.stringify(doc, null, 2));
        if (log) {
          const counts = Object.entries(doc.summary).map(([k, v]) => `${k} ${v}`).join(' · ');
          console.log(`\n${counts} — evidence written to ${file}`);
        }
      }
      return doc.failed;
    },
  };
}
