import * as Alchemy from "alchemy";
import * as Fly from "@alchemy.run/fly";
import * as Effect from "effect/Effect";

import { PREVIEW_STACK_NAME } from "./src/preview-identity.ts";
import PreviewSprite from "./src/preview-sprite.ts";

/**
 * Composition root for private per-PR Sprites.
 *
 * State is local on purpose: Fly has no state backend, and this package must
 * not create a Cloudflare, AWS, GCP, or Postgres store. CI durability is the
 * per-PR artifact described in README.md. Destroy is `alchemy destroy --stage pr-<number>`
 * and touches only that stage's state file.
 */
export default Alchemy.Stack(
  PREVIEW_STACK_NAME,
  {
    providers: Fly.providers(),
    state: Alchemy.localState(),
  },
  Effect.gen(function* () {
    const preview = yield* PreviewSprite;
    return {
      url: preview.url,
      name: preview.name,
      urlAuth: preview.urlAuth,
    };
  }),
);
