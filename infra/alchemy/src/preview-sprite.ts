import * as Alchemy from "alchemy";
import * as Fly from "@alchemy.run/fly";
import * as Config from "effect/Config";
import * as Effect from "effect/Effect";
import * as FileSystem from "effect/FileSystem";
import * as Option from "effect/Option";
import { HttpServerRequest } from "effect/http/HttpServerRequest";
import * as HttpServerResponse from "effect/http/HttpServerResponse";
import * as NodePath from "node:path";
import { fileURLToPath } from "node:url";

import {
  parsePreviewStage,
  PREVIEW_LOGICAL_ID,
  PREVIEW_URL_AUTH,
} from "./preview-identity.ts";
import {
  contentTypeForRelative,
  routePreview,
  type PreviewDecision,
} from "./stub-server.ts";
import { uiAssetsPlugin } from "./ui-assets.ts";

const bundleDir = NodePath.dirname(fileURLToPath(import.meta.url));

/**
 * One private Fly Sprite per Alchemy stage `pr-<number>`.
 * `urlAuth` is the constant `sprite`. Alchemy would otherwise publish the URL.
 */
export default class PreviewSprite extends Fly.Sprite<PreviewSprite>()(
  PREVIEW_LOGICAL_ID,
  Effect.gen(function* () {
    const stack = yield* Alchemy.Stack;
    const identity = parsePreviewStage(stack.stage);
    if (identity === undefined) {
      return yield* Effect.die(
        new Error(`Preview stack refused stage ${JSON.stringify(stack.stage)}`),
      );
    }
    return {
      main: import.meta.url,
      name: identity.spriteName,
      urlAuth: PREVIEW_URL_AUTH,
      port: 3000,
      build: {
        input: {
          plugins: [uiAssetsPlugin()],
        },
      },
    };
  }),
  Effect.gen(function* () {
    const fs = yield* FileSystem.FileSystem;
    const stage = yield* Config.String("ALCHEMY_STAGE");
    const identity = parsePreviewStage(stage);
    if (identity === undefined) {
      return yield* Effect.die(new Error(`Preview sprite refused stage ${JSON.stringify(stage)}`));
    }
    const uiRoot = NodePath.resolve(bundleDir, "ui");
    return {
      fetch: Effect.gen(function* () {
        const request = yield* HttpServerRequest;
        const decision = routePreview(
          { method: request.method, url: request.url },
          { spriteName: identity.spriteName, now: new Date().toISOString() },
        );
        return yield* renderDecision(fs, uiRoot, decision);
      }),
    };
  }),
) {}

const STATIC_HEADERS = {
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
  "x-opencrm-preview": "stub",
} as const;

const MISSING_UI_HTML =
  "<!doctype html><html><head><meta charset=\"utf-8\"><title>Preview UI missing</title></head><body><p>The Angular UI bundle was not packed onto this Sprite.</p></body></html>";

function renderDecision(
  fs: FileSystem.FileSystem,
  uiRoot: string,
  decision: PreviewDecision,
): Effect.Effect<HttpServerResponse.HttpServerResponse> {
  switch (decision._tag) {
    case "Empty":
      return Effect.succeed(
        HttpServerResponse.empty({ status: decision.status, headers: decision.headers }),
      );
    case "Json":
      return Effect.succeed(
        HttpServerResponse.jsonUnsafe(decision.body, {
          status: decision.status,
          headers: decision.headers,
        }),
      );
    case "Html":
      return Effect.succeed(
        HttpServerResponse.setHeaders(
          HttpServerResponse.setStatus(HttpServerResponse.html(decision.body), decision.status),
          STATIC_HEADERS,
        ),
      );
    case "File":
      return readPacked(fs, uiRoot, decision.relativePath, false);
    case "Spa":
      return readPacked(fs, uiRoot, "index.html", true);
    case "Reject":
      return Effect.succeed(HttpServerResponse.text("bad path", { status: 400, headers: STATIC_HEADERS }));
    default: {
      const unreachable: never = decision;
      return Effect.die(new Error(`unhandled preview decision ${String(unreachable)}`));
    }
  }
}

function readPacked(
  fs: FileSystem.FileSystem,
  uiRoot: string,
  relativePath: string,
  spaFallback: boolean,
): Effect.Effect<HttpServerResponse.HttpServerResponse> {
  const absolute = resolveUnder(uiRoot, relativePath);
  if (absolute === undefined) {
    return Effect.succeed(HttpServerResponse.text("bad path", { status: 400, headers: STATIC_HEADERS }));
  }
  return Effect.gen(function* () {
    const read = yield* Effect.option(fs.readFile(absolute));
    if (Option.isNone(read)) {
      if (spaFallback) {
        return HttpServerResponse.setHeaders(
          HttpServerResponse.setStatus(HttpServerResponse.html(MISSING_UI_HTML), 503),
          STATIC_HEADERS,
        );
      }
      return HttpServerResponse.text("not found", { status: 404, headers: STATIC_HEADERS });
    }
    return HttpServerResponse.uint8Array(read.value, {
      status: 200,
      contentType: contentTypeForRelative(relativePath),
      headers: STATIC_HEADERS,
    });
  });
}

function resolveUnder(root: string, relativePath: string): string | undefined {
  if (relativePath.includes("\0") || NodePath.isAbsolute(relativePath)) return undefined;
  const absolute = NodePath.resolve(root, relativePath);
  const prefix = root.endsWith(NodePath.sep) ? root : `${root}${NodePath.sep}`;
  if (absolute !== root && !absolute.startsWith(prefix)) return undefined;
  return absolute;
}
