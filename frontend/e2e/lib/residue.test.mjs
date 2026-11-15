import test from 'node:test';
import assert from 'node:assert/strict';
import { purgeLocalResidue } from './residue.mjs';

const accepted = { type: 'quote', id: 268, reason: 'DELETE /api/v1/quotes/268 -> HTTP 400: {"errorCode":"QUOTE_ACCEPTED_NO_DELETE"}' };

test('purges accepted-quote residue of a local run with the repo script', () => {
  const calls = [];
  const out = purgeLocalResidue({
    base: 'http://localhost:4200', residue: [accepted], env: {},
    run: (cmd, args) => { calls.push([cmd, ...args]); return 'BEGIN\nsoft-deleted 1\nsoft-deleted 1\nCOMMIT\n'; },
  });
  assert.equal(calls.length, 1);
  assert.match(calls[0][0], /scripts\/dev\/purge-uat-residue\.sh$/);
  assert.deepEqual(calls[0].slice(1), ['--apply']);
  assert.equal(out, 'purged locally: soft-deleted 1, soft-deleted 1');
});

test('does nothing for a non-local base URL, without accepted quotes, or when asked to keep residue', () => {
  const run = () => assert.fail('must not run');
  assert.equal(purgeLocalResidue({ base: 'https://staging.example.com', residue: [accepted], env: {}, run }), null);
  assert.equal(purgeLocalResidue({ base: 'http://localhost:4200', residue: [{ type: 'lead', id: 1, reason: 'CONVERTED' }], env: {}, run }), null);
  assert.equal(purgeLocalResidue({ base: 'http://localhost:4200', residue: [accepted], env: { UAT_KEEP_RESIDUE: '1' }, run }), null);
});

test('reports a failed purge instead of throwing', () => {
  const out = purgeLocalResidue({
    base: 'http://127.0.0.1:4200', residue: [accepted], env: {},
    run: () => { throw new Error('docker: not found'); },
  });
  assert.equal(out, 'local purge failed: docker: not found');
});
