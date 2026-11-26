import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

test('records missing Jev credentials as blocked evidence and exits unsuccessfully', (t) => {
  const cwd = mkdtempSync(join(tmpdir(), 'jev-uat-'));
  t.after(() => rmSync(cwd, { recursive: true }));
  const entry = fileURLToPath(new URL('../uat-jev-local.mjs', import.meta.url));
  const result = spawnSync(process.execPath, [entry], {
    cwd,
    encoding: 'utf8',
    env: { ...process.env, TYPESAFE_TRANSPORT: 'openrouter', OPENROUTER_API_KEY: '' },
    timeout: 10000,
  });

  assert.equal(result.status, 1, result.stderr);
  const report = JSON.parse(readFileSync(join(cwd, '.ci-tmp/jev-local-uat.json'), 'utf8'));
  assert.equal(report.model, 'typesafe/jev-1.14');
  assert.equal(report.apiCalls, 0);
  assert.equal(report.summary.BLOCKED, 1);
  assert.match(report.entries[0].detail, /OPENROUTER_API_KEY/);
});
