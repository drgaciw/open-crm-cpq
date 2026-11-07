import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  PREVIEW_CSRF_COOKIE,
  PREVIEW_LOGICAL_ID,
  PREVIEW_STACK_NAME,
  PREVIEW_URL_AUTH,
  parsePreviewStage,
} from "./preview-identity.ts";

const here = path.dirname(fileURLToPath(import.meta.url));

test("names a PR sprite cpq-pr-<number> and rejects other stages", () => {
  assert.deepEqual(parsePreviewStage("pr-42"), {
    pr: "42",
    stage: "pr-42",
    spriteName: "cpq-pr-42",
  });
  assert.equal(parsePreviewStage("pr-1")?.spriteName, "cpq-pr-1");
  for (const stage of ["prod", "live_sam", "dev_sam", "pr-0", "pr-01", "PR-42", "pr-", "main", ""]) {
    assert.equal(parsePreviewStage(stage), undefined, stage);
  }
});

test("rejects a PR number whose Fly name would be clipped", () => {
  const tooLong = `pr-${"9".repeat(30)}`;
  assert.equal(parsePreviewStage(tooLong), undefined);
});

test("url auth stays sprite and the csrf cookie is not HttpOnly", () => {
  assert.equal(PREVIEW_URL_AUTH, "sprite");
  assert.equal(PREVIEW_CSRF_COOKIE.includes("HttpOnly"), false);
  assert.match(PREVIEW_CSRF_COOKIE, /^XSRF-TOKEN=preview-stub;/);
  const sprite = readFileSync(path.join(here, "preview-sprite.ts"), "utf8");
  assert.match(sprite, /urlAuth: PREVIEW_URL_AUTH/);
  assert.equal(sprite.includes('urlAuth: "public"'), false);
  assert.match(sprite, new RegExp(`PREVIEW_LOGICAL_ID`));
  assert.equal(PREVIEW_LOGICAL_ID, "PreviewSprite");
  assert.equal(PREVIEW_STACK_NAME, "opencrm-cpq-preview");
});
