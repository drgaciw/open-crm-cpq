import { createServer } from 'node:http';
import { readFile, realpath } from 'node:fs/promises';
import { resolve, sep, extname } from 'node:path';

// Test-owned static SPA and real quote HTTP proxy. No gateway or mock responses.
export async function startRampAcceptanceServer(integrationRoot) {
  const wave = resolve('C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009');
  const root = await realpath(integrationRoot);
  if (!root.toLowerCase().startsWith((wave + sep).toLowerCase())) throw new Error('Expected isolated wave worktree');
  const staticRoot = await realpath(resolve(root, 'frontend/dist/apps/crm/browser'));
  await readFile(resolve(staticRoot, 'index.html'));
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.png': 'image/png' };
  const server = createServer(async (incoming, outgoing) => {
    try {
      const url = new URL(incoming.url, 'http://127.0.0.1:18090');
      if (url.pathname.startsWith('/api/')) {
        if (!/^\/api\/v1\/quotes(?:\/|$)/.test(url.pathname)) {
          outgoing.writeHead(503, { 'Content-Type': 'text/plain' });
          outgoing.end('Dependency outside scoped acceptance'); return;
        }
        const chunks = []; for await (const chunk of incoming) chunks.push(chunk);
        const body = Buffer.concat(chunks);
        const response = await fetch(`http://127.0.0.1:18084${url.pathname}${url.search}`, {
          method: incoming.method, headers: { 'Content-Type': incoming.headers['content-type'] || 'application/json' },
          ...(body.length ? { body } : {}), signal: AbortSignal.timeout(30000),
        });
        const headers = {};
        for (const key of ['content-type', 'content-disposition']) if (response.headers.has(key)) headers[key] = response.headers.get(key);
        outgoing.writeHead(response.status, headers);
        outgoing.end(Buffer.from(await response.arrayBuffer())); return;
      }
      const candidate = resolve(staticRoot, `.${decodeURIComponent(url.pathname)}`);
      if (!candidate.startsWith(staticRoot + sep) && candidate !== staticRoot) throw new Error('Invalid static path');
      let file = candidate;
      try { file = await realpath(candidate); } catch { file = resolve(staticRoot, 'index.html'); }
      if (!file.startsWith(staticRoot + sep)) throw new Error('Static path escaped build root');
      let bytes; try { bytes = await readFile(file); } catch { file = resolve(staticRoot, 'index.html'); bytes = await readFile(file); }
      outgoing.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
      outgoing.end(bytes);
    } catch (error) { outgoing.writeHead(502, { 'Content-Type': 'text/plain' }); outgoing.end(`Acceptance request failed: ${error.message}`); }
  });
  await new Promise((accept, reject) => { server.once('error', reject); server.listen(18090, '127.0.0.1', accept); });
  return { origin: 'http://127.0.0.1:18090', close: async () => {
    server.closeAllConnections(); await new Promise((accept, reject) => server.close(error => error ? reject(error) : accept()));
  } };
}
