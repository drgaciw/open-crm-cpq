# Delegated renewals and amendments decisions — 2026-10-17

**Status:** Decided under explicit project-owner delegation on 2026-10-17. Decision adoption becomes effective when this record merges to `main`.

## Authority and scope

The owner wrote: **“I delegate all these decisions to you and defer to your recommendations.”** This follows the discussion of the remaining S3/CAM, LifecycleChange, commercial-policy, catalog, fulfilment, ARR, visibility and migration decisions in the documentation batch #648–#661. These are decisions made under delegation, not individually authored choices by the owner.

The frozen reading baseline is `285ffef8e08274600090d8ba077a0c92f489c062` (documentation acceptance #662). The 13 frames listed in the disposition index are in scope. This record adopts the substantive choice in each baseline **Default** column except for the explicit replacements in that index and the contracts below. Pre-decision labels such as “not adopted”, “owner option” and “owner decision required” in those baseline choices describe their old status; they do not limit this adoption. If a baseline default keeps an issue outside the first slice or requires explicit contract data, that is a deliberate scope/input rule, not an unresolved design choice. Recommendations that differ from the default are selected only where the index says so.

Priority is: already Accepted invariants, the explicit contracts below, the index replacements, then the adopted baseline default. Source quotations, code observations and test claims are historical evidence, not changed by adoption. No earlier digest or executed commercial fact may be rewritten to fit the new design.

This delegation resolves policy and design choices. It does not release implementation stories, approve a code-slice allowlist, run a migration/backfill, enable a flag or deploy. S3–S8 require their existing separate implementation authorization and verified delivery gates. OQ9 remains a verification requirement. ADR-017 and ADR-024–027 and separate older DRG-609/539/533/564/612 proposals are not accepted wholesale. Narrow contracts decided here supersede overlapping open questions; the unread or conflicting remainder of those proposals stays outside this decision. No ADR number is assigned and no issue workflow transition is claimed.

## Storage and digests

**D-STORAGE.** Adopt nullable pricing pins (`product_external_id`, `pricebook_version`, `configuration_hash`), both document/revision `content_digest` columns, and current-header `tcv_delta`, `arr_delta`, `one_time_total`, `credit_total` while unpriced. Clear old policy-decision identities, trace and approval/signature references on the new revision as required by OQ6. A priced revision requires all required pins, valid decision evidence and all computed totals. Submit and execute reject missing/stale pricing; real computed zero is distinct from unknown null. Old snapshots preserve actual nulls and amounts. A future additive CAM migration uses the next free V1_10 or later; V1_9 stays reserved for S4 and existing migrations are immutable. Rollback to NOT NULL refuses while any affected row is null and never fabricates values.

**D-SNAPSHOT.** CAM owns an append-only commercial-envelope store in its own database. Use a new table of immutable payload rows with tenant, document id, revision, envelope schema version, SHA-256, canonical UTF-8 bytes and creation audit metadata; its unique identity is `(tenantId, documentId, revision, envelopeDigest)`. Store and validate the exact canonical bytes, not just a mutable JSON object. `inputs_snapshot_ref` binds the revision to that durable identity through an opaque `cam-envelope:v1:<uuid>` reference; the original caller input reference remains a separate field inside the payload, avoiding a reference cycle. CAM validates tenant and identity on every lookup. Source input material needed to reconstruct the decision is copied by value or retained by immutable, content-addressed evidence with a verified digest and retention at least as long as the document. A mutable URL alone is insufficient.

The versioned envelope includes every commercial header field and reason/date, currency and all four totals, all commercial lines and stable source/change-line identities, units/quantities/service periods, pricing and product/configuration pins, policy/mapping versions, decision identity/trace, observed schedule revision, source input identity/digest and other commercial digest inputs. The implementation must supply a closed DTO/schema and golden fixtures for this projection; adding a commercial input changes the envelope schema version. Workflow status, row version, approvals/signatures, computed digest, the CAM envelope reference itself and volatile transport/audit timestamps are excluded from the hashed commercial projection. Approval/signature evidence binds to the resulting digest separately.

Before supersession mutates a current header or totals, CAM archives that complete previous envelope and freezes its reference in the same database transaction. An OPEN row may move to a newly inserted immutable envelope; a frozen reference/payload cannot change. Archive or validation failure aborts supersession. A previously recorded digest is retained byte-for-byte with its original digest-format version; never rehash legacy frozen evidence under a new format. For a never-priced draft with no prior digest, compute a genuine close digest over its null-bearing envelope before freezing. The new OPEN/unpriced digest is null until submit or close. Thus null open digests do not produce digest-less frozen revisions.

**D-DIGEST / OQ18.** Confirm a single pure canonicalization/hash utility in `backend/libs/common`, delivered with S3 and reusable by S5/S6. No CAM-local interim implementation. Its format id is `CAM-COMMERCIAL-1`: a closed commercial DTO, explicit nullable fields, money/quantities represented as normalized decimal **strings** (plain notation, no redundant trailing fractional zeroes, negative zero becomes `0`), canonical UUID/date spellings, stable sorting of line arrays by unique `changeLineRef` and unit arrays by unit number, then [RFC 8785 JCS](https://www.rfc-editor.org/rfc/rfc8785) and SHA-256 of its UTF-8 bytes, lower-case hex. Reject duplicate identities, duplicate JSON keys, invalid Unicode and unsupported/nonfinite numeric inputs. Preserve ordinary Unicode strings as JCS requires. Decimal normalization is a domain DTO step before JCS, not a change to JCS string serialization. Existing S2 event-chain hashing keeps its own format and fixtures. S3 owns shared commercial-digest fixtures; S5/S6 may not fork the algorithm. This confirmation satisfies the OQ18 design-confirmation gate, not implementation authorization.

## CAM transactions and delivery

**D-REVISION.** Adopt DRG-616 defaults 1–9, 11 and 13 and the complete commercial request described by the frame. CAM owns `POST /api/v1/amendments/{amendmentId}/revisions`, expected row `version`, complete new commercial lines without prices/pins, caller `targetQuoteId`, source `inputsSnapshotRef` and `applicationUnitNo`. Resolved header dates/reason and structural hooks are mandatory as in the draft. First-slice categories use Cluster E L4's exact derivation. New success is 201; identical replay is 200. No cross-service table reads and no header-only partial revision. Terminals REJECTED, WITHDRAWN and VOID refuse with `ILLEGAL_TRANSITION`; executed/apply states refuse with `IMMUTABLE_EXECUTED`. OUT_FOR_SIGNATURE stays unsupported and returns 409 for revision; WET/E_SIGN execution retains the existing 422 slice rejection. No VOID mutation is introduced in the slice.

**D-RACE.** Revision, submit, approval mutation, priced-result installation and execute serialize on the CAM document row; execute also locks its contract row for gapless numbering. Operations needing both use contract-then-document lock order consistently. Check expected version under the lock and atomically change the revision, header, evidence and audit. Revision versus execution against the same version has exactly one committed winner. Loser returns `VERSION_CONFLICT`; a subsequent attempt observing executed state returns `IMMUTABLE_EXECUTED`. Submission validates/recomputes the current projection digest; execution verifies that digest and approval binding under the same lock. An executed digest equals the executed revision's frozen envelope digest. A failed operation makes no commercial transition. Refusal audit is persisted in a separate successful audit transaction after releasing failed business locks, with command id/actor/reason; audit delivery failure is observable and never reports a successful business transition. Success `AMENDMENT_SUPERSEDED` and refusal `AMENDMENT_REVISION_REJECTED` are local audit types. Existing S2 hash-chain and audit outbox obligations remain; “local” never means suppressing its outbox. No new S6 domain-event topic/schema is adopted here.

**D-IDEMPOTENCY.** Scope operation receipts by tenant, caller, operation and idempotency key, plus natural identity `(documentId, sourceLifecycleChangeId, sourceRevision, operation)`. Persist canonical request digest and the committed semantic response/identities atomically with the CAM operation. Identical replay returns the original receipt without another revision, price installation or audit success; same key/natural identity with different content returns 409 `IDEMPOTENCY_CONFLICT`. Receipt lookup precedes expected-version checks so a lost acknowledgement is safely replayable after the row version advances. Receipt uniqueness and row locks prevent concurrent double commit. Retain identities for the life of the document; no short TTL can permit a duplicate commercial operation.

**D-PRICED-WRITE.** Choose a synchronous CAM command, `POST /api/v1/amendments/{amendmentId}/revisions/{revision}/pricing`, separate from quote-service `:price`. Require idempotency key, expected CAM row `version`, source LifecycleChange id/revision, target quote id, immutable input and pricing-decision references/digests, policy id/version and trace, observed schedule revision, currency, complete per-line stable identities with all product/pricebook/configuration and policy-decision pins, unit prices/net amounts, and all four document totals. Require a complete result for the current commercial lines; missing, duplicate or foreign line identities and inconsistent totals/currency reject the entire command. Only the current OPEN revision of a DRAFT document may accept a priced write; other workflow states return ILLEGAL_TRANSITION, and executed states return IMMUTABLE_EXECUTED. Repricing an IN_REVIEW or APPROVED document first opens a new revision under OQ6. CAM verifies durable decision evidence and input binding without recomputing price, then atomically installs all values, a new envelope and receipt. It increments row version and invalidates any prior approval binding; it does not execute. An identical retry replays the receipt; a different source revision/input digest, stale expected version or frozen/executed target refuses and cannot install a late result. Success is 200 with committed document/revision/version, decision identity and input/envelope digests.

**D-DELIVERY.** Quote-service durably records the pending source operation, payload digest and idempotency/natural identities before invoking CAM. Chosen transport is synchronous HTTP with a 5-second connect and 15-second total call deadline, no database transaction held across the network call. Worker retry is at least once with jittered exponential delay from 1 second to 5 minutes; 408/429/5xx and lost acknowledgements retry the **same** operation. A durable reconciler retries pending/unknown operations, using receipt replay to discover the committed result. A matching CAM receipt alone confirms completion. Semantic 4xx refusals are recorded REJECTED; unexpected/auth/configuration failures remain visible BLOCKED for remediation, never falsely completed. Timeout is UNKNOWN/PENDING, not rejection and not rollback. Before creating a newer source revision, reconcile earlier unknown operations in source-revision order. Different source content requires a new operation; never mutate a retry payload. CAM and quote-service storage are not a distributed transaction. This is a narrow integration decision, not acceptance of ADR-017 as a whole.

**D-ACCEPTANCE.** Adopt AC-S3-9, AC-S3-10 and AC-S3-11 against these local contracts, and AC-S5-7/AC-S8-6 against the durable delivery contract. Required delivery evidence includes PostgreSQL concurrency/rollback tests, duplicate natural-key races, repricing after supersession, stale pricing rejection, frozen-null digest reconstruction, durable snapshot/receipt survival across restart and lost-request/response reconciliation. These are required future tests, not executed evidence. OpenAPI completion and closed DTO/golden fixtures are required before any implementation slice can be called ready; prose adoption alone does not deliver those artifacts.

## Renewal automation

Keep `auto_renew=false` and `kickoff_opt_in=false`; clauses cannot enable them. Keep the existing 30-day eligibility publication/receipt identity. Choose a distinct CAM daily scan for opted-in contracts with a finite end date when today is at or after `endDate - leadTimeDays` and before term end. Persist one kickoff operation per tenant/contract/end-date cycle, with settings version captured as evidence but not in the dedupe key. The default lead time is 90 days. Missed runs catch up; changing settings cannot create a second draft for the same cycle. Recheck opt-in and contract/end-date version before dispatch. Term-end handling uses the existing separate clock; notice timing is independently configured. No global notice default is invented: missing required notice input means no automated notice/action requiring it, with an explicit configuration reason. Manual drafting remains available. Evergreen is header term model Evergreen with null end date, distinct from perpetual lines; finite-date scans skip it.

Choose versioned CAM settings with a separate append-only settings-change audit. Keep the single-line zero-uplift 12-month auto-generated draft boundary; widening it needs delivered pricing contracts. No unattended path reaches EXECUTED in this slice: it stops for the existing explicit signature/approval flow. Automated legal-signature execution is deliberately excluded. No listener, relay, ramp or Jev flag is enabled by these choices.

## Catalog and policy

Adopt the catalog relationship/eligibility defaults and Cluster A mapping/dispute gates, including the immediate pre-execution check serialized with dispute changes. Product-service owns one versioned relationship/eligibility query returning DTOs and version pins. Select a read-only versioned HTTP contract with tenant-scoped lookup by source/target SKU, relationship type and effective date; return matching relationship/eligibility versions and explicit missing/contested reasons, never a price or a mutable entity. Pin the result at pricing; revalidate the dispute gate at submit, execute and apply. A shared database read is excluded. No new catalog event is selected. Catalog schema/DTO completion and authorization tests remain delivery work.

Adopt `RetainContracted` as the amendment default, `NoCredit`, `NoFee`, line overrides snapshotted at pricing, and the existing accepted proration formula. Legal-review classification/gates are a later increment; until its named legal permission is delivered, the override path refuses. Sales cannot override. A missing policy, conversion multiple, maintenance percentage, mapping version or required acceptance date is a refused/unresolved **request input**, never an invented 2.5 multiple or 20 percent. This closes the design choice by requiring configuration rather than asking the owner for a universal commercial value. The existing seeded 22%-of-list maintenance rule remains the legacy path until an explicitly versioned replacement is implemented and validated; no applied rule or history is rewritten.

## One-time charges and conversion

Choose DRG-572 recommendations for a separate optional pair `servicePeriodStart/servicePeriodEnd`, both absent or both present and end not before start. Allow a service period starting before effective date only for ONE_TIME; other charge types retain their effective-date validation. Optional per-line effectiveDate falls back to contractEffectiveDate; trigger dates remain on the header. Use ADD + ONE_TIME for a non-commitment charge. These are later-scope features and do not widen the AMEND/ADD-UPDATE-REMOVE first-slice commercial scope. Billing exposes explicit service period, effective date, amount/currency and immutable decision identity as a field contract; no receiver or outbound billing operation is selected. New negative credit-line behavior is excluded; credits retain their separately versioned policy.

Adopt SpendParity with an explicitly configured multiple, version-pinned quantity map, defined unit rounding and empty default credit rows, no default price lock or parity ramp. For maintenance choose configured LicenceNet percentage and recalc against linked perpetual quantity, with the accepted precedence order. Existing migration-credit code is not duplicated or silently summed. Surrender is a business disposition encoded by existing TERMINATE line action plus its entitlement-end metadata, not a new action token contrary to Cluster E. One Restructure and a successor agreement with priorAgreementId remains the design. Separate commercial application units follow create-before-terminate; operational revocation additionally requires the fulfilment evidence below. Metric-only conversion remains its own intent/action boundary, and first-slice conversion remains excluded. Conversion pricing execution still requires delivered and verified pricing/audit contracts; no older design is accepted wholesale.

## Pricing and ARR

Store versioned renewal policy bodies in CAM policy records keyed by tenant plus account/agreement/document line, instead of enriching the thin header or placing contract policy in CRM. Quote-service passes the five unmerged rungs to pricing-service, the sole merger. Select `POST /api/v1/pricing/renewals/resolve` as the versioned, idempotent, read-only/no-persistence resolver contract described in DRG-549. Same inputs produce the same semantic `data`; envelope timestamps/trace can differ. Select SAME plus UPLIFT_PCT for the renewal pricing slice; SAME carries the prior accepted price. LIST and multi-year expansion remain excluded until their separate delivered contracts exist.

Before commercial use, pricing-service durably confirms one immutable versioned commercial-decision snapshot per priced continuation line (policy/mode/version, all inputs and schedule revision, output/explanations/actor and digest) in a pricing-owned append-only store. Choose a distinct idempotent confirmation command in pricing-service; the pure resolver remains read-only. Confirmation returns a decision id and immutable reference/digest. Quote-service retains those identities and CAM priced-write validates them. Pure preview output cannot be presented as a confirmed commercial price. Decision-store DTO/API completion, audit retention and crash/replay tests are delivery prerequisites; this adoption replaces the overlapping ownership/handoff question without accepting all DRG-564/612 details.

Pricing-service computes ARR bridge values for the current revision and returns them with the audited pricing decision. Quote-service/CAM store the result and never recompute it. Base and deltas are recurring annualized money in the same currency; never annualize one-time licence charges or include one-time credits/fees. For comparable annualized per-unit prices p0/p1 and common-unit quantities q0/q1, choose **price then quantity**: `priceEffect=(p1-p0)*q0`, `qtyEffect=p1*(q1-q0)`. Their sum is exactly `p1*q1-p0*q0` before final currency rounding. Normalize replacement quantities using the pinned mapping before this split. Missing mapping, currency comparability or annualization evidence leaves the bridge null with an explicit reason, never guessed zero. Round final components half-up at currency minor-unit precision and assign any rounding residual to qtyEffect so the rounded bridge reconciles to rounded closing ARR.

Replace continues the relationship, with zero churn/crossSell and mapped price/quantity effects. NonRenew is a renewal disposition, not REMOVE; its lost recurring ARR is negative churn with no credit/fee from the disposition. A conversion uses a group accounting anchor on the arriving SaaS line: base is prior recurring ARR (for example maintenance, excluding a perpetual one-time licence), conversion is new recurring ARR minus that base, and source lines contribute zero bridge base/deltas to avoid double counting. Source commercial lines/amounts remain intact. New recurring product ARR is crossSell; quantity movement on a held SKU is qtyEffect. Currency mixes do not aggregate without a separate FX contract. A manual override invalidates the current bridge until pricing-service recomputes; the frozen previous bridge is immutable. Unpriced/uncomputed bridge is null. Record LIST_DISCOUNT_RESET in immutable pricingWarnings/explanations when list repricing removes an old discount. No business category or document arr_delta definition is overloaded.

## Fulfilment

Choose a CAM-owned durable per-unit request recorded only after successful commercial unit application, atomically with that unit's completion and required audit/outbox. A separate asynchronous worker uses idempotent HTTP commands to an adapter; it never dispatches before commit or calls externally under a CAM database lock. Natural command identity is tenant/document/unit/operation plus immutable payload digest. Same identity/content replays; conflicting content refuses. Record attempt status, retry schedule and correlated evidence; worker leases and restart-safe retries cannot create a second grant/revoke. Adapter operation receipts and evidence must survive lost acknowledgements; unknown outcomes reconcile before another destructive operation. No order-service scaffold, public bus schema or ADR-026 command topic is authorized or accepted by this narrow decision.

Commitment projections are PendingActive, Active, Suspended, InGrace, PendingTerminate, Terminated, Expired, Superseded and Surrendered. CAM alone writes them from correlated, authenticated, version-bound adapter evidence. Request intent is separate from confirmed state. Provisioning starts PendingActive; confirmed success makes Active. Suspension/resumption and termination/revocation require corresponding evidence; a request alone is insufficient. Surrendered is terminal after confirmed licence revocation. Commercial rights end at entitlementEndDate; operational PendingTerminate/failed revocation never extends those rights. Grace is disabled unless a policy supplies a finite grace-end date; otherwise finite rights expire at term end. Superseded requires a linked confirmed successor and is distinct from OQ6 revision closure. These projections do not rename existing SaaS/ERP status columns.

Create a scheduled REVOKE request with notBefore=entitlementEndDate and a confirmed replacement dependency. Dispatch only when both conditions hold. If replacement evidence is absent or revocation outcome is unknown, retain the visible blocked/pending operation and reconcile; never infer success or silently extend rights. REVOKE and REGENERATE remain non-compensatable; post-commit remediation uses a correcting LifecycleChange, not a revised executed document. An unmapped uncommitted unit is BLOCKED, with APPLY_BLOCKED/PARTIALLY_APPLIED determined by OQ5 and committed units preserved. Adapter endpoint schemas, authenticated evidence and failure/reconciliation tests are required delivery artifacts before this path can run.

## Migration and access

Adopt explicit audited contract ownership, existing tenant boundaries, read-only sales-ops/finance access under delivered permissions, owner-only assignment/due-date writes and shared-spine queue filters. No new Entra role or approval/execute permission is silently granted. An optional due date has no invented SLA. Require delivered ownership checks before a mutating S3 endpoint is released.

Adopt one thin contract per existing subscription, header-only migration and no synthetic history/regrouping. The initial manifest window is finite subscription end dates from execution date through plus 90 days, inclusive, ordered by end_date/id. Each manifest records ids, expected versions/counts and observed dry-run results; expansion and execution retain existing operational approval gates. The existing unfiltered V1_8 function is **not** a manifest selector: never use it to claim subset migration. A separately delivered selector/guarded wrapper with exact-manifest validation, idempotency and rollback evidence is required before a phased run. No backfill runs here. Missing data/test evidence is a delivery gap, not a remaining policy choice.

## Evidence and next delivery steps

Primary evidence is the pinned GitHub documentation and Accepted invariants, read for this decision; the RFC reference defines JCS, not our domain projection. No source implementation, database, endpoint, UI or production operation was exercised by this record. Live Jev is unavailable: no project OpenRouter credential is configured, and no Jev judgment or probability is claimed. GitNexus list/impact found no index for the docs snapshot; graph impact is UNKNOWN, not a clean result. Text review is documentation evidence only.

Next delivery order: (1) complete the documentation for exact commercial-envelope/command/receipt DTOs and golden fixtures against these choices; (2) obtain the existing separate implementation release for each bounded story; (3) implement and prove CAM storage/concurrency/input validation, followed by pricing audit and durable quote-to-CAM reconciliation; (4) execute each story's required tests and record the observed evidence. Policy choice adoption is complete for the indexed forks; runtime contract/schema completion, missing commercial input values, integration evidence and implementation authorization are not claimed complete.

## Fork disposition index

Each row has one disposition. “Adopt baseline default” selects the substantive Default-column choice at the pinned baseline; if it deliberately excludes a feature or demands configuration, that exclusion/input rule is selected. Explicit replacements link to the governing contract above. Original frames remain available as historical alternatives and rationale.

### A: cluster-a-amendment-modes-and-policy-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-a-amendment-modes-and-policy-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| 592-F1 Flag shape | Adopt baseline default. |
| 592-F2 Who may change the flag | Adopt baseline default. |
| 592-F3 Overlap for pricing and visibility | Adopt baseline default. |
| 592-F4 Original at its own end date | Adopt baseline default. |
| 592-F5 Where the mode is stored | Adopt baseline default. |
| 592-F6 Which contract owns the document | Adopt baseline default. |
| 554-F1 Co-term mode set | Adopt baseline default. |
| 554-F2 Missing policy | Adopt baseline default. |
| 554-F3 Which lines move | Adopt baseline default. |
| 554-F4 Shortening | Adopt baseline default. |
| 554-F5 Lengthening past the governing end | Adopt baseline default. |
| 554-F6 Merge versus co-term, OQ7 regrouping | Adopt baseline default. |
| 554-F7 Source after merge | Adopt baseline default. |
| 554-F8 Home versus DRG-461 | Adopt baseline default. |
| 594-F1 Seats versus Add and Remove | Adopt baseline default. |
| 594-F2 Where the gate sits | Adopt baseline default. |
| 594-F3 Override | Adopt baseline default. |
| 594-F4 Mixed edits | Adopt baseline default. |
| 594-F5 First-slice timing | Adopt baseline default. |
| 599-F1 Where the mapping lives | Adopt baseline default. |
| 599-F2 Dispute status and resolver | Adopt baseline default. |
| 599-F3 Where the hold bites | Adopt baseline default. |
| 599-F4 Which lines need a mapping | Adopt baseline default. |
| 599-F5 Pin versus live row | Adopt baseline default. |
| 550-F1 Price mode | Adopt baseline default. |
| 550-F2 Credit policy | Adopt baseline default. |
| 550-F3 Termination fee | Adopt baseline default. |
| 550-F4 Where the three policies are stored | Adopt baseline default. |

### B: cluster-b-onprem-to-saas-and-maintenance-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-b-onprem-to-saas-and-maintenance-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| DRG-573 / Price basis | Adopt baseline default. |
| DRG-573 / What rounding holds fixed | Adopt baseline default. |
| DRG-573 / Credit rows | Adopt baseline default. |
| DRG-573 / Application target | Adopt baseline default. |
| DRG-573 / Where the quantity map lives | Adopt baseline default. |
| DRG-573 / Map rounding and minimum | Adopt baseline default. |
| DRG-573 / Credit transfer and expiry | Adopt baseline default. |
| DRG-573 / Price-lock length | Adopt baseline default. |
| DRG-573 / `ConvertMetric` beside this policy | Adopt baseline default. |
| DRG-573 / Parity ramp | Adopt baseline default. |
| DRG-574 / Action on the perpetual line | Surrender is a business disposition on a TERMINATE line; no SURRENDER action token. See [contract](#one-time-charges-and-conversion). |
| DRG-574 / Commitment status | Surrendered is a terminal commitment projection only after confirmed revocation evidence. See [contract](#fulfilment). |
| DRG-574 / Entitlement end | Adopt baseline default. |
| DRG-574 / Dual-use length | Adopt baseline default. |
| DRG-574 / When revocation is requested | Adopt baseline default. |
| DRG-574 / Status during the window | Commercial rights end at entitlementEndDate; operational state waits for confirmed revocation. See [contract](#fulfilment). |
| DRG-556 / Where the licence model lives | Adopt baseline default. |
| DRG-556 / How maintenance finds its licence | Adopt baseline default. |
| DRG-556 / What a renewal may do | Adopt baseline default. |
| DRG-556 / Quantity match | Adopt baseline default. |
| DRG-556 / Version-date refresh | Version-date refresh is excluded from commercial renewal; later fulfilment request may regenerate without repricing. See [contract](#one-time-charges-and-conversion). |
| DRG-559 / How many commercial changes | Adopt baseline default. |
| DRG-559 / Agreement shape | Adopt baseline default. |
| DRG-559 / Lines inside the change | Adopt baseline default. |
| DRG-559 / Which credit policy | Adopt baseline default. |
| DRG-559 / Apply shape | Adopt baseline default. |
| DRG-571 / Basis | Adopt baseline default. |
| DRG-571 / Recalc when the licence quantity or net changes | Adopt baseline default. |
| DRG-571 / Uplift field | Adopt baseline default. |
| DRG-571 / Precedence for the maintenance fields | Adopt baseline default. |
| DRG-571 / Reinstatement | Adopt baseline default. |
| DRG-571 / Quantity used when recalc is Yes | Adopt baseline default. |

### C: cluster-c-trueup-and-meters-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-c-trueup-and-meters-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| F1 Aggregation | Adopt baseline default. |
| F2 Tolerance | Adopt baseline default. |
| F3 Pack rounding | Adopt baseline default. |
| F4 True-down | Adopt baseline default. |
| F5 Overage rate | Adopt baseline default. |
| F6 Arrears | Adopt baseline default. |
| F7 Rounding | Adopt baseline default. |
| F8 Rung order | Adopt baseline default. |
| F9 Dry-run | Adopt baseline default. |
| F10 Policy storage | Adopt baseline default. |
| G1 Reading status | Adopt baseline default. |
| G2 Attestation | Adopt baseline default. |
| G3 Pricing input | Adopt baseline default. |
| G4 Freeze | Adopt baseline default. |
| G5 Correction | Adopt baseline default. |
| G6 Snapshot home | Adopt baseline default. |
| G7 Dispute scope | Adopt baseline default. |
| H1 Metric | Adopt baseline default. |
| H2 Intent | Adopt baseline default. |
| H3 Threshold | Adopt baseline default. |
| H4 Quantities | Adopt baseline default. |
| H5 Reading body | Adopt baseline default. |

### D: cluster-d-renewal-automation-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-d-renewal-automation-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| RN-A | Adopt baseline default. |
| RN-B | Retain the 30-day eligibility publication; add a separate daily CAM kickoff scan due at endDate minus leadTimeDays. See [contract](#renewal-automation). |
| RN-C | Adopt baseline default. |
| RN-D | Adopt baseline default. |
| QM-A | Adopt baseline default. |
| QM-B | Adopt baseline default. |
| QM-C | Adopt baseline default. |
| QM-D | Adopt baseline default. |
| AS-A | Adopt baseline default. |
| AS-B | Adopt baseline default. |
| AS-C | Versioned CAM settings row with a separate append-only settings audit; clause notice fields remain distinct. See [contract](#renewal-automation). |
| AS-D | Adopt baseline default. |

### E: cluster-e-lifecyclechange-model-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-e-lifecyclechange-model-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| E1 | Adopt baseline default. |
| E2 | Adopt baseline default. |
| E3 | Adopt baseline default. |
| E4 | Adopt baseline default. |
| E5 | Adopt baseline default. |
| E6 | Adopt baseline default. |
| E7 | Adopt baseline default. |
| L1 | Adopt baseline default. |
| L2 | Adopt baseline default. |
| L3 | Adopt baseline default. |
| L4 | Adopt baseline default. |
| L5 | Adopt baseline default. |
| L6 | Adopt baseline default. |
| L7 | Adopt baseline default. |
| D1 | Adopt baseline default. |
| D2 | Adopt baseline default. |
| D3 | Adopt baseline default. |
| D4 | Adopt baseline default. |
| D5 | Adopt baseline default. |
| D6 | Adopt baseline default. |

### F: cluster-f-fulfilment-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-f-fulfilment-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| C1 What this frame ships | Adopt baseline default. |
| C2 Transport | CAM durable per-unit request plus audit/outbox; asynchronous worker delivers idempotent HTTP commands to the fulfilment adapter. See [contract](#fulfilment). |
| C3 Failure after commit | Adopt baseline default. |
| C4 Money | Adopt baseline default. |
| F1 `EXTEND` | Adopt baseline default. |
| F2 Quantity up and down | Adopt baseline default. |
| F3 Downgrade | Adopt baseline default. |
| F4 Decomposition key | Adopt baseline default. |
| F5 Compensatability | Adopt baseline default. |
| F6 Unmapped on-prem line | Adopt baseline default. |
| F7 Evidence | Adopt baseline default. |
| F8 Revocation outside conversions | One REVOKE mechanism, shared by conversions and other accepted revocations. See [contract](#fulfilment). |
| F9 When `REVOKE` is submitted | Record a scheduled revocation request after committed commercial apply; dispatch no earlier than entitlementEndDate and only after replacement evidence. See [contract](#fulfilment). |
| S1 Commitment states | Adopt the eight candidate commitment states plus Surrendered as separate projections, with evidence-based transitions. See [contract](#fulfilment). |
| S2 Fulfilment-required signal | Per-unit durable request and correlated evidence callback; no new public bus event. See [contract](#fulfilment). |
| S3 Writer, if a status is later confirmed | CAM is the single commitment-projection writer; adapter supplies evidence only. See [contract](#fulfilment). |
| S4 SaaS subscription row | Adopt baseline default. |
| S5 `InGrace` and `Expired` | Grace disabled by default; require an explicit policy grace end to enter InGrace; otherwise expire at term end. See [contract](#fulfilment). |
| S6 `Superseded` versus OQ6 | Superseded commitment links to a confirmed successor; distinct from document revision SUPERSEDED. See [contract](#fulfilment). |
| S7 `PendingActive` and `Active` | PendingActive until successful evidence; Active only after confirmation. See [contract](#fulfilment). |

### G: cluster-g-visibility-queues-migration-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/cluster-g-visibility-queues-migration-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| F-595-1 | Adopt baseline default. |
| F-595-2 | Adopt baseline default. |
| F-595-3 | Adopt baseline default. |
| F-595-4 | Adopt baseline default. |
| F-595-5 | Adopt baseline default. |
| F-595-6 | Adopt baseline default. |
| F-595-7 | Adopt baseline default. |
| F-597-1 | Adopt baseline default. |
| F-597-2 | Adopt baseline default. |
| F-597-3 | Adopt baseline default. |
| F-597-4 | Adopt baseline default. |
| F-597-5 | Adopt baseline default. |
| F-598-1 | Adopt baseline default. |
| F-598-2 | Initial manifest selects end dates from execution date through execution date plus 90 days, inclusive; no run authorized. See [contract](#migration-and-access). |
| F-598-3 | Adopt baseline default. |
| F-598-4 | Adopt baseline default. |
| F-598-5 | Adopt baseline default. |
| F-598-6 | Adopt baseline default. |
| F-598-7 | Adopt baseline default. |

### 549: drg-549-renewal-pricing-resolution-design-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/drg-549-renewal-pricing-resolution-design-frame.md).

| Fork | Adopted disposition |
|---|---|
| Where account, agreement, and line renewal policy inputs live | Versioned CAM policy records keyed by tenant and account/agreement/line; keep the thin header thin. See [contract](#pricing-and-arr). |
| How quote-service reaches the merge | Choose the versioned read-only HTTP resolve operation; audit confirmation required before commercial use. See [contract](#pricing-and-arr). |
| `LIST` in the first slice | Adopt baseline default. |
| First-slice modes | Adopt baseline default. |
| `SAME` semantics | Adopt baseline default. |
| `UPLIFT_AMT` semantics | Adopt baseline default. |
| `OVERRIDE` semantics | Adopt baseline default. |
| When `RENEGOTIATE` is allowed | Adopt baseline default. |
| Later-year expansion (DRG-568 leak guard) | Adopt baseline default. |
| Where the later orchestration lives | Adopt baseline default. |
| List price at as-of | Adopt baseline default. |
| Later code-slice file allowlist | Adopt baseline default. |

### S3: s3-reconciliation-and-owner-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/s3-reconciliation-and-owner-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| 1 Unpriced storage | Nullable pins, both content digests and all four current totals; next free V1_10+ migration; guarded rollback. See [contract](#storage-and-digests). |
| 2 OQ18 or interim digest | Confirm OQ18: one shared canonical digest library in backend/libs/common, delivered with S3; no interim fork. See [contract](#storage-and-digests). |
| 3 S3 authorization | Adopt baseline default. |
| 4 Order versus #653 | DRG-616 is already merged; use this main baseline and the decided contracts. See [contract](#authority-and-scope). |
| 5 `VOID` and `OUT_FOR_SIGNATURE` | VOID is terminal and not reachable through first-slice mutation; OUT_FOR_SIGNATURE remains unsupported in the slice. See [contract](#cam-transactions-and-delivery). |

### 572: story-drg-572-one-time-retroactive-charge-lines-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/story-drg-572-one-time-retroactive-charge-lines-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| 1. `chargeType` | Adopt baseline default. |
| 2. Where the window is stored | Choose recommendation A: distinct servicePeriodStart/servicePeriodEnd, both absent or both present. See [contract](#one-time-charges-and-conversion). |
| 3. Early `ONE_TIME` window | Choose recommendation A: service window may start before the effective date for ONE_TIME only. See [contract](#one-time-charges-and-conversion). |
| 4. Per-line `effectiveDate` | Choose recommendation A: optional per-line effectiveDate, falling back to contractEffectiveDate. See [contract](#one-time-charges-and-conversion). |
| 5. Non-commitment charge | Choose recommendation A: ADD plus ONE_TIME; no new action code. See [contract](#one-time-charges-and-conversion). |
| 6. Billing | Choose recommendation A: a billing field contract only; outbound billing integration is excluded until a receiver contract exists. See [contract](#one-time-charges-and-conversion). |
| 7. Credit sign | Adopt baseline default. |

### 575: story-drg-575-arr-bridge-per-change-line-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/story-drg-575-arr-bridge-per-change-line-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| D1 | Adopt baseline default. |
| D2 | Adopt baseline default. |
| H1 | Adopt baseline default. |
| D3 | Adopt baseline default. |
| R1 | Choose C: mapped price/quantity split for comparable successor units; missing map leaves bridge null with an explicit reason. See [contract](#pricing-and-arr). |
| D4 | Adopt baseline default. |
| C1 | Adopt baseline default. |
| C2 | Choose D: signed recurring ARR delta against prior recurring ARR; exclude one-time amounts and credits. See [contract](#pricing-and-arr). |
| F1 | Adopt baseline default. |
| F2 | Adopt baseline default. |
| F3 | Adopt baseline default. |
| F4 | Choose price then quantity, with the cross term assigned to quantity. See [contract](#pricing-and-arr). |
| F5 | Adopt baseline default. |
| F6 | Persist LIST_DISCOUNT_RESET in pricingWarnings/explanations when list repricing removes a prior discount. See [contract](#pricing-and-arr). |
| F7 | Adopt baseline default. |
| T1 | Pricing-service computes the bridge and returns its version-bound, audited result; no quote-side recomputation. See [contract](#pricing-and-arr). |
| T2 | Reprice replaces the current revision bridge; manual override invalidates it until pricing-service recomputes. See [contract](#pricing-and-arr). |
| T3 | Adopt baseline default. |

### 576: story-drg-576-product-relationship-and-eligibility-decision-frame.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/story-drg-576-product-relationship-and-eligibility-decision-frame.md).

| Fork | Adopted disposition |
|---|---|
| 576-F1 Relationship type set | Adopt baseline default. |
| 576-F2 `MAINTENANCE_FOR` | Adopt baseline default. |
| 576-F3 `SUCCESSOR` | Adopt baseline default. |
| 576-F4 `ADD_ON_OF` and first-slice Add | Adopt baseline default. |
| 576-F5 How many targets | Adopt baseline default. |
| 576-F6 Endpoint status | Adopt baseline default. |
| 576-F7 Where the flags live | Adopt baseline default. |
| 576-F8 Missing flags | Adopt baseline default. |
| 576-F9 Which lines read the flags | Adopt baseline default. |
| 576-F10 What the line pins | Adopt baseline default. |
| 576-F11 Read API | Choose a versioned read-only product-service relationship/eligibility query; no entity or shared-database read. See [contract](#catalog-and-policy). |
| 576-F12 Events | Adopt baseline default. |
| 576-F13 `quantityMapping` and `creditPolicyRef` | Adopt baseline default. |

### 616: story-drg-616-oq6-design-contract-gaps.md

[Baseline decision table](https://github.com/drgaciw/open-crm-cpq/blob/285ffef8e08274600090d8ba077a0c92f489c062/docs/renewals-amendments/prereq/story-drg-616-oq6-design-contract-gaps.md).

| Fork | Adopted disposition |
|---|---|
| 1 Where the CAM write lives | Adopt baseline default. |
| 2 Expected-version property | Adopt baseline default. |
| 3 Line payload | Adopt baseline default. |
| 4 Unit number on each new line | Adopt baseline default. |
| 5 Idempotency and replay | Adopt baseline default. |
| 6 `targetQuoteId` and `inputsSnapshotRef` | Adopt baseline default. |
| 7 Terminal 409 code | Adopt baseline default. |
| 8 Stale version when revision races execute | Adopt baseline default. |
| 9 Cleared pins | Adopt baseline default. |
| 10 Digest of the open revision | Open unpriced digest null; compute a genuine digest on every close, including unpriced supersession. See [contract](#storage-and-digests). |
| 11 Success supersession fact | Adopt baseline default. |
| 12 Refusal audit type | Local AMENDMENT_REVISION_REJECTED, preserving the S2 audit/outbox obligation. See [contract](#cam-transactions-and-delivery). |
| 13 Pin migration, only if default 9 is later accepted | Adopt baseline default. |
| 14 OQ18 digest library | OQ18 confirmed: shared library with versioned envelope and RFC 8785 canonicalization. See [contract](#storage-and-digests). |
| 15 S3 / DRG-581 | Adopt baseline default. |
| 16 Unpriced current totals | Adopt baseline default. |
| 17 CAM priced-state synchronization | Version-bound synchronous CAM pricing command, durable source operation, replay and reconciliation. See [contract](#cam-transactions-and-delivery). |

