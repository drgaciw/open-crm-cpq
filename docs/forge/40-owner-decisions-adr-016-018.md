# Owner decisions still open: ADR-016 and ADR-018 (2026-09-28)

Ten decisions stand between the merged code and a live environment. Each one has a recommended default, so the quickest way through this sheet is to accept or change one line per decision. A recommendation is not a decision: nothing here is applied until the owner records it.

Sources: [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md) (step 4 and step 5 notes), [ADR-018](../adr/ADR-018-event-delivery-guarantees-idempotency.md) (acceptance checklist), [identity go-live runbook](../runbooks/identity-go-live.md) sections 5 and 9, [event operations runbook](../runbooks/event-operations.md) sections 1, 2, 6 and 7.

## At a glance

| # | Decision | Recommended default | Gates |
|---|----------|--------------------|-------|
| 1 | Who may pass a quote review (`IN_REVIEW` to `APPROVED`) | Deal Desk or above, and never the quote's own creator | Identity go-live (role table) |
| 2 | Discount-tier mapping (`MANAGER`, `DIRECTOR`, `VP_SALES`) | Keep the current mapping, confirm the tiers the discount path emits | Identity go-live (role table) |
| 3 | Four-eyes rule (requester cannot decide their own approval) | Keep it, with no exceptions, and staff the CFO group with two or more people | Identity go-live (role table) |
| 4 | How Prometheus scrapes `/actuator/prometheus` | Separate management port, reachable only from the monitoring namespace | Identity go-live; every ADR-018 alert |
| 5 | Accept ADR-018 | Accept, with the D4 contract step and D8 step 3 staged after environment proof | Nothing technical |
| 6 | DLT retention and alert routing | 14 days minimum on every `*.DLT`; page only on a failed outbox row | Enabling the relay |
| 7 | Backlog per environment and topic | Skip history that is not real business data; always start crm at the end of the topic | Enabling the relay |
| 8 | Dual-run window and exit criterion | The proposed 7 days, plus a minimum number of acceptances | Removing Redis (D8 step 3) |
| 9 | Changed discount figures: supersede or add | Supersede the open approval | Nothing technical |
| 10 | ERP upsert by subscription number | ERP upserts; do not enable `cpq.quote.status.accepted` against a real ERP until confirmed | Enabling the relay |

Suggested order: 1 to 4 before the staging run of the identity runbook, 6, 7 and 10 before any relay is enabled, 8 once crm's Kafka listener is live, and 5 and 9 whenever convenient.

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

Fill in when decided, then move the outcome into the ADR or runbook it changes.

| # | Decision taken | Date | Recorded in |
|---|----------------|------|-------------|
| 1 |  |  |  |
| 2 |  |  |  |
| 3 |  |  |  |
| 4 |  |  |  |
| 5 |  |  |  |
| 6 |  |  |  |
| 7 |  |  |  |
| 8 |  |  |  |
| 9 |  |  |  |
| 10 |  |  |  |
