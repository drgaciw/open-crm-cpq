# Private per-PR Fly Sprites

Code-only Alchemy stack. It does not deploy, destroy, or create Fly resources. The GitHub Actions jobs that would do that are `if: false`. Turning them on is a separate owner decision.

Each pull request is one Alchemy stage, `pr-<number>`, and one private Fly Sprite named `cpq-pr-<number>`. The Sprite serves the Angular `crm` build plus read-only API stubs. `urlAuth` is `sprite`. It is never `public`.

## Layout

| Path | Role |
| --- | --- |
| `alchemy.run.ts` | Composition root. Stack `opencrm-cpq-preview`, `Fly.providers()`, `Alchemy.localState()`. |
| `src/preview-sprite.ts` | `Fly.Sprite` class `PreviewSprite`. Bundled program and stable physical name. |
| `src/stub-server.ts` | Request router for stubs and static paths. No Effect, no Fly. |
| `src/ui-assets.ts` | Packs the Angular dist into the Sprite bundle as `ui/**`. Refuses symlinks. |
| `src/preview-identity.ts` | Stage and name rules. |
| `scripts/fetch-pr-state.sh` | Destroy-time download of one PR's state artifact. Fails if it is missing. |
| `.github/workflows/preview-sprites.yml` | Disabled deploy and destroy jobs. |

Run Alchemy from this directory so it discovers `alchemy.run.ts` here, not a stack at the repo root:

```sh
npx alchemy deploy --config alchemy.run.ts --stage pr-42 --yes
npx alchemy destroy --config alchemy.run.ts --stage pr-42 --yes
```

Those commands are not part of the checks below. They need `FLY_API_TOKEN` and they create or delete a Sprite.

## Identity

| Piece | Value | Why it is stable |
| --- | --- | --- |
| Stack | `opencrm-cpq-preview` | One program. No production stage exists in it. |
| Stage | `pr-<number>` | Alchemy isolates state and physical names by stage. Pattern is `[a-z0-9][-_a-z0-9]*`. |
| Logical id | `PreviewSprite` | Same id in every stage. State for PR 42 does not share a record with PR 43. |
| Sprite name | `cpq-pr-<number>` | Explicit `name`. Alchemy would otherwise invent one from stack, stage, and logical id. |

`parsePreviewStage` accepts only `pr-` plus a positive integer with no leading zeros. `prod`, `live_<user>`, `dev_<user>`, and `pr-01` are rejected in the resource props, before Fly is called. A name that `sanitizeFlyAppName` would change is also rejected.

`sanitizeFlyAppName` in `@alchemy.run/fly` lowercases, keeps `[a-z0-9-]`, forces a leading letter, and clips to 30 characters. `cpq-pr-` plus a GitHub PR number fits. Fly cannot rename a Sprite: a different `name` creates a new Sprite and then deletes the old one, which only works when the state file for that stage is present.

The repo token is the fixed prefix `cpq`, not the GitHub repository slug. Renaming the GitHub repo does not rename Sprites.

## What the Sprite serves

Alchemy bundles `src/preview-sprite.ts` with Rolldown and writes the bundle to `/home/sprite/alchemy/` on the Sprite (see `APP_DIR` in `@alchemy.run/fly` `Sprite.ts`). There is no Docker image. The UI is not a `Fly.Website` and not a Fly App.

`uiAssetsPlugin` runs on the deploy host. It reads `OPENCRM_UI_DIST` or, by default, `frontend/dist/apps/crm`, and emits every file under `ui/`. The Angular application builder writes `browser/index.html`; the packer uses that directory when it exists. Symlinks are refused.

At request time the Sprite:

- `GET /api/v1/csrf` returns 204 and `XSRF-TOKEN=preview-stub` (not `HttpOnly`, so the Angular cookie reader can see it). The value is a fixed stub, not a session.
- `GET /api/v1/<collection>` for a fixed set of collections returns an empty `ApiResponse` page. `GET /api/v1/leads/stats` returns zero counts.
- Other API reads return 404. Writes return 405 `PREVIEW_STUB_READ_ONLY`. Nothing is stored.
- `/oauth2/**` and `/login/oauth2/**` return a fixed HTML page. The page does not echo the request path.
- Other paths serve packed files, or `ui/index.html` for extensionless SPA routes. `..`, encoded `..`, and NUL are 400.

The disabled deploy job builds `crm` with `npx nx build crm --configuration=production` and sets `OPENCRM_UI_DIST` to `frontend/dist/apps/crm`.

## Security model

**URL auth.** `Fly.Sprite` defaults `urlAuth` to `"public"` so a fetch handler is reachable without a token. Fly's API default is `"sprite"`. This class sets `urlAuth: PREVIEW_URL_AUTH`, and that constant is `"sprite"`. The URL `https://{name}-….sprites.app` requires Sprites auth. Alchemy mints the Sprites bearer from `FLY_API_TOKEN`. There is no second token in the repo.

**No secrets in the program.** The Sprite yields `Config.String("ALCHEMY_STAGE")` only. `effect@4.1.2` exports `Config.String` and `Config.Redacted` (the Sprite doc sample). It does not export a lowercase `string` helper. Do not pass `env` on the Sprite; Alchemy's Sprite docs forbid that and copy yielded `Config` onto the sandbox instead.

**Token handling in Actions.** `FLY_API_TOKEN` appears only as a step `env` on the two disabled jobs. It is not a workflow-level env. The workflow trigger is `pull_request` plus `workflow_dispatch`. It is not `pull_request_target`. Fork PRs are rejected by a shell guard that runs before the token step (`head.repo.full_name` must equal `github.repository`). Destroy checks out `pull_request.base.ref` so the destroy command is the base branch's stack, not the PR head's `alchemy.run.ts`.

Deploy, once enabled, still executes the PR's Angular build and this package. A same-repo PR can also change this workflow file, because `pull_request` uses the workflow from the merge commit. That is why the jobs stay `if: false` until the owner picks a trusted-workflow control (see open questions). `workflow_dispatch` does not bypass `if: false`.

**Input handling.** The packer refuses symlinks and `..` segments. The request router refuses path escape before any file read. Stub HTML does not reflect request input.

**What this stack will not do.** No public Sprite, no production stage, no database, no Fly Postgres or Redis, no real CRM service, no Entra login, no `alchemy deploy` from the checks in this package.

## Durable state and narrow cleanup

Fly has no object-storage state backend (`https://alchemy.run/fly/setup`). The stack uses `Alchemy.localState()`.

Local state is a directory, not a cloud resource. `makeLocalState` resolves `<cwd>/.alchemy/state/<stack>/<stage>/<fqn>.json` (`alchemy` `State/LocalState.ts`). For this stack that is:

```text
infra/alchemy/.alchemy/state/opencrm-cpq-preview/pr-<number>/
```

`.alchemy/` is gitignored. A state file records the resource type, props, outputs (`spriteId`, physical `name`, `url`, `urlAuth`), and lifecycle. Destroy reads that file and calls Fly's delete for the Sprite named there. Alchemy's own local-state comments say a destroy that sees an empty directory plans no changes and leaks the cloud resource. Missing state must fail the job, not run `alchemy destroy`.

Stages do not share state. `alchemy destroy --stage pr-42` deletes records under `pr-42` only. There is no `prod` stage in this stack. The shell guard rejects any stage that is not `pr-<number>`.

GitHub-hosted runners do not keep `.alchemy/` between jobs. The disabled deploy job uploads that stage directory as the artifact `opencrm-alchemy-state-pr-<number>` (30-day retention). The disabled destroy job downloads the newest unexpired artifact of that exact name and writes it back to the stage directory, then runs `alchemy destroy --stage pr-<number>`. The artifact name is the attribution: one PR number, one stage, one Sprite name, one artifact. A missing artifact exits 1 in `scripts/fetch-pr-state.sh` before Alchemy starts.

That artifact is the durable copy of local state. It is not a Fly resource and not an Alchemy remote store. It dies if retention expires or the artifact upload failed after a successful deploy. Those leaks are an owner decision, not something this scaffold papers over by scanning the Fly org.

## Checks that do not call Fly

From `infra/alchemy`, with no `FLY_API_TOKEN` and no `alchemy` CLI invocation:

```sh
npm ci
npm test
npm run typecheck
npm run lint
npm run build
```

`npm run build` runs Rolldown on a fixture UI and asserts `ui/index.html` was emitted. It does not build the Angular app and it does not call Fly.

## Verified imports

Pinned to the packages installed in this directory. Docs: `https://alchemy.run/fly/compute/sprites`, `https://alchemy.run/fly/setup`, `https://alchemy.run/environments/stages`, `https://alchemy.run/state-store`, `https://alchemy.run/project-structure/file-layout`, `https://alchemy.run/environments/ci`.

| Symbol | Import | Where it was checked |
| --- | --- | --- |
| `Alchemy.Stack`, `Alchemy.localState` | `alchemy` | `alchemy@2.0.0-beta.85` `lib/index.d.ts` exports both. `localState` is `lib/State/LocalState.d.ts`. Stack call shape matches the getting-started and Sprite samples. |
| `Fly.Sprite`, `Fly.providers` | `@alchemy.run/fly` | `lib/index.d.ts` re-exports `./Sprite.ts` and `./Providers.ts`. `providers` is a zero-arg function. |
| `urlAuth`, `name`, `main`, `port`, `build` | `Fly.Sprite` props | `lib/Sprite.d.ts` `SpriteProps`. `urlAuth` is `"public" \| "sprite"`, default `"public"`. |
| `sanitizeFlyAppName` | `@alchemy.run/fly/Metadata` | Package export `./*` maps to `lib/Metadata.d.ts`. 30-character rule is in `src/Metadata.ts`. |
| `Effect` | `effect/Effect` | `effect@4.1.2` exports `./*`. |
| `Config` | `effect/Config` | `Config.String` and `Config.Redacted` are functions on `effect@4.1.2`. The constructor yields `Config.String("ALCHEMY_STAGE")`. |
| `FileSystem` | `effect/FileSystem` | Sprite doc: yield in the constructor, not inside `fetch`. The handler closes over the service. |
| `HttpServerResponse` | `effect/http/HttpServerResponse` | Sprite doc sample. `text`, `html`, `jsonUnsafe`, `empty`, `uint8Array`, `setStatus`, `setHeaders` exist on `effect@4.1.2`. |
| `HttpServerRequest` | `effect/http/HttpServerRequest` | Service tag yielded inside `fetch`. `HttpEffect` in `alchemy/Http.d.ts` provides it. |
| `Plugin`, `rolldown` | `rolldown` | `rolldown@1.2.13`, the same version `alchemy` and `@alchemy.run/fly` depend on. `emitFile({ type: "asset", fileName, source })` is `EmittedAsset`. Assets are part of `Bundle.build` output and `Sprite.ts` writes every bundle file under `APP_DIR`. |
| Stage `pr-<number>` | CLI `--stage` / `ALCHEMY_STAGE` | `https://alchemy.run/environments/stages`. Resolution is `--stage`, then `ALCHEMY_STAGE`, then `live_$USER`. This stack rejects the default. |
| State path | `Alchemy.localState()` | `<cwd>/.alchemy/state/<stack>/<stage>/`. Remote stores exist (`Cloudflare.state`, `AWS.state`, `GCP.state`, `postgresState` from `@alchemy.run/sql/PostgresState`) and are not used. |

`Stack.useSync`, shown on the stages page, is not a member of `Stack` in `alchemy@2.0.0-beta.85`. The stage is read with `yield* Alchemy.Stack` inside the props Effect, which `Platform` accepts.

## Open questions for the owner GO

1. **Trusted workflow.** Once `if: false` is removed, a same-repo PR runs this workflow from the merge commit and the deploy step has `FLY_API_TOKEN`. Forks are rejected in-script, and `pull_request_target` is not used. What additional control is required before enablement (GitHub Environment reviewers, a ruleset that only runs workflows from the default branch, or something else)?
2. **State durability.** Is a 30-day Actions artifact acceptable, or should a later change switch `state:` to a remote store? A remote store creates Cloudflare, AWS, GCP, or Postgres resources and is out of scope until that decision. Artifact expiry or a lost upload leaks the Sprite, because destroy will not run against an empty directory.
3. **Browser access.** `urlAuth: "sprite"` means the `sprites.app` URL requires Sprites auth. Alchemy docs do not describe the browser header or cookie. How should a reviewer open the UI without setting `urlAuth` to `public`?
4. **Fly org.** Which org owns the Sprites, and is the org token's scope limited to Sprites? `cpq-pr-<number>` collides with any other repo in that org using the same prefix.
5. **CLI runtime.** Fly setup says Node.js 22+. Getting started says Node.js 24.11.1+. These checks ran on Node 22 and did not invoke the `alchemy` CLI. Which runtime should the enabled job use?
6. **Stub depth.** Stubs are empty read-only collections plus CSRF and a fixed OAuth page. Screens that need a signed-in user or real records will not work. Is that the preview contract, or do specific routes need fixtures before enablement?
7. **PR comment.** The CI guide's `GitHub.Comment` resource is not in this stack. Adding it needs `pull-requests: write` and a GitHub token inside Alchemy. Should enablement include a comment with the Sprite URL?
8. **Angular build cost.** The disabled deploy job runs `npm ci --legacy-peer-deps` and `nx build crm`. Confirm that belongs on this workflow rather than a reused frontend artifact.
