import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const runner = fileURLToPath(new URL('../uat-asset-lifecycle-crm-api.mjs', import.meta.url));

async function exercise(allowWrites) {
  const requests = [];
  let assetReads = 0;
  const server = createServer((req, res) => {
    requests.push([req.method, req.url]);
    res.setHeader('Content-Type', 'application/json');
    if (req.url === '/actuator/health') return res.end(JSON.stringify({status:'UP'}));
    if (req.url.startsWith('/api/v1/organizations')) return res.end(JSON.stringify({data:{content:[{id:1, uuid:'00000000-0000-4000-8000-000000000001'}]}}));
    if (req.method === 'POST' && req.url === '/api/v1/quotes') { res.statusCode=403; return res.end('{}'); }
    if (req.method === 'POST' && req.url === '/api/v1/deals') return res.end(JSON.stringify({data:{id:1}}));
    if (req.url.startsWith('/api/v1/deals')) return res.end(JSON.stringify({data:{content:[]}}));
    if (req.url.startsWith('/api/v1/assets')) { if (req.url.includes('accountId=uat-alm')) assetReads++; res.statusCode = req.url.includes('accountId=uat-alm') && assetReads > 1 ? 403 : 404; return res.end(JSON.stringify({errorCode:'RESOURCE_NOT_FOUND'})); }
    res.statusCode=404; res.end('{}');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const child = spawn(process.execPath, [runner], {env:{...process.env,UAT_BASE_URL:base,UAT_CAM_BASE_URL:base,UAT_QUOTE_BASE_URL:base,UAT_ALLOW_WRITES:String(allowWrites)}});
  let output=''; child.stdout.on('data', chunk => output += chunk); child.stderr.on('data', chunk => output += chunk);
  const code = await new Promise((resolve,reject) => {child.on('error',reject); child.on('close',resolve)});
  await new Promise(resolve => server.close(resolve));
  return {requests,output,code};
}

test('default run blocks unexercised checks and does not write fixtures', async () => {
  const {requests,output,code} = await exercise(false);
  assert.equal(code,0,output);
  assert.equal(requests.some(([method]) => method === 'POST'), false);
  for (const id of ['asset-lifecycle.api.assets-up','asset-lifecycle.api.history-shape','asset-lifecycle.hold.armed','asset-lifecycle.null-start.unit-gate','asset-lifecycle.quote.draft-create'])
    assert.match(output, new RegExp(`${id.replaceAll('.', '\\.')} \\[BLOCKED\\]`));
});

test('an unauthorized quote create cannot pass as a draft', async () => {
  const {requests,output,code} = await exercise(true);
  assert.equal(code,0,output);
  assert.ok(requests.some(([method,path]) => method === 'POST' && path === '/api/v1/quotes'));
  assert.match(output, /asset-lifecycle\.quote\.draft-create \[BLOCKED\]/);
});
