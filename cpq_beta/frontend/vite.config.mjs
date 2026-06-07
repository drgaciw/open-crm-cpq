import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import path from 'path'
import { fileURLToPath } from 'url'
import frappeui from 'frappe-ui/vite'
import tailwindcss from '@tailwindcss/vite'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [
    tailwindcss(),
    frappeui({ frappeProxy: false, buildConfig: false }),
    vue({
      script: {
        propsDestructure: true,
      },
    }),
    vueJsx(),
    {
      name: 'transform-index.html',
      transformIndexHtml(html, context) {
        if (!context.server) {
          return html.replace(
            /<\/body>/,
            `
            <script>
                {% for key in boot %}
                window["{{ key }}"] = {{ boot[key] | tojson }};
                {% endfor %}
            </script>
            </body>
            `
          )
        }
        return html
      },
    },
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      'frappe-ui/src': path.resolve(__dirname, 'node_modules/frappe-ui/src'),
    },
  },
  build: {
    outDir: '../crm/public/frontend',
    emptyOutDir: true,
    sourcemap: true,
  },
  optimizeDeps: {
    include: [
      'feather-icons',
      'showdown',
      'engine.io-client',
      'prosemirror-state',
    ],
  },
})
