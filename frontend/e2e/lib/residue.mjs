import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** scripts/dev/purge-uat-residue.sh at the repository root. */
export const PURGE_SCRIPT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..', 'scripts', 'dev', 'purge-uat-residue.sh');

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

/**
 * After teardown, soft-delete the accepted UAT quotes (and their contracts) that the API refuses to
 * delete, when the run used a local stack. Returns a one-line outcome for the report, or null when
 * nothing applied: a non-local base URL, no accepted-quote residue, or UAT_KEEP_RESIDUE=1.
 * Never throws.
 */
export function purgeLocalResidue({ base, residue, env = process.env, run = defaultRun }) {
  if (env.UAT_KEEP_RESIDUE === '1') return null;
  let host;
  try { host = new URL(base).hostname; } catch { return null; }
  if (!LOCAL_HOSTS.has(host)) return null;
  const accepted = (residue ?? []).some((r) => r.type === 'quote' && /QUOTE_ACCEPTED_NO_DELETE|accepted quote/i.test(r.reason ?? ''));
  if (!accepted) return null;
  try {
    const out = run(PURGE_SCRIPT, ['--apply']);
    const done = String(out).split('\n').filter((l) => l.startsWith('soft-deleted')).join(', ');
    return `purged locally: ${done || 'nothing to purge'}`;
  } catch (error) {
    return `local purge failed: ${String(error.message || error).split('\n')[0]}`;
  }
}

function defaultRun(cmd, args) {
  return execFileSync(cmd, args, { encoding: 'utf8', timeout: 30000 });
}
