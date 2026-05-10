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
