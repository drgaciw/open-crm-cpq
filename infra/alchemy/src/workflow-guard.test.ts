import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const workflow = readFileSync(
  path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    "../../../.github/workflows/preview-sprites.yml",
  ),
  "utf8",
);

test("deploy and destroy stay explicitly disabled", () => {
  const disabled = workflow.match(/^ {4}if: false$/gm);
  assert.equal(disabled?.length, 2);
  assert.equal(/^\s*pull_request_target\s*:/m.test(workflow), false);
  assert.equal(workflow.includes("workflow_dispatch"), true);
});
