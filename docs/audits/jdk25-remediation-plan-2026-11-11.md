# JDK 25 mismatch remediation plan — open-crm-cpq

**Date:** 2026-11-11, 1:29 PM CST (America/Chicago)
**Audit this plan remediates:** `docs/audits/jdk25-parity-audit-2026-11-11.md` (commit `bef8e27ebe039160a95ae5f6c9761791ab898cd6`)
**Source commit of the audit (`origin/main`):** `61f27e07cf7913d59fe2c82e400558e21e113dce`
**This file’s branch:** `docs/jdk25-audit-2026-11-11` (docs-only; no PR from this plan)

## Snapshot used for sequencing

- **#968** (DRG-670, Node 24): open draft. Merges first. No Java pin changes.
- **#969** (DRG-671, Jenkins controller `2.580.2-lts-jdk25`): open draft, head `3cde312c237a14e653ac3eba6040ad3a70c1c0fc`. Merges **last**, after #968, with a **manual Jenkins smoke test** on the merge checklist. Re-verified on this pass: the #969 diff already updates the Dockerfile `FROM` and comments, `docs/ci-contract.yaml` WAR lines, and the Jenkins README controller-image row. **Do not edit #969 now** — a new push would lapse its score.
- **GitHub draft cap:** 12 open PRs, all drafts, 0 ready (read-only count at 1:29 PM CST). Do **not** open a docs-only remediation PR until the open-draft count is **8 or fewer**.

## Counts

| Class | Count | Items |
|---|---|---|
| Inconsistent versions across components | **1** | 1 |
| Version drift (declared vs latest stable) | **3** | 2, 3, 4 |
| Stale config/doc | **9** | 5–13 |
| **Total** | **13** | |

| Timing | Count | Items |
|---|---|---|
| Do now (new work) | **0** | — |
| Park — already queued in #969 (do not re-edit) | **4** | 1, 2, 3, 13 |
| Park — one docs-only PR, open only when drafts ≤ 8 and after #969 | **9** | 4–12 |
| Tracker follow-ups (not in the 13; Linear / Notion) | **2** | DRG-671 description; Notion CI-J25 “from 2.492.3” |

---

## Per-mismatch actions

### 1. `infra/jenkins/jenkins/Dockerfile:4` — controller `FROM jenkins/jenkins:2.580.2-lts-jdk21`

- **Class:** inconsistent versions across components (controller image JDK 21 vs pom / GHA / service images / Jenkins Maven already on 25). The `2.580.2` LTS *number* is already current; the suffix is the split.
- **Recommended action:** Leave this to **#969**, which already pins `FROM jenkins/jenkins:2.580.2-lts-jdk25` and keeps the separate Temurin 25 Maven `JAVA_HOME`. After #968 merges, run the manual Jenkins smoke test, then merge #969. Do not start a second Dockerfile PR.
- **Effort:** already implemented in #969. Remaining work is the smoke test and merge, not a new patch.
- **Risk:** medium. A earlier #969 head had a JCasC compose boot failure (`GitHubBranchSourceContext.traits()` → `ConfigurationAsCodeBootFailure`). The smoke test is required for that reason. `pr-check` does not build this image.
- **Now or park:** **Park** — queued in #969. Do not edit #969 now.

### 2. `infra/jenkins/jenkins/Dockerfile:3,34` — comments still cite `2.492.3` / “image JDK 21”

- **Class:** version drift (declared vs latest stable). Comments still name `2.492.3` after #896 already moved the live tag to `2.580.2-lts-jdk21`.
- **Recommended action:** Leave this to **#969**, which already rewrites line 3 to “Jenkins WAR on image JDK 25 (LTS ≥2.541.1 supports Java 25; pinned 2.580.2-lts-jdk25)” and generalizes line 34 to “the image JDK” (no 21, no 2.492.3). Do not duplicate the comment fix in a docs PR.
- **Effort:** none additional (already in #969).
- **Risk:** low (comments only). Lands with item 1, so it inherits the controller smoke-test risk.
- **Now or park:** **Park** — already in the #969 diff, even though the audit’s primary #969 vehicles were items 1 and 3. Do not edit #969 now.

### 3. `docs/ci-contract.yaml:10,15,215-216` — `jenkins_war_image: 2.492.3-lts-jdk21`

- **Class:** version drift (declared vs latest stable). Contract still declares `2.492.3-lts-jdk21`; live Dockerfile on main is already `2.580.2-lts-jdk21`.
- **Recommended action:** Leave this to **#969**, which already sets `jenkins_war_image: jenkins/jenkins:2.580.2-lts-jdk25` and rewrites the two narrative lines to match (and Node 24.22.3 from the #968 stack). Do not copy those contract edits into a separate PR that would collide with #969.
- **Effort:** none additional (already in #969).
- **Risk:** low for the YAML text. The new tag is only true after the #969 image change and smoke test succeed.
- **Now or park:** **Park** — queued in #969. Do not edit #969 now.

### 4. `docs/ARCHITECTURE.md:218` — P0, Jenkins WAR still `2.492.3-lts-jdk21`

- **Class:** version drift (declared vs latest stable). Same stale `2.492.3-lts-jdk21` tag as the contract, in the current-state architecture SSOT. **Not** in #968 or #969.
- **Recommended action:** One-line toolchain update in the future docs-only PR, **after** #968 and #969, so a single edit can say: `Toolchain: Temurin **25** (Maven), Node **24.22.3**, Jenkins WAR \`2.580.2-lts-jdk25\`; service runtime image \`eclipse-temurin:25-jre-alpine\`.` Do not land this before #969 — an interim `2.580.2-lts-jdk21` / Node 22.23.4 line would need a second edit.
- **Effort:** small (one line, plus a quick read of nearby Java 25 lines that are already correct).
- **Risk:** low, provided it waits for #969. Editing it now would either stay wrong (still jdk21 / Node 22) or prematurely claim jdk25 / Node 24.
- **Now or park:** **Park** — include in the docs-only PR; open that PR only when drafts ≤ 8 and #969 has merged.

### 5. `docs/CRM_CUTOVER_CHECKLIST.md:113` — Azure DevOps mirror “JDK 21”

- **Class:** stale config/doc.
- **Recommended action:** In the docs-only PR, change “JDK 21” to “JDK 25 (Temurin)” so the Azure DevOps mirror sentence matches GHA `ci.yml`. Leave the rest of the CI sentence (it already points at `.github/workflows/ci.yml`) unchanged.
- **Effort:** small (one phrase).
- **Risk:** low. Docs only; no CI or image change.
- **Now or park:** **Park** — docs-only PR (same gate as item 4).

### 6. `docs/plans/jenkins-ai-ci-plan-summary.md:7` — “toolchain matching GHA (Java 21, Node 22, …)”

- **Class:** stale config/doc.
- **Recommended action:** In the docs-only PR, update to “matching GHA (Java 25, Node 24.22.3, Maven, Python 3.11, Docker for Testcontainers). Jenkins controller image is `2.580.2-lts-jdk25` after #969.” Keep the rest of the plan summary as historical planning text.
- **Effort:** small (one sentence).
- **Risk:** low. Wording only.
- **Now or park:** **Park** — docs-only PR (after #969 so the controller tag in that sentence is true).

### 7. `docs/plans/00-harvest-index.md:25,30` — Java 21 / `JAVA_HOME` 21.0.2 as this repo’s target

- **Class:** stale config/doc. Current harvest constraints still prescribe Java 21; the 2026-09-21 gap plan already recorded this disagreement.
- **Recommended action:** In the docs-only PR, change line 25 to “Boot **4.1.2** / Java **25** stay” (keep the “do not treat GLM-5…” caution) and line 30 to “Java 25 / Boot 4.1.2 … Port algorithms, not Boot 3 modules. `JAVA_HOME` Temurin **25** for Maven.” Do not regenerate the harvest matrices.
- **Effort:** small (two table cells).
- **Risk:** low, if limited to the toolchain cells. Do not treat this as permission to re-open harvest DAG scope.
- **Now or park:** **Park** — docs-only PR.

### 8. `docs/plans/pricing-ai-rules-reuse-gap-analysis.md` — several lines still target Java 21

- **Class:** stale config/doc.
- **Recommended action:** In the docs-only PR, retarget only the *this-repo / harvest target* lines (`:25`, `:30` `JAVA_HOME` 21.0.2, `:142`, `:218`, `:249`, `:448`) so the target is Java 25. Invert the risk note at `:142`: the repo already builds on 25; do not require the harvest to compile on JDK 21. Leave line 65 if it is describing a *source* stack as Java 25.
- **Effort:** small-to-medium (half a dozen lines in one file; needs a careful pass so source vs target are not swapped).
- **Risk:** low-to-medium. A sloppy edit could mis-label the source prototype’s JDK as the target, or the reverse. Review those lines individually.
- **Now or park:** **Park** — docs-only PR.

### 9. `docs/plans/pricing-ai-rules-harvest-backlog-t1.md` and `docs/plans/pricing-ai-rules-capability-matrix-t1.md` — “adapt/target JDK 21”

- **Class:** stale config/doc.
- **Recommended action:** In the docs-only PR, change “Target remains Java 21 (no Java 25 APIs from source)” and “adapt … to JDK **21**” / matrix “adapt JDK 21” rows to: target is Java 25; source-side Java 21 is fine; do not introduce APIs the source cannot express, but this repo builds on 25.
- **Effort:** small (backlog checkbox plus a few matrix cells).
- **Risk:** low, same source-vs-target caution as item 8. Batch with item 8; they are the same harvest stream.
- **Now or park:** **Park** — docs-only PR.

### 10. `docs/plans/trycompai-crm-reuse-gap-analysis.md:48` — “Java 21 / Boot 4.0.6”

- **Class:** stale config/doc.
- **Recommended action:** In the docs-only PR, change the constraint cell to “Java 25 / Boot 4.1.2, PostgreSQL + Flyway”. Do not change the PASS verdict or the “patterns only” note unless a separate review says the gate itself is wrong.
- **Effort:** small (one cell).
- **Risk:** low.
- **Now or park:** **Park** — docs-only PR.

### 11. `docs/plans/price-book-reuse-gap-analysis.md:19` — this repo’s `pricebook-service` described as “Java 21”

- **Class:** stale config/doc.
- **Recommended action:** In the docs-only PR, change only the *this repo* phrase to “Boot 4.1.2 / Java 25 / PostgreSQL / Flyway”. Leave lines 63 and 384 (“Stack: Java 21, Boot 3.2.5…”, “Java **21.0.2**”) as the *source* prototype stack; optionally prefix those with “source stack:” if the sentence is ambiguous, but do not “upgrade” the source pin.
- **Effort:** small (one phrase; optional label on the source lines).
- **Risk:** low, if the source-stack lines are left alone.
- **Now or park:** **Park** — docs-only PR.

### 12. `.claude/settings.local.json:54,57` — leftover Microsoft `jdk-21.0.7.6-hotspot` allowlist

- **Class:** stale config/doc (toolchain / agent allowlist, not a build pin).
- **Recommended action:** In the same docs-only PR, remove those two JDK 21 `JAVA_HOME` allowlist entries, or replace them with a Temurin 25 path so agents are not nudged toward 21. This is not a project compiler pin, but it is still misleading if left in-tree.
- **Effort:** small (two allowlist strings).
- **Risk:** low. Worst case, a local Claude allowlist needs a one-line add of a developer’s real Temurin 25 path. No build or CI effect.
- **Now or park:** **Park** — include in the docs-only PR (same PR is still docs/config wording; no reason to open a second PR).

### 13. `infra/jenkins/README.md` — no controller image / JDK row

- **Class:** stale config/doc (omission of the live pin).
- **Recommended action:** Leave this to **#969**, which already adds `| **Controller image** | \`jenkins/jenkins:2.580.2-lts-jdk25\` (+ separate Temurin 25 for Maven \`JAVA_HOME\`) |`. Do not add a `2.580.2-lts-jdk21` row on main now; it would conflict with #969 and be wrong after that merge.
- **Effort:** none additional (already in #969).
- **Risk:** low (README row). Lands with item 1.
- **Now or park:** **Park** — already in the #969 diff. Do not edit #969 now.

---

## Tracker follow-ups (not scored in the 13)

These were recorded in the 1:08 PM CST box audit and are still stale baseline wording. They are not git fixes and do not add to the GitHub draft cap. Do them after #969 so the “current” sentence is the post-merge tag.

1. **Linear DRG-671 description** — still reads as findings at `bf41c230` (`FROM jenkins/jenkins:2.492.3-lts-jdk21`). Comments already record `2.580.2-lts-jdk21` → `2.580.2-lts-jdk25`. Add one current-baseline sentence and keep the original findings block dated. Do not change Linear status from this plan.
2. **Notion CI-J25 page** — body still says “from 2.492.3-lts-jdk21”. Update the *from* tag to current main / post-#969 `2.580.2-lts-jdk25`, noting the original filing tag. Destination “LTS ≥2.541.1 -jdk25” can stay.

---

## Sequencing

### Fix first (no new PRs, no edits to #969)

1. Leave #969 untouched so its score does not lapse.
2. Merge **#968 first** (Node 24 only).
3. Then, before merging #969, run the **manual Jenkins smoke test** from the merge checklist (controller image start / JCasC boot, not just `pr-check`).
4. Merge **#969 last**. That is the vehicle for mismatches **1 and 3**, and it already also lands **2 and 13**. After that merge, live pins are: pom / GHA / service images Java 25; Jenkins controller `2.580.2-lts-jdk25`; Maven `/opt/java/temurin-25`; contract + Dockerfile comments + Jenkins README in sync.

### Batch into one docs-only PR (open only when drafts ≤ 8)

Nine remaining mismatches, all wording, one PR, after #969:

| Item | File |
|---|---|
| 4 (P0) | `docs/ARCHITECTURE.md:218` |
| 5 | `docs/CRM_CUTOVER_CHECKLIST.md:113` |
| 6 | `docs/plans/jenkins-ai-ci-plan-summary.md:7` |
| 7 | `docs/plans/00-harvest-index.md:25,30` |
| 8 | `docs/plans/pricing-ai-rules-reuse-gap-analysis.md` (target lines only) |
| 9 | `docs/plans/pricing-ai-rules-harvest-backlog-t1.md` and `docs/plans/pricing-ai-rules-capability-matrix-t1.md` |
| 10 | `docs/plans/trycompai-crm-reuse-gap-analysis.md:48` |
| 11 | `docs/plans/price-book-reuse-gap-analysis.md:19` (not source-stack lines 63/384) |
| 12 | `.claude/settings.local.json:54,57` |

This is a single docs/config PR: no Dockerfile, no workflow, no pom, no image tag. Suggested title once it is allowed: `docs: align JDK 25 current-state wording after #969`. Write `ARCHITECTURE.md:218` for the *post-#968 / post-#969* world (Node 24.22.3, WAR `2.580.2-lts-jdk25`).

**Do not open that PR now.** Open-draft count is 12. Wait until it is 8 or fewer. Opening it earlier would add to the cap. The work can be prepared on a docs branch later; this plan branch stays plan-only unless the owner asks to apply the edits here.

Do not include historical `docs/forge/**`, forge-wave `.rhai` prompts, `MODERNIZATION_PLAN.md`, or harvest *source*-stack Java 21 lines in that PR.

### Stay as follow-ups

- Linear DRG-671 “from 2.492.3” description (tracker; after #969).
- Notion CI-J25 “from 2.492.3” body (tracker; after #969).
- Historical forge / modernization / source-stack text — do not “fix” as current SSOT.
- Any further Jenkins controller issue found by the #969 smoke test — new story, not this docs PR.

---

## Out of scope for this plan

- Merging #968 or #969 (owner merges; report first).
- Editing #969, retargeting, or marking it ready.
- Opening any new PR from this branch.
- Changing Linear or Notion in this pass (noted only).

No secrets or tokens are included in this file.
