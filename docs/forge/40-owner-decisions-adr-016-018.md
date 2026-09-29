# Owner decisions: ADR-016 and ADR-018 (decided 2026-09-28)

Ten decisions stood between the merged code and a live environment. On 2026-09-28 the project owner delegated all ten ("I delegate the ten decisions to you to determine"), and they were taken as the recommended defaults, with the corrections that reading the code forced (marked in the [Record](#record)). Three changed code and shipped (decisions 1, 4 and 9, as #557, #558 and #559). The other seven are documentation and runbook changes.

The sections below are the analysis as it was put to the owner, kept so the reasoning stays readable. Where a "Fact" line turned out wrong, the Record says so. The Record says what was decided, what shipped and where each outcome now lives. The confirmations that only a person can give are in [What still needs a person](#what-still-needs-a-person).

Sources: [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md) (step 4 and step 5 notes), [ADR-018](../adr/ADR-018-event-delivery-guarantees-idempotency.md) (acceptance checklist), [identity go-live runbook](../runbooks/identity-go-live.md) sections 5 and 9, [event operations runbook](../runbooks/event-operations.md) sections 1, 2, 6 and 7.

## At a glance

| # | Decision | Decided | Gates |
|---|----------|---------|-------|
| 1 | Who may pass a quote review (`IN_REVIEW` to `APPROVED`) | Deal Desk, Sales VP or CFO, and never the quote's own creator. Shipped (#557) | Identity go-live (role table) |
| 2 | Discount-tier mapping (`MANAGER`, `DIRECTOR`, `VP_SALES`) | Keep the mapping. The discount-path owner still confirms the tier names it emits | Identity go-live (role table) |
| 3 | Four-eyes rule (requester cannot decide their own approval) | Keep it always, with no exceptions. The CFO group needs two or more people | Identity go-live (role table) |
| 4 | How Prometheus scrapes metrics | A management port with no token, reachable only from the monitoring namespace. Shipped (#558) | Identity go-live; every ADR-018 alert |
| 5 | Accept ADR-018 | Accepted. The D4 contract step and D8 step 3 stay staged after environment proof | Nothing technical |
| 6 | DLT retention and alert routing | 14 days on every `*.DLT`, 30 days on `cpq.quote.status.accepted.DLT`. Page only on a failed outbox row | Enabling the relay |
| 7 | Backlog per environment and topic | Skip history that is not real business data; decide real history per topic; start crm at the end of the topic everywhere | Enabling the relay |
| 8 | Dual-run window and exit criterion | 7 consecutive days and at least 30 acceptances, whichever ends later | Removing Redis (D8 step 3) |
| 9 | Changed discount figures: supersede or add | Supersede the open approval. Shipped (#559) | Nothing technical |
| 10 | ERP upsert by subscription number | The ERP upserts. Confirm it before a real ERP is wired | Enabling the relay for `cpq.quote.status.accepted` against a real ERP |

Order in which the decisions bite: 1 to 4 before the staging run of the identity runbook, 6, 7 and 10 before any relay is enabled, 8 once crm's Kafka listener is live in production.

---

## ADR-016: identity

### 1. Who may pass a quote review

- **Fact.** `IN_REVIEW` to `APPROVED` has no tier or role check. Any signed-in user can do it, including the person who created the quote. The SPA offers Approve and Reject on a quote only while it is in review. Pending discount approvals are a separate flow, decided in the approvals inbox.
- **Options.**
  - (a) Leave it: any authenticated user.
  - (b) Deal Desk, Sales VP or CFO only, reusing `ApprovalAuthority`.
  - (c) A new `Reviewer` app role, which means a manifest change and another group to run.
- **Recommended: (b), and the creator cannot pass their own review.** It reuses the existing role table and extends the four-eyes rule of decision 3 to the step that currently has none.
- **If accepted.** quote-service gets an authority check and the SPA hides the button for other roles. That is one small PR. The runbook's role-to-group table does not change.

### 2. Discount-tier mapping

- **Fact.** `DEAL_DESK` and `MANAGER` may be decided by Deal Desk, Sales VP or CFO. `SALES_VP`, `DIRECTOR` and `VP_SALES` by Sales VP or CFO. `CFO` by CFO. Any other tier is decided by no one (fails closed). `DIRECTOR` has no role of its own, so it maps up, not down. The policy is code, so changing it is a reviewed change.
- **Options.** Keep it; add Entra roles for Manager and Director (finer control, more groups); collapse tiers in the discount path.
- **Recommended: keep it.** Before go-live, ask whoever owns the discount path to confirm it emits only these six tier names. An unknown tier blocks the approval rather than weakening it, so a mismatch shows up as a stuck approval, not a hole.

### 3. Four-eyes rule

- **Fact.** A requester who tries to decide their own approval gets 403 `SELF_APPROVAL`. The ADR did not ask for this; it became enforceable once the requester came from the token.
- **Options.** Keep it always; enforce it only above a threshold; allow it with an audit entry.
- **Recommended: keep it always.** One operational consequence: a `CFO`-tier approval raised by a CFO can only be decided by a different CFO-role holder, so the CFO group in production needs at least two people. Add that to the runbook's group table when this is accepted.

### 4. Prometheus scraping

- **Fact.** `/actuator/prometheus` needs a token, like everything except health and info, so metrics are not scraped today. ADR-018's alerts (outbox failed, relay behind, dead letters, DLT depth, consumer lag) are all Micrometer metrics, so this also decides whether those alerts can exist.
- **Options.**
  - (a) A management port on each service, reachable only from the monitoring namespace by network policy, with no token.
  - (b) A scraper workload identity with its own app role and token refresh.
  - (c) Leave it unscraped.
- **Recommended: (a).** It adds no credential or Entra object to run, and it keeps the metrics off the ingress and the gateway. Its weak point is that it relies on a network policy; that policy has to ship with the deployment manifests, since the runtime-topology ADR (ADR-022) does not exist yet. Option (c) makes the ADR-018 runbook's alerts unenforceable.

---

## ADR-018: events

### 5. Accept ADR-018

Every decision D1 to D10 is implemented and has evidence in the acceptance checklist. Two steps are deliberately still ahead: the D4 contract step (`event_type` holds the registry type) and D8 step 3 (remove Redis), both after every environment has proven the release. **Recommended: accept.** The status change is the only effect.

### 6. DLT retention and alert routing

- **Fact.** Until decided, the runbook says to set `retention.ms` to at least 14 days (`1209600000`) on each `*.DLT`. Alerts are: failed outbox row (page); relay behind, dead letters arriving, DLT untriaged for 24 h and consumer lag (tickets).
- **Recommended.** Keep 14 days as the floor, and give `cpq.quote.status.accepted.DLT` longer (suggested 30 days), because a lost record there means a missing subscription or ERP sync. Keep the runbook's severities. The owner needs to name which team or channel receives the page and the tickets.

### 7. Backlog per environment and topic

- **Fact.** The relay has never run, so every producer schema holds all of its history. Enabling it sends all of that to the consumers. On `cpq.quote.status.accepted` that means a PDF job, a subscription and an ERP sync per quote, and crm would set WON, `wonAt`, amount and currency again on each deal, overwriting later manual edits.
- **Options, per topic.** Skip the rows (`SKIPPED`); send them; send them but start one consumer group at the end of the topic.
- **Recommended.** Run the runbook's inventory query in each environment first. Where the rows are test data, skip them all. That is likely true of dev, test and staging, and of production if it has not launched. Where real history exists, decide per topic. For `cpq.quote.status.accepted`, get sign-off from whoever owns contracts and the ERP before sending anything. In every environment, start crm at the end of the topic (runbook step 6) whatever else is decided.

### 8. Dual-run window and exit criterion

- **Proposed by the runbook.** For 7 consecutive days with at least one business week of real traffic, Kafka's count (`applied` plus `duplicate`) equals Redis's for acceptances that have a deal, `failed` is 0, and there are no crm records on the DLT. Then remove `QuoteEventPublisher`, `QuoteAcceptedEventListener`, `RedisSubscriberConfig` and the fitness-test allowlist entry in one release.
- **Recommended: accept it, and add a floor on volume.** Seven quiet days can prove nothing if there were three acceptances. Pick a minimum count you would trust (suggested 30) and end the window at whichever comes later. The window starts when crm's Kafka listener is enabled in production. Staging traffic does not count.

### 9. Changed discount figures: supersede or add

- **Fact.** An identical open discount request for the same quote line is treated as a duplicate. A request with different figures creates a second open approval, so two can be open for one line.
- **Options.** Add (today); supersede the older one; refuse the new event until the old is decided.
- **Recommended: supersede.** An approver should never be able to approve a figure the quote no longer carries. This needs a `SUPERSEDED` terminal status, an audit entry and a change to how the inbox displays it, so it is a small PR after the decision, and history stays readable under ADR-013.

### 10. ERP upsert by subscription number

- **Fact.** `ErpSyncJob` claims each subscription once, but a sync repeated after a crash is possible, so the ERP side must be safe to repeat.
- **Options.** The ERP upserts by subscription number; OpenCRM sends an idempotency key if the ERP supports one; accept duplicates and reconcile by hand.
- **Recommended: the ERP upserts.** Ask the ERP owner to confirm. Until then, do not enable the relay for `cpq.quote.status.accepted` in an environment wired to a real ERP.

---

## Record

Decided 2026-09-28 by the delegate of the project owner. Each outcome has moved into the document it changes.

| # | Decision taken | Date | Recorded in |
|---|----------------|------|-------------|
| 1 | Option (b). Deal Desk, Sales VP or CFO may pass or fail a review; `CPQ.User` and `Admin` may not. The creator cannot pass their own quote (403 `SELF_REVIEW`; failing it is allowed). A quote with no recorded creator has the role check only. Refusals: 401 `AUTHENTICATION_REQUIRED`, 403 `REVIEWER_MUST_BE_A_USER`, 403 `REVIEWER_ROLE_REQUIRED`. **Correction:** the sheet said the SPA would hide the button for other roles. The SPA has no way to learn the signed-in user's roles yet, so it still offers the button and shows the server's refusal. Hiding it needs a session-info endpoint (follow-up below) | 2026-09-28 | Code: #557 (`ReviewAuthority`, `QuoteService`). ADR-016 "Owner decisions"; identity runbook sections 3, 7 |
| 2 | Keep the mapping (`DEAL_DESK` and `MANAGER`: Deal Desk, Sales VP or CFO; `SALES_VP`, `DIRECTOR`, `VP_SALES`: Sales VP or CFO; `CFO`: CFO; anything else: nobody). Owed before go-live: the discount-path owner confirms it emits only those tier names | 2026-09-28 | ADR-016 "Owner decisions"; identity runbook sections 3, 7 |
| 3 | Keep four-eyes always, no exceptions. The CFO group in production has at least two people, because a `CFO`-tier approval raised by a CFO can only be decided by another CFO | 2026-09-28 | Identity runbook section 3 (group table) |
| 4 | Option (a). Every service serves the actuator on `MANAGEMENT_SERVER_PORT` (9091), where `health`, `info` and `prometheus` need no token and every other path is 403; the service port serves no actuator endpoint. `OPENCRM_ENV=prod` refuses to start a service that exposes more than `health` and `info` on its service port. A network policy, shipped with the manifests, is the only protection of the port. **Correction:** the sheet said `/actuator/prometheus` needed a token. It did not exist: no service had a Prometheus registry, and three services did not expose the endpoint. Where a service exposed `metrics`, any valid user token could read it. Both are fixed | 2026-09-28 | Code: #558. Identity runbook sections 4, 5, 6, 7; event runbook section 1; `infra/k8s/opencrm-management-port-networkpolicy.yaml`; ADR-016 "Owner decisions" |
| 5 | Accept ADR-018. The D4 contract step and D8 step 3 stay ahead, after every environment has proven the release | 2026-09-28 | ADR-018 (status, "Owner decisions") |
| 6 | 14 days is the floor for `retention.ms` on every `*.DLT`; `cpq.quote.status.accepted.DLT` gets 30 days (`2592000000`). The runbook's severities stand: page only on a failed outbox row, tickets for the rest. The destinations are a deployment fact, still to be named (below) | 2026-09-28 | Event runbook section 1 |
| 7 | Run the inventory query in each environment first. Rows that are test data are skipped (`SKIPPED`). Real history is decided per topic. `cpq.quote.status.accepted` history is sent only with sign-off from whoever owns contracts and the ERP. In every environment crm starts at the end of the topic, whatever else is decided | 2026-09-28 | Event runbook section 2 (steps 4 to 6) |
| 8 | Accept the runbook's criterion (7 consecutive days, at least one business week of real traffic, Kafka `applied` + `duplicate` equal to Redis's, `failed` 0, no crm records on the DLT) and add a volume floor: at least 30 acceptances that have a deal, ending at whichever comes later. The window starts when crm's Kafka listener is enabled in production, and staging traffic does not count. Then remove `QuoteEventPublisher`, `QuoteAcceptedEventListener`, `RedisSubscriberConfig` and the fitness-test allowlist entry in one release | 2026-09-28 | Event runbook section 6; ADR-018 "Owner decisions" |
| 9 | Supersede. A discount event with different figures for a quote line marks the open PENDING request `SUPERSEDED` (terminal, names its replacement) and creates a new PENDING one. An identical event is still a duplicate. Decided requests, other lines and other approval types are never touched. Deciding a superseded request answers 409 `APPROVAL_SUPERSEDED` (after the 403 checks). History keeps the row (ADR-013) | 2026-09-28 | Code: #559 (migration `V1_8`). ADR-018 D6 consumer table; event runbook sections 2, 5 |
| 10 | The ERP upserts by subscription number: the subscription number is the ERP's idempotency key, and a sync repeated after a crash must be safe. **Finding:** `ErpAdapter` is a stub today (it returns `ERP-<number>` and calls nothing), so nothing external is at risk yet. The gate binds when a real adapter replaces it: the ERP owner confirms the upsert before the relay is enabled for `cpq.quote.status.accepted` in an environment wired to that ERP | 2026-09-28 | Event runbook sections 2 (step 4) and 7; ADR-018 "Owner decisions" |

## What still needs a person

Nothing below can be decided from the code. Each item names who can close it.

- **Alert destinations (decision 6).** Name the team or channel that receives the page and the tickets. The event runbook leaves `<page channel>` and `<ticket channel>` for each environment.
- **Discount tiers (decision 2).** Whoever owns the discount path confirms it emits only `MANAGER`, `DIRECTOR` or `VP_SALES` (and `DEAL_DESK`, `SALES_VP` or `CFO` through the quote BFF). An unknown tier is decided by nobody, so a mismatch shows up as a stuck approval.
- **CFO group (decision 3).** Staff the production `CFO` group with two or more people before go-live.
- **Contracts and ERP owners (decisions 7 and 10).** Sign off any history sent on `cpq.quote.status.accepted`, and confirm the ERP upserts by subscription number before a real adapter is wired.
- **Each environment.** The identity runbook sections 1 to 8, and the event runbook section 2 (inventory, then skip, send or start crm at the end of the topic).

## Follow-ups this exercise found

- **The SPA cannot tell who is signed in.** Hiding Approve, Reject and the approvals inbox controls by role needs an endpoint on the gateway that returns the session's roles. Until then the server refuses and the SPA shows the refusal.
- **A replayed discount event can bring back old figures.** The consumer has no ordering field: a record replayed from `cpq.discount.approval.required.DLT` after newer events for the same quote line were processed would supersede the current request with its older figures. The runbook tells the operator to check the line before replaying (section 5). A version or timestamp on the event would remove the hazard.
- **No rule yet decides whether a quote needs a tiered approval at all.** That is a pricing rule, outside ADR-016 (unchanged).
