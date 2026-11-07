# DRG-644 — renewal-automation cluster line on DRG-607

**Status:** Documentation record. Docs only. This file authorizes no code, schema change, migration, flag, route, or deploy. No business choice in this file is marked adopted.

**Linear:** [DRG-644](https://linear.app/drgamtd-workspace/issue/DRG-644) (prerequisite under [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607), theme 4).

**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster key:** `renewal-automation`.

**Read against:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (branch cut for this record).

## Acceptance criterion AC1 (checklist line)

The [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) per-cluster checklist gains this line (one line per cluster):

- [ ] renewal-automation: [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555) / [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593) / [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596)

Related stories named by DRG-644: DRG-555, DRG-593, DRG-596. The cluster's later spec story is [DRG-643](https://linear.app/drgamtd-workspace/issue/DRG-643); the landed proposal file is [`specs/renewal-automation.md`](specs/renewal-automation.md) (merged via [#701](https://github.com/drgaciw/open-crm-cpq/pull/701)).

This git record does not tick the checkbox. Ticking remains a Linear Hygiene action after owner review. It does not edit the Linear description from this pull request.

## Provenance (cited, not re-decided)

| When | Where | What was recorded |
|---|---|---|
| 2026-10-29 (Hygiene comment on DRG-607, comment `6dad4c66-d0a2-4889-ab3b-aa877c5fe4eb`) | Linear DRG-607 | Hygiene wrote that it added the renewal-automation cluster line (DRG-555/593/596; spec DRG-643, draft PR #701), citing CPQ Decision ruling 2026-10-27 (`decision-scores-2026-10-27.md`, DRG-644: Linear edit, no PR). |
| 2026-10-29 (DRG-644 comment `f4e12772-9cca-45c5-be0f-54eecea37460`) | Linear DRG-644 | Recorded as done as a Linear edit on DRG-607; no PR or CI at that time, per the 2026-10-27 ruling; story stayed open until #701 PRE-PASS. |
| 2026-11-01 1:15 PM CT (DRG-644 comment `9ec09c45-3ef1-42d1-b466-1ba7f1f4a4ce`) | Linear DRG-644 | CPQ Decision pre-score chose Option A at 90%: no PR and no cloud agent; AC2 ("docs-only PR, CI green") treated as superseded pending drg acceptance of that AC change; #701 already merged. |
| 2026-11-05 (Wave 2 fan-out on the [delegation record](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82)) | Notion delegation page | Wave 2 lane 2 re-queues DRG-644 as a docs-only draft PR (renewal-automation cluster line); draft PRs only; no merges; CPQ Decision gates. |

Linear DRG-607 description, read for this record, already lists the renewal-automation checklist line with DRG-555 / 593 / 596 and points at DRG-643 / #701. This file places the same checklist membership in git so Wave 2 AC2 (docs-only PR) has a mergeable artifact without reopening product forks.

## What this file does not do

- It does not adopt auto-renew defaults, notice numbers, kickoff windows, term-end actions, quote-creation modes, or any other renewal-automation fork.
- The companion edit in [`specs/renewal-automation.md`](specs/renewal-automation.md) adds only a DRG-607 / DRG-644 cross-link paragraph. It does not change acceptance criteria, fixtures, or proposed defaults. This record does not edit the [cluster-d frame](cluster-d-renewal-automation-decision-frame.md) or the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md#renewal-automation).
- It does not mark DRG-607 Done, tick any checklist box, enable a flag, or authorize implementation.
- Where a prior ruling said "Linear edit, no PR," that citation stays above; Wave 2's docs draft is a separate delivery path and does not silently rewrite that ruling into an owner product adoption.

## Link check (local)

| Ref | Path or URL | Present on this branch cut |
|---|---|---|
| Spec | `docs/renewals-amendments/prereq/specs/renewal-automation.md` | Yes |
| Cluster-d frame | `docs/renewals-amendments/prereq/cluster-d-renewal-automation-decision-frame.md` | Yes |
| Delegated renewal-automation contract | `docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md` (anchor `#renewal-automation`) | Yes |
| Linear DRG-555 / 593 / 596 / 607 / 643 / 644 | linear.app issue URLs above | Cited; not edited by this file |
| GitHub #701 | https://github.com/drgaciw/open-crm-cpq/pull/701 | Merged (read 2026-11-05) |
