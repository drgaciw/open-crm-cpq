---
name: project-cleanup
description: "Composite repo hygiene workflow covering Git/worktree cleanup, technical debt scan, dependency audit, PR/diff review, and changelog/release-note generation. Use when the user mentions project cleanup, repo cleanup, Git cleanup, stale worktrees, merged branches, technical debt, dependency audit, unused dependencies, PR review, changelog, release notes, Conventional Commits, cleanup sprint, or code health. Optimized for cross-language repos (JS/TS, Python, Go, Rust, Java). Produces a phased cleanup plan, a prioritized findings table, recommended PRs scoped one-concern-per-PR, and validation evidence. Never deletes branches, worktrees, files, or dependencies without verifying clean state, upstream, merge status, and explicit user approval for destructive actions."
---

# Project Cleanup

A composite skill that merges five focused engineering skills (git-worktree-manager, tech-debt-tracker, dependency-auditor, pr-review-expert, changelog-generator) into a single repo-hygiene workflow. Use it when a repo needs a structured cleanup sprint — not just style polish, but real Git, debt, dependency, review, and release-history hygiene.

## 1. Activation Triggers & When Not to Use

**Use when the user says (or implies):**

- "Clean up this repo", "project cleanup", "repo hygiene", "cleanup sprint", "code health"
- "Prune stale branches", "delete merged branches", "stale worktrees", "Git cleanup"
- "Tech debt scan", "debt inventory", "refactor priority", "legacy code"
- "Dependency audit", "outdated deps", "unused dependencies", "CVE scan", "license audit"
- "Review this PR", "diff review", "blast radius", "breaking change check"
- "Generate changelog", "release notes", "Conventional Commits lint", "semver bump"

**Do NOT use for:**

- A single-file bug fix or a focused feature task (use targeted tools instead)
- Production incident response (use incident playbooks, not cleanup)
- Pure documentation edits with no Git/dep/PR scope
- One-off `rm` of a known-bad file — just do it directly

## 2. Safety Guardrails (Read First)

Cleanup is destructive by nature. The default posture is **propose, then confirm**.

| Action | Required precondition |
| --- | --- |
| Delete local branch | Branch is merged into default branch, has no unpushed commits, and is not the current HEAD |
| Delete remote branch | Above + explicit user approval; never auto-delete protected or release branches |
| Remove a worktree | `git status` is clean inside it AND branch is merged or user approved discard |
| Drop a dependency | Verified unused via static + runtime check; lockfile updated; tests pass |
| Upgrade a major version | Read changelog, identify breaking changes, plan migration, run tests |
| Rewrite history (`rebase`, `filter-branch`) | Branch is not shared, or all collaborators approved |
| Run `rm -rf`, `git reset --hard`, `git clean -fd` | Stop. Ask. These are last-resort, not cleanup primitives |

**Red flags to refuse or escalate:**

- `rm -rf <anything>` without a dry-run first
- `git push --force` to a shared or protected branch
- Deleting unmerged branches "to tidy up"
- Bulk `npm update` / `pip install --upgrade` without reading changelogs
- Mixing unrelated cleanup categories (Git + deps + debt) in one PR
- Skipping CI because "it's just cleanup"
- Removing a `.env*`, lockfile, or migration file without explicit confirmation

## 3. Intake & Scope

Before touching anything, build a baseline. Spend 10–15 minutes, not hours.

1. **Repo type & ecosystem:** detect manifests
   - `package.json` / `pnpm-lock.yaml` / `yarn.lock` → JS/TS (npm, pnpm, yarn)
   - `pyproject.toml` / `requirements*.txt` / `poetry.lock` / `Pipfile.lock` → Python
   - `go.mod` / `go.sum` → Go
   - `Cargo.toml` / `Cargo.lock` → Rust
   - `pom.xml` / `build.gradle*` → Java/JVM
   - `Gemfile.lock`, `composer.lock`, `*.csproj` → Ruby / PHP / .NET
2. **Default branch:** `git symbolic-ref refs/remotes/origin/HEAD | sed 's@^refs/remotes/origin/@@'`
3. **Active branches & worktrees:** `git branch -vv`, `git worktree list`
4. **CI / release conventions:** look for `.github/workflows/`, `Jenkinsfile`, `.gitlab-ci.yml`, `CHANGELOG.md`, release tags
5. **Convention files:** read `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md`, `CODEOWNERS` for repo-specific rules

Record what you found in the cleanup plan (see §7). If anything is ambiguous, ask before phase 2.

## 4. Cleanup Phases

Phases run in order. Each phase ends with a checkpoint — present findings, get approval, then continue. Do **not** open one mega-PR; split per phase or per concern.

### Phase A — Baseline Inventory (read-only)

- `git log --oneline -20`, `git status`, `git remote -v`
- List branches grouped by merge status (merged / unmerged / no upstream)
- List worktrees with age and dirty state
- Capture `git diff --stat origin/<default>...HEAD` for in-flight work
- Snapshot dependency count per ecosystem
- Snapshot TODO / FIXME / XXX / HACK count per language

**Output:** baseline table written to the cleanup plan. No file changes.

### Phase B — Git & Worktree Cleanup

- Identify branches merged into the default branch: `git branch --merged origin/<default>`
- Identify branches with no upstream: `git branch -vv | grep -v '\[origin/'`
- Identify worktrees that are stale (no commits in N days) or dirty
- Propose deletions; ask for approval; then act
- After deletions: `git remote prune origin`, `git gc --auto` (optional)

**Never:**
- Delete a branch that isn't merged without explicit per-branch approval
- `git worktree remove --force` on a dirty tree
- Touch `main` / `master` / `develop` / `release/*` deletion

### Phase C — Technical Debt Scan & Prioritization

- Inventory debt signals:
  - TODO/FIXME/HACK/XXX comments with file:line and author age (`git blame`)
  - Files >500 LOC, cyclomatic-complex functions, deeply nested code
  - Test gaps (modules with no `*test*` neighbor)
  - Deprecated API usage (language-specific lints)
  - Outdated frameworks / EOL runtimes
- Score each item against the model in §6.
- Group into themes (refactor X, add tests to Y, modernize Z) — one PR per theme.
- Do **not** rewrite code in this phase; produce a backlog, not a megadiff.

### Phase D — Dependency Hygiene

Run the audit appropriate to the ecosystem (see §5). For each finding:

1. **Security (CVE):** highest priority. Patch immediately if a fix exists; otherwise flag for upstream tracking.
2. **License conflict:** flag GPL/AGPL contamination, missing licenses, dual-license traps. Defer policy decisions to the user.
3. **Unused:** confirm with static + grep before removal; some deps are used only at build/runtime.
4. **Outdated:** group by patch / minor / major. Patches and minors are usually safe; majors require migration planning.
5. **Bloat / duplication:** identify multi-package overlap; propose consolidation, do not enforce.

Open one PR per concern: security patches, removals, minor bumps, major bumps. Never bundle.

### Phase E — PR / Diff Review

If the cleanup includes open PRs or in-flight branches, review them with the structured checklist:

- **Blast radius:** who imports the changed files; cross-service impact
- **Security:** SQL injection, XSS, secrets, auth bypass, weak hashes, eval/exec, path traversal
- **Tests:** new lines vs new test lines; deleted tests without deleted code; coverage delta
- **Breaking changes:** API contracts, DB migrations (NOT NULL, DROP), env var add/remove
- **Performance:** N+1 queries, unbounded loops, heavy new deps, missing `await`
- **Ticket linking:** PR references a tracked ticket and stays in scope

Categorize findings as **MUST FIX (blocking)**, **SHOULD FIX**, **SUGGESTION**, **LOOKS GOOD**. Acknowledge good patterns explicitly.

### Phase F — Changelog & Release History

- Validate commit history follows Conventional Commits (`feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`, `security`, `deprecated`, `remove`)
- Detect breaking markers (`!:` suffix, `BREAKING CHANGE:` footer)
- Derive semver bump: breaking → major, feat → minor, else → patch
- Generate `CHANGELOG.md` entries under Keep-a-Changelog sections (Added / Changed / Fixed / Removed / Security / Deprecated)
- Prepend (never overwrite) historical sections; keep an `[Unreleased]` block for manual curation
- Each bullet must be user-meaningful; drop chore/internal noise from user-facing notes

### Phase G — Documentation & Onboarding

- Update `README.md` if commands / structure changed
- Update `CONTRIBUTING.md` if branching / commit conventions changed
- Update `CHANGELOG.md` per Phase F
- Note removed/renamed files in PR descriptions
- Refresh any onboarding doc that referenced deleted modules or branches

## 5. Command Checklists

Use the relevant block for the detected ecosystem. All commands are read-only unless noted.

### Git status, branches, worktrees

```bash
git status
git fetch --all --prune
git branch -vv
git branch --merged origin/$(git symbolic-ref --short refs/remotes/origin/HEAD | sed 's@origin/@@')
git branch --no-merged origin/$(git symbolic-ref --short refs/remotes/origin/HEAD | sed 's@origin/@@')
git for-each-ref --sort=-committerdate refs/heads/ \
  --format='%(committerdate:short) %(refname:short) %(upstream:short)'
git worktree list
git remote prune origin --dry-run   # preview only
git log --oneline --decorate -20
```

### JavaScript / TypeScript

```bash
npm outdated
npm audit
npx depcheck                          # unused deps
npx license-checker --summary         # licenses
# pnpm: pnpm outdated / pnpm audit
# yarn: yarn outdated / yarn npm audit
```

### Python

```bash
pip list --outdated
pip-audit                             # CVEs
pip install pipdeptree && pipdeptree --warn fail
deptry .                              # unused deps
# poetry: poetry show --outdated
```

### Go

```bash
go list -u -m all                     # outdated
govulncheck ./...                     # CVEs
go mod tidy -diff                     # unused / missing
go mod why <module>                   # why a dep is present
```

### Rust

```bash
cargo outdated
cargo audit
cargo udeps                           # unused (nightly)
cargo tree --duplicates
```

### Java / JVM

```bash
mvn versions:display-dependency-updates
mvn dependency:analyze                # unused / undeclared
mvn org.owasp:dependency-check-maven:check
# Gradle: ./gradlew dependencyUpdates ; ./gradlew dependencyCheckAnalyze
```

### Conventional Commits & changelog

```bash
git log --pretty=format:'%s' origin/<prev-tag>..HEAD | \
  grep -Ev '^(feat|fix|perf|refactor|docs|test|build|ci|chore|security|deprecated|remove)(\([^)]+\))?!?: '
# Anything that prints is a commit that fails the convention.
```

## 6. Prioritization Model

Score every finding on five axes (1–3 each). Higher total = sooner.

| Axis | 1 (low) | 2 (med) | 3 (high) |
| --- | --- | --- | --- |
| **Severity** | cosmetic / nit | bug or latent risk | CVE, data-loss, security |
| **Blast radius** | one file / one team | one service | shared lib, API, DB schema |
| **Business impact** | internal-only | affects one feature | affects revenue / customers |
| **Effort (inverted)** | days+ (=1) | hours (=2) | minutes (=3) |
| **Reversibility** | irreversible (=1) | reversible w/ work (=2) | trivially reversible (=3) |

**Bands:** 12–15 = do now · 8–11 = next sprint · 4–7 = backlog · <4 = drop or accept.

Always patch security findings first regardless of score.

## 7. Output Format

Produce a single cleanup report with these sections. Keep it tight.

```markdown
# Project Cleanup Report — <repo> @ <commit>

## Baseline
- Default branch: <name>
- Ecosystems: <list>
- Branches: X total / Y merged / Z stale
- Worktrees: N (D dirty)
- Open PRs: N
- Dependency count: <per ecosystem>
- TODO/FIXME count: <n>

## Findings
| ID | Phase | Category | Severity | Blast | Effort | Score | Summary | Evidence |
|----|-------|----------|----------|-------|--------|-------|---------|----------|
| F1 | B     | git      | low      | low   | 3      | 8     | 12 merged branches safe to delete | `git branch --merged` |
| F2 | D     | sec      | high     | high  | 3      | 14    | CVE-2024-XXXX in <pkg> | `npm audit` |

## Cleanup Plan
1. <action> — owner, PR title, risk note
2. ...

## Recommended PRs (one concern each)
- `chore(git): prune merged local branches` — Phase B
- `fix(deps): patch <pkg> for CVE-2024-XXXX` — Phase D
- `docs(changelog): regenerate from Conventional Commits` — Phase F

## Validation Evidence
- Commands run, exit codes, key outputs
- Tests run / skipped (with reason)
- CI status

## Deferred / Accepted Debt
- Items intentionally not addressed, with rationale
```

## 8. Red Flags & Rationalizations to Avoid

- "It's just a small cleanup, I'll skip CI." — No. Cleanup PRs break things too.
- "Let me bundle deps + branch cleanup + the refactor into one PR for efficiency." — No. One concern per PR.
- "`npm update` will sort the deps out." — No. Read changelogs; majors break.
- "This branch hasn't been touched in a year, just delete it." — Check `--no-merged` first; ask owner if found.
- "We can `git push --force` after the history rewrite, it's only main." — Stop. Escalate.
- "I'll `rm -rf node_modules` and reinstall to fix the lockfile drift." — Diagnose drift first; don't paper over.
- "The TODO is from 2019, must be obsolete." — Maybe. Check `git blame` and the surrounding code before removing.
- "Tests aren't part of cleanup." — Tests are part of cleanup. Coverage gaps are debt.

## 9. Definition of Done

A cleanup is done when:

- A cleanup report exists with baseline, findings, plan, and evidence
- Each merged PR addresses exactly one concern and links to the report
- CI is green on every PR (no `--no-verify`, no skipped checks)
- `CHANGELOG.md` reflects the changes in the relevant section
- No branches/worktrees/files were deleted without verification and approval
- Deferred debt is captured in an issue or the report's "Deferred" section

## Source Inspiration

This composite skill merges ideas from:

- https://github.com/alirezarezvani/claude-skills/blob/main/engineering/skills/git-worktree-manager/SKILL.md
- https://github.com/alirezarezvani/claude-skills/blob/main/engineering/skills/tech-debt-tracker/SKILL.md
- https://github.com/alirezarezvani/claude-skills/blob/main/engineering/skills/dependency-auditor/SKILL.md
- https://github.com/alirezarezvani/claude-skills/blob/main/engineering/skills/pr-review-expert/SKILL.md
- https://github.com/alirezarezvani/claude-skills/blob/main/engineering/skills/changelog-generator/SKILL.md
- https://github.com/ComposioHQ/awesome-claude-skills/blob/main/file-organizer/SKILL.md
