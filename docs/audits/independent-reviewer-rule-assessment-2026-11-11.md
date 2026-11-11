# Independent-reviewer rule: open-crm-cpq compared with cmano-clone

**Status:** Local draft, read-only analysis. Nothing was committed, pushed, branched, commented, or changed in either repo.
**Prepared:** Wed Nov 11, 2026, about 2:30 PM CT (America/Chicago, UTC-6). All times are CT.
**Window:** Oct 12 – Nov 11, 2026, the same 30 days for both repos. That covers 365 merged PRs in `drgaciw/open-crm-cpq` and 79 in `drgaciw/cmano-clone`. Both are well above the 20-merge minimum.
**Access:** read-only `gh` (REST GET plus read-only GraphQL queries) using the GitHub PAT stored for CMANO. It reached both repos, so the Sprite fallback wasn't needed.

> CPQ Decision states that this is **read-only analysis**. It is not a scored decision and not a DoD policy change. (See "CPQ Decision input (attributed)".)

---

## Verdict (one sentence)

In its current form, open-crm-cpq's one-approval rule is a rubber stamp and isn't worth keeping. 99% of approvals land within 5 minutes of the merge with no comment, and the rule let 112 of 175 PRs merge with a red PR-gate check, including #901, which then kept main red for about 67 hours. Replace it with **risk-tiered review**: a required human review only on auth, tenant, security, migration and CI-infra paths, plus **required green status checks** in both repos. Today open-crm-cpq has no required checks at all, and cmano-clone does not require its ReSharper `inspect` gate.

## Key numbers

| | open-crm-cpq | cmano-clone |
|---|---|---|
| Merges, Oct 12 – Nov 11 | 365 (123 code / 154 docs / 88 deps) | 79 (47 code / 24 docs / 8 deps) |
| Approval → merge, median / p90 | **9 s / 21 s** (174 of 175 within 5 min) | n/a (1 approval in 30 days) |
| Approvals with no comment on PRs over 50 lines (C4) | **131 / 141 = 92.9%** | proxy: 27 of 48 PRs over 50 lines had no non-author review artifact (56%) |
| Merged with a PR-gate check red or not finished | 140 (38%); **120 of 175 (69%) after the rule took effect** | 30 (38%), 26 of them because ReSharper `inspect` hadn't finished |
| Red-main escapes traced to a PR | 5 (#676, #692, #741, #773, #901) = 0.68 per 50 merges | 4 (#809, #825, #821, #822) = 2.53 per 50 merges |
| Weighted rubric total (CPQ Decision rubric) | **46 / 100** (36 for the rule-active period alone) | **48 / 100** |

---

## 1. What the rule actually is (verified)

**open-crm-cpq**: ruleset 15678208, "Protect main branch", active on the default branch. Read with `GET /repos/.../rulesets` and `GET /rules/branches/main`.
- `pull_request`: `required_approving_review_count: 1`, `dismiss_stale_reviews_on_push: true`, `require_code_owner_review: false`, `require_last_push_approval: false`, `required_review_thread_resolution: false`, `require_extra_approval_for_unattributed_changes: true`.
- Also: deletion, non_fast_forward, required_linear_history. `bypass_actors: []`.
- **There is no `required_status_checks` rule.** No PR check is required to merge.
- There is no CODEOWNERS file at `CODEOWNERS`, `.github/CODEOWNERS` or `docs/CODEOWNERS` (all return 404).
- Classic branch protection (`/branches/main/protection`) and ruleset history both return 403 to this token. Those two items are **unverified**.

**cmano-clone**: ruleset 17086544, active on the default branch.
- `required_approving_review_count: 0`, stale dismissal off.
- **Required status checks:** `buildkite/cmano-clone`, `Build and test`, `tools/qa-gauntlet pytest` (strict policy off).
- The ReSharper regression gate (`inspect`) is **not** required. There's no CODEOWNERS and no bypass actors.

**When the rule took effect in open-crm-cpq.** The ruleset's `updated_at` is Apr 28, 2026, but 189 merges between Oct 15 and Nov 4 had no approval. The last of them was Nov 4 at 1:11 PM CT. On Oct 23, commit `a0d9524c` was even edited straight onto main through the web UI. Every merge since Nov 6 at 7:50 AM CT has an approval (175 of 175). So the rule has only been **observably enforced since about Nov 6**. I couldn't see why, because the history endpoint returns 403. I use this as a natural experiment: **pre-rule** is Oct 12 – Nov 6 (190 merges) and **rule-active** is Nov 6 – Nov 11 (175 merges).

## 2. Today's claims, checked against PR data

| Claim | Result |
|---|---|
| drgamtd0 approved and merged #968, #972, #969, #973, #971 between 1:34 and 1:51 PM CT | **Verified.** #968 approved 1:34:54, merged 1:35:30 · #972 1:45:52 → 1:46:09 · #969 1:46:35 → 1:46:49 · #973 1:51:25 → 1:51:32 · #971 1:51:46 → 1:51:57 PM CT. Each approval was an empty-body APPROVED review. |
| "About a minute" from approval to merge | **Tighter than claimed: 7 to 36 seconds.** #973 was opened at 1:50:47 PM and merged 45 s later. |
| #969 merged before its manual Jenkins smoke test, with a JCasC boot failure open | **Verified from the PR body.** These boxes are still unchecked: "Manual Jenkins smoke test", "Decision on `docs/ci-contract.yaml` scope" and "Green exact-head pr-check on main base + CPQ Decision". The body says of the JCasC `ConfigurationAsCodeBootFailure`: "Do not merge until a casc/plugin follow-up is filed or the owner accepts that failure." #969's PR checks themselves were green. #968 also merged with the same smoke box unchecked. Caveat: I read the bodies as they are now, not as they were at merge. |
| #971/#972/#973 merged with `Frontend npm audit (PR gate)` red | **Verified for #971 and #972**, which were red at merge. **#973 was not red yet**: the audit, Backend Unit and Frontend checks hadn't finished when it merged, and the audit then failed. #972's body had "Owner decision on 4 HIGH npm findings before undraft/merge" unchecked. |
| Codex (`chatgpt-codex-connector[bot]`) only commented, on #968 and #973 | **Verified.** Both of its summaries say `has_any_findings: false`. Codex left no review on #969, #971 or #972. Across the window it never APPROVED in either repo (92 COMMENTED reviews in open-crm-cpq, 30 in cmano-clone). |
| Drafts #960–#967 are all assigned to drgamtd0 | **Verified**: 8 drafts, all assigned to drgamtd0. Also open: #974 (draft, unassigned) and #975 (ready, assigned to Copilot, an npm audit fix). |
| The owner raised a security finding on #961 | **Verified, with a nuance.** A COMMENTED review at 1:37 PM CT flagged the caller-controlled `tenant` query param and ID-only subject load/mutate. It was posted from the **`drgaciw` account, the same login as the PR author**, not from the required reviewer `drgamtd0`. A remediation commit (`f1ef3e94`) followed at 1:49 PM CT. The PR is still a draft. |
| The rule's configuration as stated | **Verified** (section 1). Classic branch protection is unverified (403). |

## 3. Data and method

- **Source:** read-only GraphQL search `is:pr is:merged merged:>=2026-10-12` for each repo, with reviews, comments, commits, the check rollup on the merged head, and timeline events (ready-for-review, review-dismissed). Main-branch push workflow runs were pulled from Oct 12 on. Revert and hotfix commits were found by searching main's history.
- **Segments:** *docs* means every changed file is docs/ADR/Markdown/text. *deps* means the author is renovate or dependabot. Everything else is *code*.
- **Red at merge:** for each check name, the latest run completed on the PR head at or before the merge time is FAILURE/ERROR/TIMED_OUT/etc. A cancelled run that a newer push superseded is not counted. **Not finished at merge** means the check ran but completed only after the merge.
- **Owner gate:** an unchecked `- [ ]` box in the PR body mentioning owner, smoke, manual, accept, scope, decision, human or G0x. This is a heuristic and is noisy for cmano-clone, where many are template "PlayMode smoke" boxes. I report C1 both with and without it.
- **Substantive finding acted on (C2):** a CHANGES_REQUESTED review (none in either repo), or a non-author review or comment with real content followed by a commit. Codex counts only when it reports findings (a priority badge, inline comments or `has_any_findings:true`). Agent-automation logins (cursor, linear-code, renovate, copilot-swe-agent) are excluded. Comments from the author's own account are reported separately, because the owner sometimes reviews from `drgaciw` (see #961).
- **Escape (C3):** a red run of a main-branch workflow (open-crm-cpq: `Main verify`; cmano-clone: ReSharper / dotnet-ci / gauntlet) whose previous non-cancelled run was green, traced to the merge commit. I also looked for revert and hotfix commits. None were independent of these incidents. cmano-clone's `2f3f90d5` "fix(resharper): revert redundant qualifier churn" is the fix for the #821/#822 incident.
- **Limits:** check results come from the current rollup on the merged head, so re-runs after the merge are not visible. PR bodies are as of now. Commit dates can be rewritten by a rebase. The rule-active period in open-crm-cpq is only 5.5 days, but it has 175 merges.

## 4. Evidence by repo

### open-crm-cpq

| Metric | Pre-rule (Oct 12 – Nov 6, n=190) | Rule-active (Nov 6 – 11, n=175) | Whole window (n=365) |
|---|---|---|---|
| Open → merge, median / p90 | 0.41 h / 67.9 h | **36.4 h** / 61.5 h | 2.66 h / 61.8 h |
| Ready → approval (or merge), median / p90 | 0.04 h / 0.57 h | 1.81 h / 22.1 h | 0.18 h / 15.0 h |
| Approval → merge, median / p90 | n/a | **9 s / 21 s** | 9 s / 21 s |
| Approvers | none | drgamtd0 161, drgaciw 23 (renovate PRs), annagornell-cmd 11 | |
| Mergers | drgaciw 156, cursor 34 | **drgamtd0 132 (75%)**, drgaciw 35, annagornell-cmd 8 | |
| Merged with a check red / not finished | 16 / 4 | **112 / 11** | 128 / 15 |
| PRs with a finding acted on (C2) | 38 (20.0%) | 8 (4.6%); **0 of 38 code PRs** | 46 (12.6%) |
| Escapes | 4: #676, #692, #741, #773 | 1: #901 | 5 |
| CHANGES_REQUESTED reviews | 0 | 0 | 0 |
| Stale-review dismissals | 0 | **16 events on 11 PRs** (#791, #792, #817, #818, #828, #884, #916, #925, #933, #935, #937) | |

- **Approval substance.** drgamtd0 left 175 approvals, and only 2 had any text or inline comment. Of all 196 approvals, 176 were empty and landed within 5 minutes of the merge.
- **Red-main incidents on main (`Main verify`):**
  - #676, merged Oct 17 at 8:02 AM: red for about 4 min.
  - `a0d9524c`, a direct web edit with no PR: red from Oct 23 at 7:44 AM until Nov 1.
  - #692, Nov 1: red from 9:28 AM for about 9 min.
  - #741, Nov 1: red from 12:50 PM for about 21 min.
  - **#773**, merged Nov 3 at 8:41 PM: red until Nov 6 at 7:50 AM (13 failing runs).
  - **#901**, merged Nov 7 at 11:56 AM: red until **Nov 10 at 7:16 AM** (23 failing runs).
- **#901 is the rule-period escape.** drgamtd0 approved it while `Backend Unit (PR gate)` was **already red**. Afterwards **109 more PRs merged onto the red main, all approved, 90 of them with a red PR gate.**
- **The most useful finding today came from outside the required reviewer**: the #961 tenant-isolation blocker, posted from the author account. The rule's reviewer approved 5 PRs in 17 minutes with no comments.

### cmano-clone

| Metric | Whole window (n=79) | Nov 6 – 11 (n=16, **low confidence**) |
|---|---|---|
| Open → merge, median / p90 | 3.60 h / 71.6 h | 57.1 h / 155.8 h |
| Ready → merge, median / p90 | 0.84 h / 18.0 h | 0.58 h / 83.4 h |
| Approvals | 1 (drgaciw) | 1 |
| Mergers | drgaciw 67, cursor 3, unattributed 9 | |
| Merged with ReSharper `inspect` red | 4: #809, #825, #833, #837 | 3 |
| Merged with `inspect` not finished | 26 (including #821, #822, #814, #815, #816) | 5 |
| Merged with required checks red | 0, because the ruleset enforces them | 0 |
| PRs with a finding acted on (proxy C2) | 28 (35.4%), mostly Codex findings followed by a fix commit (e.g. #814, #812, #809, #804, #803, #802, #797) | 1 (6.2%) |
| Escapes | 4: #809, #825, #821, #822 | 3 |

- **Red-main incidents (ReSharper regression gate on main):**
  - **#809**, merged Nov 4 at 6:56 PM: red until Nov 6 at 1:33 PM. It merged with `inspect` already red.
  - **#825**, merged Nov 7 at 9:56 PM: red until **Nov 10 at 2:51 PM**. It merged with `inspect` already red.
  - **#821**, merged Nov 11 at 7:21 AM, then **#822** at 7:52 AM: both merged before `inspect` finished. Main was red from 7:21 AM to 8:06 AM, and the fix landed through #814 and #834.
- **Duplicate fix PRs (cost of having no single owner):**
  - #835, opened 7:38 AM, closed 7:49 AM.
  - #836, opened 7:41 AM, closed 7:49 AM.
  - #839, opened 8:05 AM, closed 8:16 AM, after #814 had already carried the fixes.
  - All three closed unmerged on Nov 11.
- **All four cmano-clone escapes would have been blocked by making ReSharper `inspect` a required check.** Two merged with it red and two before it finished. A reviewer would have caught them only by reading the check, which drgamtd0's 9-second approvals in open-crm-cpq show doesn't reliably happen.

## 5. Assessment

### 5.1 The real value of the rule
- **A forced pause and a single accountable merger.** Every rule-active merge passed through a human login, mostly drgamtd0. That gives a clear audit trail and keeps agents (`cursor` merged 34 PRs pre-rule, 0 after) from merging their own work.
- **Draft-first discipline.** Since Nov 6, work sits in draft until the owner undrafts it. That stops half-finished slices from landing. #961's security blocker was caught while the PR was a draft, so the slice did not merge.
- **Stale-review dismissal works mechanically.** 16 dismissals on 11 PRs forced a re-approval after new pushes.
- **What it doesn't do: review.** There were 0 CHANGES_REQUESTED, 2 of 175 approvals had any comment, the median approval-to-merge time was 9 s, and the code PRs that merged under the rule had 0 findings acted on.

### 5.2 What open-crm-cpq would miss without it
- **The mechanical pieces:** agent self-merge prevention and a named merger. Both can be kept more cheaply (see 5.5).
- **Not much in review quality.** C2 *fell* from 20.0% before the rule to 4.6% under it. The day's one real security finding (#961) came from outside the required reviewer, and Codex never approves.
- **Escapes did not fall in severity.** The rule-period escape (#901) caused the longest red main in the window, about 67 h. The other long one (#773) was pre-rule.

### 5.3 What cmano-clone gains and what it risks by not having the rule
- **Gains:**
  - Speed: ready→merge median 0.84 h.
  - Autonomy: agents and the owner merge without a hand-off, and nothing waits in a reviewer's queue (0 open PRs now, versus 10 in open-crm-cpq, 8 of them drafts assigned to drgamtd0).
  - Codex findings get acted on more often: 35.4% versus 12.6%.
- **Risks, all seen this window:**
  - 4 red-main escapes in 79 merges (2.53 per 50, versus 0.68 per 50 in open-crm-cpq). All four came from the **un-required** ReSharper gate: #809, #825, #821, #822.
  - Red main lasted up to about 65 h (#825).
  - Three duplicate fix PRs (#835, #836, #839) in about 40 minutes.
- **The gap is a missing required check plus coordination, not a missing reviewer.**

### 5.4 Real costs of the rule in open-crm-cpq
- **Latency.** Median open→merge went from **0.41 h before the rule to 36.4 h under it**. The queue now lives in draft state, because the "ready" event is usually the owner's own undraft, so ready→approval looks fast at 1.8 h median. In practice the rule turns into "wait for the owner to batch-undraft".
- **Queue buildup.** 8 drafts (#960–#967) are waiting on drgamtd0, and the DRG-512 slice (#961) is held on a security fix.
- **One person does nearly everything.** drgamtd0 left 161 of 195 approvals and did 132 of 175 merges under the rule, including all 5 today. Codex cleared 0, and annagornell-cmd did 11 approvals and 8 merges. There's no backup reviewer.
- **It encourages merging onto red.** Because the rule requires a review but no checks, 120 of 175 rule-period merges had a red or unfinished PR gate. That includes 90 during #901's red main, #971 and #972 with the npm audit red, and #973 with the gate still running.
- **Owner gates are recorded but not enforced**: smoke-test, scope-accept and owner-decision boxes on #968, #969 and #972 were unchecked at merge.

### 5.5 Verdict and a middle-ground option

**Verdict:** don't keep the blanket one-approval rule as it is. It costs about a day and a half of median latency and puts all approvals on one person, while giving almost no review signal (93% rubber stamps, 0 change requests) and no protection against merging on red. Keep the accountability it provides, but move enforcement to checks and to review that's limited to the risky paths.

**Proposed risk-tiered setup:**

1. **Required green checks, both repos.**
   - open-crm-cpq: add `required_status_checks` for `Backend Unit (PR gate)` and `Frontend (PR gate)`. Add `Frontend npm audit (PR gate)` once the HIGH findings are fixed (#975).
   - cmano-clone: add ReSharper `inspect` to the existing three required checks.
   - Consider `strict` (up to date with base) for code PRs.
2. **Required human review only on risky paths.**
   - Use a CODEOWNERS file covering auth/tenant/security code, gateway and filters, Flyway/DB migrations, `.github/workflows`, `infra/jenkins`, and dependency manifests for major bumps.
   - Set `require_code_owner_review: true` and `require_last_push_approval: true` for those paths.
   - Test whether the general approval count can drop to 0 while code-owner review still applies to owned paths. Confirm this on a test branch before relying on it.
   - Tenant and authorization code like #961 always gets a human review with written findings.
3. **Fast lane:** docs-only PRs and renovate patch/minor bumps auto-merge on green (154 + 88 of 365 merges in the window).
4. **Make owner gates machine-checked.** A small required check fails while the PR body has an unchecked owner gate (smoke, scope accept, Decision). That would have held #968, #969 and #972.
5. **Hold merges while main is red** (merge queue or a "main green" required check), so #901-style pile-ups (109 merges on a red main) stop.
6. **cmano-clone coordination:** have one fix owner per red-main incident, so duplicates like #835/#836/#839 don't recur.
7. **Spread the review load:** name a second code owner per area and let Codex review run on every PR (comment-only is fine).

---

## Rubric (proposed by CPQ Decision)

Quoted as relayed: "The five criteria, each scored 0-5, total 100":

- **C1 Gate adherence at merge, weight 25:** % of merges with all required checks green on the exact merged head/base AND every recorded owner gate (smoke test, scope accept) satisfied before merge. Scale: 100%=5, >=98%=4, >=95%=3, >=90%=2, >=80%=1, <80%=0.
- **C2 Issues caught before merge, weight 25:** % of merged PRs with at least one substantive finding acted on (changes requested, or a non-author comment followed by an addressing commit). Scale: >=30%=5, 20%=4, 12%=3, 6%=2, 2%=1, <2%=0. Read together with C3.
- **C3 Post-merge escapes, weight 20:** reverts, hotfixes, or red-main runs traced to a merged PR within 7 days, per 50 merges. Scale: 0=5, 1=4, 2=3, <=4=2, <=6=1, >6=0.
- **C4 Review substance, weight 15:** % of approvals that are rubber stamps (no comment, within 5 min of the last push or the merge, on PRs over 50 changed lines). Scale: <=5%=5, 10%=4, 20%=3, 35%=2, 50%=1, >50%=0. Also report how often stale-review dismissal fired.
- **C5 Review cost/latency, weight 15:** median time from ready to approval, or to merge where there's no approval. Scale: <=2h=5, 4h=4, 8h=3, 24h=2, 72h=1, >72h=0. Also give p90.

**How I applied it:**
- **C1 required checks:** open-crm-cpq has no formally required checks, so I used every PR-gate check reported on the head (de facto gate). For cmano-clone, the three required checks were never red, so the breaches come from the non-required `inspect` gate and from unchecked owner boxes. The "checks only" column drops the noisy body-checkbox heuristic. Both repos score 0 either way.
- **Exact base:** I did not evaluate whether the head was green against the exact merged base (strict mode is off in both repos).
- **C2/C4 proxies for cmano-clone:** C2 uses non-author comments followed by commits. C4 uses the share of PRs over 50 lines merged with no non-author review artifact at all.
- **C3** counts red-main spans by the PR whose merge started them. #821 and #822 are counted separately, because each merge produced its own failing run.

### Scores: same window (Oct 12 – Nov 11)

| Criterion (weight) | open-crm-cpq value | Score | cmano-clone value | Score |
|---|---|---|---|---|
| C1 Gate adherence (25) | 58.6% (checks only 61.6%) | 0 | 45.6% (checks only 62.0%) | 0 |
| C2 Issues caught (25) | 12.6% (46/365) | 3 | 35.4% (28/79, proxy) | 5 |
| C3 Escapes per 50 merges (20) | 0.68 (5 escapes) | 4 | 2.53 (4 escapes) | 2 |
| C4 Rubber-stamp rate (15) | 92.9% (131/141); 16 stale dismissals on 11 PRs | 0 | 56.2% proxy (27/48); 0 dismissals | 0 |
| C5 Ready → approval/merge (15) | median 0.18 h, p90 15.0 h | 5 | median 0.84 h, p90 18.0 h | 5 |
| **Weighted total** | | **46** | | **48** |

### Scores: open-crm-cpq before and after the rule took effect

| Criterion (weight) | Pre-rule, Oct 12 – Nov 6 (n=190) | Score | Rule-active, Nov 6 – 11 (n=175) | Score |
|---|---|---|---|---|
| C1 (25) | 84.7% (checks only 89.5%) | 1 | 30.3% (checks only 31.4%) | 0 |
| C2 (25) | 20.0% | 4 | 4.6% | 1 |
| C3 (20) | 1.05 (4) | 3 | 0.29 (1, #901, about 67 h red) | 4 |
| C4 (15) | 52.3% proxy | 0 | 93.6% (131/140) | 0 |
| C5 (15) | 0.04 h, p90 0.57 h | 5 | 1.81 h, p90 22.1 h (open→merge median 36.4 h) | 5 |
| **Weighted total** | | **52** | | **36** |

cmano-clone for Nov 6 – 11 alone has n=16, so it's **low confidence**: 25/100 (C1 0, C2 2, C3 0, C4 0, C5 5).

**Segmented (code vs docs), same window:**
- open-crm-cpq code: C1 74.0%, C2 14.6% (n=123). Docs: C1 49.4%, C2 13.0% (n=154).
- cmano-clone code: C1 48.9%, C2 40.4% (n=47). Docs: C1 20.8%, C2 33.3% (n=24).
- Rule-active open-crm-cpq code PRs: C2 **0/38**.

**How to read the rubric:**
- Both repos land in the mid-40s for different reasons. open-crm-cpq loses C1 and C4: it merges on red, and the review is a rubber stamp. cmano-clone loses C1 and C3: its ReSharper gate isn't required, and it had more escapes.
- C5 flatters open-crm-cpq, because "ready" is set by the same owner who approves. Open→merge latency shows the real cost.
- Neither score supports the blanket rule. Both point to required checks plus targeted review.

## CPQ Decision input (attributed)

*Placeholder for the parent agent to fill in.*

- The rubric (C1–C5, weights, scales) and these method rules are **from CPQ Decision, relayed via Projects Manager** on Nov 11, 2026:
  - same date window for both repos, with at least 20 merges each, otherwise low confidence;
  - score cmano-clone's C2/C4 from proxies rather than zero;
  - segment docs-only from code PRs;
  - cite PR numbers for every escape and gate breach;
  - "read-only analysis, not a scored decision and not a DoD policy change".
- CPQ Decision's further commentary: _[to be filled in]_

---

## Appendix: PR numbers for every gate breach and finding (Oct 12 – Nov 11)

### open-crm-cpq (Oct 12 – Nov 11, 365 merges)

- **Red at merge — Backend Unit (PR gate)** (123): #616, #617, #618, #619, #620, #621, #691, #775, #776, #781, #782, #783, #784, #785, #786, #787, #788, #792, #800, #801, #802, #804, #807, #808, #810, #811, #812, #814, #815, #817, #818, #820, #822, #823, #824, #825, #826, #829, #831, #832, #833, #834, #835, #836, #837, #838, #839, #840, #841, #842, #843, #844, #845, #846, #847, #848, #849, #850, #852, #853, #854, #855, #856, #857, #858, #859, #861, #862, #863, #864, #865, #866, #867, #868, #869, #870, #871, #872, #873, #874, #875, #876, #877, #878, #879, #880, #881, #900, #901, #903, #904, #906, #907, #908, #909, #912, #913, #914, #915, #917, #918, #920, #921, #922, #923, #924, #925, #926, #927, #928, #929, #930, #931, #932, #936, #939, #940, #941, #942, #943, #944, #945, #946
- **Red at merge — renovate/artifacts** (5): #931, #933, #936, #943, #944
- **Red at merge — Frontend npm audit (PR gate)** (2): #971, #972
- **Red at merge — Frontend (PR gate)** (2): #935, #937
- **Not finished at merge — Backend Unit (PR gate)** (15): #599, #608, #725, #764, #821, #828, #896, #902, #911, #919, #933, #934, #935, #971, #973
- **Not finished at merge — Frontend (PR gate)** (1): #973
- **Not finished at merge — Frontend npm audit (PR gate)** (1): #973
- **Unchecked owner/decision checkbox in PR body at merge** (16): #606, #607, #609, #611, #619, #620, #623, #624, #626, #657, #658, #801, #825, #968, #969, #972
- **Docs-only merges** (154), **dependency-bot merges** (88), **code merges** (123)
- **C2 PRs with a non-author finding followed by a commit** (46): #597, #598, #600, #601, #609, #622, #623, #624, #625, #626, #627, #634, #636, #637, #639, #640, #641, #642, #655, #661, #662, #663, #671, #677, #685, #690, #709, #743, #744, #745, #746, #748, #752, #764, #774, #775, #776, #777, #791, #792, #798, #902, #904, #957, #958, #959

### cmano-clone (Oct 12 – Nov 11, 79 merges)

- **Red at merge — inspect** (4): #809, #825, #833, #837
- **Not finished at merge — inspect** (26): #716, #722, #723, #724, #725, #726, #729, #730, #732, #733, #734, #769, #779, #784, #785, #786, #787, #790, #791, #804, #806, #807, #815, #816, #821, #822
- **Unchecked owner/decision checkbox in PR body at merge** (19): #727, #728, #764, #768, #770, #778, #782, #796, #802, #810, #814, #815, #816, #821, #822, #825, #831, #832, #837
- **Docs-only merges** (24), **dependency-bot merges** (8), **code merges** (47)
- **C2 PRs with a non-author finding followed by a commit** (28): #710, #718, #719, #720, #722, #723, #724, #725, #726, #729, #730, #732, #733, #734, #764, #781, #786, #787, #791, #797, #801, #802, #803, #804, #806, #809, #812, #814

Escapes: open-crm-cpq #676, #692, #741, #773, #901, plus direct commit `a0d9524c` (no PR). cmano-clone #809, #825, #821, #822.

Working data and scripts (box only): `/workspace/reviewer-rule/` (`*-30d.json`, `rows.json`, `analyze.py`, `score.py`).
