import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';
import { nxViteTsPaths } from '@nx/vite/plugins/nx-tsconfig-paths.plugin';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  root: fileURLToPath(new URL('./browser', import.meta.url)),
  cacheDir: fileURLToPath(
    new URL('../../../.cache/orders-browser-vite', import.meta.url),
  ),
  plugins: [
    angular({
      tsconfig: fileURLToPath(
        new URL('./tsconfig.browser.json', import.meta.url),
      ),
    }),
    nxViteTsPaths(),
  ],
  server: {
    host: '127.0.0.1',
    port: 4317,
    strictPort: true,
    fs: { allow: [fileURLToPath(new URL('../../../', import.meta.url))] },
  },
});
