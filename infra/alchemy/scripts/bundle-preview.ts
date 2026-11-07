import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { rolldown } from "rolldown";

import { uiAssetsPlugin } from "../src/ui-assets.ts";

const here = path.dirname(fileURLToPath(import.meta.url));
const fixture = path.resolve(here, "../fixtures/ui");
const outDir = path.resolve(here, "../dist/preview-bundle");

const bundle = await rolldown({
  input: path.resolve(here, "empty-entry.ts"),
  platform: "node",
  plugins: [uiAssetsPlugin(fixture)],
});

await bundle.write({
  dir: outDir,
  format: "esm",
  entryFileNames: "index.mjs",
});

const indexHtml = path.join(outDir, "ui", "index.html");
const script = path.join(outDir, "ui", "assets", "app.js");
if (!existsSync(indexHtml) || !existsSync(script)) {
  throw new Error(`UI assets were not emitted into ${outDir}`);
}

console.log(`packed preview UI fixture into ${outDir}`);
