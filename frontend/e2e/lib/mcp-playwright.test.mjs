import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseSnapshot, responseFacts, createMcpClient } from './mcp-playwright.mjs';

const SNAPSHOT = `- generic [ref=e4]:
  - link "Skip to main content" [ref=e5] [cursor=pointer]:
    - /url: "#main-content"
  - navigation "Main navigation" [ref=e19]:
    - link "Accounts" [ref=e29] [cursor=pointer]:
      - /url: /organizations
      - img [aria-hidden] [ref=e30]: business
    - link "Quotes" [ref=e45] [cursor=pointer]:
      - /url: /quotes
  - main [ref=e68]:
    - heading "New Account" [level=1] [ref=e80]
    - textbox "Account name" [ref=e90]
    - combobox "Industry" [ref=e91]
    - textbox "Notes \\"quoted\\"" [ref=e92]: some value
    - button "Create" [disabled] [ref=e95]
    - button "Cancel" [ref=e96] [cursor=pointer]
`;

test('parseSnapshot lists role, name, ref, url and state of every referenced element', () => {
  const elements = parseSnapshot(SNAPSHOT);
  const byRef = Object.fromEntries(elements.map((e) => [e.ref, e]));
  assert.deepEqual(byRef.e29, { role: 'link', name: 'Accounts', ref: 'e29', url: '/organizations', disabled: false });
  assert.equal(byRef.e45.url, '/quotes');
  assert.equal(byRef.e90.role, 'textbox');
  assert.equal(byRef.e90.name, 'Account name');
  assert.equal(byRef.e92.name, 'Notes "quoted"');
  assert.equal(byRef.e95.disabled, true);
  assert.equal(byRef.e96.disabled, false);
  assert.equal(byRef.e80.role, 'heading');
  assert.equal(byRef.e4.name, '');
});

test('responseFacts reads the page URL, title and snapshot file from a tool response', () => {
  const text = [
    '### Ran Playwright code', '```js', "await page.goto('http://localhost:4200/quotes');", '```',
    '### Page', '- Page URL: http://localhost:4200/quotes?status=DRAFT', '- Page Title: Open CRM',
    '### Snapshot', '- [Snapshot](.playwright-mcp/page-2026-11-15T10-39-34-329Z.yml)',
  ].join('\n');
  assert.deepEqual(responseFacts(text), {
    url: 'http://localhost:4200/quotes?status=DRAFT',
    title: 'Open CRM',
    snapshotFile: '.playwright-mcp/page-2026-11-15T10-39-34-329Z.yml',
  });
  assert.deepEqual(responseFacts('### Result\nok'), { url: null, title: null, snapshotFile: null });
});

test('createMcpClient speaks newline-delimited JSON-RPC: initialize, tools/list and tools/call', async () => {
  // A stand-in MCP server: answers initialize, lists one tool and echoes tool calls.
  const dir = mkdtempSync(join(tmpdir(), 'mcp-fake-'));
  const server = join(dir, 'server.mjs');
  writeFileSync(server, `
    import { createInterface } from 'node:readline';
    const seen = [];
    createInterface({ input: process.stdin }).on('line', (line) => {
      const m = JSON.parse(line);
      seen.push(m.method);
      const reply = (result) => process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: m.id, result }) + '\\n');
      if (m.method === 'initialize') reply({ protocolVersion: '2025-06-18', serverInfo: { name: 'Fake', version: '1' }, capabilities: {} });
      else if (m.method === 'tools/list') reply({ tools: [{ name: 'echo' }] });
      else if (m.method === 'tools/call' && m.params.name === 'boom') reply({ isError: true, content: [{ type: 'text', text: 'it broke' }] });
      else if (m.method === 'tools/call') reply({ content: [{ type: 'text', text: 'got ' + JSON.stringify(m.params.arguments) + ' after ' + seen.join(',') }] });
    });
  `);
  const client = await createMcpClient({ command: process.execPath, args: [server] });
  try {
    assert.equal(client.serverInfo.name, 'Fake');
    assert.deepEqual(client.tools, ['echo']);
    const text = await client.call('echo', { ref: 'e5' });
    assert.equal(text, 'got {"ref":"e5"} after initialize,notifications/initialized,tools/list,tools/call');
    await assert.rejects(client.call('boom', {}), /boom: it broke/);
  } finally {
    client.close();
  }
});
