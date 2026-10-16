# OQ7 — Contract header backfill: scope and grouping

*open-crm-cpq (drgaciw/open-crm-cpq). Owner-decision document only. Date: 2026-10-16. Base of this note: `main` at `dd0f1a8`.*

**This file authorizes no code, schema migration, job, data rewrite, feature flag, topic, or gateway route.** It asks the project owner to confirm a default. [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585/s7-contract-header-backfill-for-existing-subscriptions-no-synthetic) (S7, RA-51) is the story that would build the backfill after that confirmation. ADR-035, Accepted 2026-10-16, authorizes the first-slice schema and audit stories only (S1, S2; DRG-579, DRG-580). S7 is outside that acceptance.

Marks used below:

| Mark | Meaning |
|---|---|
| `read` | The sentence cites a file, SQL script, or Linear issue opened for this note. |
| `unverified` | Not opened, not executed, or a combination of texts that was not run against a database. |

**Integration update (#622).** S1's CAM V1_7 and its V1_7_1/V1_7_2 maintenance migrations reach main through #622. V1_8 remains reserved for the unimplemented header backfill. The inventory and source readings below describe `dd0f1a8`; this note neither executes a backfill nor decides OQ7.

## Decision requested

OQ7, as framed for this confirmation, is two questions:

1. **Scope.** Does the first backfill create thin contract headers only, or does it also synthesize amendment history?
2. **Grouping.** How are existing subscriptions attached to those headers?

The open-questions register (`renewals-amendments-open-questions-register.md`, `read`) states OQ7 as "Backfill scope", status **DEFAULT PROPOSED for slice**, proposed default "Slice: contract header only (S7), no synthetic history. History backfill stays DRG-566." Owner: Product. Needed by: before DRG-566. The Blocks cell is "S7 history only."

ADR-035 (`adr-035-amendment-document-placement.md`, `read`) lists OQ7 under open questions out of scope: "Backfill strategy for pre-spine acceptances." The acceptance addendum does not decide it. The acceptance checklist (`adr-035-acceptance-checklist.md`, `read`) places OQ7 after ADR acceptance and before DRG-566, with the slice reading "header only."

Spike §6.3 (`amendment-document-design-spike.md`, `read`) still says grouping rules are an open question: one Contract per existing subscription, or group by customer plus original quote. DRG-566 (`read`) repeats that pair as the suggested next step. The register's scope sentence does not itself choose the grouping. This note asks the owner to confirm both together.

## Proposed default

**One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.**

Texts that already state that default (`read`):

- FR-20 in `amendment-first-slice-requirements-and-acceptance.md`: "Contract header backfill creates one contract per existing subscription, with no synthetic amendment history and no retroactive events." The requirements file marks FR-20 `[I]`.
- Technical approach §3 item 2: "V1_8 backfill (S7): one contract per subscription, set `contract_id`. Idempotent. No documents, no events."
- Risk R11: grouping assumption "one contract per subscription" is Medium / Medium, mitigated by "Header only, re-groupable via later merge intent DRG-554."
- AC-S7-1 through AC-S7-4, and the DRG-585 description (`read`): one contract per existing subscription, `subscriptions.contract_id` set, idempotent, no AmendmentDocument rows, no outbox events. DRG-585 lists DRG-554 as "later merge."

DRG-554 (`read` on Linear) is titled "Co-termination policy and agreement merge." Its suggested next step is a `CoTermPolicy` of `{LatestEnd|EarliestEnd|None|AlignToAgreement}` and `mergedFromIds` on the agreement. The co-termination proposal on `main` (`prereq/specs/co-termination.md`, `read`) is a proposal only. It says a merge writes `mergedFromIds` on the survivor and does not delete the source agreement rows. It lists contract-header backfill (DRG-585) as out of that slice, and lists DRG-585 as a dependency "for agreements that do not yet have an end date to align to." Its open question 6 leaves merge versus co-term unset. None of those texts specify an update that moves `subscriptions.contract_id` from one contract to another. That move is `unverified`.

## Alternatives

1. **Group by customer plus original quote.** Spike §6.3 and the DRG-566 suggested next step (`read`). "Original quote" is not defined further in either text. `subscriptions.quote_id` exists (`V1_1__create_contract_tables.sql`, `VARCHAR(255) NOT NULL`, `read`). Whether that column is the "original quote" is `unverified`.
2. **Synthesize history in the same change.** Spike §6.3 steps 2–5 (`read`): an ORIGINAL document (seq 0) per subscription from `subscriptions.quote_id`; a BACKFILLED document per `applied_quote_acceptances` row whose `operation` is not NEW, status APPLIED, `signature_policy = NOT_REQUIRED`, evidence `LEGACY`, no signed copy; link rate periods; emit no retroactive events, mirroring the V1_6 comment. The spike addendum (2026-10-16, `read`) says decision 7 reduced the first slice to header only and left history on DRG-566.
3. **Wait for DRG-458 before any header.** Spike §6.4 (`read`) names the risk that a later Contract definition in DRG-458 would force a header migration, and mitigates it by keeping the header thin. OQ3 is already confirmed in ADR-035 (`read`): thin header now (id, number, tenant, customer reference, start, end, status, `last_executed_seq`, `prior_contract_id`); richer fields stay on DRG-458. Waiting is the risk the spike recorded, and it is outside the proposed default.

## Consequences

### A backfill writes existing rows

- `V1_7__contract_documents.sql` was read on branch `cursor/s1-amendment-document-schema-5fd4` at `195149fd`. It is not on `main` at `dd0f1a8` (CAM migrations on `main` stop at `V1_6__subscription_rate_schedule.sql`). The V1_7 header says expand-only: new tables plus nullable columns, no backfill (`read`).
- The V1_8 shape in the technical approach inserts `contracts` rows and sets `subscriptions.contract_id` on existing subscription rows (`read`). That is a write to existing `subscriptions` rows. The same section says "no data rewrite of existing rate periods" (`read`).
- AC-S7-2 (`read`): the backfill creates no AmendmentDocument rows and no outbox events.
- V1_6 on `main` (`read`) already inserted `LEGACY_AGGREGATE` rate periods from non-deleted subscriptions and comments "Backfilled periods never emit retroactive RampApplied." That insert is a prior migration. It is not V1_8.
- No `V1_8` SQL file was found on `main` or on the S1 branch. The write described above is the documented shape. It was not executed. `unverified` as a script.
- `subscriptions.customer_id` is `VARCHAR(255) NOT NULL` (V1_1, `read`). `contracts.customer_account_id` is `uuid NOT NULL` (V1_7, `read`). The S1 companion `s1-v1-7-contract-documents.md` on the S1 branch (`read`) flags that pair for DRG-585. A direct copy of the varchar into the uuid column is not a valid cast. No mapping from varchar customer id to uuid was opened. `unverified` that a mapping exists.
- `subscriptions.status` allows PENDING, ACTIVE, SUSPENDED, CANCELLED, EXPIRED (V1_1 check, `read`). `contracts.status` allows ACTIVE, EXPIRED, TERMINATED (V1_7 check, `read`). No text opened for this note maps one set onto the other, or says which subscription dates fill `contracts.start_date` and `end_date`, or how `term_model` is chosen. `unverified`.

### V1_8 idempotent shape

Documented, and not present as SQL (`read` of the file lists above):

- Runs after the V1_7 expand.
- One contract per subscription; set `contract_id`.
- Idempotent. AC-S7-1: re-running creates no duplicates.
- No documents and no events.
- AC-S7-4: contract numbers are unique per tenant and stable.
- V1_7 constraint `uq_contracts_tenant_number UNIQUE (tenant_id, contract_number)` is the uniqueness those numbers have to satisfy (`read`).

The key a second run would use to find the contract it already created (subscription id, subscription number, or another value) is not named in the technical approach, the requirements, DRG-585, or V1_7. `unverified`.

### Rollback

- Technical approach §3 (`read`): "rollback is app-level via the flag, not down-migrations." In that file the flag is the per-tenant legacy versus document apply path (S6, OQ11). The sentence does not say the flag clears `contract_id`. Treating the flag as an undo of header rows is `unverified`.
- The S1 companion (`read`) says Flyway has no down migration. Its drop script is for use while the new tables are empty (S1 has no writer). If any document row exists, it says restore from backup. A completed header backfill would leave `contracts` non-empty and `contract_id` set, so that empty-table drop would no longer match the data. No V1_8 rollback script was found. A safe undo other than restore from backup is `unverified`.
- `subscriptions.contract_id` is nullable in V1_7 (`read`). AC-S1-2 says the previous application boots after V1_7 (`read` of the acceptance criterion). Behavior of the current application after `contract_id` is populated was not executed. `unverified`.

### Uniqueness

- `contracts`: `UNIQUE (tenant_id, contract_number)` (V1_7, `read`).
- `subscriptions.contract_id`: nullable foreign key to `contracts(id)`, index `idx_subscriptions_contract_id`, not a unique index (V1_7, `read`). Field schema §3 (`read`): ContractHeader 1 : 1..* subscriptions. FR-01 (`read`): every subscription in scope resolves to exactly one contract. The column holds one contract id per subscription. It does not require one subscription per contract.
- AC-S7-1 (`read`) is the population rule: N subscriptions yield N contracts. That 1:1 count is the backfill rule. The schema already allows several subscriptions to reference one contract without a uniqueness change (`read`).
- `amendment_documents`: `UNIQUE (contract_id, amendment_seq)` (V1_7 constraint `uq_amendment_document_seq`, `read`). Field schema: ORIGINAL is seq 0 (`read`). Spike §6.3 step 2 synthesizes an ORIGINAL (seq 0) per subscription (`read`). Two subscriptions that share a contract cannot both receive seq 0 on that contract under that unique constraint. The proposed default creates no documents (AC-S7-2) and one contract per subscription, so S7 does not insert those rows. The collision appears if DRG-566 later synthesizes a per-subscription ORIGINAL onto a shared contract. That insert was not executed. `unverified` as a database result.
- `amendment_seq` is nullable until EXECUTED (field schema, `read`). How PostgreSQL treats multiple NULL `amendment_seq` values under that unique constraint was not tested. `unverified`.

### DRG-546 deal type and DRG-566 history

Header backfill, as specified, does not classify history (`read` of AC-S7-2 and technical approach §3). Deal type still matters for the history story that follows.

- `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` (`QuoteContinuationRequest.java`, `read`).
- `QuoteContinuationService.dealType` maps `RENEWAL` and `EXTENSION` both to `DealType.RENEWAL`, and stores the request on `continuationRequestJson` (current `main`, lines 82–85 and 301–303, `read`).
- Quote `DealType` is `NEW`, `AMENDMENT`, `RENEWAL`, `EXPANSION` (`DealType.java`, `read`). There is no `EXTENSION` constant.
- `AcceptedQuoteSubscriptionService` sets `applied_quote_acceptances.operation` from the accepted event's deal type and allows `NEW`, `AMENDMENT`, `EXPANSION`, `RENEWAL` (lines 51–53, `read`). `EXTENSION` is not in that allow-list. V1_6 defines `operation VARCHAR(20) NOT NULL` and `accepted_quote_id` unique, with one `subscription_id` (`read`).
- DRG-546 (`read`) states the same collapse: EXTENSION and RENEWAL both store `DealType.RENEWAL`, and the original operation survives in `continuation_request_json`. Column `continuation_request_json` was added in quote-service `V1_10__quote_subscription_continuation.sql` (`read` of the spike citation and of the service write above). The JSON field list inside a stored payload was not re-printed for this note. `unverified` as a sample row.
- `docs/renewals-amendments/crm-quote-dealtype-mapping.md` on `main` (`read`) is a different map: CRM opportunity names (`NEW_BUSINESS`, `RENEWAL`, `UPSELL`, `CROSS_SELL`, `EXISTING_BUSINESS`) onto quote `DealType` at create (DRG-538). It says `AMENDMENT` is reached only by typed continuation. It does not mention `EXTENSION`. This note does not treat that CRM map as the DRG-546 collapse.
- DRG-566 (`read`) says existing history is `applied_quote_acceptances` rows with a non-NEW operation, plus rate periods, some with `LEGACY_AGGREGATE` provenance (V1_6). It asks for a synthesized ORIGINAL (seq 0) per subscription and a BACKFILLED document per historical non-NEW acceptance, with no retroactive events. It also asks the grouping question. If that later backfill classifies documents from quote `deal_type` rather than from `continuation_request_json`, EXTENSION and RENEWAL stay indistinguishable. Confirming the header default does not choose that classifier. Which column DRG-566 will read is `unverified`.

## What S7 (DRG-585) would build once the default is confirmed

Confirmation of this default is a product decision. It still does not authorize the build. ADR-035 does not authorize S7.

From the requirements file and DRG-585 (`read`), S7 would then be:

- Depends on S1 (DRG-579). DRG-585 also names OQ3 and OQ7. OQ3 is confirmed in ADR-035 (`read`). OQ7 is this decision.
- A migration or job named V1_8: create one contract per existing subscription, set `subscriptions.contract_id`, idempotent.
- AC-S7-1. N existing subscriptions yield N contracts, each subscription has `contract_id`, and a re-run creates no duplicates.
- AC-S7-2. No AmendmentDocument rows and no outbox events.
- AC-S7-3. A subscription created after cutover by the NEW path gets a contract header at creation.
- AC-S7-4. Contract numbers are unique per tenant and stable.
- Linear relations read on DRG-585: blocked by DRG-579; related to DRG-566, DRG-554, DRG-545, DRG-598, and DRG-535. DRG-598's body was not opened. `unverified`.

Gaps the opened texts leave for that later PR (`unverified` as designs, because no source named them):

- The idempotency match key.
- How varchar `customer_id` becomes uuid `customer_account_id`.
- How subscription status, dates, and `term_model` are filled.
- The pattern used to mint `contract_number` for a backfilled header. AC-S7-4 requires uniqueness per tenant and stability. The `{contract_number}-A{nnn}` format in the field schema is the amendment number, assigned at EXECUTED (`read`). It is not a header-minting rule.

## What a confirmation does

A later change can record the owner's choice. This file leaves the choice unconfirmed.

Confirming the default would let a future S7 PR implement the header-only, one-contract-per-subscription backfill described above. It would leave history synthesis on DRG-566 and leave regrouping on DRG-554.

It would not accept or amend ADR-035, authorize V1_7 or V1_8, start DRG-585, or edit the open-questions register. The register remains DEFAULT PROPOSED until a later docs change records the confirmation.

## Sources opened

On `main` at `dd0f1a8`:

- `docs/renewals-amendments/adr-035-amendment-document-placement.md`
- `docs/renewals-amendments/adr-035-acceptance-checklist.md`
- `docs/adr/README.md` (ADR-035 catalog row)
- `docs/renewals-amendments/renewals-amendments-open-questions-register.md`
- `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md`
- `docs/renewals-amendments/amendment-technical-approach-and-dependencies.md`
- `docs/renewals-amendments/amendment-entity-field-schema.md`
- `docs/renewals-amendments/amendment-document-design-spike.md` (§6.3, §6.4, §6.5, addendum)
- `docs/renewals-amendments/mid-term-amendment-policy-spec.md` (scope item 5 and the coterm addendum)
- `docs/renewals-amendments/prereq/specs/co-termination.md`
- `docs/renewals-amendments/crm-quote-dealtype-mapping.md`
- `backend/services/contract-asset-management/src/main/resources/db/migration/V1_1__create_contract_tables.sql`
- `backend/services/contract-asset-management/src/main/resources/db/migration/V1_6__subscription_rate_schedule.sql`
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java`
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationService.java` (lines cited above)
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/DealType.java`
- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionService.java` (lines 51–53)

On `cursor/s1-amendment-document-schema-5fd4` at `195149fd` (not on `main`):

- `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`
- `docs/renewals-amendments/s1-v1-7-contract-documents.md`

Linear, read 2026-10-16: DRG-585, DRG-554, DRG-546, DRG-566.

## Unverified

- Any `V1_8` SQL, its match key, and any execution of it.
- A varchar-to-uuid customer mapping, and the fill rules for contract status, dates, `term_model`, and `contract_number`.
- That DRG-554 updates `subscriptions.contract_id`.
- That `subscriptions.quote_id` is the spike's "original quote."
- DRG-598's body, and which column DRG-566 will use to classify EXTENSION versus RENEWAL.
- PostgreSQL NULL behavior for `amendment_seq`, and the seq-0 collision as a database result.
- A rollback of populated header rows other than restore from backup.
- Current-application behavior once `contract_id` is populated.
- Sample contents of a stored `continuation_request_json` value.
