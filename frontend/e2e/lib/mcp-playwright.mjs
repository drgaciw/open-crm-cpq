/**
 * A minimal client for the Playwright MCP server (@playwright/mcp) over stdio, plus readers for
 * what its tools return. Jev decides from the server's own accessibility snapshots and acts
 * through its tools (browser_click, browser_type, ...), so the browser is driven only through MCP.
 *
 * The MCP stdio transport is newline-delimited JSON-RPC 2.0: initialize, then the
 * notifications/initialized notification, then tools/list and tools/call.
 */
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';

const PROTOCOL_VERSION = '2025-06-18';

/**
 * Elements of an aria snapshot that carry a ref or a name, in document order.
 * A line looks like `- link "Accounts" [ref=e29] [cursor=pointer]:`; a link's target is the
 * `- /url: /organizations` line nested under it.
 *
 * @param {string} yaml
 * @returns {{role:string,name:string,ref:string|null,url?:string,disabled:boolean}[]}
 */
export function parseSnapshot(yaml) {
  const elements = [];
  const stack = []; // [{indent, element}]
  for (const line of yaml.split('\n')) {
    const indent = line.search(/\S/);
    if (indent < 0) continue;
    while (stack.length && stack.at(-1).indent >= indent) stack.pop();

    const url = line.match(/^\s*- \/url: "?([^"]*)"?\s*$/);
    if (url) {
      const owner = stack.at(-1)?.element;
      if (owner && owner.url === undefined) owner.url = url[1];
      continue;
    }
    const m = line.match(/^\s*- ([a-z]+)(?: "((?:[^"\\]|\\.)*)")?((?: \[[^\]]+\])*)/);
    if (!m) continue;
    const attrs = m[3] ?? '';
    // A disabled control has no ref (it cannot be acted on) but is kept when named, so checks can see it.
    const ref = attrs.match(/\[ref=([^\]]+)\]/)?.[1] ?? null;
    if (!ref && m[2] === undefined) continue;
    const element = {
      role: m[1],
      name: (m[2] ?? '').replace(/\\(.)/g, '$1'),
      ref,
      disabled: /\[disabled\]/.test(attrs),
    };
    if (m[1] === 'link') element.url = undefined;
    elements.push(element);
    stack.push({ indent, element });
  }
  return elements.map((e) => (e.url === undefined ? withoutUrl(e) : e));
}

function withoutUrl({ url, ...rest }) {
  return rest;
}

/**
 * Data rows of the tables in a parsed snapshot, each with the text of its named cells. The data
 * table makes the row the click target but gives it no name, so a row is described by its cells.
 * Header rows (those holding column headers) are skipped.
 *
 * @param {ReturnType<typeof parseSnapshot>} elements
 * @returns {{ref:string,cells:string[]}[]}
 */
export function tableRows(elements) {
  const rows = [];
  let current = null;
  const close = () => {
    if (current && !current.header && current.ref) rows.push({ ref: current.ref, cells: current.cells });
  };
  for (const e of elements) {
    if (e.role === 'row') {
      close();
      current = { ref: e.ref, cells: [], header: false };
    } else if (current && e.role === 'columnheader') {
      current.header = true;
    } else if (current && e.role === 'cell' && e.name) {
      current.cells.push(e.name);
    }
  }
  close();
  return rows;
}

/**
 * Facts the Playwright MCP prints in a tool response: page URL, title, and the snapshot. Action
 * tools link the snapshot as a file (relative to the server's working directory); browser_snapshot
 * returns it inline in a yaml block.
 * @param {string} text
 */
export function responseFacts(text) {
  return {
    url: text.match(/^- Page URL: (.+)$/m)?.[1]?.trim() ?? null,
    title: text.match(/^- Page Title: (.*)$/m)?.[1]?.trim() ?? null,
    snapshotFile: text.match(/^- \[Snapshot\]\(([^)]+)\)$/m)?.[1] ?? null,
    snapshot: text.match(/^### Snapshot\n```yaml\n([\s\S]*?)\n```/m)?.[1] ?? null,
  };
}

/**
 * Start an MCP server and complete the handshake.
 *
 * @param {object} options
 * @param {string} options.command
 * @param {string[]} [options.args]
 * @param {string} [options.cwd]
 * @param {number} [options.timeoutMs] Per request.
 */
export async function createMcpClient({ command, args = [], cwd, timeoutMs = 60000 }) {
  const child = spawn(command, args, { cwd, stdio: ['pipe', 'pipe', 'pipe'] });
  const pending = new Map();
  let nextId = 0;
  let exited = null;
  const stderr = [];
  child.stderr.on('data', (d) => { stderr.push(String(d)); if (stderr.length > 50) stderr.shift(); });
  child.on('exit', (code) => {
    exited = code;
    for (const { reject } of pending.values()) reject(new Error(`MCP server exited (${code}): ${stderr.join('').slice(-500)}`));
    pending.clear();
  });
  createInterface({ input: child.stdout }).on('line', (line) => {
    if (!line.trim()) return;
    let message;
    try { message = JSON.parse(line); } catch { return; } // not protocol output
    const waiter = message.id !== undefined && pending.get(message.id);
    if (!waiter) return;
    pending.delete(message.id);
    clearTimeout(waiter.timer);
    if (message.error) waiter.reject(new Error(`${waiter.method}: ${message.error.message}`));
    else waiter.resolve(message.result);
  });

  const send = (message) => child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', ...message })}\n`);
  const request = (method, params) => new Promise((resolve, reject) => {
    if (exited !== null) return reject(new Error(`MCP server exited (${exited})`));
    const id = ++nextId;
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error(`${method} timed out after ${timeoutMs}ms`));
    }, timeoutMs);
    pending.set(id, { resolve, reject, timer, method });
    send({ id, method, params });
  });

  const init = await request('initialize', {
    protocolVersion: PROTOCOL_VERSION,
    capabilities: {},
    clientInfo: { name: 'open-crm-cpq-jev-uat', version: '1' },
  });
  send({ method: 'notifications/initialized' });
  const listed = await request('tools/list', {});

  return {
    serverInfo: init.serverInfo,
    protocolVersion: init.protocolVersion,
    tools: listed.tools.map((t) => t.name),
    /** Call a tool; resolves with its text content, rejects when the tool reports an error. */
    async call(name, args = {}) {
      const result = await request('tools/call', { name, arguments: args });
      const text = (result.content ?? []).filter((c) => c.type === 'text').map((c) => c.text).join('\n');
      if (result.isError) throw new Error(`${name}: ${text}`);
      return text;
    },
    close() {
      child.stdin.end();
      child.kill();
    },
  };
}
