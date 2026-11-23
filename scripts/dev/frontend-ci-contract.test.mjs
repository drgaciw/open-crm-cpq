import test from 'node:test';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '../..');
const FRONTEND_DIR = resolve(REPO_ROOT, 'frontend');

function nxProjects(extraArgs = '') {
  const out = execSync(`npx nx show projects ${extraArgs}`, {
    cwd: FRONTEND_DIR,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  return out
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .sort();
}

test('T1.1: every Nx project exposes a typecheck target', () => {
  const allProjects = nxProjects();
  const typecheckProjects = nxProjects('--with-target=typecheck');

  assert.ok(allProjects.length >= 20, `expected >= 20 Nx projects, got ${allProjects.length}`);
  assert.deepEqual(
    typecheckProjects,
    allProjects,
    `projects missing typecheck target: ${allProjects.filter((p) => !typecheckProjects.includes(p)).join(', ')}`,
  );
});

test('T1.2: scripts/ci/frontend.sh runs nx typecheck across --all projects', () => {
  const script = readFileSync(resolve(REPO_ROOT, 'scripts/ci/frontend.sh'), 'utf8');
  assert.match(
    script,
    /npx nx run-many\s+(--target=typecheck|-t\s+typecheck)\s+--all/,
    'scripts/ci/frontend.sh must invoke nx run-many typecheck --all',
  );
});

test('T1.3: .github/workflows/pr-check.yml has a frontend job calling run-core.sh frontend with pinned actions, cache, timeout, and contents: read', () => {
  const yml = readFileSync(resolve(REPO_ROOT, '.github/workflows/pr-check.yml'), 'utf8');

  assert.match(yml, /^permissions:\s*\n\s+contents:\s*read\s*$/m, 'permissions must stay contents: read');
  assert.doesNotMatch(yml, /write/, 'pr-check.yml must not request write permissions');

  const frontendIdx = yml.indexOf('\n  frontend:');
  assert.ok(frontendIdx !== -1, 'pr-check.yml must define a frontend job');
  const rest = yml.slice(frontendIdx + 1);
  const nextJobIdx = rest.slice(1).search(/\n  [a-zA-Z0-9_-]+:/);
  const jobBody = nextJobIdx === -1 ? rest : rest.slice(0, nextJobIdx + 1);

  assert.match(jobBody, /timeout-minutes:\s*\d+/, 'frontend job must declare timeout-minutes');
  assert.match(jobBody, /uses:\s*actions\/checkout@v\d+/, 'frontend job must pin actions/checkout');
  assert.match(jobBody, /uses:\s*actions\/setup-node@v\d+/, 'frontend job must pin actions/setup-node');
  assert.match(jobBody, /cache:\s*npm/, 'frontend job must enable npm cache');
  assert.match(
    jobBody,
    /cache-dependency-path:\s*frontend\/package-lock\.json/,
    'frontend job must cache on frontend/package-lock.json',
  );
  assert.match(
    jobBody,
    /bash scripts\/ci\/run-core\.sh frontend/,
    'frontend job must run bash scripts/ci/run-core.sh frontend',
  );
});
