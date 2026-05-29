---
name: project-cleanup
description: Repo hygiene workflow covering Git/worktree cleanup, tech debt scans, dependency audits, PR review, and changelog generation. Canonical source is .claude/skills/project-cleanup/SKILL.md.
type: agent_requested
---

# project-cleanup (Augment rule)

Canonical workflow lives in **`.claude/skills/project-cleanup/SKILL.md`**.
This file is the Augment-facing adapter — keep it short and point Augment
agents at the canonical Claude skill for full detail.

## When to activate

The user mentions any of:

- project cleanup, repo cleanup, cleanup sprint, code health
- Git cleanup, stale worktrees, merged branches
- technical debt, tech debt scan, refactor priority
- dependency audit, unused dependencies, outdated deps, CVE scan, license audit
- PR review, diff review, blast radius, breaking change check
- changelog, release notes, Conventional Commits, semver bump

Do not activate for one-shot bug fixes, focused feature work, or incident
response.

## Safety guardrails

- Propose first, then act only with explicit approval for destructive steps.
- Before deleting a branch/worktree: verify merged status, clean working tree, upstream presence.
- Never bundle unrelated cleanup categories into one PR — one concern, one PR.
- Security CVEs are patched before non-critical work.
- Avoid `rm -rf`, `git reset --hard`, `git clean -fd`, `git push --force` on shared branches, and `git worktree remove --force` on dirty trees.

## Phased workflow

1. **Baseline** — read-only inventory: branches, worktrees, deps, debt signals, CI state.
2. **Git & worktrees** — prune merged branches; remove stale, clean worktrees.
3. **Tech debt** — scan TODO/FIXME, complexity, test gaps; score and backlog.
4. **Dependencies** — per ecosystem (JS, Python, Go, Rust, Java): outdated, audit, unused, license.
5. **PR review** — blast radius, security, tests, breaking changes, performance, ticket scope.
6. **Changelog** — Conventional Commits lint, semver bump, Keep-a-Changelog rendering.
7. **Docs** — refresh `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`.

Checkpoint with the user between phases. Each phase ends in a findings batch, not a code dump.

## Prioritization

Score findings on severity, blast radius, business impact, effort (inverted), reversibility — 1–3 each. 12–15 = do now, 8–11 = next sprint, 4–7 = backlog, <4 = drop. CVEs always first.

## Output

A single cleanup report containing: baseline, findings table, cleanup plan, recommended PRs (one concern each), validation evidence, deferred debt.

## Red flags

- Bulk dependency upgrades without reading changelogs
- Deleting `--no-merged` branches without owner confirmation
- Skipping CI on a "small cleanup" PR
- Force-pushing rewritten history to shared branches
- Removing TODOs purely because they look old (check `git blame` and surrounding context first)

## Reference

Full workflow, command catalog (Git, npm/pnpm/yarn, pip/poetry, go, cargo,
maven/gradle, conventional commits), prioritization table, and output template
are in `.claude/skills/project-cleanup/SKILL.md`. Read that file for any
operation not summarized here.
