# ADR-035 acceptance checklist and review-thread summary

*open-crm-cpq. Documentation only. Date: 2026-10-16 (CT). Status of ADR-035: **Accepted 2026-10-16** by the project owner (drgaciw). Scope: authorizes the first-slice schema and audit stories only (S1, S2). OQ3, OQ10 and OQ12 defaults confirmed. Sections below are kept as written while the ADR was Proposed; see section 8 (Outcome).*

## 1. Repo convention (verified via GitHub connector, `main`)
- ADRs live in `docs/adr/ADR-NNN-*.md`, with a catalog row in `docs/adr/README.md`. Status values seen: Proposed, Accepted, Superseded. Accepted rows carry a date and what the acceptance authorizes (for example ADR-032: "Accepted 2026-10-15, authorizes DRG-509 only").
- Who accepts: no CODEOWNERS file was found at the repo root or in `.github/`, and no required-reviewer rule is documented in the ADR README. Precedent is owner acceptance, sometimes delegated (ADR-023 and ADR-018: "owner-delegated"; ADR-032 accepted in docs PR #597). So the decider is the project owner (drgaciw). Architect review is advisory per OQ1 ("Architect or user, Jev-routed Choice").
- Proposed ADRs are documentation only and authorize no code, schema, topic or route. Numbers 019 to 022 are reserved. ADR-035 follows ADR-034.
- Gaps against convention (fix in the acceptance PR, not now): ADR-035 sits in `docs/renewals-amendments/`, not `docs/adr/`, and has no catalog row in `docs/adr/README.md`. On acceptance, add the README row and either move the file or keep a stub at `docs/adr/`.

## 2. What acceptance needs
| Item | State |
|---|---|
| Decision text (D-01 to D-06) | Written |
| Decision drivers | RCA split of ledger and document; CAM is single writer of applied state; no network boundary before the spine exists; unblock DRG-562 to 564 without waiting on ADR-024 |
| Alternatives considered | A chosen, B quote-service rejected, C new service deferred to D-04 triggers, D order-service rejected for v1 |
| Consequences | Written (CAM grows; module discipline; reviewers reject out-of-module ledger or document writes) |
| Fitness functions | Written (three checks) |
| Rollout | Written (accept, schema and skeleton behind flags, mid-term policy, reconcile ADR-024..027, extract only on trigger) |
| Reviewer sign-off | **Missing.** Needs owner decision; architect review recommended |
| Catalog row in docs/adr/README.md | Missing, add on acceptance |
| Status line edit to Accepted with date and scope | Owner action only |

## 3. Reviewers and roles
- Decider: project owner (drgaciw). Required.
- Architect review: recommended, on D-01, D-02 and D-04 (placement, thin header, extraction triggers).
- Quote-service owner: confirm D-03 (LifecycleChange, pricing evaluation and render requests stay).
- Product: confirm OQ10 and OQ12 slice defaults.
- Legal and Finance: not required for acceptance (OQ8 and OQ17 come later).
No reviewer is auto-requested on the PR because the repo names none.

## 4. Open questions: before acceptance versus after
| ID | Question | Needed before acceptance? |
|---|---|---|
| OQ1 | Accept ADR-035 as written | This is the decision itself |
| OQ3 | Thin header field set (id, number, tenant, customer ref, start, end, status, last_executed_seq, prior_contract_id) vs DRG-458 | Confirm default at acceptance (condition). Field enrichment can follow |
| OQ10 | Signature policies in slice: NOT_REQUIRED, NOTICE_ONLY, CLICK_THROUGH | Confirm default at acceptance (condition) |
| OQ12 | Slice action codes: Add, Update, Remove | Confirm default at acceptance (condition) |
| OQ5 | Partial application | After. Before S4 (about Day 2) |
| OQ6 | Revision edge cases | After. Before S3 |
| OQ7 | Backfill (slice: header only) | After. Before DRG-566 |
| OQ8 | Retention N and legal hold (Legal, DRG-313) | After. Blocks production go-live only |
| OQ9 | pricing-service amendment modes unverified | After. Gates S5 only |
| OQ17 | Approval thresholds (Finance) | After. Before S5 ends |
| Render home | quote-service request vs document-generation type | After. Implementation ticket |
| Price-lock intent | Own intent or not (stub says no) | After |

Reviewer questions that need an explicit answer: (1) Is a CAM contract-documents module acceptable given CAM becomes the largest service in the renewals path? (2) Are the three D-04 extraction triggers the right ones? (3) Is a thin Contract header in CAM now correct given DRG-458 owns enrichment? (4) Render stays in quote-service: agreed? (5) Accept with OQ3, OQ10 and OQ12 defaults as conditions?

## 5. If rejected or changed
- Quote-service ownership (B) or a new service (C) resets S1 to S3, about two days of documentation rework and no code rework (readiness file, section 5). Record the competing choice and rationale in the ADR, then update OQ1, RA-45 to RA-52 and DRG-579 to DRG-586.
- Acceptance with named changes: edit the Decision section, keep a dated addendum, do not renumber.
- Until accepted: no code, migration, service, topic or gateway route. RA-28 to RA-32 and DRG-562 to 564 stay blocked. OQ9 verification and the Legal question on OQ8 can proceed in parallel.

## 6. Steps to acceptance
1. Review the docs PR (reviewers answer the questions in section 4).
2. Owner decides: accept, accept with changes, or reject.
3. If accepted: a follow-up PR sets Status to Accepted with date and scope ("authorizes the first-slice schema and audit stories only"), adds the README catalog row, and records OQ3, OQ10 and OQ12 defaults as confirmed.
4. Update RA-27 / DRG-561 and OQ1. Then, and only then, S1 and S2 may start.

## 7. Decision summary for the review thread
**ADR-035: Placement and ownership of the amendment document entity. Status: Proposed. Requesting review and a decision.**
- Decision: AmendmentDocument (revisions, lines, application units, hash-chained event log) lives in contract-asset-management as a separate contract-documents module, with a thin Contract header. CAM stays the single writer of applied state. LifecycleChange, pricing evaluation and render requests stay in quote-service. Extract to its own service only on named triggers (retention or legal hold, full CLM scope, tenant isolation).
- Why: matches Salesforce Revenue Cloud's split (ledger beside assets, legal document separate), keeps one writer and strong idempotency, avoids a premature network boundary, and unblocks DRG-562 to 564 without waiting on the Proposed order-service ADRs.
- Rejected: quote-service ownership (mixes mutable quote state with executed evidence), new service now (saga risk before the spine exists), order-service (ADR-024 conflicts with apply-after-EXECUTED).
- Cost: CAM grows; module boundaries must be enforced in review.
- Needed to accept: owner decision, plus confirmation of OQ3, OQ10 and OQ12 defaults. Not needed before acceptance: OQ5 to OQ9, OQ17.
- This PR is documentation only. Nothing here authorizes code.

## 8. Outcome (2026-10-16)
- Decision: **Accepted** by the project owner (drgaciw) on 2026-10-16. Scope: authorizes the first-slice schema and audit stories only (S1, S2).
- Step 3 of section 6 done in docs PR #600: ADR Status set to Accepted with date and scope, `docs/adr/README.md` catalog row added (ADR-035), defaults recorded as confirmed. The ADR file stays under `docs/renewals-amendments/` and the catalog row links to it; no move and no renumbering.
- Confirmed: OQ3 thin Contract header (id, number, tenant, customer reference, start, end, status, last_executed_seq, prior_contract_id; richer fields to DRG-458). OQ10 NOT_REQUIRED, NOTICE_ONLY, CLICK_THROUGH now; E_SIGN and WET later via the DRG-457 adapter. OQ12 Add, Update, Remove only; Swap, Upgrade, Downgrade deferred to RA-42 / DRG-576.
- Step 4: RA-27 / DRG-561 updated and OQ1 closed. S1 and S2 (RA-45 / RA-46, DRG-579 / DRG-580) may now start. They have not been started. Merging PR #600 records the acceptance on main.
