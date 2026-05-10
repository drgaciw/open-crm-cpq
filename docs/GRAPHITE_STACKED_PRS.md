# Graphite stacked PRs (open-crm-cpq)

This guide describes how we use [Graphite](https://graphite.dev) to open **stacked** pull requests against `main`, keeping reviews small and ordered.

**Trunk branch:** `main` (matches [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)).

---

## Prerequisites (org and local)

Complete this checklist before your first stack.

| Step | Action |
|------|--------|
| 1 | **Graphite account** linked to GitHub (same identity you use for `origin`). |
| 2 | **Graphite GitHub app** installed on the org/repo (org admin); grants PR/stack automation. |
| 3 | **Write access** to `open-crm-cpq`. |
| 4 | **Graphite CLI** (`gt`): `npm install -g @withgraphite/graphite-cli` (or current [install docs](https://graphite.dev/docs/install-the-cli)). Verify: `gt --version`. |
| 5 | **Authenticate:** `gt auth` (browser flow; re-run after token expiry). |
| 6 | **Trunk:** From the repo root, run `gt log` or `gt sync` once; Graphite should detect **`main`** as trunk. If not: follow `gt init` / onboarding prompts to set trunk to `main`. |

**Note:** Permission and app install cannot be automated from this repo; confirm with your org admin if `gt submit` fails with authorization errors.

---

## First-time repo setup

From a clean working tree on `main` (or stash unrelated work):

```bash
git checkout main
git pull origin main
gt sync   # refreshes trunk; follow prompts if first run
```

Create work with Graphite (typical flow):

```bash
gt create my-feature-part1 -m "feat: part 1 description"
# make changes, commit
gt create my-feature-part2 -m "feat: part 2 description"
# make changes, commit
gt submit --no-interactive   # or interactive; opens/updates stacked PRs
```

After the **bottom** PR merges:

```bash
git checkout main
git pull origin main
gt sync
# resolve any restack conflicts; then gt submit again as needed
```

Command names may vary slightly by CLI version—use `gt --help` and the [Graphite CLI reference](https://graphite.dev/docs/category/cli) when in doubt.

---

## Stacking conventions (this monorepo)

### Preferred slice order

1. **Shared contracts first** — `backend/libs/common`, protobuf/gRPC contracts, shared Kafka topic names, DTOs used by multiple services.
2. **Single service boundary** — one `backend/services/*` module, or a tight pair (e.g. `gateway-service` route + one downstream service).
3. **Frontend** — `frontend/**` (NX): libraries that many apps consume first, then feature libs, then app shells.
4. **CPQ beta** — `cpq_beta/**` (Python/Frappe): framework/bootstrap changes before app-specific modules.
5. **Cross-cutting last** — CI-only (`.github/workflows/**`), broad doc edits, or mechanical refactors across unrelated packages (or isolate them in a **separate** stack so domain reviewers are not blocked).

Prefer **vertical slices** (one behavior end-to-end) when they stay reviewable (~400 lines or team norm).

### Branch naming

- `gt` auto-names from commits; override with an explicit name on `gt create <name>`.
- Manual pattern: `<area>/<short-slug>-partN` (e.g. `ai/audit-logger-part2`). Keep names short; Graphite shows stack order in the UI.

### What not to stack together

Avoid unrelated domains in one chain (e.g. `ai-service` agent sweep + `crm-service` API) unless they share one compile-time dependency line.

### CI

GitHub Actions use `concurrency: group: ci-${{ github.ref }}` — each PR ref gets its own CI run, which fits stacked PRs.

### Merge policy

If the org requires **linear history** or a **merge queue**, align with admin policy (squash vs merge commit) before relying on Graphite’s default merge button behavior.

---

## Splitting large batches (example: ai-service)

When many files move together:

1. **Bottom:** Mechanical moves/renames only (no behavior change).
2. **Middle:** Behavior + unit tests for one subsystem.
3. **Top:** Config (`application.yml`, `pom.xml`) and small doc updates.

**Local verify per branch** before `gt submit`:

```bash
cd backend
mvn -B verify --no-transfer-progress -pl services/ai-service -am
```

Adjust `-pl` for other services.

---

## Risks

- **Restack conflicts:** run `gt sync` often; keep PRs small.
- **Wrong base:** if PR B needs types from PR A, B must not target `main` alone—keep the stack in Graphite so the GitHub base is the branch below.
- **Reviewer context:** mention stack position in the PR description when reviewers do not use Graphite’s UI.
