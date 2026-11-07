import { sanitizeFlyAppName } from "@alchemy.run/fly/Metadata";

/**
 * Stable stack name. Stage `pr-<number>` is the per-PR partition under it.
 * Alchemy keeps a separate state directory per stack and stage.
 */
export const PREVIEW_STACK_NAME = "opencrm-cpq-preview" as const;

/**
 * Logical id of the only resource in the stack. Stable across deploys of one
 * stage. The physical Fly name is {@link previewSpriteName}, not this id.
 */
export const PREVIEW_LOGICAL_ID = "PreviewSprite" as const;

/**
 * Repo token baked into the Fly Sprite name (`cpq-pr-<number>`).
 * Fly Sprite names are passed through `sanitizeFlyAppName`, which keeps
 * `[a-z0-9-]`, forces a leading letter, and clips to 30 characters
 * (`@alchemy.run/fly` Metadata.ts). Changing this token replaces every Sprite.
 */
export const PREVIEW_REPO_TOKEN = "cpq" as const;

/**
 * Alchemy's Sprite default is `"public"`. This stack never uses that default.
 * Fly's own API default is `"sprite"` (Sprites auth required).
 */
export const PREVIEW_URL_AUTH = "sprite" as const;

/** Fixed readable CSRF cookie so the Angular client can attach `X-XSRF-TOKEN`. Not a session. */
export const PREVIEW_CSRF_COOKIE = "XSRF-TOKEN=preview-stub; Path=/; SameSite=Lax";

const STAGE_PATTERN = /^pr-([1-9][0-9]*)$/;
const FLY_NAME_MAX = 30;

export interface PreviewIdentity {
  readonly pr: string;
  readonly stage: string;
  readonly spriteName: string;
}

/**
 * Accept only `pr-<positive integer>` with no leading zeros.
 * The resulting Sprite name must survive `sanitizeFlyAppName` unchanged,
 * so Alchemy cannot clip or rewrite it into a different Fly object.
 */
export function parsePreviewStage(stage: string): PreviewIdentity | undefined {
  const match = STAGE_PATTERN.exec(stage);
  const pr = match?.[1];
  if (pr === undefined) return undefined;
  const spriteName = `${PREVIEW_REPO_TOKEN}-pr-${pr}`;
  if (spriteName.length > FLY_NAME_MAX) return undefined;
  if (sanitizeFlyAppName(spriteName) !== spriteName) return undefined;
  return { pr, stage: `pr-${pr}`, spriteName };
}

export function requirePreviewStage(stage: string): PreviewIdentity {
  const parsed = parsePreviewStage(stage);
  if (parsed === undefined) {
    throw new Error(
      `Preview stack only accepts stage pr-<number> (Fly name ${PREVIEW_REPO_TOKEN}-pr-<number>, max ${FLY_NAME_MAX} chars). Received ${JSON.stringify(stage)}.`,
    );
  }
  return parsed;
}
