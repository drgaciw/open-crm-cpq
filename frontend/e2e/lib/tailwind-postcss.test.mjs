/**
 * Guards the CRM Vite/Angular style pipeline: `@import "tailwindcss"` in
 * apps/crm/src/tailwind.css requires `@tailwindcss/postcss` (Tailwind v4).
 * Without it, the dev server surfaces vite-error-overlay "Could not resolve tailwindcss".
 */
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it } from 'node:test';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const require = createRequire(resolve(root, 'package.json'));

describe('tailwind v4 postcss wiring', () => {
  it('keeps apps/crm/src/tailwind.css on the Tailwind v4 import', () => {
    const css = readFileSync(resolve(root, 'apps/crm/src/tailwind.css'), 'utf8');
    assert.match(css, /@import\s+["']tailwindcss["']/);
  });

  it('lists tailwind.css in the crm application styles', () => {
    // project.json may contain // comments (Angular/Nx); avoid JSON.parse.
    const project = readFileSync(resolve(root, 'apps/crm/project.json'), 'utf8');
    assert.match(
      project,
      /"styles"\s*:\s*\[[^\]]*apps\/crm\/src\/tailwind\.css/s,
      'crm build styles must include tailwind.css',
    );
  });

  it('registers @tailwindcss/postcss in postcss.config.json', () => {
    const configPath = resolve(root, 'postcss.config.json');
    assert.equal(existsSync(configPath), true, 'frontend/postcss.config.json must exist');
    const config = JSON.parse(readFileSync(configPath, 'utf8'));
    assert.ok(config.plugins?.['@tailwindcss/postcss'], '@tailwindcss/postcss plugin required');
  });

  it('can resolve @tailwindcss/postcss and tailwindcss packages', () => {
    assert.doesNotThrow(() => require.resolve('@tailwindcss/postcss'));
    assert.doesNotThrow(() => require.resolve('tailwindcss'));
  });
});
