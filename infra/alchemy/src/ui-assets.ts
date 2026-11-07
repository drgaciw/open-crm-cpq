import { lstatSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Plugin, PluginContext } from "rolldown";

export interface PackedAsset {
  readonly fileName: string;
  readonly source: Uint8Array;
}

/**
 * Angular `crm` output from `@angular/build:application`.
 * Override with `OPENCRM_UI_DIST` (a directory path, not a secret).
 * The directory itself or its `browser/` child must contain `index.html`.
 */
export function defaultUiDist(moduleUrl: string = import.meta.url): string {
  const override = process.env.OPENCRM_UI_DIST;
  if (override !== undefined && override.trim() !== "") {
    return path.resolve(override);
  }
  return fileURLToPath(new URL("../../../frontend/dist/apps/crm", moduleUrl));
}

export function resolveUiIndexRoot(distDir: string): string {
  const root = path.resolve(distDir);
  const browser = path.join(root, "browser");
  if (isFile(path.join(browser, "index.html"))) return browser;
  if (isFile(path.join(root, "index.html"))) return root;
  throw new Error(
    `UI dist ${root} has no index.html at the root or in browser/. Build the crm app before deploy.`,
  );
}

/**
 * Read a UI directory into rolldown asset names under `ui/`.
 * Symlinks are refused so a dist tree cannot escape into the Sprite bundle.
 */
export function packUiDist(distDir: string): readonly PackedAsset[] {
  const root = resolveUiIndexRoot(distDir);
  const assets: PackedAsset[] = [];
  walk(root, root, assets);
  if (!assets.some((asset) => asset.fileName === "ui/index.html")) {
    throw new Error(`UI dist ${root} has no index.html`);
  }
  return assets;
}

export function uiAssetsPlugin(distDir: string = defaultUiDist()): Plugin {
  return {
    name: "opencrm-preview-ui-assets",
    buildStart(this: PluginContext) {
      let assets: readonly PackedAsset[];
      try {
        assets = packUiDist(distDir);
      } catch (error) {
        this.error(error instanceof Error ? error : new Error(String(error)));
        return;
      }
      for (const asset of assets) {
        this.emitFile({
          type: "asset",
          fileName: asset.fileName,
          source: asset.source,
        });
      }
    },
  };
}

function isFile(candidate: string): boolean {
  try {
    return statSync(candidate).isFile() && !lstatSync(candidate).isSymbolicLink();
  } catch {
    return false;
  }
}

function walk(root: string, current: string, assets: PackedAsset[]): void {
  for (const entry of readdirSync(current, { withFileTypes: true })) {
    const absolute = path.join(current, entry.name);
    const listed = lstatSync(absolute);
    if (listed.isSymbolicLink()) {
      throw new Error(`Refusing to pack symlink ${absolute}`);
    }
    if (listed.isDirectory()) {
      walk(root, absolute, assets);
      continue;
    }
    if (!listed.isFile()) continue;
    const relative = path.relative(root, absolute);
    const segments = relative.split(path.sep);
    if (segments.some((segment) => segment === "" || segment === "." || segment === "..")) {
      throw new Error(`Refusing to pack path ${relative}`);
    }
    assets.push({
      fileName: ["ui", ...segments].join("/"),
      source: new Uint8Array(readFileSync(absolute)),
    });
  }
}
