/**
 * Result recording and evidence output, shared by the smoke, chaos and agent scripts.
 * Each previously rolled its own PASS/FAIL logging and JSON writing.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** Where evidence JSON lands; .ci-tmp is already gitignored. */
const EVIDENCE_DIR = resolve(process.cwd(), '.ci-tmp');

export function createReport(name) {
  const entries = [];
  const started = Date.now();

  const push = (status, id, detail, severity) => {
    entries.push({ id, status, detail, severity });
    const line = `${status.padEnd(4)}  ${severity ? `[${severity}] ` : ''}${id}${detail ? ` — ${detail}` : ''}`;
    (status === 'FAIL' ? console.error : console.log)(line);
  };

  return {
    pass: (id, detail = '') => push('PASS', id, detail),
    fail: (id, detail = '') => push('FAIL', id, detail),
    info: (id, detail = '', severity = 'info') => push('INFO', id, detail, severity),
    entries,

    /**
     * Write evidence JSON and return the failure count, so callers can set an exit code.
     * @param {object} [extra] Merged into the written document.
     */
    finish(extra = {}) {
      const failed = entries.filter((e) => e.status === 'FAIL');
      const document = {
        name,
        durationMs: Date.now() - started,
        passed: entries.filter((e) => e.status === 'PASS').length,
        failed: failed.length,
        entries,
        ...extra,
      };
      const file = resolve(EVIDENCE_DIR, `${name}.json`);
      mkdirSync(dirname(file), { recursive: true });
      writeFileSync(file, JSON.stringify(document, null, 2));
      console.log(
        `\n${document.passed}/${entries.length} passed — evidence written to ${file}`,
      );
      return failed.length;
    },
  };
}
