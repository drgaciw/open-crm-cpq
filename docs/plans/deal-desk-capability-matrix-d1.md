# Track D1 capability matrix (C1)

**Scope:** Approval / state-machine parity only — Deal lifecycle → Quote + `approval-routing`.  
**Parent plan:** [deal-desk-reuse-gap-analysis.md](./deal-desk-reuse-gap-analysis.md) (architect **APPROVED-WITH-GATES**).  
**Harvest backlog:** [deal-desk-harvest-backlog-d1.md](./deal-desk-harvest-backlog-d1.md).  
**Dual-read:** unchanged (`${APPROVALS_LEGACY_LOCAL_READ:true}`). Do **not** flip. ADR-005 stays **Proposed**.  
**Track 2 / Queue D:** **HOLD** — no TCV/repricing PRs; no Easy Rules; no Salesforce; no Mongo deal API.  
**Paths:** FE and agents keep quote BFF only (`docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §6). Do **not** add `/api/v1/deals/**` approve APIs.

---

## Source SHA pin

| Field | Value |
|-------|-------|
| Source repo | [drgaciw/deal-desk-service](https://github.com/drgaciw/deal-desk-service) |
| Local clone | `/home/username01/Projects/deal-desk-service` |
| Pinned commit | `d9bf654a487846d54964c13d1f9363fe99f747aa` |
| Pin message | `Merge pull request #56 from drgaciw/cursor/gitnexus-readme-mcp-b635` |
| Pin date | 2026-05-25 |
| Verify | `git -C /home/username01/Projects/deal-desk-service rev-parse HEAD` → `d9bf654…` |
| C0 package | `JAVA_HOME` **21.0.2**; `backend/mvnw -f …/deal-desk-service/pom.xml -DskipTests package` → BUILD SUCCESS. **No** `newgrp docker`. |

---

## Matrix legend

| Column | Meaning |
|--------|---------|
| **Disposition** | `landed` · `port-method (B)` · `port-pattern (C)` · `defer` · `won't-do` · `hold-d2` |
| **Do now?** | Track D1 authorized **code** after C1–C2 merge (C3). Docs-only this PR. |
| **PR** | Backlog ID (`D1-1`…`D1-3`) |

Primary mapping (locked): source **Deal** = commercial package under approval ≈ target **Quote** + **ApprovalRequest**. Not CRM `Deal` / `DealStage`.

---

## 0. Vocabulary (Deal → Quote + ApprovalRequest)

| Source (`d9bf654`) | Target (open-crm-cpq) | Notes |
|--------------------|------------------------|-------|
| Mongo `Deal` + `DealStatus` | `Quote` (`QuoteStatus`, `ApprovalState`) + routing `ApprovalRequest` (`ApprovalStatus`, `approvalTier`) | Two target records for one source deal lifecycle |
| `DRAFT` | `QuoteStatus.DRAFT` | Create default |
| `SUBMITTED` | `QuoteStatus.PENDING_APPROVAL` + `ApprovalStatus.PENDING` + `ApprovalState.PENDING` | Source has no separate approval aggregate |
| `IN_REVIEW` | Closest: `QuoteStatus.IN_REVIEW` | **Unused** in source `DealServiceImpl` transitions; target IN_REVIEW is pre-BFF review (`QuoteService.submitForReview`) |
| `APPROVED` | `QuoteStatus.APPROVED` + `ApprovalStatus.APPROVED` + `ApprovalState.APPROVED` | Source also sets `approvedBy` / `approvedAt` |
| `REJECTED` | `QuoteStatus.REJECTED` + `ApprovalStatus.REJECTED` | Source stores reason in `Deal.notes`; routing uses `rejectionReason` |
| `CANCELLED` | `QuoteStatus.CANCELLED`; routing `ApprovalStatus.CANCELLED` | BFF `ApprovalStatus` enum has **no** CANCELLED (maps to null) |
| `CLOSED_WON` | Weak: `QuoteStatus.ACCEPTED` | **Not** CRM `DealStage.WON` |
| `CLOSED_LOST` | Weak: CRM `DealStage.LOST` or quote `EXPIRED` | Do **not** harvest into CRM kanban |
| Single approver (`X-User-ID`) | Tiers `DEAL_DESK` / `SALES_VP` / `CFO` (`QuoteApprovalTierMapper`) | Source has no DEAL_DESK tier — the *phrase* is the target approval tier |
| CRM `Deal` (`DealStage` PROSPECTING…WON/LOST) | — | **Won't-do** conflation |

---

## 1. Status enum + allowed transitions

Source `DealStatus` (`model/DealStatus.java`) vs `DealServiceImpl` guards:

| ID | Action | Source from → to | Source guard | Target analog | Gap | Disposition | Do now? | PR |
|----|--------|------------------|--------------|---------------|-----|-------------|---------|-----|
| T1 | Submit | `DRAFT` → `SUBMITTED` | Else `InvalidDealStateException` | BFF `POST /api/v1/quotes/{id}/approvals`: Quote must be `DRAFT` or `IN_REVIEW` → `PENDING_APPROVAL`; routing `POST /api/v1/approvals` creates `PENDING` | Landed happy path; source does **not** allow submit from IN_REVIEW (enum unused) | `landed` + living-spec tests | Yes (tests) | D1-1 |
| T2 | Approve | `SUBMITTED` → `APPROVED` | Else `InvalidDealStateException` | Routing `ApprovalRequest.approve`: only `PENDING`; BFF projects Quote `APPROVED` | Landed; double-approve throws `IllegalStateException` (test exists) | `landed` + living-spec | Yes (tests) | D1-1 |
| T3 | Reject | `SUBMITTED` only (`REJECTABLE_STATUSES`) → `REJECTED` | Non-blank rejector + reason | Routing `reject` only `PENDING`; BFF `ApprovalRoutingClient.reject` **substitutes** `"Rejected via quote BFF"` if reason blank | Reason-required gap; IN_REVIEW not rejectable in source (same as target routing) | `port-pattern (C)` — require reason on BFF | Yes | D1-2 |
| T4 | Cancel | Not `CLOSED_WON` / `CLOSED_LOST` / `CANCELLED` → `CANCELLED`; reason required | `cancelDeal` — **no REST mapping** | `POST /api/v1/quotes/{id}/cancel` blocks only `ACCEPTED` (`CANNOT_CANCEL_ACCEPTED`); **does not** call `ApprovalRequest.cancel()` | Pending routing row can remain `PENDING` after quote cancel | `port-pattern (C)` — cascade via existing quote cancel; **no** new FE path | Yes | D1-3 |
| T5 | Update | Blocked in `APPROVED` / `CLOSED_WON` / `CLOSED_LOST` | | Quote header PUT DRAFT/REJECTED (Wave M) | Different commercial lock; not D1 core | `defer` | No | — |
| T6 | Delete | `DRAFT` only | | Quote soft-delete; `QUOTE_ACCEPTED_NO_DELETE` | Analog exists; not D1 | `landed` / out | No | — |
| T7 | `IN_REVIEW` / `CLOSED_*` writers | Enum values; no service setter | | Quote owns IN_REVIEW / ACCEPTED / EXPIRED | Do not invent source IN_REVIEW harvest | `won't-do` | No | — |
| T8 | `batchUpdateStatus` | Unguarded parallel status write | | None | Dangerous; no analog | `won't-do` | No | — |

**Allowed-transition snapshot (source tests `DealServiceEdgeCaseTest`):**

| From \ action | submit | approve | reject | cancel |
|---------------|:------:|:-------:|:------:|:------:|
| DRAFT | yes | no | no | yes |
| SUBMITTED | no | yes | yes | yes |
| IN_REVIEW | (not in submit param list; service = DRAFT-only) | no | no | yes |
| APPROVED | no | no | no | yes |
| REJECTED | no | no | no | yes |
| CANCELLED | — | — | — | no |
| CLOSED_WON / CLOSED_LOST | no | no | no | no |

---

## 2. Invalid-state / validation failure semantics

| ID | Capability | Source (`d9bf654`) | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------------------|--------|-----|-------------|---------|-----|
| G1 | Typed transition exception | `InvalidDealStateException` / `InvalidDealStateTransitionException`; code `INVALID_DEAL_STATE`; details `currentStatus` + `targetStatus` or `action` | Routing: `IllegalStateException` messages (`Can only approve PENDING requests`); handler → **409** `INVALID_STATE_TRANSITION` (no from/to payload). BFF submit: `BusinessException` **400** `INVALID_STATUS_TRANSITION`. BFF legacy approve/reject: `APPROVAL_NOT_PENDING` | Payload + HTTP + code split across BFF vs routing; BFF routing-409 becomes `APPROVAL_ROUTING_APPROVE_FAILED` **502** | `port-pattern (C)` — `BusinessException` on routing entity + preserve codes through BFF | Yes | D1-2 |
| G2 | HTTP for invalid transition | ProblemDetail **422** `UNPROCESSABLE_ENTITY` | Routing **409** CONFLICT; BFF **400** | Align on existing target codes; do **not** import ProblemDetail as API shape (ADR-006 `ApiResponse<T>`) | `port-pattern (C)` — keep `ApiResponse`; optionally map 409→BFF `INVALID_STATE_TRANSITION` not 502 | Yes | D1-2 |
| G3 | Reject reason required | `validateRequiredText(reason)` → `IllegalArgumentException` before load | BFF comments optional; client default string | Missing guard | `port-pattern (C)` | Yes | D1-2 |
| G4 | Not found | `DealNotFoundException` → 404 `DEAL_NOT_FOUND` | `ResourceNotFoundException` → `RESOURCE_NOT_FOUND` | Naming only | `won't-do` rename | No | — |
| G5 | `InvalidDealStateTransitionException.forSubmission` | Factory exists; handler 422; **service throws** `InvalidDealStateException` string ctor instead | — | Dead factory vs tests on handler | `defer` (source hygiene; do not port unused factory) | No | — |

---

## 3. Submit / approve / reject API vs quote BFF + `ApprovalService`

Contract stay: agents/FE → quote BFF; routing `/api/v1/approvals/**` internal (S2C-12).

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| A1 | Submit | `POST /api/v1/deals/{id}/submit` (`DEAL_CREATOR`) | `POST /api/v1/quotes/{quoteId}/approvals` → routing `POST /api/v1/approvals` | Path remap **landed**; dual-read default **true** (do not flip) | `landed` | — | — |
| A2 | Approve | `POST /api/v1/deals/{id}/approve` + header `X-User-ID` | `POST /api/v1/quotes/approvals/{approvalId}/approve` body `{approver, comments}` | Actor from body/`SecurityUtils`; dual-read 404 fallback | `landed` | — | — |
| A3 | Reject | `POST …/reject` + `reason` query | `POST …/reject` body comments | Defaulted blank reason (G3) | `port-pattern (C)` | Yes | D1-2 |
| A4 | Pending inbox | Search `GET /deals?status=` | `GET /api/v1/quotes/approvals/pending` (agent-contract) | Landed K4 FE | `landed` | — | — |
| A5 | New `/api/v1/deals` approve API | Source mount | Would bypass BFF | Forbidden | `won't-do` | No | — |
| A6 | Dual-read flip | — | `${APPROVALS_LEGACY_LOCAL_READ:true}` | HIGH forge hold | `won't-do` | No | — |
| A7 | QuoteService.approve/reject (non-BFF) | — | Direct Quote status without routing | Pre-existing parallel path; D1 must **not** enlarge it | `won't-do` (do not harvest onto this path) | No | — |

---

## 4. DealValidation / DealStatus / Profitability / PaymentThreshold as **policy specs**

Easy Rules classes are **reference specs only** — do **not** port `easy-rules-core`.

| ID | Source rule | Spec intent | Target analog | Gap | Disposition | Do now? | PR |
|----|-------------|-------------|---------------|-----|-------------|---------|-----|
| P1 | `DealStatusRules` submit/approve/reject/cancel + `ValidateStatusChangeRule` | Transition table (same as §1); extra: APPROVED/CLOSED_* cannot move except cancel | `ApprovalRequest.approve\|reject\|cancel` + Quote BFF | `ValidateStatusChangeRule` overlaps service guards; Easy Rules fire **after** status already set in `DealServiceImpl` | `port-pattern (C)` — tests/guards, **not** engine | Yes | D1-1 / D1-2 |
| P2 | `DealValidationRules.RequiredFieldsRule` | name, Salesforce opp id, account id | Quote create validation + CRM org | SF opp **parked**; quote has name/customer | `won't-do` SF field; quote validation landed | No | — |
| P3 | `ValueValidationRule` / `ComponentValidationRule` | value > 0; component qty > 0, unit price ≥ 0 | `QUOTE_NO_LINE_ITEMS` + line-item constraints | Partial | `defer` unless product gap vs quote lines | No | — |
| P4 | `DateValidationRule` | start not past; end after start | Quote validity / expire | Weak fit | `defer` | No | — |
| P5 | `SalesforceValidationRule` | opp + products exist in SF | None | Parked | `won't-do` | No | — |
| P6 | `ProfitabilityRules` | ACI **payment** metrics (gross/net revenue, ACH vs card uplift) | `approval-margin-exception.drl` (CPQ margin floor → DEAL_DESK) | **Different domain** — not CPQ margin | `won't-do` for D1 (architect: implement only if missing vs margin DRL — these are not that gap) | No | — |
| P7 | `PaymentThresholdRule` | average payment < $5000 | None in CPQ | ACI payment threshold | `won't-do` for D1 | No | — |
| P8 | `PaymentRules` / Credit* / Debit* | Card/ACH fee tables | pricing residual | Track D2 / pricing, not approval | `hold-d2` | No | — |

---

## 5. Exception taxonomy → BFF / `BusinessException`

| Source code | Source HTTP | Target code | Target HTTP | D1 decision |
|-------------|-------------|-------------|-------------|-------------|
| `INVALID_DEAL_STATE` | 422 | `INVALID_STATE_TRANSITION` (routing) / `INVALID_STATUS_TRANSITION` (BFF quote) / `APPROVAL_NOT_PENDING` (legacy) | 409 / 400 / 400 | Keep target names; add from/to details on routing; stop mapping routing 409 → BFF 502 | D1-2 |
| `DEAL_NOT_FOUND` | 404 | `RESOURCE_NOT_FOUND` | 404 | Keep | — |
| `VALIDATION_ERROR` / `CONSTRAINT_VIOLATION` | 400 | existing validation | 400 | Keep | — |
| `SALESFORCE_*` | 503 | none | — | Parked | — |
| `TCV_*` | 422 | pricing | — | D2 handoff | — |
| `RULE_*` | 404/400 | pricing rule-sets | — | Exclude Easy Rules | — |
| (none) | — | `CANNOT_CANCEL_ACCEPTED` | 400 | Keep; extend cancel to cascade routing | D1-3 |
| (none) | — | `APPROVAL_ROUTING_*_FAILED` 502 | 502 | Narrow: invalid-state must not look like routing down | D1-2 |

---

## 6. Explicit defer / exclude (not D1 “do now”)

| Theme | Status |
|-------|--------|
| TCV / NPV / contingent / repricing math | **hold-d2** — catalog in parent D0; file `deal-desk-tcv-handoff-to-pricing-t1.md` after C-GATE; **do not** reopen T1-1…T1-5 |
| Salesforce sync / batch-sync / `.env` | **Parked** — product ADR required; **no** credential copy |
| Easy Rules / SpEL `DynamicRule` / `RuleDefinitionController` | **Exclude** |
| Mongo deal aggregate / new `/api/v1/deals` | **Exclude** |
| Dual-read default flip | **Exclude** |
| CRM Deal kanban / Activities / NgRx | **Exclude** |
| AI `DealDeskWorkflowOrchestrationAgent` prompt | **Defer** after D1 gate |
| `deal-desk-rules` Drools adapter as second runtime | **Exclude** (port-design reference only) |
| FE harvest | **None** in source |

---

## 7. Ranked Track D1 candidates (top 6–8)

| Rank | Candidate | Mode | Land in | PR |
|-----:|-----------|------|---------|-----|
| 1 | Vocabulary + transition parity **living spec tests** (source table vs Quote + `ApprovalRequest`) | C | `approval-routing` + quote BFF tests | D1-1 |
| 2 | Invalid-state guards: reject-reason required; routing `BusinessException` + from/to; BFF must not 502 invalid-state | C | `approval-routing`, `quote-service` BFF / `ApprovalRoutingClient` | D1-2 |
| 3 | Cancel cascade: `QuoteService.cancel` → routing `ApprovalRequest.cancel()` for PENDING on that quote | C | quote-service + approval-routing (existing `POST /quotes/{id}/cancel`; **no** new external approval path) | D1-3 |
| 4 | Exception/code alignment (table §5) | C | same as D1-2 | folded into D1-2 |
| 5 | Profitability / payment-threshold **policy** | — | — | **won't-do** (not CPQ margin; P6/P7) |
| — | Easy Rules, `/api/v1/deals`, dual-read flip, Salesforce, TCV | — | — | won't-do / hold-d2 |

**GitNexus notes (every C3 code PR):** impact before symbol edits; always `-r /home/username01/Projects/open-crm-cpq`. Disambiguate two `ApprovalService` classes with `--file`. `ApprovalRequest.approve` impact (2026-09-14): **LOW** / epistemic lower-bound (1 untyped caller dropped) — confirm with `rg`. `UNKNOWN` ≠ unused.

**C1 exit:** This matrix. No C3 implementation in this PR.
