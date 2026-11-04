import test from 'node:test';
import assert from 'node:assert/strict';
import { readAssetResponse } from './asset-observations.mjs';

test('authorization, routing and service errors do not count as successful reads', async () => {
  for (const status of [400, 401, 403, 404, 503]) {
    await assert.rejects(readAssetResponse({ get: async () => { throw Object.assign(new Error('HTTP error'), { status }); } }, '/assets', 'assets', Array.isArray),
      (error) => error.kind === 'blocked' && error.message.includes(`HTTP ${status}`));
  }
});

test('network errors block, malformed successes fail, and shaped responses pass', async () => {
  await assert.rejects(readAssetResponse({ get: async () => { throw new Error('network'); } }, '/assets', 'assets', Array.isArray), (error) => error.kind === 'blocked');
  await assert.rejects(readAssetResponse({ get: async () => '<html>SPA fallback</html>' }, '/assets', 'assets', Array.isArray), /unexpected successful response shape/);
  assert.deepEqual(await readAssetResponse({ get: async () => [] }, '/assets', 'assets', Array.isArray), []);
});
