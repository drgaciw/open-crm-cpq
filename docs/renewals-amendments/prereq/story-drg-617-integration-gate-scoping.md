# DRG-617 — Integration-test PR gate for migrations/CAM (scoping)

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-11-06
**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.
**Project:** OPEN-CRM-CPQ, Linear [DRG-617](https://linear.app/drgamtd-workspace/issue/DRG-617/integration-test-pr-gate-for-migrationscam-changes-scoping).
**Related:** [DRG-610](https://linear.app/drgamtd-workspace/issue/DRG-610) (Done — quote-service unit pickup / #635), [DRG-615](https://linear.app/drgamtd-workspace/issue/DRG-615) (In Review — intermittent Backend Integration on GHA), EL-21 in [`environment-ladder-spec.md`](environment-ladder-spec.md).

**This note authorizes no CI workflow edit, branch-protection change, required-check change, code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** Accepting this note is an owner governance choice only. Implementing either option below is a **later** change after that choice, and is out of scope for the PR that lands this file.

Marks used below:

| Mark | Meaning |
|---|---|
| `read` | The file named in the same sentence was opened on this checkout. |
| `verified` | Opened and checked against this tree (or a cited prior run record already in-repo). |
| `unverified` | Not opened, not executed, or not re-queried live in this pass. |

---

## Decision requested

Does the owner want:

- **(a)** a required `backend-integration` check on pull requests that touch Flyway migrations or `contract-asset-management` (CAM), or
- **(b)** to keep pull requests unit-only and treat the nightly / manual `ci.yml` `backend-integration` job as the only scheduled integration signal?

Also confirm the fail-fast finding in this note (Maven reactor stop vs GitHub Actions job matrix).

This is the same governance fork already recorded as **EL-21** in the environment ladder: the ladder does not add a gate; it leaves the choice to the owner.

---

## Current state (`verified`)

### What runs on PRs and on `main` pushes

| Workflow | File | Trigger | Integration stage? |
|---|---|---|---|
| PR check | `.github/workflows/pr-check.yml` | `pull_request` → `main` | **No.** One job: `bash scripts/ci/run-core.sh backend-unit` (+ CI-contract drift). |
| Main verify | `.github/workflows/main-verify.yml` | `push` → `main` | **No.** Same `backend-unit` stage only. |
| CI (manual fallback + nightly) | `.github/workflows/ci.yml` | `workflow_dispatch` + `schedule` `17 2 * * *` | **Yes.** Job `backend-integration` runs after `shared-libraries` and `backend-unit`. |

`docs/ci-contract.yaml` (`github_pr_gate`, `github_main_gate`, `github_actions`) matches that split (`read`). ADR-003 sections 7–8 describe the same layout (`read`).

### Where CAM V1_8 evidence runs

- Test class: `backend/services/contract-asset-management/src/test/java/com/opencrm/contract/documents/ContractHeaderBackfillIntegrationTest.java` (`read`). Tagged `@Tag("integration")`, uses Testcontainers PostgreSQL 16, exercises Flyway through V1_7_2 then V1_8 functions.
- Module: `services/contract-asset-management` is the **last** reactor module in `backend/pom.xml` (`read`): listed after `pricebook-service`, `product-service`, `quote-service`, and the other services.
- Invocation path for the only automated full-reactor run that includes it in GHA:  
  `.github/workflows/ci.yml` job `backend-integration` → `bash scripts/ci/run-core.sh backend-integration` → `scripts/ci/backend-integration.sh` → `ci_mvn -B -U verify --no-transfer-progress` (`read`).
- That script does **not** pass `--fail-at-end`, `--fail-never`, `-pl`, or a CAM-only selector (`read` of `scripts/ci/backend-integration.sh` and `scripts/ci/common.sh` `ci_mvn`).

So `ContractHeaderBackfillIntegrationTest` is **not** part of the PR gate or main-verify. It only runs when the full Docker-backed verify reaches the CAM module.

### S7 / readiness link

S7 backfill NO-GO precondition 1 (green integration run on current `main` that includes the V1_8 test) is already framed in the environment ladder: readiness item 2 and FB-1/FB-2/FB-3 (`read` `environment-ladder-spec.md`). A green Backend Unit job does not substitute for that. Nightly run **38995521632** failed in `pricebook-service` (`PriceBookOutboxWiringTest`); the ladder records that a search of the failed-job log for `ContractHeaderBackfillIntegrationTest` returned no hits (`verified` as in-repo citation; live re-fetch of that run is `unverified` in this pass).

Related work:

- **DRG-610** (Done): quote-service unit-test classpath pickup; CI `verify` path was already the unaffected path for those nine outbox ITs.
- **DRG-615** (In Review): intermittent Backend Integration failures on GHA; Done criterion is ten consecutive full-matrix greens. A flaky nightly weakens option (b) as a sole merge signal.

---

## Fail-fast finding

### Question

Does the workflow’s fail-fast stop later modules — specifically, can a `pricebook-service` failure skip CAM so `ContractHeaderBackfillIntegrationTest` never runs?

### Answer: **Yes** (Maven reactor default), not a GHA `strategy.fail-fast` matrix knob

Evidence (`verified` on this tree):

1. **No job matrix on `backend-integration`.** `.github/workflows/ci.yml` defines a single `backend-integration` job with `needs: [shared-libraries, backend-unit]`. There is no `strategy.matrix` and therefore no Actions `fail-fast` toggle among modules (`read`). Sibling jobs (`frontend`, `cpq-beta-python`, `security-audit`) run independently and do not cancel CAM.
2. **One serial Maven reactor.** `scripts/ci/backend-integration.sh` runs `ci_mvn -B -U verify --no-transfer-progress` with no `--fail-at-end` / `-fae` and no `--fail-never` / `-fn` (`read`). Maven’s default reactor behavior is fail-fast: when a module’s lifecycle fails, later modules are **not** built or tested.
3. **Module order puts CAM after pricebook.** `backend/pom.xml` lists `services/pricebook-service` before `services/contract-asset-management` (CAM is last) (`read`). A failing test in pricebook therefore stops the reactor before CAM’s `verify` phase.
4. **Consistent with run 38995521632.** Ladder record: failed test `com.opencrm.pricebook.PriceBookOutboxWiringTest`; no log hits for `ContractHeaderBackfillIntegrationTest` (`read` `environment-ladder-spec.md`). That pattern matches reactor skip, not a separate “CAM job was cancelled by Actions fail-fast.”

**Implication for both options:** a green “integration job” on a sha only proves CAM ran if the reactor reached CAM. Option (a) or (b) that relies on the current full-reactor command still has this skip risk until pricebook (and every earlier module) is green, or until a later implementation PR (out of scope here) adds `--fail-at-end`, a CAM-scoped `-pl`, or a dedicated CAM job.

---

## Option (a) — Required check on PRs touching migrations / CAM

**Shape (conceptual only; not implemented here):** path-filtered or conditional required status that runs `backend-integration` (or a CAM/migration-scoped verify) when the PR touches `**/db/migration/**` and/or `backend/services/contract-asset-management/**`, and makes that status required for merge.

Maps to environment-ladder EL-21 alternative (1).

### Pros

- Catches Flyway / CAM breakage before merge, which is the risk surface for S7 V1_8 and later CAM migrations.
- Aligns the PR gate with readiness item 2 for migration-touching changes, instead of discovering red only on the nightly or at deploy-sha time.
- Shrinks the window where `main` can advance with untested CAM SQL while unit gates stay green.

### Cons

- Needs a later workflow and (usually) branch-protection / required-check change — **explicitly out of scope for this PR.**
- Docker + full (or scoped) Testcontainers verify is slower and more expensive than today’s unit-only PR gate; path filters reduce but do not eliminate that cost.
- Flake surface from DRG-615 becomes a **merge blocker** for every migration/CAM PR until intermittent failures are tamed.
- Path-filter false negatives (shared lib / parent POM / test harness changes that break CAM without touching those paths) still need a deploy-sha or nightly net.
- Does not by itself fix reactor skip: a required full-reactor job can still go red in pricebook and leave CAM unexecuted unless the implementation also changes how verify is invoked.

---

## Option (b) — Nightly / manual gate only (status quo + governance)

**Shape:** keep `pr-check.yml` / `main-verify.yml` unit-only. Treat `ci.yml` `backend-integration` (nightly schedule + `workflow_dispatch`) as the integration signal. Merges that touch migrations/CAM stay allowed on unit green; owners use nightly (and readiness FB-1..FB-3) before deploy or S7 apply.

Maps to EL-21 alternatives (2) and/or (3) depending on whether “nightly green” is a soft watch or a hard pre-merge / pre-deploy rule stated outside branch protection.

### Pros

- No workflow or branch-protection change required to adopt the decision text.
- Avoids turning DRG-615 flake into a standing PR blocker.
- Matches current ADR-003 Option B cutover (Jenkins primary when reachable; GHA nightly fills the integration gap while Jenkins is unreachable).
- Deploy readiness can still demand a green integration run on the **deploy sha** without making every unrelated PR pay Testcontainers cost.

### Cons

- Migration/CAM defects can land on `main` and sit until the next nightly (or a manual dispatch).
- Nightly red does not bisect which merge broke CAM; FB-3 already calls out clearing or waiving a failed nightly before first-batch deploy.
- Reactor fail-fast means a pricebook (or earlier) failure can hide whether CAM/V1_8 still passes — the S7 precondition “includes the V1_8 test” is then `unverified` for that run.
- Relies on humans watching the nightly and on DRG-615 progressing; ignored flake recreates the gap this ticket describes.

---

## Owner Decision ask

Please choose one:

1. **Adopt (a)** — later implementation ticket: path-filtered (or always-on for CAM/migration paths) required `backend-integration` on PRs; branch protection updated in that later change. This scoping PR still lands docs only.
2. **Adopt (b)** — keep PR/main gates unit-only; nightly/manual `ci.yml` remains the integration signal; deploy/S7 readiness continues to require a green integration run that actually executed CAM/V1_8 on the deploy sha (FB-1..FB-3). Optionally state whether “nightly must be green before merge to `main`” is a process rule without a required check.
3. **Adopt (b) now, revisit (a) after DRG-615** — same as (2), with an explicit revisit once ten consecutive Backend Integration runs pass (DRG-615 Done criterion).

Also confirm: **fail-fast answer above is accepted** (Maven reactor yes; GHA matrix fail-fast N/A for modules).

Record the choice by updating this file’s Status line (or a short owner-decision addendum) — do **not** edit `.github/workflows/**` in the same change as this note unless a later, explicitly authorized implementation ticket says so.

---

## NO-GO for this PR

| Change | Status in the PR that lands this note |
|---|---|
| `.github/workflows/**` | **NO-GO** — do not edit |
| Branch protection / required checks | **NO-GO** — do not change |
| `docs/ci-contract.yaml` SSOT wiring for a new PR integration job | **NO-GO** until owner adopts (a) and a follow-up implements it |
| Code, Flyway, feature flags, deploys | **NO-GO** |
| Linear status → Done | **NO-GO** — leave DRG-617 In Progress / Backlog until the owner decides |

Allowed: this scoping markdown under `docs/` only.

---

## Evidence index

| Claim | Source |
|---|---|
| PR gate is unit-only | `.github/workflows/pr-check.yml` (`read`) |
| Main verify is unit-only | `.github/workflows/main-verify.yml` (`read`) |
| Integration job exists only on `ci.yml` | `.github/workflows/ci.yml` job `backend-integration` (`read`) |
| Integration command | `scripts/ci/backend-integration.sh` → `ci_mvn -B -U verify --no-transfer-progress` (`read`) |
| No Maven `--fail-at-end` | same script + `scripts/ci/common.sh` (`read`) |
| CAM last in reactor; pricebook earlier | `backend/pom.xml` modules list (`read`) |
| V1_8 IT location and tag | `ContractHeaderBackfillIntegrationTest.java` (`read`) |
| EL-21 / run 38995521632 / FB-* | `docs/renewals-amendments/prereq/environment-ladder-spec.md` (`read`) |
| ADR-003 PR + nightly roles | `docs/adr/ADR-003-ci-llm-integration.md` §§7–8 (`read`) |
| Related tickets | Linear DRG-610 (Done), DRG-615 (In Review), DRG-617 (this) |
