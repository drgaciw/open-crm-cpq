import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseSnapshot, responseFacts, createMcpClient, tableRows, actionCandidates } from './mcp-playwright.mjs';

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
    - button "Create" [disabled]
    - button "Cancel" [ref=e96] [cursor=pointer]
`;

test('parseSnapshot lists role, name, ref, url and state of every element it can act on or report', () => {
  const elements = parseSnapshot(SNAPSHOT);
  const byRef = Object.fromEntries(elements.map((e) => [e.ref, e]));
  assert.deepEqual(byRef.e29, { role: 'link', name: 'Accounts', ref: 'e29', url: '/organizations', disabled: false, scope: 'navigation' });
  assert.equal(byRef.e45.url, '/quotes');
  assert.equal(byRef.e90.role, 'textbox');
  assert.equal(byRef.e90.name, 'Account name');
  assert.equal(byRef.e92.name, 'Notes "quoted"');
  // Playwright MCP gives a disabled control no ref; it is still listed so a check can see it.
  const create = elements.find((e) => e.role === 'button' && e.name === 'Create');
  assert.deepEqual(create, { role: 'button', name: 'Create', ref: null, disabled: true, scope: 'main' });
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
    snapshot: null,
  });
  assert.deepEqual(responseFacts('### Result\nok'), { url: null, title: null, snapshotFile: null, snapshot: null });
});

test('responseFacts returns an inline snapshot, as browser_snapshot sends it', () => {
  const text = [
    '### Page', '- Page URL: http://localhost:4200/organizations/new', '- Page Title: Open CRM',
    '### Snapshot', '```yaml', '- generic [ref=e4]:', '  - button "Create" [disabled]', '```',
  ].join('\n');
  const facts = responseFacts(text);
  assert.equal(facts.snapshotFile, null);
  assert.equal(facts.snapshot, '- generic [ref=e4]:\n  - button "Create" [disabled]');
  assert.equal(parseSnapshot(facts.snapshot).find((e) => e.name === 'Create').disabled, true);
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

test('tableRows groups each data row with its cell texts; header rows are skipped', () => {
  // As the data table renders: rows carry no name, only their cells do.
  const yaml = `- table [ref=e150]:
  - rowgroup [ref=e151]:
    - row "Account Name Industry" [ref=e152]:
      - columnheader "Account Name" [ref=e153]
      - columnheader "Industry" [ref=e154]
  - rowgroup [ref=e160]:
    - row [ref=e161] [cursor=pointer]:
      - cell "UAT Org 1" [ref=e162]
      - cell "—" [ref=e163]
      - cell [ref=e168]:
        - button "Actions for UAT Org 1" [ref=e169]
    - row [ref=e170] [cursor=pointer]:
      - cell "UAT Org 2" [ref=e171]
- button "Next page" [ref=e200]`;
  assert.deepEqual(tableRows(parseSnapshot(yaml)), [
    { ref: 'e161', cells: ['UAT Org 1', '—'] },
    { ref: 'e170', cells: ['UAT Org 2'] },
  ]);
});

test('parseSnapshot records the nearest scope (listbox, menu, dialog, main, navigation) of each element', () => {
  const yaml = `- generic [ref=e1]:
  - navigation "Main navigation" [ref=e2]:
    - link "Leads" [ref=e3]:
      - /url: /leads
  - main [ref=e4]:
    - button "New Lead" [ref=e5]
    - generic [ref=e6]:
      - combobox "Priority" [ref=e7]
  - dialog "Mark lead lost" [ref=e8]:
    - textbox "Reason" [ref=e9]
    - button "Confirm" [ref=e10]
  - listbox "Priority" [ref=e11]:
    - option "High" [ref=e12]
  - menu [ref=e13]:
    - menuitem "Edit" [ref=e14]
  - button "Outside" [ref=e15]`;
  const scopes = Object.fromEntries(parseSnapshot(yaml).map((e) => [e.ref, e.scope]));
  assert.equal(scopes.e3, 'navigation');
  assert.equal(scopes.e5, 'main');
  assert.equal(scopes.e7, 'main');
  assert.equal(scopes.e9, 'dialog');
  assert.equal(scopes.e10, 'dialog');
  assert.equal(scopes.e12, 'listbox');
  assert.equal(scopes.e14, 'menu');
  assert.equal(scopes.e15, null);
  assert.equal(scopes.e8, null); // a landmark is not inside itself
});

const PAGE = `- navigation "Main navigation" [ref=e2]:
  - link "Leads" [ref=e3]:
    - /url: /leads
- main [ref=e4]:
  - button "New Lead" [ref=e5]
  - button "Save" [disabled]
  - textbox "Title" [ref=e6]
  - combobox "Priority" [ref=e7]
  - table [ref=e20]:
    - row "Title Status" [ref=e21]:
      - columnheader "Title" [ref=e22]
    - row [ref=e23] [cursor=pointer]:
      - cell "UAT lead" [ref=e24]
      - cell "New" [ref=e25]
      - cell [ref=e26]:
        - button "Actions for UAT lead" [ref=e27]`;

test('actionCandidates offers the main region: clicks, fills, one "use" per combobox, and rows by their cells', () => {
  const actions = actionCandidates(parseSnapshot(PAGE));
  const keys = actions.map((a) => `${a.key} ${a.kind} ${a.label}`);
  assert.deepEqual(keys, [
    'e5 click button "New Lead"',
    'e6= fill textbox "Title"',
    'e7 use combobox "Priority"',
    'e27 click button "Actions for UAT lead"',
    'e23 click table row: UAT lead | New',
  ]);
  assert.equal(actions.find((a) => a.key === 'e6=').ref, 'e6');
});

test('actionCandidates narrows to an open dialog, menu or listbox', () => {
  const withDialog = `${PAGE}
- dialog "Mark lead lost" [ref=e40]:
  - textbox "Reason" [ref=e41]
  - button "Cancel" [ref=e42]
  - button "Confirm" [ref=e43]`;
  assert.deepEqual(actionCandidates(parseSnapshot(withDialog)).map((a) => a.key), ['e41=', 'e42', 'e43']);
  const withList = `${withDialog}
- listbox "Reason code" [ref=e50]:
  - option "Budget" [ref=e51]
  - option "Timing" [ref=e52]`;
  assert.deepEqual(actionCandidates(parseSnapshot(withList)).map((a) => a.label), ['option "Budget"', 'option "Timing"']);
});

test('actionCandidates leaves out column sort buttons and paginator controls, and offers navigation only when asked', () => {
  const yaml = `- navigation "Main navigation" [ref=e2]:
  - link "Leads" [ref=e3]:
    - /url: /leads
- main [ref=e4]:
  - button "New Lead" [ref=e5]
  - table [ref=e20]:
    - row "Title Status" [ref=e21]:
      - columnheader "Title" [ref=e22]:
        - button "Title" [ref=e23]
    - row [ref=e24]:
      - cell "UAT lead" [ref=e25]
  - generic [ref=e30]:
    - combobox "Items per page:" [ref=e31]
    - button "Previous page" [disabled]
    - button "Next page" [ref=e32]`;
  const elements = parseSnapshot(yaml);
  assert.equal(elements.find((e) => e.ref === 'e23').header, true);
  assert.equal(elements.find((e) => e.ref === 'e5').header, undefined);
  assert.deepEqual(actionCandidates(elements).map((a) => a.key), ['e5', 'e24']);
  assert.deepEqual(actionCandidates(elements, { navigation: true }).map((a) => a.key), ['e3', 'e5', 'e24']);
});

test('parseSnapshot keeps the value a field shows', () => {
  const elements = parseSnapshot(`- main [ref=e1]:
  - textbox "City" [ref=e2]: Austin
  - spinbutton "Amount" [ref=e3]: "12000"
  - combobox "Priority" [ref=e4]: High
  - textbox "Empty" [ref=e5]:
    - /placeholder: Enter a name
  - button "Create" [ref=e6]
  - textbox "Account Name" [active] [ref=e7]:
    - /placeholder: Enter account name
    - text: UAT-PROBE`);
  const value = (ref) => elements.find((e) => e.ref === ref).value;
  assert.equal(value('e2'), 'Austin');
  assert.equal(value('e3'), '12000');
  assert.equal(value('e4'), 'High');
  assert.equal(value('e5'), undefined);
  assert.equal(value('e6'), undefined);
  // As the Playwright MCP renders a typed input: the value is a `text:` child, after the placeholder.
  assert.equal(value('e7'), 'UAT-PROBE');
  assert.equal(elements.find((e) => e.ref === 'e7').name, 'Account Name');
});

test('parseSnapshot reads YAML-quoted lines (names containing "#" or ":")', () => {
  // As the Playwright MCP writes them: the whole entry is single-quoted, '' escapes a quote.
  const elements = parseSnapshot(`- main [ref=e1]:
  - row [ref=e2]:
    - cell "11" [ref=e3]
    - 'button "Approve approval #11 for quote 211" [ref=e4]':
      - img [aria-hidden] [ref=e5]: check
    - 'button "Reject approval #11 for quote 211" [ref=e6]'
    - 'link "Ada''s note: #2" [ref=e7]':
      - /url: /notes/2`);
  const byRef = Object.fromEntries(elements.map((e) => [e.ref, e]));
  assert.equal(byRef.e4.name, 'Approve approval #11 for quote 211');
  assert.equal(byRef.e4.role, 'button');
  assert.equal(byRef.e6.name, 'Reject approval #11 for quote 211');
  assert.equal(byRef.e7.name, "Ada's note: #2");
  assert.equal(byRef.e7.url, '/notes/2');
});

test('actionCandidates leaves out controls without a name', () => {
  const elements = parseSnapshot(`- main [ref=e1]:
  - button "Submit" [ref=e2]
  - button [ref=e3]:
    - img [aria-hidden] [ref=e4]: more_vert
  - link [ref=e5]:
    - /url: /x`);
  assert.deepEqual(actionCandidates(elements).map((a) => a.key), ['e2']);
});

test('parseSnapshot keeps the text of a paragraph, with or without a ref', () => {
  const els = parseSnapshot([
    '- dialog "Retire product" [ref=e1]:',
    '  - paragraph [ref=e2]: A retired product cannot be sold or reactivated.',
    '  - paragraph: Why is this product retired?',
    '  - button "Retire" [ref=e4]',
  ].join('\n'));
  const paragraphs = els.filter((e) => e.role === 'paragraph').map((e) => [e.name, e.scope]);
  assert.deepEqual(paragraphs, [
    ['A retired product cannot be sold or reactivated.', 'dialog'],
    ['Why is this product retired?', 'dialog'],
  ]);
});
