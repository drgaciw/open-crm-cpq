# OQ5 option A rules: partial application (DRG-565)

**Status:** First-slice rules note for RA-31 / [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565/partial-application-rules-for-mixed-line-amendment-documents). It records option A as the first-slice rule.

**Reading base:** `main` at `fd6421e414dc443d3730b870b0e0c4d4a3be693e`. That commit is a descendant of `a825641bd`. Citations below were read at that commit, or from Linear DRG-565 and the Notion RA-31 page on 2026-10-16. Claims not opened in this pass are marked **unverified**.

This note does not accept `specs/partial-apply.md`. It does not authorize S4 (DRG-582) or S6. It does not add a migration, a flag, a topic, a gateway route, or an apply ledger. It does not edit an ADR, the open-questions register, or `partial-apply-owner-decision.md`.

## Decision condition

1. Create-before-terminate (who writes `dependsOnUnitNos`) is **UNMET**. It waits on S4's apply path (DRG-582).
2. The three test scenarios (restructure, true-up, future-dated lines) are **UNMET**. They wait on S4's apply path (DRG-582).
3. Nothing in this note accepts `specs/partial-apply.md`.

[#626](https://github.com/drgaciw/open-crm-cpq/pull/626) is merged on `main` as the owner-decision frame. GitHub records `mergedAt` `2026-10-16T22:03:29Z`; the merge commit `3b1e78f5` is dated 2026-10-16 17:03:28 -0500 (5:03 PM CT). Section 9 of `partial-apply-owner-decision.md` and the OQ5 register row still do not record the owner's delegated option A decision. This note does not edit them. Both were read on `origin/main` after `fd6421e4`: section 9 is still the blank owner record, and the register row is still `DEFAULT PROPOSED`.

## 1. What is adopted

The owner instruction for this change adopts option A, the proposed default in `partial-apply-owner-decision.md` section 2. On this base, that file's header still says the file does not decide OQ5, and its section 9 owner record is still blank. This note does not edit that file. The register row for OQ5 on this base still says `DEFAULT PROPOSED`. This note does not edit the register.

The adopted rule is the seven points in that section, for the first slice. The slice definition in `amendment-first-slice-requirements-and-acceptance.md` is a mid-term amendment of an ACTIVE SaaS subscription set under one contract, with action codes Add, Update, and Remove. That file places on-prem and capacity lines, ordering, fulfilment, renewal, and price lock outside the slice. The issue's on-prem capacity example, and the fulfilment step of an on-prem to SaaS restructure, sit in that out-of-slice list. The issue's services-fee example is not named in the slice definition; this note does not add a rule for it.

1. **One unit per target subscription.** Lines that share one target subscription are one application unit.
2. **A unit is atomic.** One transaction writes the unit's rate periods, end dates, revision bump, and ledger row, or it writes nothing. Which table holds that ledger row is not chosen in this note (section 4). Units apply in the dependency order already stored on the unit.
3. **No automatic rollback.** A unit that has committed stays committed.
4. **`PARTIALLY_APPLIED`** when at least one unit has committed and a later unit fails. The failed unit is `BLOCKED`. Each committed unit stays `APPLIED`.
5. **`APPLY_BLOCKED` means no unit has committed.** A failure before any unit has committed leaves no unit's commercial rows written. The failed unit is `BLOCKED`. The document is `APPLY_BLOCKED`.
6. **The remedy is a correcting amendment.** A new amendment document carries the fix. Rebase (re-observe, re-price, new revision of the same document) is not a first-slice remedy.
7. **Signature stays all-or-nothing.** The customer signs one document. Partial application is an apply outcome.

A unit that has neither committed nor failed stays `PENDING`. That reading uses the existing unit statuses `PENDING`, `APPLIED`, and `BLOCKED`. It is not a new status.

When every unit has committed, the document is `APPLIED`.

| Committed units when apply stops | Document status | Unit outcome |
|---|---|---|
| None | `APPLY_BLOCKED` | Failed unit `BLOCKED`. No unit is `APPLIED`. |
| One or more, and a later unit has failed | `PARTIALLY_APPLIED` | Committed units stay `APPLIED`. Failed unit is `BLOCKED`. |
| All | `APPLIED` | Every unit is `APPLIED`. |

## 2. Illustrations of the adopted cut

These are written illustrations of section 1. They are not automated tests, and they do not accept `specs/partial-apply.md`.

**Two subscriptions, second unit fails.** An EXECUTED document has one unit for subscription A and one unit for subscription B. Unit A commits. Unit B fails because its observed revision is stale. Unit A stays applied. Unit B is `BLOCKED`. The document is `PARTIALLY_APPLIED`. The fix is a correcting amendment.

**Only unit fails before any commit.** An EXECUTED document has one unit. That unit fails its revision check and writes nothing. The document is `APPLY_BLOCKED`.

**Both units commit.** An EXECUTED document has one unit for subscription A and one unit for subscription B. Both commit. The document is `APPLIED`.

## 3. DRG-565: what the issue asks, and what this note covers

Linear DRG-565, read 2026-10-16, title "Partial application rules for mixed-line amendment documents", priority Medium, labels `renewals-amendments` and `Open-CRM-CPQ`. The issue body has no acceptance-criteria section and no checklist. The Notion RA-31 page has the same body and no separate criteria. The mapping below uses the body text.

The body says mixed-line documents need partial application rules, because lines in one document can target different ledgers and dates. Its examples are a SaaS seat change together with an on-prem capacity raise and a services fee, and an on-prem to SaaS restructure with a dual-entitlement window. It says nothing defines:

- whether a document applies all-or-nothing or unit by unit;
- the order of operations (create before terminate);
- what happens when one unit fails after others have committed;
- `PARTIALLY_APPLIED` or `APPLY_BLOCKED` states.

It records the spike proposal: signature is all-or-nothing; apply is per atomic unit, one target each, in dependency order; each target checks its own observed revision; committed units are never silently rolled back; fixes come from a rebase revision or a correcting amendment. It cites TMF622's partial order state as precedent.

Its suggested next step is: specify how lines are grouped into ApplicationUnits and the state transitions, and add test scenarios for restructure, true-up, and future-dated lines.

Related issues named on the Linear issue: DRG-536, DRG-559, DRG-557, DRG-341, DRG-551, and, on the relation list, DRG-563, DRG-582, DRG-572, DRG-599, DRG-607.

| Issue text | Met by this note | Needs code, and why it is not in this change |
|---|---|---|
| All-or-nothing or unit by unit | Unit by unit. One unit per target subscription. Signature stays all-or-nothing. | The apply engine that commits a unit is S4 / DRG-582. |
| Order of operations (create before terminate) | Not adopted. `partial-apply-owner-decision.md` section 8 item 1 still leaves who writes `dependsOnUnitNos` open. | Not built. Deciding and enforcing that order is outside option A. |
| A unit fails after others have committed | No automatic rollback. Document `PARTIALLY_APPLIED`. Failed unit `BLOCKED`. Remedy is a correcting amendment. | Persisting that outcome is apply. Technical approach section 3 assigns it to V1_9 `document_apply_ledger` under S4 / DRG-582. That ledger is not in the SQL tree on this base. This change stops there. |
| `PARTIALLY_APPLIED` and `APPLY_BLOCKED` | The cut in section 1. `APPLY_BLOCKED` means no unit has committed. | No new enum and no migration. Both names are already on `AmendmentDocumentStatus` and in `chk_amendment_document_status` (`V1_7__contract_documents.sql`). Unit statuses `PENDING`, `APPLIED`, and `BLOCKED` are already on `ApplicationUnitStatus` and in `chk_amendment_unit_status`. `ApplicationUnitStatus` says apply behavior is DRG-582. |
| Grouping into ApplicationUnits and the state transitions | Section 1 states the first-slice grouping and the three apply outcomes. | A state-model class that performs those transitions would write apply rows. That needs the apply ledger. Not added. |
| Test scenarios for restructure, true-up, and future-dated lines | Not met. Section 2 illustrates only the adopted SaaS cut. | Automated tests of those cases need the apply path and the ledger. The spike's restructure, true-up, and future-dated groupings are labeled `[INFERENCE]` in spike section 4. Option A does not adopt them. `specs/partial-apply.md` is not accepted. |

Linear status at the time of the read was **Done** (completed 2026-10-16T22:03:31Z). The Notion RA-31 property `Status` was **Not started**. This note does not treat either value as evidence that the rules are implemented, and it does not change Linear or Notion.

## 4. Why this slice has no code

DRG-565 has no acceptance criterion that requires a new type. The status names the issue asks about already exist in the domain enum and in the V1_7 check constraints, read at the base above. Adding a transition class under `contract-asset-management` `domain/documents` would be the apply behavior `ApplicationUnitStatus` already assigns to DRG-582. The technical approach section 3 assigns the apply ledger to S4 as V1_9 `document_apply_ledger`. No SQL file on this base defines that table. Building the transition would need that ledger, or a migration this story does not own. This slice does not add the class, the migration, or the ledger.

`partial-apply-owner-decision.md` section 9 also leaves the ledger table unchosen (`document_apply_ledger` or columns on `applied_quote_acceptances`). This note does not choose it.

## 5. Left open on purpose

These stay open. They are the list in `partial-apply-owner-decision.md` section 8, plus the boundaries of option A:

- Who writes `dependsOnUnitNos`.
- When a restructure revoke unit is submitted to fulfilment. The first slice excludes fulfilment.
- Which queue receives a failed-unit task, and which actor works it.
- Rebase, including whether a revision-only rebase can proceed on the existing approval.
- Whether `NONE` units are stored and skipped, or omitted.
- Signature policy per document. The slice policies already confirmed elsewhere (register OQ10) are `NOT_REQUIRED`, `NOTICE_ONLY`, and `CLICK_THROUGH`. This note does not reopen them.
- S6 event names for a partial document. This note does not authorize S6.
- The S4 ledger table.
- Renewal half-apply and price-lock segment grouping. Both are outside the first slice in the decision file's risk section.

## 6. Impact

**UNKNOWN.** This VM has no GitNexus index (no `.gitnexus` directory). No symbol was edited, so there is no impact walk to report. A text search on this base found the existing status enum, the unit-status enum, and the V1_7 check constraints cited above. Those files are unchanged.

## 7. Unverified

- TMF622's `partial` order state. DRG-565 cites it. The standard text was not opened.
- Full bodies of DRG-536, DRG-559, DRG-557, DRG-341, DRG-551, DRG-563, DRG-572, DRG-599, and DRG-607. The Linear relation list and titles were read.
- The full body of DRG-582. Its title was read. The S4 assignment of V1_9 was read in `amendment-technical-approach-and-dependencies.md` section 3.
- `AcceptedQuoteSubscriptionService` and `applied_quote_acceptances`. Not opened.
- Backend Unit. No code changed, so no Backend Unit run. Run id: N/A.
- Whether any later commit on `main` filled section 9 of the owner-decision file after `fd6421e4`. The file was read at that commit.
