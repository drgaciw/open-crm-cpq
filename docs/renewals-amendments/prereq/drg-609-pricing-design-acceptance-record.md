# DRG-609 pricing design acceptance

**Accepted by the owner on 2026-10-17; takes effect on merge of this PR.**

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. This file authorizes no code, migration, flag, or deploy.*

## Owner statement

On 2026-10-17 at 7:37 AM CT the owner said, as relayed by Projects Manager:

> Yes: S3 (DRG-581) is authorized and I accept the DRG-609 pricing design.

This pass did not open a chat log, email, or Linear comment that contains that sentence. **Unverified** against a stored message. It is recorded as relayed for this pull request.

The owner's merge of this PR is the confirming act.

## Effect

The quoted sentence records an authorization of S3 ([DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581)) and acceptance of the DRG-609 pricing design. Merging this pull request confirms that acceptance. This acceptance authorizes no code, migration, flag, or deploy.

DRG-609 accepted does not unblock S5 / [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) code by itself. S5 code also needs S3 merged and S4 done, meaning DRG-562 / DRG-563 and the V1_9 ledger.

S6, S8, flags, deploys, [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614), and the S7 backfill apply stay held.

[#663](https://github.com/drgaciw/open-crm-cpq/pull/663) did not accept DRG-609, and this record supplies that acceptance. The merged delegation says the separate older DRG-609 proposal is not accepted wholesale: [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) (`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`). [#662](https://github.com/drgaciw/open-crm-cpq/pull/662) left that design on its existing status: [documentation-acceptance-2026-10-17.md](documentation-acceptance-2026-10-17.md) (`docs/renewals-amendments/prereq/documentation-acceptance-2026-10-17.md`). This file edits neither record.

[DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) was **In Review** when read on 2026-10-17. This pull request does not change that status. The ticket stays In Review until the owner merges.

## Documents

Accepted design: [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) (`docs/renewals-amendments/prereq/story-drg-609-pricing-service-continuation-design.md`).

S5 gate: [story-s5-lifecyclechange-readiness.md](story-s5-lifecyclechange-readiness.md) (`docs/renewals-amendments/prereq/story-s5-lifecyclechange-readiness.md`).

Both files were read on `main` at `63e6ec8d` (merge of #663). The design note is not in the #662 or #663 diffs. This pull request does not edit it. Its opening still calls the text a design proposal and says no code starts until the owner accepts the design. This file is that acceptance.

## Accepted design, by the note's section names

The headings below are the note's own. This record adds no section, field, formula, or route.

| Section | What that section states |
|---|---|
| Verification | Drools was not re-executed. The marks are read-only. Claims marked as executed: none. |
| Question | How `pricing-service` can price an Add, Update, or Remove continuation line and return a PolicyDecision id, a policy version, and a trace id. The note designs the capability and does not close the gap. |
| Current state | Five subsections: "Execute prices a list line and returns no decision identity"; "What `executeInternal` does with a line"; "The two classpath rules do not price a change line"; "Audit rows that exist"; "What S5 already requires of a priced line". |
| Options | Option A, Option B, Option C, and Option D. Each option is a proposal. None is implemented. |
| Recommended option | Option B. A separate continuation evaluation returns decision identity. `executeInternal` and the two classpath DRL files stay as they are until an accepted ADR says to change them. The note does not copy the proration or uplift formula. |
| API and contract sketch | A sketch. Not an OpenAPI file and not a controller. The sketched name is `POST /api/v1/pricing/continuation/evaluate`. |
| Audit implications | The operation returns the ids and the inputs digest. The note adds no table. The commercial-decision store remains DRG-612. |
| Migration | No migration ships with the note. |
| Test plan | Cases for a later implementation story. The note says this pass ran no tests. |
| Scope against DoD v0.2 | Scope of the design. Not the score of an implementation pull request. |
| Evidence index | What was read. Nothing in that index was executed for the note. |
| Unverified in this pass | The note's own list. This record does not clear those items. |

Anything touching conversion price or entitlements follows the accepted design only as the note states it. A search of the design note found neither "conversion" nor "entitlement". The note states no conversion-price rule and no entitlement rule. This record adds none.

## S5 gate, as read

The S5 note's opening verdict says S5 is not ready to implement, and that pricing for Add, Update, and Remove lines with a PolicyDecision id, policy version, and trace id waits on DRG-609. Section "3. What S5 can do before DRG-609, and what waits" says DRG-583 stays blocked until that gap is scoped and delivered. Accepting the design is not that delivery.

Section "4. Dependencies on S3 and S4" says S5's written dependency is the S3 contract, not a finished S3 implementation, and that S4 does not block S5 on the requirements "Depends on" line. That section names DRG-562, DRG-563, and the CAM `V1_9` `document_apply_ledger` as S4's own dependencies and migration. **Unverified** as a statement already written in the S5 gate: section 4 does not say S5 code waits on S3 merged and on S4 done. The S5 limit in "Effect" is the boundary of this acceptance. This file does not rewrite the S5 note.

Section "1. Authorization" says ADR-035 does not authorize S5, and that S3 to S8 require their own readiness and authorization gates.

## Holds read outside the design note

- DRG-614, read on Linear on 2026-10-17: title "Ramp R1 rollout readiness: non-prod enablement, Entra login, UAT and reconciliation", status Backlog. No file on `main` at `63e6ec8d` names DRG-614. This acceptance does not release it. The issue body was not read. **Unverified** beyond title and status.
- S7 backfill apply, read in [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) (`docs/renewals-amendments/s7-v1-8-header-backfill.md`): the data backfill is a manual owner step, and running `contract_header_backfill_apply` against a real environment is an owner decision. This acceptance does not run it.
- DRG-581 was Todo and DRG-583 was Todo when read on Linear on 2026-10-17. This pull request does not change those statuses. The quoted S3 authorization is recorded here and still starts no code.
- [#623](https://github.com/drgaciw/open-crm-cpq/pull/623) ("docs: accept three renewals prerequisite ADRs") is merged to `main` (merged 2026-10-16). "Recommended option" and "API and contract sketch" say proration and uplift are Proposed until #623 merges, then Accepted. This record does not copy either formula and does not treat that merge as permission to implement an amount.
- Whether a narrow contract inside the #663 delegation overlaps a sentence of the DRG-609 note was not compared line by line. **Unverified** as a complete non-overlap. The sentence that was read says the DRG-609 proposal is not accepted wholesale.

## Confirming act

The design is accepted when the owner merges this pull request to `main`. Until that merge, the acceptance has not taken effect.
