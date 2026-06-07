import { build } from "vite";
import vue from "@vitejs/plugin-vue";
console.log("starting build");
await build({
  root: ".",
  plugins: [vue()],
  build: { outDir: "../crm/public/frontend", emptyOutDir: true },
  logLevel: "info",
});
console.log("done");
