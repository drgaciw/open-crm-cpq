# DRG-619 — Quote-accepted NEW-path contract header writer: Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-11.*

**Story:** [DRG-619](https://linear.app/drgamtd-workspace/issue/DRG-619/wire-quote-accepted-new-path-contract-header-writer-event-carries-no) (tracking item split from [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) AC-S7-3).

**Status:** Proposed Decision brief for CPQ Decision scoring. **Current product code is NO-GO.** This file inventories how a tenant could reach the NEW-path header writer as **Proposed OQ-619-***. It **authorizes no production Java or TypeScript change, no call to `ensureHeaderAtCreation` from `AcceptedQuoteSubscriptionService`, no schema change, no Flyway migration, no edit in place of landed `V1_8` or `V1_18`, no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S7 `contract_header_backfill_count` / `_apply` / `_compensate` run, no asset-write hold bypass, and no deploy.** Wiring AC-S7-3, treating the interim Entra `tid` as an authoritative contract tenant, or running the header backfill is **NO-GO** until the owner closes the forks below (or explicitly defers a named fork with words and a time). Every open fork is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**This brief does not claim that the S7 data backfill has been applied.** [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) stays **In Review**. Manual gates **M1–M8** are unmet. Merging this file does not satisfy them and does not mark DRG-585 or DRG-619 Done.

**Base:** `main` at `d3256ae4b4960f58a2c060883bc980c8f27c2be1` (2026-11-11 read).

**Linear status when read:** DRG-619 **In Progress** (started 2026-11-11; not Done). DRG-585 **In Review**. This brief does not mark either Done.

## Current NO-GO

Closing or merging this documentation PR does **not** lift the following.

| Hold | What stays forbidden |
|---|---|
| **AC-S7-3 production wiring** | No edit to `AcceptedQuoteSubscriptionService`, `QuoteAcceptedEventConsumer`, `ContractHeaderBackfill`, or `QuoteAcceptedEvent` that calls `ensureHeaderAtCreation` / `contract_header_at_creation` on the quote-accepted path. |
| **S7 apply / compensate (M1–M8)** | No bot or agent runs `contract_header_backfill_count`, `_apply`, or `_compensate`. A creation-path design is not an apply GO. M1–M8 stay owner-gated on DRG-585. |
| **Authoritative tenant** | No fabricated or default tenant. The interim Entra `tid` on the event is **not** scored here as `contracts.tenant_id`. |
| **ADR-036** | Remains **Proposed**, not Accepted. This brief does not accept it and does not add an account-tenant lookup. |
| **Asset-write / flags / Flyway** | No `opencrm.assets.legacy-acceptance-write-enabled`, no dual-read, no `opencrm.jev.*`, no ramp enable, no document-path tenant flip. No new migration and no in-place edit of landed V1_7 / V1_8 / V1_18. |

## Alignment (cite; do not reopen closed cells)

| Artifact | Relation to this brief |
|---|---|
| [DRG-619](https://linear.app/drgamtd-workspace/issue/DRG-619) issue body | **Tracking item only: no code authorized.** Gap named there: `contract_header_at_creation` / `ensureHeaderAtCreation` has no production caller; `AcceptedQuoteSubscriptionService` NEW never sets `contract_id`; tenant must be designed before any build. Related to DRG-585, which stays In Review until AC-S7-3 is wired **and** M1–M8 are met. **Cite. This file is the decision brief, not the build.** |
| [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) AC-S7-3 and M1–M8 | AC-S7-3: a subscription created after cutover gets a header at creation. M1–M8 are the owner-gated manual backfill sequence (quoted below). **None of M1–M8 has been run by this pass. Do not claim apply.** |
| [#864](https://github.com/drgaciw/open-crm-cpq/pull/864) / [story-drg-585-s7-header-backfill-decision-brief.md](story-drg-585-s7-header-backfill-decision-brief.md) | Merged residual S7 brief. **OQ-585-4** already points AC-S7-3 at DRG-619 and forbids wiring without a scored tenant contract. **Cite OQ-585-\*. Do not adopt them here. Do not reopen OQ7.** |
| [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) / `V1_8__contract_header_backfill.sql` / [#634](https://github.com/drgaciw/open-crm-cpq/pull/634) | Functions only. Companion open item still says the accepted event carries no tenant and the NEW path is unedited. That sentence is **stale** relative to DRG-631/662 (an optional interim `tid` is now copied onto `subscriptions.tenant_id`). The production writer is still absent. **Cite; do not edit the companion or V1_8 in this PR.** |
| [ADR-036](../../adr/ADR-036-account-tenant-ownership.md) | **Proposed, not Accepted.** `tid` is not a verified customer tenant. Unresolved mappings must not authorize contract transitions. **Cite; do not accept.** |
| ADR-016 / DRG-632 / DRG-631 / DRG-662 | Interim: one Entra `tid` per deployment, copied onto `quotes.tenant_id`, optional on `QuoteAcceptedEvent.tenantId`, parsed onto `subscriptions.tenant_id` (null when absent or not a GUID). **Cite as landed provenance. Not an authorization to insert `contracts`.** |
| Register **OQ7** | **Adopted** header-only first slice. One thin contract per existing subscription. No synthetic history. **Cite; do not reopen.** History stays [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566). |

### DRG-585 M1–M8 (owner gates — not met, not run)

`verified by reading the issue` (Linear DRG-585 description, this pass). Quoted so this brief cannot be read as an apply record.

* **M1** Owner names the environment and tenant, and records a backup plus restore plan before anything runs.
* **M2** A green integration run on `main` shows `ContractHeaderBackfillIntegrationTest` executed (run id and sha recorded).
* **M3** Dry run `contract_header_backfill_count(tenant)` output is attached, ineligible counts are listed, and the owner confirms the numbers.
* **M4** Explicit written owner go (words, time, environment, tenant). No bot runs apply or compensate. A merge or approval is not the go.
* **M5** Before/after reconciliation: contracts gained = dry-run count; subscriptions with `contract_id` up by the same number; `created_by = 'V1_8_HEADER_BACKFILL'`; zero amendment documents, events, outbox rows or price rows touched.
* **M6** A second apply returns 0.
* **M7** `_compensate` is rehearsed first; it refuses if a header is referenced, otherwise restore from backup; creation-time headers are never deleted.
* **M8** Merging #634 does not close DRG-585.

AC-S7-3 (requirements): given a subscription created after cutover, when created by the NEW path, then it gets a contract header at creation. The integration test calls `ensureHeaderAtCreation` directly. That test is **not** production evidence and does **not** close AC-S7-3 or M2.

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from the docs cited in Alignment. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `text search after empty graph` | Used only because GitNexus returned no index. Not a substitute for a clean impact. |
| `unverified` | Not established in this pass. |

## GitNexus

Repository: **open-crm-cpq** (`/agent/repos/open-crm-cpq`). Worktree: that checkout. Index commit: `d3256ae4b4960f58a2c060883bc980c8f27c2be1` (matches this base). Indexed 2026-11-11T16:29:45Z with GitNexus 1.6.12 after the first MCP `list_repos` returned `total: 0`. Fresh index: 3301 files, 45402 symbols, 99391 edges, 1021 processes. `incomplete_reasons: []` on `gitnexus://repo/open-crm-cpq/context`. Embeddings: 0 (keyword search only).

**Process coverage is partial.** The analyzer log reported `truncation.truncated: true`: 2509 of 2709 entry-point candidates never ranked, 39 walks cut by budget, 2324 callees skipped at max branching. It said an absent flow does **not** mean the path does not exist. `query` for the NEW path, `ensureHeaderAtCreation`, and `QuoteAccepted` tenant returned **`processes: []`**. That empty process list is **not** a clean “no flow” result.

**Symbol results (upstream impact / context):**

| Symbol | Risk | Epistemic | What the graph showed |
|---|---|---|---|
| `ensureHeaderAtCreation` | **LOW** (2 direct) | `exact` | Incoming `CALLS` only from tests: `subscriptionCreatedAfterCutoverGetsAStableHeader`, `missingTenantDoesNotTouchTheDatabase` (confidence 0.85). Outgoing call is `requireTenant` only. The SQL string `contract_header_at_creation` is **not** a graph edge. |
| `AcceptedQuoteSubscriptionService` (class) | **HIGH** (20 direct, 23 total) | `exact` | Modules Service, Asset, Dto, Event. `affected_processes: []` under the truncated process index, so process impact is **not** a clean zero. This brief does **not** edit the class. A future implementation story must re-run impact before editing it. |
| `apply` (2-arg, line 88) | reported LOW, **not authoritative** | `lower-bound` | `causes.receiverTyping: 19`. Nineteen `apply` call sites were dropped because the receiver type was unresolved. Direct hits are a floor. Do not read LOW as “safe to change”. |
| `SubscriptionTenant.resolve` | not scored as LOW | `lower-bound` | One resolved caller: `AcceptedQuoteSubscriptionService.apply`. `causes.receiverTyping: 36`. Other `resolve` call sites may exist and are absent. |
| `QuoteAcceptedEvent` (class) | **MEDIUM** (14 direct, 53 total) | `exact` | Affected process name `accept` (earliest step 3) in quote-service `QuoteController`. This brief does **not** edit the DTO. |

`contract_header_at_creation` is a SQL function. The Java graph does not resolve it. **Risk for that symbol: UNKNOWN.** Text search after the graph gap: defined in `V1_8__contract_header_backfill.sql`; the only Java invocation found is the JDBC string inside `ensureHeaderAtCreation`.

`detect_changes` scope `all` on this worktree reported `changed_count: 0`, `risk_level: none`, no `partial` flag. The only change is this untracked markdown file, which is not a code symbol. That zero is **not** a blast-radius all-clear for the Java symbols above. No production symbol is modified by this PR.

## What the tree does today (orientation only)

`verified by reading code` at the base SHA, after the empty graph result. Not executed.

1. **Event schema has an optional tenant field.** `QuoteAcceptedEvent.tenantId` is a `String`, `@JsonInclude(NON_NULL)`, documented as Quote's interim Entra `tid` (DRG-631 / ADR-016 / DRG-632), **not** a verified customer tenant. Null when the quote has no tenant or on payloads written before DRG-631. `EVENT_TYPE` / `EVENT_VERSION` stay v1. `QuoteAcceptedKafkaEventFactory` sets it from `quote.getTenantId()`.
2. **Consumer reads only the top-level field.** `QuoteAcceptedEventConsumer.parse` copies textual top-level `tenantId` into `provenanceTenantId` and ignores nested spoof fields. `route` always takes the legacy path and calls `acceptedQuotes.apply(event, provenanceTenantId)`. Document-path routing stays off.
3. **NEW path stamps `subscriptions.tenant_id` and does not set `contract_id`.** `AcceptedQuoteSubscriptionService.apply`: deal type defaults to `NEW`. Non-NEW goes to `mutate` and returns. The NEW branch resolves `UUID tenantId = SubscriptionTenant.resolve(provenanceTenantId, tenantMeters)`, saves a `Subscription` with `.tenantId(tenantId)`, saves rate periods and the acceptance ledger, and may record assets. The builder call has **no** `contractId`. A repo text search finds **no** `ensureHeaderAtCreation` or `contract_header_at_creation` under this class.
4. **`Subscription` has no `contractId` field.** `subscriptions.contract_id` exists (nullable, `V1_7`). The JPA entity maps `tenantId`, `customerAccountId`, and `contractLineId`, not `contract_id`. A header link cannot be set through the builder. `V1_18` says `subscriptions.tenant_id` must equal `contracts.tenant_id` when `contract_id` is set. That is an invariant for a future link, not a writer.
5. **Resolved tenant may be null, and the sale still commits.** `SubscriptionTenant.resolve` parses a GUID. Absent or non-GUID returns **null**, logs, increments `opencrm.subscription.tenant.invalid`, and does not reject the event. Javadoc: the value is **not** a real per-tenant id.
6. **Header writer requires a non-null tenant and has no production caller.** `ContractHeaderBackfill.ensureHeaderAtCreation` throws `TENANT_REQUIRED` (`"authoritative tenant is required"`) when `tenantId` is null, then calls `contract_header_at_creation(subscriptionId, tenantId)`. Its Javadoc still says the quote-accepted NEW writer is not called from here because that path has no tenant. GitNexus `context` / `impact` (`epistemic: exact`) list two incoming `CALLS`, both tests: `ContractHeaderBackfillIntegrationTest.subscriptionCreatedAfterCutoverGetsAStableHeader` and `ContractHeaderBackfillTest.missingTenantDoesNotTouchTheDatabase`. No production Java caller is in that exact set. The SQL function itself stays **UNKNOWN** to the symbol graph (see GitNexus). Text search agrees: the function body is `V1_8__contract_header_backfill.sql`, and the Java call is the JDBC string in `ensureHeaderAtCreation`.
7. **SQL eligibility can raise inside the acceptance transaction.** `contract_header_at_creation` raises `authoritative tenant is required` on null; raises `subscription is not eligible for a contract header` when the row is missing, deleted, dates are inverted, `customer_id` is not a canonical UUID, or status is outside PENDING/ACTIVE/SUSPENDED/EXPIRED/CANCELLED. NEW sales are stored as `PENDING` (maps to contract status `ACTIVE`). `customer_id` on the subscription is the event string; `customer_account_id` on the subscription stays null at ingest (DRG-663) and is **not** what the function reads. A raise from this function in the listener transaction rolls back the subscription insert as well as the inbox claim (`handleQuoteAccepted` is `@Transactional`).
8. **Commercial digest includes `tenantId` when present.** `digest` removes only `acceptedAt`, `correlation`, `eventType`, and `eventVersion`. A later payload that adds `tenantId` is a different digest. Replay of a quote id already ledgered without that field fails closed (`Accepted quote identity was reused with changed commercial facts`). `unverified` whether any stored outbox row predates DRG-631.
9. **Backfill functions are installed and not applied.** `V1_8` comment: Flyway installs the functions and does not execute them. A null tenant raises and writes nothing. `created_by` is `V1_8_HEADER_BACKFILL` on apply and `S7_HEADER_AT_CREATION` on creation. Compensate deletes only backfill markers, never creation headers. **This pass did not call count, apply, or compensate.**
10. **CAM migrations on this base run through `V1_21`.** `V1_16`–`V1_21` are files in this tree (contract lines, subscription tenant, customer account id, renewal mode, and others). Older briefs that reserved those numbers for draft PRs #805–#812 described an earlier base. This brief invents **no** next version and does not edit landed files. The V1_8 header line “subscriptions has no tenant_id” is stale relative to V1_18; left unchanged here.

## Owner forks (Proposed OQ-619-\*)

Every row is `Status: Proposed`. Recommended default is **Recommended (Proposed)**. **No Adopted cells.** Picking a row authorizes **no** code in this PR.

| ID | Question | Recommended (Proposed) | Reject / avoid | Blocks |
|---|---|---|---|---|
| **OQ-619-1** | How does a tenant value reach the NEW-path header writer? | **C. Other:** pass the UUID already stored on the new `subscriptions.tenant_id` row, and only when it is non-null. Do not add a second event field. Do not call CRM. | Invent a UUID; read a nested JSON field; treat IT-only coverage as Done | Future implementation story |
| **OQ-619-2** | May that UUID be the interim Entra `tid`? | **No write until an owner sentence.** The column comments already say the subscription tid must match `contracts.tenant_id` **when** `contract_id` is set. They do not authorize creating the contract. ADR-036 stays Proposed. | Call the tid “authoritative” inside this brief; accept ADR-036 here | OQ-619-1; ADR-036 |
| **OQ-619-3** | What if the tid is null, blank, or not a GUID? | **Skip the writer.** Leave `contract_id` null. The sale commits, as it does today. Do not fabricate a tenant. Those rows stay eligible for a later DRG-585 apply, which this brief does **not** run. | Let `requireTenant` / the SQL raise and roll back the sale; default a deployment UUID | Acceptance reliability |
| **OQ-619-4** | What if `customer_id` is not a canonical UUID (or dates/status make the SQL function raise)? | **Skip the writer** on the same predicate the function uses (`contract_header_customer_uuid` null, inverted dates, unknown status). Do not hash a customer id. Do not widen V1_8 in place. | Raise inside the listener transaction; hash free-text into a uuid | OQ-585-2 (cite only) |
| **OQ-619-5** | Same transaction or after commit? | **Same transaction as the subscription insert**, after the id is flushed, **only** when OQ-619-3 and OQ-619-4 say the call is safe. A raise must not be the skip policy. | After-commit job that can diverge from the sale; catch-and-swallow a raise | Idempotency; inbox |
| **OQ-619-6** | Does creation-path wiring close DRG-585 or M1–M8? | **No.** AC-S7-3 is necessary for DRG-585 and is not sufficient. M1–M8 stay unmet. Do not call count/apply/compensate. Creation headers (`S7_HEADER_AT_CREATION`) are outside compensate. | Claim apply; mark DRG-585 Done; run compensate to “undo” a creation header | DRG-585 |
| **OQ-619-7** | Mutate path (AMENDMENT / EXPANSION / RENEWAL)? | **Out of scope.** Those operations return before the NEW insert and do not create a subscription. Existing rows keep a null `contract_id` until a scored S7 apply. | Create a second header on mutate; set `contract_id` in `mutate` | DRG-585 apply |
| **OQ-619-8** | JPA `contractId` mapping? | **No mapping change in this brief.** A future story may keep the link write inside `contract_header_at_creation` (SQL updates `subscriptions.contract_id`) so the unmapped column stays the function’s job. A mapped field is a separate scored change. | Add a setter that bypasses the function’s match checks | Schema / entity |
| **OQ-619-9** | Flyway / comment drift? | **No file.** Do not edit V1_8 or V1_18 in place to refresh “no tenant” comments. A comment-only migration is still a later story. | Consume an unassigned version from this brief; rewrite V1_8 | Landed migrations |
| **OQ-619-IMPL** | Implementation authorization | Separate owner act **after** OQ-619-1..9 (or a deferred subset) close, **including** the OQ-619-2 sentence. **No** S7 apply. **No** flags. **No** Linear Done from this brief. | Wire on merge of this PR; wire without the tenant sentence | Owner release |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-619-1 — How the tenant reaches the writer

| Option | What the owner would be choosing |
|---|---|
| **A. Event field** | Pass top-level `QuoteAcceptedEvent.tenantId` (already on the wire, DRG-631) straight into `ensureHeaderAtCreation`. The field is optional and is the interim `tid`. The Java method and the SQL function **raise** when it is null. That turns today’s successful null-tid NEW sales into failed consumptions unless a skip policy is added (that skip policy is C, not A). |
| **B. Lookup** | Do not use the event `tid` as `contracts.tenant_id`. Resolve a verified account tenant (ADR-036 D-04 projection or synchronous CRM read) and pass only that UUID. ADR-036 is **not** Accepted. No lookup table or endpoint is authorized by this brief. AC-S7-3 stays unwired until that source exists. Fail closed when unmapped: do not fall back to `tid`. |
| **C. Other (Recommended, Proposed)** | Reuse the UUID `SubscriptionTenant.resolve` already stored on the new subscription. Call the writer only when that UUID is non-null **and** the row would pass the SQL eligibility predicate. Otherwise leave `contract_id` null. No new event field, no CRM call, no fabricated id, no backfill apply. Still blocked by OQ-619-2: the bits are the interim `tid`, and this brief does not score them as the contract tenant. |

**Recommended (Proposed):** C, after OQ-619-2 is scored. Until that sentence exists, **no option is a build GO**. `Owner decision: ___ (drg, time)`.

### OQ-619-2 — Interim `tid` versus authoritative contract tenant

| Option | What the owner would be choosing |
|---|---|
| **A. Explicit sentence that creation headers may store the interim `tid` on `contracts.tenant_id` (required before C or A)** | Matches the V1_18 equality comment **if** a header is created. Does not accept ADR-036. Does not call the value a verified customer tenant. |
| **B. Refuse the `tid`; wait for ADR-036 lookup (pairs with OQ-619-1 B)** | Matches ADR-036’s “must not authorize contract transitions” while that ADR stays Proposed. Leaves AC-S7-3 open. |
| **C. Treat this brief as accepting ADR-036 or as declaring the `tid` authoritative** | Out of lane. **NO-GO.** |

**Recommended (Proposed):** the owner writes either A or B in words and a time. This file chooses neither as Adopted. Default until then: **no header insert**. `Owner decision: ___ (drg, time)`.

### OQ-619-3 — Null / non-GUID provenance

| Option | What the owner would be choosing |
|---|---|
| **A. Skip writer; sale commits; `contract_id` stays null (Recommended, Proposed)** | Same outcome as today for service-token, dev, and pre-DRG-631 payloads. |
| **B. Raise and roll back the sale** | Makes `SubscriptionTenant`’s “never reject” rule false for NEW sales that also create headers. |
| **C. Invent a tenant** | Forbidden by V1_8 and by `TENANT_REQUIRED`. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-4 — Ineligible `customer_id` and other SQL raises

| Option | What the owner would be choosing |
|---|---|
| **A. Skip when the function would raise for eligibility; do not hash (Recommended, Proposed)** | Preserves NEW sales whose `customer_id` is not a canonical UUID. Aligns with V1_8 “not hashed”. |
| **B. Let the function raise** | Rolls back an otherwise valid subscription. |
| **C. Edit V1_8 eligibility in place** | Rewrites a landed migration. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-5 — Transaction boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Same transaction, flush the new id, call the function only on the safe subset (Recommended, Proposed)** | Header and subscription commit together. Idempotent second call returns the same contract id (already the SQL behaviour when the link matches tenant and customer). |
| **B. After-commit worker** | Sale can exist without a header even when a tenant was present. Extra failure mode. |
| **C. Catch the raise and continue** | Hides eligibility bugs and can mark the inbox processed without a header the owner thought was required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-6 — DRG-585 / M1–M8 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Wiring AC-S7-3 does not run or close the backfill (Recommended, Proposed)** | M1–M8 remain the only apply gate. Quote them; do not execute them. |
| **B. A green creation-path test satisfies M2 and closes DRG-585** | M2 asks for a recorded run of the backfill IT, and M8 says merging the functions PR does not close the ticket. A different test is not M2. |
| **C. Call `contract_header_backfill_apply` for rows the writer skipped** | That is the owner-gated apply. **NO-GO** here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-7 — Mutate path

| Option | What the owner would be choosing |
|---|---|
| **A. NEW insert only (Recommended, Proposed)** | `mutate` updates an existing subscription and does not insert one. |
| **B. Also header-link the target subscription on amend/renew** | Collides with null `contract_id` on historical rows and with S7 apply. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-8 — Entity mapping

| Option | What the owner would be choosing |
|---|---|
| **A. SQL function remains the link writer (Recommended, Proposed)** | The column is already updated inside `contract_header_at_creation`. No JPA field is required for that update. |
| **B. Map `contractId` on `Subscription` in the same story** | Extra entity surface. Needs its own review so a later `save` cannot null the column. Not authorized here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-9 — Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No migration in the implementation story unless a later scored design needs one (Recommended, Proposed)** | V1_8 already defines the function. V1_18 already defines `subscriptions.tenant_id`. |
| **B. Edit V1_8 or V1_18 comments in place** | Rewrites landed migrations. |
| **C. New migration whose only job is to run apply** | Flyway-at-deploy data write. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-619-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate story after the forks above close (Recommended, Proposed)** | That story may edit production Java. It still must not run S7 apply, flip flags, or mark DRG-585 Done. |
| **B. This docs PR is the authorization** | Violates the Linear tracking-item rule. |
| **C. Wire now using the `tid` and the raising function as-is** | Rolls back null-tid and non-UUID-customer sales. **NO-GO** without OQ-619-2..4. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Acceptance criteria for a future authorized implementation story

These apply only after OQ-619-IMPL is closed with words and a time. They are **not** met by this brief.

1. The owner record names the fork (A, B, or C on OQ-619-1) and the OQ-619-2 sentence (interim `tid` allowed on `contracts.tenant_id`, or wait for a verified lookup).
2. `AcceptedQuoteSubscriptionService` NEW path, after the subscription id exists in the transaction, calls `ContractHeaderBackfill.ensureHeaderAtCreation` (or a wrapper with the same SQL) **only** under the scored null and eligibility policy. Non-NEW operations do not call it.
3. The call uses a non-null UUID from the scored source. It does not read a nested JSON tenant, does not hash `customer_id`, and does not invent a UUID.
4. When the policy says skip, the subscription, rate periods, and ledger commit and `subscriptions.contract_id` stays null. When the policy says write, `contract_header_at_creation` sets `contract_id`, `created_by = S7_HEADER_AT_CREATION`, and `contracts.tenant_id` equals the subscription tenant.
5. Redelivery (existing ledger row, or existing `SUB-{quoteId}`) does not insert a second header. A second call for the same subscription and the same tenant returns the same contract id.
6. No amendment documents, outbox events, or price changes (AC-S7-2). No call to `contract_header_backfill_count`, `_apply`, or `_compensate`. M1–M8 are not checked off by this story.
7. Tests cover: null tid skip, non-GUID skip, non-UUID `customer_id` skip, happy-path header link, idempotent second call. Docker-backed tests stay `@Tag("integration")`. Unit tests do not start Testcontainers.
8. DRG-619 may move to Done only when that story’s evidence is recorded. DRG-585 stays In Review until AC-S7-3 **and** M1–M8 are actually met. This list does not mark either Done.

## Explicit product / schema / flag NO-GO

Until OQ-619-1..9 and OQ-619-IMPL close:

1. **No** production Java/TypeScript change on the quote-accepted path, the header backfill service, or the event DTO.
2. **No** running S7 count, apply, or compensate, and **no** claim that the backfill has been applied.
3. **No** fabricated tenant, hashed customer id, or silent promotion of the interim `tid` to an authoritative contract tenant.
4. **No** Flyway file and **no** in-place edit of V1_7, V1_8, or V1_18.
5. **No** feature-flag flip, dual-read flip, Jev enablement, ramp enable, document-path enablement, or asset-write hold bypass.
6. **No** accepting ADR-036, adopting open OQ-585-\* cells, or reopening Adopted OQ7.
7. **No** marking Linear DRG-619 or DRG-585 Done from this brief.

## Out of scope

- Runtime wiring of `ensureHeaderAtCreation`.
- S7 backfill apply, compensate, dry-run against a real tenant, and M1–M8 execution.
- Mutate-path header linking, DRG-566 history, DRG-554 regroup, DRG-598 phase selection.
- Merging this PR (draft review only) and any flag or gateway change.

## What accepting this brief would unblock

Documentation clarity only: a later implementation story may cite **OQ-619-1..9** and **OQ-619-IMPL** as the owner menu. Accepting or merging this file **does not** authorize the writer, **does not** apply the S7 backfill, and **does not** mark DRG-619 or DRG-585 Done.

## Unverified

- Whether any environment has called `contract_header_backfill_apply` or `contract_header_at_creation` outside tests.
- How many live quotes have a null `tenant_id`, and how many accepted events were stored before DRG-631.
- Whether quote `customer_id` values are canonical UUIDs often enough for AC-S7-3 to cover the NEW path under the skip policy.
- GitNexus process membership for these symbols (`processes: []` under a truncated process index). `apply` and `SubscriptionTenant.resolve` caller sets are lower bounds (`receiverTyping` 19 and 36). SQL `contract_header_at_creation` callers outside the JDBC string are UNKNOWN.
- CI run evidence for `ContractHeaderBackfillIntegrationTest` on this base SHA (M2 is unmet regardless).

## References

- Linear: [DRG-619](https://linear.app/drgamtd-workspace/issue/DRG-619/wire-quote-accepted-new-path-contract-header-writer-event-carries-no), [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585/s7-contract-header-backfill-for-existing-subscriptions-no-synthetic) (AC-S7-3, M1–M8), [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566), [DRG-631](https://linear.app/drgamtd-workspace/issue/DRG-631), [DRG-632](https://linear.app/drgamtd-workspace/issue/DRG-632), [DRG-662](https://linear.app/drgamtd-workspace/issue/DRG-662).
- GitHub: [#864](https://github.com/drgaciw/open-crm-cpq/pull/864) (merged S7 residual brief), [#634](https://github.com/drgaciw/open-crm-cpq/pull/634) (V1_8 functions).
- Docs: [story-drg-585-s7-header-backfill-decision-brief.md](story-drg-585-s7-header-backfill-decision-brief.md), [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md), [ADR-036](../../adr/ADR-036-account-tenant-ownership.md), [amendment-first-slice-requirements-and-acceptance.md](../amendment-first-slice-requirements-and-acceptance.md) AC-S7-3.
- Code (read-only): `QuoteAcceptedEvent.java`, `QuoteAcceptedKafkaEventFactory.java`, `QuoteAcceptedEventConsumer.java`, `AcceptedQuoteSubscriptionService.java`, `SubscriptionTenant.java`, `Subscription.java`, `ContractHeaderBackfill.java`, `V1_7__contract_documents.sql`, `V1_8__contract_header_backfill.sql`, `V1_18__subscription_tenant_id.sql`.

---

*End of Decision brief. All OQ-619-* rows remain `Status: Proposed` until `Owner decision: ___ (drg, time)`. Current code is NO-GO.*
