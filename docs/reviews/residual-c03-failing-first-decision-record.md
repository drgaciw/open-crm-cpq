# Residual C03: decision record for missing failing-first evidence

**Linear:** DRG-922 (residual DoD C03, FU-02 under DRG-652 W0-3)
**Rubric:** DoD v0.4 — C03 (Failing test first, w8)
**Scope:** Read-only evidence record. No status changes, no thread resolution.
**CI verification:** Architect (w1:pB) verified `gh run list --branch` for all six PRs. No failing
run found in retained branch-name history. This is a branch-name search over runs GitHub still
retains; it was not cross-checked by commit SHA.
**Disposition:** All six PRs ruled C03 FAIL by Decision (Jev). drg waiver needed.

## C03 requirement

> The PR body names the failing-first SHA and links either a RED CI run id or a SHA-tied local
> test log. The test must fail before the production code is committed and pass after.
>
> — DoD v0.4, C03 (Failing test first, w8)

Compliant example: PR #1068 (DRG-514), which provides a zero-diff evidence-only branch with a
RED/GREEN CI run table, run IDs, job IDs, and inline failure messages.

## Per-PR evidence

### #713 — DRG-654: Create asset when an order activates

| Item | Value |
| --- | --- |
| PR body claim | "Failing tests landed first (`bc41cb10`), then the implementation." |
| SHA `bc41cb10` in repo | **Not located.** `git log --all bc41cb10` returns "unknown revision." The SHA was force-pushed away before merge. |
| GitHub check runs on `bc41cb10` | **0 check runs.** `gh api commits/bc41cb10/check-runs` → `total_count: 0`. |
| Local RED evidence | The PR body describes `AssetCreationEntitlementNullTest` failing when `setAssetActivation` is left unset (zero assets), and passing after the production code was restored. No log file, timestamp, or terminal output is attached. |
| CI RED run link | None. |
| HEAD branch CI runs | `cursor/drg-654-asset-on-activation-a53d`: 4 runs (3 success, 1 cancelled run `41316844221`). No failing run. |

**Assessment:** The PR body names a failing-first SHA and describes a local RED/GREEN cycle, but the
SHA is not locatable in the repository or on GitHub, and no CI run or attached log corroborates the
claim. The branch's retained CI runs show no failures. This is a **local-only claim without
recoverable evidence**.

Source: [PR #713 body](https://github.com/drgaciw/open-crm-cpq/pull/713)

### #719 — DRG-656: Queryable asset history and supersede chain

| Item | Value |
| --- | --- |
| PR body claim | "Failing tests committed first (`4d2de2da`); they did not compile." |
| SHA `4d2de2da` in repo | **Not located.** `git log --all 4d2de2da` returns "unknown revision." The SHA was force-pushed away. The final branch is a single commit, `21986e93`. |
| GitHub check runs on `4d2de2da` | **0 check runs.** `gh api commits/4d2de2da/check-runs` → `total_count: 0`. |
| Local RED evidence | The PR body says tests "did not compile" at the failing-first SHA. No log or terminal output is attached. |
| CI RED run link | None. |
| HEAD branch CI runs | `cursor/drg-656-asset-history-5ccc`: 0 runs of any workflow. (Note: `cursor/drg-654-asset-on-activation-a53d` is #719's base branch and #713's head — do not cite it for #719.) |

**Assessment:** Same pattern as #713 — a local-only claim naming a non-locatable SHA. The stated
failure mode is a compilation error, not a test assertion. The HEAD branch has zero retained CI
runs. **Local-only claim without recoverable evidence.**

Source: [PR #719 body](https://github.com/drgaciw/open-crm-cpq/pull/719)

### #741 — Amend, terminate, and supersede assets inside document apply

| Item | Value |
| --- | --- |
| PR body claim | None. The PR body does not name a failing-first SHA. |
| RED commits in repo | None located. |
| CI RED run link | None. |
| HEAD branch CI runs | 1 run, success. No failing run. |

**Assessment:** **No failing-first evidence of any kind.** The branch's retained CI run succeeded.

Source: [PR #741 body](https://github.com/drgaciw/open-crm-cpq/pull/741)

### #742 — Open renewal quotes from active assets

| Item | Value |
| --- | --- |
| PR body claim | None. The PR body does not name a failing-first SHA. |
| RED commits in repo | None located. |
| CI RED run link | None. |
| HEAD branch CI runs | `cursor/drg-658-renewal-from-assets-58ab`: 1 run, PR check `41338081186`, success, head `f657b169`. No failing run. |

**Assessment:** **No failing-first evidence of any kind.** The branch's retained CI run succeeded.

Source: [PR #742 body](https://github.com/drgaciw/open-crm-cpq/pull/742)

### #740 — Report upcoming renewals and asset churn

| Item | Value |
| --- | --- |
| PR body claim | None. The PR body does not name a failing-first SHA. |
| RED commits in repo | None located. |
| CI RED run link | None. |
| HEAD branch CI runs | `cursor/drg-659-installed-base-reporting-58ab`: 1 run, PR check `41338076686`, success, head `cda999c6`. No failing run. |

**Assessment:** **No failing-first evidence of any kind.** The branch's retained CI run succeeded.

Source: [PR #740 body](https://github.com/drgaciw/open-crm-cpq/pull/740)

### #1003 — DRG-761: Read-only contracts and assets screens

| Item | Value |
| --- | --- |
| PR body claim | "The new `@frontend/cpq-contracts` library is test-driven: RED commits come before each fix." No specific SHA is named. |
| RED commits in repo | Five commits with "RED —" in the message are present: `118341c9`, `fa0ae776`, `5ed28ce0`, `307de165`, `1180c1b5`. All are reachable via `git log`. |
| GitHub check runs on RED SHAs | **0 check runs on all five.** Each returns `total_count: 0` from `gh api commits/{sha}/check-runs`. |
| Local RED evidence | Commit messages carry "RED —" prefixes describing the expected failure, but no log file or terminal output is attached. |
| CI RED run link | None. |

| HEAD branch CI runs | 1 run, success, head `f8daac53`. No failing run. |

**Assessment:** RED-intent commits exist in the repository and their messages describe expected
failures, but none triggered a CI run, the PR body does not name a specific failing-first SHA, and
no local log is attached. The branch's retained CI run succeeded on a later head. This is **partial
evidence: commits exist, CI confirmation absent**.

Source: [PR #1003 body](https://github.com/drgaciw/open-crm-cpq/pull/1003)

### #958 — ADR-037 asset installed base (Proposed)

Not in scope for C03. This PR is a docs-only ADR with no production code or test changes.

## Evidence categories

| Category | PRs | Description |
| --- | --- | --- |
| Local-only claim, SHA not locatable | #713, #719 | PR body names a failing-first SHA and describes a local RED cycle, but the SHA was force-pushed away and has 0 GitHub check runs. The claim cannot be independently verified. |
| No evidence | #741, #742, #740 | PR body does not name a failing-first SHA. No RED commits located. |
| Partial (commits exist, no CI) | #1003 | RED-prefixed commits exist in the repo, but 0 check runs on any of them and no SHA named in the PR body. |
| Not applicable | #958 | Docs-only ADR. |

## Irrecoverability

The force-pushed SHAs for #713 and #719 cannot be recreated. The code has been merged, squashed, and
the intermediate branch history is gone. For #741, #742, and #740, no failing-first commit was ever
recorded. For #1003, the RED commits exist but were never pushed to a CI-triggering ref.

Architect verification (`gh run list --branch`) confirms no failing run exists in GitHub's retained
run history for any of the six PRs. This does not prove a failing run never existed (GitHub may have
expired it, or it may have run on a force-pushed ref), but no evidence of one is recoverable.

C03 evidence for these six PRs cannot be produced retroactively without fabricating history.

## Wave 1 requirement restatement

For future PRs under the DRG-652 workstream (Wave 1 and beyond), C03 requires:

1. The PR body names the failing-first SHA explicitly.
2. That SHA has either a RED CI run id (preferred) or an attached/inline local test log with a
   timestamp and the failure output.
3. The failing-first SHA must remain reachable in the repository (no force-push before merge).
4. PR #1068 (DRG-514) is the reference implementation.

## Disposition

Decision (Jev) per-PR rulings, relayed by architect (w1:pB). Basis: retained branch-name history
via `gh run list --branch`. Not a commit-SHA cross-check. Label: "no failing run found in retained
branch-name history."

| PR | Branch | Retained runs | Ruling |
| --- | --- | --- | --- |
| #713 (DRG-654) | `cursor/drg-654-asset-on-activation-a53d` | 4 PR check runs (heads `086daa3f`, `d308d5f8`, `344625f5`, `41069371`); 3 success, 1 cancelled (`41316844221`). No failing run. | **C03 FAIL** |
| #719 (DRG-656) | `cursor/drg-656-asset-history-5ccc` | 0 runs. Feature-branch target; `pr-check` fires only for PRs into `main`. | **C03 FAIL** |
| #741 (DRG-655) | `cursor/drg-655-asset-amend-supersede-58ab` | 1 run `41338079411`, success. | **C03 FAIL** |
| #742 (DRG-658) | `cursor/drg-658-renewal-from-assets-58ab` | 1 run `41338081186`, success on `f657b169`. | **C03 FAIL** |
| #740 (DRG-659) | `cursor/drg-659-installed-base-reporting-58ab` | 1 run `41338076686`, success on `cda999c6`. | **C03 FAIL** |
| #1003 (DRG-761) | `drgamtd/drg-761-assets-contracts-ui` | 1 run `43721274542` on final head `f8daac53`, success. 5 RED-titled commits in `git log`, none with a CI run. | **C03 FAIL (partial)** — closest to waiver if drg accepts commit-title evidence |

**Resolution:** drg waiver needed for all six.

**Rubric question for drg:** the PR gate (`pr-check`) fires only once a PR into `main` exists, so
C03 cannot be met for the first commit of any PR — the failing-first SHA predates the PR creation
event that triggers CI.

## References

- DoD v0.4: Notion `408f7cb4-e4df-81b1-8b7a-dfee3d5b8e82`
- DRG-652 W0-3 evidence: Notion `422f7cb4-e4df-81e4-b286-d7e4cfa5ce0f`
- PR #1068 (DRG-514): compliant C03 evidence-only example
- Decision filing ruling: 7:03 PM CT Nov 23 2026
- DRG-922 (this record's Linear issue)
