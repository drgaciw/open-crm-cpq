# DRG-549 — renewal pricing resolution design frame

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-17. Base: `main` at `0d71251d7267b3a0124bff60bfc9f897ab1d3fb6`.*

**Status:** Proposed, unaccepted, owner decision required.

**Story:** [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549/renewal-pricing-policy-hierarchy-same-list-uplift-percent-uplift) (RA-15). This file is the design frame for how a renewal price is resolved. It is not an ADR, takes no number, and is not copied into `docs/adr/`.

**Accepting this authorizes no code, migration or deploy.** It authorizes no Flyway file, no Drools edit, no flag, no route, and no merge. Pricing flags stay as they are. No real customer data is read or written. DRG-549 stays In Review until the owner merges. The later code-slice allowlist is a draft, not approved. A merge of #658 does not release the code slice.

**Reads, and does not amend:** Accepted [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) (Accepted 2026-10-16). Precedence below cites that record. This frame does not restate it as a second rule.

**Not prerequisites:** [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md), [ADR-024](../../adr/ADR-024-order-orchestration-context.md), [ADR-025](../../adr/ADR-025-saga-process-manager.md), [ADR-026](../../adr/ADR-026-command-messages.md), and [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md). Each header on this tree says **Status: Proposed on 2026-10-04. Not accepted.** `docs/adr/README.md` lists the same status. This frame does not accept them and does not depend on them.

## Verification

Nothing was executed. This pass did not start Maven, a database, Testcontainers, or an HTTP call.

| Mark | Meaning here |
|---|---|
| `verified by reading code` | A source or doc file on this `main` (`0d71251d`). Not executed. |
| `verified by reading the issue` | Linear DRG-549, this pass. The issue was not edited. |
| `verified by reading the pull request` | GitHub pull-request title, state, and merge commit, this pass. The branch was not checked out. |
| `unverified` | Not established by a file, issue, or pull request opened in this pass. A truncated listing is `unverified`. |

**GitNexus:** no `.gitnexus` directory on this tree. **Impact: UNKNOWN (no GitNexus index).**

**Claims marked as executed: none.**

## Owner-decision table

Every open fork is in this table. The Accepted precedence record is not a fork; it is cited in the next section and is not reopened. Each default in this table is **Proposed, unaccepted, owner decision required.**

| Fork | Default | Alternatives |
|---|---|---|
| Where account, agreement, and line renewal policy inputs live | Proposed, unaccepted, owner decision required. CAM, using the CAM default shape in the policy-input section. crm-service and pricing-service are the other options. This frame adds no column and no migration. | crm-service, using that option's default shape. pricing-service, using that option's default shape. Each option's own alternatives are in the policy-input section. |
| How quote-service reaches the merge | Proposed, unaccepted, owner decision required. No transport is decided. | (Option, not a decision) A versioned, idempotent, read-only HTTP endpoint with no persistence, carrying the DoD v0.2 Open Host Service block below. Not coded. (1) Leave `RenewalUpliftResolver` uncalled. (2) A Spring bean inside pricing-service and still no quote-service caller. (3) A shared library. The Accepted record rules (3) out: pricing-service is the only component that merges the rungs. |
| `LIST` in the first slice | Proposed, unaccepted, owner decision required. `LIST` is out of the first slice. Merged note [#645](https://github.com/drgaciw/open-crm-cpq/pull/645) is the gate, and that note stays Proposed. | (1) Include `LIST` only after the owner accepts the DRG-539 note. (2) Include `LIST` in the first slice with no price-book read. This frame does not offer (2) as a price. |
| First-slice modes | Proposed, unaccepted, owner decision required. `UPLIFT_PCT` plus `SAME` only. | (1) `UPLIFT_PCT` only, which is what the resolver prices today. (2) Add `UPLIFT_AMT`. (3) Add `OVERRIDE`. (4) Add `RENEGOTIATE`. (5) Add `LIST` (see the `LIST` row). |
| `SAME` semantics | Proposed, unaccepted, owner decision required. Carry prior price. This is the only mode called uncontroversial. It is still a default. | (1) Carry prior list. (2) Carry the term-average rate. (3) Treat a null caller percent as zero, which is what `QuoteContinuationPolicy` does on the renewal branch today. |
| `UPLIFT_AMT` semantics | Proposed, unaccepted, owner decision required. Use the `UpliftAmt` meaning in D-01 of the Accepted record (a signed currency delta per unit on the basis that record names). Out of the first slice. | (1) An annual lump, not a per-unit delta. (2) A one-time credit that does not change the unit. (3) The amount replaces the unit. |
| `OVERRIDE` semantics | Proposed, unaccepted, owner decision required. Use the `Override` meaning in D-01 of the Accepted record (the explicit unit price on the line, with actor and reason). Out of the first slice. | (1) Clamp the entered unit to the cap and floor. (2) A percent-off override. (3) Return no price until a separate approval record exists. |
| When `RENEGOTIATE` is allowed | Proposed, unaccepted, owner decision required. Not in the first slice. D-01 says DRG-549 owns when the mode is allowed; this frame does not allow it. | (1) Allow it with no unit price. (2) Allow it only when the request carries a seller reason. |
| Later-year expansion (DRG-568 leak guard) | Proposed, unaccepted, owner decision required. Keep one flat period. The resolver does not expand escalator years. A later slice does not touch `RampEscalatorExpander` or price-lock fields. `retentionMarker` stays opaque. [#644](https://github.com/drgaciw/open-crm-cpq/pull/644) is Proposed. | (1) Expand later years inside the resolver. (2) Call `RampEscalatorExpander` from the renewal path. (3) Treat `retentionMarker` as a price lock. |
| Where the later orchestration lives | Proposed, unaccepted, owner decision required. `QuoteContinuationService`. `QuoteContinuationPolicy` stays static and pure. | (1) Put the pricing HTTP call inside `QuoteContinuationPolicy`. (2) Add a third orchestrator and leave both current types unchanged. |
| List price at as-of | Proposed, unaccepted, owner decision required. Treat it as a gap. `ProductServiceClient` is not the source. `LIST` stays out of the first slice. The price-book read stays the DRG-539 note. | (1) Add a list-price-at-as-of method to `ProductServiceClient`. (2) Have quote-service call pricebook-service itself. |
| Later code-slice file allowlist | Proposed, unaccepted, owner decision required. The allowlist below is a draft, not approved. A merge of #658 does not release the code slice. It contains no migration. | (1) Resolver plus a new controller only. (2) The same allowlist plus a Flyway migration. This frame does not authorize (2). |

## Precedence

This frame follows the order in Accepted [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md). The ruling names that order CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES. The record's D-04 is the text. It is quoted here and is not rewritten:

> Each field is taken from the first rung that sets it: contract clause, then line, then agreement, then account, then the rules default. Unset fields fall through. The defaults in D-01, D-02, and D-03 apply only when every rung leaves the field unset.

The rest of D-04, including what the rules rung may supply and the two unresolved results, stays in that file. This frame does not add a rung and does not swap two rungs.

The DRG-549 ticket text order is superseded. The issue description still says, verbatim: "Define RenewalPricingPolicy {Same|List|UpliftPct|UpliftAmt|Override|Renegotiate} resolved account > agreement > line (most specific wins); feed it into QuoteContinuationPolicy." `verified by reading the issue`. That sentence is not the order this frame follows.

A Linear comment on DRG-549 records the supersession. It was not edited by this work, and this frame does not edit the ticket. The comment body, verbatim:

> Precedence recorded per CPQ Decision ruling: the Accepted adr-renewal-uplift-basis-and-precedence.md order (CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES) supersedes the ticket text order (account > agreement > line). Ticket text left unedited. A docs-only design frame is being drafted; no code is authorized. Status stays Backlog/In Review as set.

`RenewalUpliftResolver` on `main` already walks that same enum order. `verified by reading code` (`PRECEDENCE` in `backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/RenewalUpliftResolver.java`). The class arrived in merged [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) (merge commit `8bf4ce32`). The constant is the implementation of D-04. It is not a second statement of the rule.

## Policy inputs are a gap

On this `main` there is no read path for account, agreement, or line renewal policy in the files opened below.

`verified by reading code`:

- Account entity: `backend/services/crm-service/src/main/java/com/opencrm/crm/organization/Organization.java`. The class declares `name`, `website`, `website_domain`, `industry`, `employee_count`, `annual_revenue`, `phone`, address columns, `description`, `linkedin_url`, `type`, `owner_id`, `territory`, `contacts`, and a contact-count formula. It declares no renewal field. Parent `BaseEntity` (`backend/libs/common/src/main/java/com/opencrm/common/entity/BaseEntity.java`) declares id, uuid, audit stamps, version, and `is_deleted`.
- Agreement header: `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/ContractHeader.java`. The class Javadoc is: "Thin contract header. Richer fields stay with DRG-458. Precedence flag stays with DRG-592." Declared columns are `id`, `contract_number`, `customer_account_id`, `term_model`, `start_date`, `end_date`, `governing_agreement_ref`, `status`, `last_executed_seq`, and `prior_contract_id`. No renewal field. Parent `DocumentAuditedEntity` declares tenant and audit columns only.
- Line tokens: `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/AmendmentDocumentLine.java` stores `policy_slot`, `policy_id`, and `policy_version`. `PolicySlot` (`PolicySlot.java`) includes `RENEWAL`. Its Javadoc is: "Policy extension slot. Renewal and price-lock slots are stored tokens only in S1." Those three columns are tokens. They are not a renewal method, a percent, or an amount.

**Unverified:** crm-service and contract-asset-management SQL migrations were not opened, and full tree listings of those services were truncated. A text search of those two service trees for `renewalPricing`, `RenewalPricing`, `upliftPercent`, and `renewal_method` returned no matches. That search is not a migration review. Whether a migration adds a renewal column that these entities do not map is `unverified`.

This gap is an owner decision. The options are CAM, crm-service, or pricing-service. None is a finding. This frame adds no column and no migration. Until a read path exists, a later resolve call still receives the five rungs as caller-supplied facts. That transport is not a fourth home.

**Default:** Proposed, unaccepted, owner decision required. CAM, using the CAM default shape below.

**Alternatives:** crm-service, using the crm-service default shape below. pricing-service, using the pricing-service default shape below.

### Option: CAM

**Default:** Proposed, unaccepted, owner decision required. The agreement method lives on the CAM contract header, and the line override lives on the CAM document line beside the `policy_slot`, `policy_id`, and `policy_version` tokens already stored there. The account rung is not a new `Organization` column. CAM keeps the `customer_account_id` it already stores and holds the account method on the contract.

**Alternatives:** (1) CAM stores only those tokens, and the method body lives in a new CAM policy document. (2) Renewal columns are added on `ContractHeader` only, with no separate line override. (3) CAM also owns an account-policy row keyed by `customer_account_id`. No alternative is a migration in this frame.

### Option: crm-service

**Default:** Proposed, unaccepted, owner decision required. The account method lives on `Organization` in crm-service. Agreement and line methods stay outside crm-service.

**Alternatives:** (1) A new crm-service policy entity keyed by organization, with no renewal columns on `Organization`. (2) crm-service stores the account, agreement, and line methods, even though the agreement header is a CAM type. (3) crm-service exposes a read and stores nothing new. No read of that kind exists in the files opened above, and this alternative does not create one.

### Option: pricing-service

**Default:** Proposed, unaccepted, owner decision required. pricing-service stays the merge and does not become the policy store. It keeps receiving unmerged rungs. The Accepted record already says pricing-service does not read another service's database.

**Alternatives:** (1) The pricing schema gains policy rows for account, agreement, and line, and the resolver reads them. That alternative is persistence, and it is not this frame. (2) pricing-service stores only a merged snapshot. That store is DRG-564, which this frame does not accept. (3) A shared library so another service merges in process. The Accepted record rules that alternative out.

## Endpoint, not a library

The Accepted record assigns the merge to one component. Decision, first sentence, quoted:

> pricing-service is the only component that merges the rungs and computes the renewal unit price.

D-04 also says quote-service sends the rungs unmerged and pricing-service merges them. A library dependency that lets quote-service merge, or that moves the merge into a shared jar both services call, is ruled out by that record. This frame does not propose one.

What is on `main`, `verified by reading code`:

- Package `backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/`, merged in [#640](https://github.com/drgaciw/open-crm-cpq/pull/640).
- `RenewalUpliftResolver` is a `public final` class. Its Javadoc says callers pass the five rungs unmerged, and that the class does not read another service's database, does not call the rules port, does not persist a snapshot, and does not write an event. `RenewalUpliftResolverTest.sameInputsResolveTwiceWithoutInstanceState` asserts that every declared field is static and that the class has no annotations.
- A search for the symbol `RenewalUpliftResolver` under `backend/` in `*.java`, `*.xml`, and `*.yml` found the class and its test only. The test constructs it with `new`. No production caller was found in those suffixes. Callers outside those suffixes are `unverified`.
- `RenewalUpliftMode` names `SAME`, `LIST`, `UPLIFT_PCT`, `UPLIFT_AMT`, `OVERRIDE`, and `RENEGOTIATE`. The resolver prices `UPLIFT_PCT` only. Any other merged mode throws `RenewalUpliftModeOutOfSliceException` (`RenewalUpliftModeOutOfSliceException.java`). The exception message says DRG-549 owns the mode catalog.

`QuoteContinuationPolicy` still prices the live renewal path: one period, exit net times the caller percent, and a null percent becomes zero (`QuoteContinuationPolicy.java`, the renewal branch). That path does not call the resolver. `verified by reading code`.

**Default:** Proposed, unaccepted, owner decision required. No transport is decided.

**Option, not a decision:** Proposed, unaccepted, owner decision required. An HTTP endpoint on pricing-service. The option carries the DoD v0.2 Open Host Service block below: versioned, idempotent, read-only, no persistence. Not coded. Selecting this option is a later owner act. This frame does not select it.

**Alternatives:** leave the resolver uncalled; register it as a Spring bean that quote-service still cannot call; a shared library, which the Accepted record rules out.

### Option: DoD v0.2 Open Host Service block (not a decision)

This block is an option. It is not a decision. Proposed, unaccepted, owner decision required. It is the Open Host Service shape attached to the HTTP option above. Versioned, idempotent, read-only, no persistence. This file does not add the route. A merge of #658 does not add it.

| Property | Sketch |
|---|---|
| Versioned | One operation beside the existing `/api/v1/pricing` routes on `PricingExecutionController` (`/execute`, `/simulate`, `/simulate/bulk`, `/audit/{quoteExternalId}`). Sketch path: `POST /api/v1/pricing/renewals/resolve`. The path is not registered. |
| Idempotent | The same request body returns the same response body. Repeating the call changes no stored state. |
| Read-only | The handler calls the resolver and returns. It opens no write transaction. |
| No persistence | No snapshot, no `PricingAuditLog` row, no outbox row, no schema-registry event. `RenewalUpliftResult`'s Javadoc already says nothing there is stored. |
| Contract sketch | Request JSON matches `RenewalUpliftRequest`: `currency`, `exitNet`, `quantity`, `termMonths`, `pins`, and `rungs`. Rungs arrive unmerged. Response JSON matches `RenewalUpliftResult`, wrapped in the `ApiResponse` envelope `PricingServiceClient` already parses for execute and simulate. When a unit is returned, `flatPeriodCount` is 1. `retentionMarker` is copied through as an opaque string. |
| Coded | No. |

Schema-registry, outbox, commercial-decision audit, and event replay stay N/A on this sketch: the sketch emits no event and writes no decision. DRG-564 owns the audit store. This frame does not accept that note and does not start it.

`PricingServiceClient` (`backend/services/quote-service/src/main/java/com/opencrm/quote/client/PricingServiceClient.java`) currently posts to `/api/v1/pricing/execute` and `/api/v1/pricing/simulate` and parses `ApiResponse<PricingExecuteView>`. It has no renewal method. A call from that client exists only if the owner later accepts the HTTP option. Not in this file.

## `LIST` is out of the first slice

[#645](https://github.com/drgaciw/open-crm-cpq/pull/645) merged the note `docs/renewals-amendments/prereq/story-drg-539-pricebook-read-for-list-bases.md` (merge commit `0a636982`). The note's status is Proposed. Not accepted. Its header says accepting that note authorizes no code, no endpoint, and no migration, and that the List bases stay unresolved until the owner accepts it. `verified by reading code` for that header. `verified by reading the pull request` for the merge.

That note gates mode `LIST`. **Default:** Proposed, unaccepted, owner decision required. `LIST` is out of the first slice.

**Alternatives:** include `LIST` only after the owner accepts the DRG-539 note; include `LIST` with no price-book read, which this frame does not offer as a price.

`ProductServiceClient` does not fill this gap. See the section below.

## Mode semantics

The Accepted record's D-01 already names `Same`, `UpliftAmt`, and `Override`. This frame does not replace those sentences. The business-policy choice is whether DRG-549 adopts them. Each default is **Proposed, unaccepted, owner decision required.**

**`SAME`.** Default: Proposed, unaccepted, owner decision required. Carry prior price. Uncontroversial, and still a default. The prior price this default means is the exit net D-01 names for `Same`. Alternatives: carry prior list; carry the term-average rate; keep the live null-percent-as-zero behavior in `QuoteContinuationPolicy`.

**`UPLIFT_AMT`.** Default: Proposed, unaccepted, owner decision required. The D-01 `UpliftAmt` meaning (signed currency delta per unit on the basis that record names). Out of the first slice. Alternatives: an annual lump; a one-time credit that does not change the unit; the amount replaces the unit.

**`OVERRIDE`.** Default: Proposed, unaccepted, owner decision required. The D-01 `Override` meaning (explicit unit price on the line, with actor and reason). Out of the first slice. Alternatives: clamp the entered unit to the cap and floor; a percent-off override; no price until a separate approval record exists.

**First-slice scope.** Default: Proposed, unaccepted, owner decision required. `UPLIFT_PCT` plus `SAME` only. The resolver on `main` prices `UPLIFT_PCT` and throws for `SAME`. Pricing `SAME` is part of the recommendation. It is not on `main`. Alternatives: `UPLIFT_PCT` only; add `UPLIFT_AMT`; add `OVERRIDE`; add `RENEGOTIATE`; add `LIST`.

**`RENEGOTIATE`.** Default: Proposed, unaccepted, owner decision required. Out of the first slice. D-01 says DRG-549 owns when the mode is allowed, and this frame does not allow it. Alternatives: allow it with no unit price; allow it only with a seller reason.

## DRG-568 leak guard

[#644](https://github.com/drgaciw/open-crm-cpq/pull/644) merged `docs/renewals-amendments/prereq/DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md` (merge commit `16369313`). That file's status is Proposed on 2026-10-16. It is not an accepted ADR. `verified by reading code` for the status line. `verified by reading the pull request` for the merge. This frame does not treat #644 as accepted and does not wait on it as a prerequisite for the one flat period.

D-06 of the Accepted uplift record already says a multi-year escalator does not grow later periods until DRG-533 is accepted, and that DRG-568 consumes the first-period price. The resolver Javadoc says escalator years are not expanded, and a priced result sets `flatPeriodCount` to 1 (`RenewalUpliftResolver.finish`). `verified by reading code`.

**Default:** Proposed, unaccepted, owner decision required. Keep one flat period. The resolver does not expand escalator years. A later code slice does not touch `backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampEscalatorExpander.java` and does not touch price-lock fields. `retentionMarker` stays opaque: the resolver copies the string onto the result and does not interpret it (`RenewalUpliftResolver.retention`). D-02 of the Accepted record says the rules rung's retention marker is not a cap, a floor, or a lock. This frame does not map the marker onto a lock.

**Alternatives:** expand later years inside the resolver; call `RampEscalatorExpander` from the renewal path; treat `retentionMarker` as a price lock.

## List price at as-of is a gap

`ProductServiceClient` (`backend/services/quote-service/src/main/java/com/opencrm/quote/client/ProductServiceClient.java`) has one public operation, `validateConfiguration`. It posts product ids to configuration-engine `POST /internal/v1/configurations/evaluate`. It has no list-price method and no as-of argument. `verified by reading code`.

**Default:** Proposed, unaccepted, owner decision required. This client is not the list-price source. `LIST` stays out of the first slice. The as-of read remains the DRG-539 note from #645.

**Alternatives:** add a list-price-at-as-of method to `ProductServiceClient`; have quote-service call pricebook-service itself.

## Later code-slice allowlist (draft, not approved)

The allowlist is a draft, not approved. A merge of #658 does not release the code slice. Merging this pull request adds this document only. It does not authorize the files below, a controller, a client call, a migration, a flag, or a deploy.

**Default:** Proposed, unaccepted, owner decision required. The list below is the draft allowlist for a later code slice. It is not approved. A merge of #658 does not release it.

| File | Later slice |
|---|---|
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/RenewalUpliftResolver.java` and the types in that package the resolver already uses | Edit the resolver so the accepted first-slice modes resolve. Today that is `UPLIFT_PCT` only. |
| A new controller in `backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/` | Only if the owner later accepts the HTTP option. The Open Host Service block on that option is not a decision. Class name is not fixed here. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/client/PricingServiceClient.java` | Call the new route only if the owner later accepts the HTTP option. |
| A new view DTO in `backend/services/quote-service/src/main/java/com/opencrm/quote/client/`, beside `PricingExecuteView.java` | Parse the renewal response. Class name is not fixed here. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationService.java` | Orchestration, if a later slice is separately authorized: load the schedule, call pricing-service only when the HTTP option is accepted, then call the policy. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java` | Stay static and pure. `plan` is static, the class has no instance fields, and it does not call another service. `verified by reading code`. |
| `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteContinuationPolicyTest.java` | Tests for the policy change. The file is already on `main`. Its current assertions were not re-run. |
| A new `RenewalPricingPolicy` at `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/RenewalPricingPolicy.java` | The mode value the service passes. The type does not exist today. |

The allowlist contains no migration and no file under a `db/migration` directory. `QuoteContinuationPolicy` is static and pure, so the HTTP call and the transaction stay in `QuoteContinuationService`.

**Alternatives:** limit the later slice to the resolver and the new controller; add a Flyway migration, which this frame does not authorize.

Out of the allowlist: `RampEscalatorExpander`, price-lock fields, `ProductServiceClient`, pricing flags, Drools, and any schema-registry or outbox type.

## Scope against DoD v0.2

[CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338) is the approved rule. This table scores this design frame. It does not score a later implementation. The Open Host Service block above is an option, not a decision. This change adds no integration surface, so the item is N/A here.

| Item | Result | Reason |
|---|---|---|
| Open Host Service | N/A | This change adds or changes no integration surface. The endpoint block above is an option, not a decision, and it is not coded. |
| Schema-registry events | N/A | This change adds or changes no event. |
| Outbox writes | N/A | This change writes no outbox row and publishes no event. |
| Commercial-decision audit | N/A | This change writes no commercial decision. |
| Event-replay test | N/A | This change produces no event, so it adds no replay test. |

No flag is changed. Nothing is deployed. Accepting this authorizes no code, migration or deploy.

## Evidence index

Read in this pass. Not executed.

| Item | Path or source |
|---|---|
| Accepted precedence, D-01, D-02, D-04, D-06 | `docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md` |
| ADR-017, ADR-024, ADR-025, ADR-026, ADR-027 headers | `docs/adr/ADR-017-synchronous-inter-service-communication.md`, `docs/adr/ADR-024-order-orchestration-context.md`, `docs/adr/ADR-025-saga-process-manager.md`, `docs/adr/ADR-026-command-messages.md`, `docs/adr/ADR-027-compensation-and-point-of-no-return.md`; catalog rows in `docs/adr/README.md` |
| Resolver, mode, exception, request, rung, result | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/` |
| Resolver has no annotations and only static fields | `RenewalUpliftResolverTest.sameInputsResolveTwiceWithoutInstanceState` |
| Resolver merge | [#640](https://github.com/drgaciw/open-crm-cpq/pull/640), merge commit `8bf4ce32` |
| DRG-539 note and its Proposed status | `docs/renewals-amendments/prereq/story-drg-539-pricebook-read-for-list-bases.md`; [#645](https://github.com/drgaciw/open-crm-cpq/pull/645), merge commit `0a636982` |
| DRG-568 / DRG-533 draft, Proposed | `docs/renewals-amendments/prereq/DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md`; [#644](https://github.com/drgaciw/open-crm-cpq/pull/644), merge commit `16369313` |
| Organization and BaseEntity | `backend/services/crm-service/src/main/java/com/opencrm/crm/organization/Organization.java`; `backend/libs/common/src/main/java/com/opencrm/common/entity/BaseEntity.java` |
| Contract header, line tokens, slot | `ContractHeader.java`, `DocumentAuditedEntity.java`, `AmendmentDocumentLine.java`, `PolicySlot.java` under `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/` |
| Continuation policy and service | `QuoteContinuationPolicy.java`, `QuoteContinuationService.java` under `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/` |
| Pricing and product clients | `PricingServiceClient.java`, `PricingExecuteView.java`, `ProductServiceClient.java` under `backend/services/quote-service/src/main/java/com/opencrm/quote/client/` |
| Existing pricing routes | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionController.java` |
| Escalator class, not modified | `backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampEscalatorExpander.java` |
| Policy test path, file present, assertions not re-run | `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteContinuationPolicyTest.java` |
| DRG-549 issue and the precedence comment | Linear, this pass. Status read: Backlog. |
| OQ6 record | Merged [#642](https://github.com/drgaciw/open-crm-cpq/pull/642). This frame does not quote it. |

## Unverified in this pass

- crm-service SQL migrations, contract-asset-management SQL migrations, and full tree listings of those services. Listings were truncated. No migration name is claimed.
- Callers of `RenewalUpliftResolver` outside `backend/**/*.java`, `backend/**/*.xml`, and `backend/**/*.yml`.
- Any runtime result: HTTP, Drools, database, or the 79 resolver tests named in the #640 body. Those tests were not run here.
- Linear status after this read. The read returned Backlog. The precedence comment says status stays Backlog/In Review as set.
- GitNexus impact. **Impact: UNKNOWN (no GitNexus index).**

## What this does not do

- It does not authorize code, a migration, a flag, a route, or a deploy. The allowlist is a draft, not approved. A merge of #658 does not release the code slice.
- It does not decide the HTTP transport. The DoD v0.2 Open Host Service block (versioned, idempotent, read-only, no persistence) is an option, not a decision.
- It does not decide a read path. The policy-input home is an owner decision among CAM, crm-service, and pricing-service.
- It does not edit DRG-549, the Accepted uplift record, ADR-017, or ADR-024 through ADR-027.
- It does not turn a pricing flag on, and it does not read or write real customer data.
- It does not price `SAME`. The resolver on `main` throws for every mode other than `UPLIFT_PCT`.
- It does not expand escalator years, and it does not interpret `retentionMarker`.
