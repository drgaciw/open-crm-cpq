import assert from "node:assert/strict";
import test from "node:test";

import { routePreview, contentTypeForRelative, type PreviewDecision } from "./stub-server.ts";

const context = { spriteName: "cpq-pr-42", now: "2026-11-07T00:00:00.000Z" };

function route(method: string, url: string): PreviewDecision {
  return routePreview({ method, url }, context);
}

test("csrf is an empty response with a readable cookie", () => {
  const decision = route("GET", "/api/v1/csrf");
  assert.equal(decision._tag, "Empty");
  if (decision._tag !== "Empty") return;
  assert.equal(decision.status, 204);
  assert.match(decision.headers["set-cookie"] ?? "", /XSRF-TOKEN=preview-stub/);
  assert.equal((decision.headers["set-cookie"] ?? "").includes("HttpOnly"), false);
});

test("collection reads are empty ApiResponse pages and writes are refused", () => {
  const list = route("GET", "/api/v1/leads?page=0");
  assert.equal(list._tag, "Json");
  if (list._tag !== "Json") return;
  assert.equal(list.status, 200);
  const body = list.body as { success: boolean; data: unknown[]; pagination: { totalElements: number } };
  assert.equal(body.success, true);
  assert.deepEqual(body.data, []);
  assert.equal(body.pagination.totalElements, 0);

  const write = route("POST", "/api/v1/leads");
  assert.equal(write._tag, "Json");
  if (write._tag !== "Json") return;
  assert.equal(write.status, 405);
  assert.equal((write.body as { errorCode: string }).errorCode, "PREVIEW_STUB_READ_ONLY");

  const detail = route("GET", "/api/v1/organizations/7");
  assert.equal(detail._tag, "Json");
  if (detail._tag !== "Json") return;
  assert.equal(detail.status, 404);
});

test("lead stats are zeros and unknown APIs are not invented", () => {
  const stats = route("GET", "https://sprite.example/api/v1/leads/stats");
  assert.equal(stats._tag, "Json");
  if (stats._tag !== "Json") return;
  assert.equal((stats.body as { data: { NEW: number } }).data.NEW, 0);
  const unknown = route("GET", "/api/v1/not-a-resource");
  assert.equal(unknown._tag, "Json");
  if (unknown._tag !== "Json") return;
  assert.equal(unknown.status, 404);
});

test("oauth is a fixed page and static paths cannot escape", () => {
  const oauth = route("GET", "/oauth2/authorization/entra");
  assert.equal(oauth._tag, "Html");
  if (oauth._tag !== "Html") return;
  assert.equal(oauth.body.includes("entra"), false);

  assert.equal(route("GET", "/")._tag, "Spa");
  assert.equal(route("GET", "/leads/42")._tag, "Spa");
  const file = route("GET", "/assets/app.js");
  assert.equal(file._tag, "File");
  if (file._tag !== "File") return;
  assert.equal(file.relativePath, "assets/app.js");
  assert.equal(route("GET", "/../secret.js")._tag, "Reject");
  assert.equal(route("GET", "/%2e%2e/secret.js")._tag, "Reject");
  assert.equal(route("GET", "/foo/%00.js")._tag, "Reject");
  assert.equal(route("GET", "/api/v1/leads?sort=createdAt,desc")._tag, "Json");
});

test("content types cover the Angular build suffixes", () => {
  assert.match(contentTypeForRelative("index.html"), /text\/html/);
  assert.match(contentTypeForRelative("main.js"), /javascript/);
  assert.match(contentTypeForRelative("styles.css"), /text\/css/);
  assert.equal(contentTypeForRelative("font.woff2"), "font/woff2");
});
