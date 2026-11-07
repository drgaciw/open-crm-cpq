import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import { defaultUiDist, packUiDist } from "./ui-assets.ts";

test("default dist points at the crm application build", () => {
  const previous = process.env.OPENCRM_UI_DIST;
  delete process.env.OPENCRM_UI_DIST;
  try {
    assert.equal(
      defaultUiDist("file:///work/infra/alchemy/src/ui-assets.ts"),
      "/work/frontend/dist/apps/crm",
    );
  } finally {
    if (previous === undefined) delete process.env.OPENCRM_UI_DIST;
    else process.env.OPENCRM_UI_DIST = previous;
  }
});

test("OPENCRM_UI_DIST overrides the default and is resolved", () => {
  const previous = process.env.OPENCRM_UI_DIST;
  process.env.OPENCRM_UI_DIST = "relative-ui";
  try {
    assert.equal(defaultUiDist("file:///ignored/ui-assets.ts"), path.resolve("relative-ui"));
  } finally {
    if (previous === undefined) delete process.env.OPENCRM_UI_DIST;
    else process.env.OPENCRM_UI_DIST = previous;
  }
});

test("packs a browser/ layout and refuses symlinks", () => {
  const root = mkdtempSync(path.join(tmpdir(), "opencrm-ui-"));
  const browser = path.join(root, "browser");
  mkdirSync(path.join(browser, "assets"), { recursive: true });
  writeFileSync(path.join(browser, "index.html"), "<p>ok</p>");
  writeFileSync(path.join(browser, "assets", "app.js"), "console.log(1)\n");
  const packed = packUiDist(root);
  const names = packed.map((asset) => asset.fileName).sort();
  assert.deepEqual(names, ["ui/assets/app.js", "ui/index.html"]);

  const linked = mkdtempSync(path.join(tmpdir(), "opencrm-ui-link-"));
  writeFileSync(path.join(linked, "index.html"), "<p>ok</p>");
  symlinkSync("/etc/passwd", path.join(linked, "passwd"));
  assert.throws(() => packUiDist(linked), /symlink/);
});
