# trycompai/crm — harvest backlog (Track CC1)

**Status:** Documentation only. Drafted 2026-10-15. Items are filed in Linear (P-DRG-6) and in the Notion **Open-CRM-CPQ Backlog** database with the IDs below.
**Parent:** [trycompai-crm-reuse-gap-analysis.md](./trycompai-crm-reuse-gap-analysis.md) · [capability matrix](./trycompai-crm-capability-matrix-cc1.md).
**Rule:** an item may start only when every ADR in its **Gate** column is Accepted. Proposed ADRs authorize nothing.

New CRM stories extend the existing epics (BC-12, BC-13, BC-19, BC-20) rather than adding capability-map epics. The gap analysis recommends the owner consider splitting **Engagement & Activity** out of BC-12 in a capability map Rev 3; if that happens, BC-12-06 to BC-12-08 move with it.

## Ranked items

| Rank | ID | Title | Epic | Phase | Pts | Priority | Gate | Depends on |
|---|---|---|---|---|---|---|---|---|
| 1 | BC-13-05 | Fix mixed-currency pipeline totals; frozen base amount per deal | BC-13 | Phase 1 | 5 | High (bug) | ADR-032 | — |
| 2 | BC-12-05 | CRM domain events from `crm-service` (outbox) | BC-12 | Phase 1 | 5 | High | — (ADR-018 registry rules apply) | — |
| 3 | BC-12-14 | Durable agent work queue in `ai-service` | BC-12 | Phase 2 | 5 | High | ADR-029 | — |
| 4 | BC-12-12 | Evidence-priced fact ledger with proposals inbox | BC-12 | Phase 2 | 8 | High | ADR-028 | BC-12-05 |
| 5 | BC-12-13 | Contact and organization enrichment agent | BC-12 | Phase 2 | 8 | Medium | ADR-028, ADR-029 | BC-12-12, BC-12-14 |
| 6 | BC-12-09 | Typed custom fields with agent-fill brief | BC-12 | Phase 2 | 8 | Medium | ADR-031 | — |
| 7 | BC-12-10 | Saved views with URL-portable list state | BC-12 | Phase 2 | 5 | Medium | ADR-031 | BC-12-09 |
| 8 | BC-12-11 | Archive, retention, purge and suppression list | BC-12 | Phase 2 | 5 | Medium | ADR-033 | BC-20-05 |
| 9 | BC-12-16 | Duplicate detection feeding account merge | BC-12 | Phase 2 | 5 | Medium | — | BC-12-04 |
| 10 | BC-13-06 | Deal contacts with buying roles | BC-13 | Phase 2 | 3 | Medium | — | — |
| 11 | BC-19-05 | Scoped machine API keys | BC-19 | Phase 2 | 5 | Medium | — (ADR-016 amendment note) | BC-19-03 |
| 12 | BC-12-06 | Activity timeline (note, task, email, meeting) | BC-12 | Phase 2 | 8 | Medium | ADR-030 (lifts Activities hold) | BC-12-05 |
| 13 | BC-12-07 | Mailbox and calendar connection with incremental sync | BC-12 | Phase 2 | 13 | Medium | ADR-030 | BC-12-06, BC-19-04 |
| 14 | BC-12-08 | Contact auto-capture from mail and calendar | BC-12 | Phase 2 | 5 | Medium | ADR-030, ADR-033 | BC-12-07, BC-12-11 |
| 15 | BC-20-06 | Agent run ledger and cost metering | BC-20 | Phase 2 | 5 | Medium | ADR-029 | BC-12-14 |
| 16 | BC-12-17 | Workspace selling profile as agent context | BC-12 | Phase 4 | 3 | Low | — | BC-12-14 |
| 17 | BC-12-15 | Team agents: versioned definitions, triggers, deployment gate | BC-12 | Phase 4 | 13 | Low | ADR-034 | BC-12-05, BC-12-14, BC-20-06 |

**Total:** 17 items, 109 points.
**Deferred (not filed):** website visitor tracking and forms (needs BC-19-04 consent first); product telemetry allowlist (wait for the reserved observability ADR); MCP server (the source also defers it).

---

## Item detail

### BC-13-05 — Fix mixed-currency pipeline totals; frozen base amount per deal
**As a** sales manager, **I want** pipeline totals in one tenant base currency, **so that** a EUR deal and a USD deal are never added as the same unit.
- [ ] Today's `SUM(d.amount) … GROUP BY d.stage` in `DealRepository` is replaced; a regression test with two currencies fails on the old query.
- [ ] Each deal stores `amount`, `currency`, `base_amount`, `base_currency`, `fx_rate`, `fx_rate_as_of`, `fx_source`.
- [ ] The rate freezes when the amount or currency changes, and again at close (won/lost). It never drifts silently.
- [ ] Missing rate → `base_amount` is null and the deal is excluded from totals with a visible "n deals not converted" count; never treated as 1:1.
- [ ] Rates come from the same FX source BC-03-07 uses, so CRM and CPQ agree.
**Source:** `docs/currency.md`, `ExchangeRate` model.

### BC-12-05 — CRM domain events from `crm-service`
**As a** platform engineer, **I want** `crm-service` to publish `ContactCreated`, `OrganizationCreated` (the `AccountCreated` of BC-12-01), `DealStageChanged` and `DealClosed` through an outbox, **so that** agents, audit and CPQ react to CRM changes.
- [ ] Transactional outbox, same pattern as quote and pricing services; events registered per ADR-018.
- [ ] Event actor headers per ADR-016 step 4, including agent actors.
- [ ] Closes the open acceptance criterion "AccountCreated event is emitted" on BC-12-01.

### BC-12-14 — Durable agent work queue in `ai-service`
**As a** platform engineer, **I want** a PostgreSQL work queue for agent tasks, **so that** background agents run exactly once per due item without Kafka (#241 stays held).
- [ ] `agent_task` table: `tenant_id`, `kind`, `subject_type`, `subject_id`, `reason`, `payload`, `priority`, `budget`, `attempts`, `due_at`, `leased_until`, `outcome`.
- [ ] Claim with `FOR UPDATE SKIP LOCKED`; two dispatchers never take the same row; an expired lease frees the row.
- [ ] Partial unique index on (`tenant_id`, `kind`, `subject_type`, `subject_id`) where unfinished, so the same work is not queued twice.
- [ ] The dispatcher only leases and starts; it makes no business decisions.
- [ ] Retry with backoff up to `max_attempts`, then parked for a human with the last error.
**Source:** `AgentTask` model, `README.md` l.88–93.

### BC-12-12 — Evidence-priced fact ledger with proposals inbox
**As a** seller, **I want** every machine-written fact on a contact or organization to carry its evidence, **so that** I can trust it and nothing an AI guessed overwrites what I typed.
- [ ] `crm_fact` table: subject, field key, value, score, band, evidence (list of {kind, weight, url, observed_at}), method, status (`PROPOSED`, `APPLIED`, `DISMISSED`, `SUPERSEDED`), decided_by, decided_at.
- [ ] Score is computed by `crm-service` from evidence weights (`1 − Π(1 − wᵢ)`, capped at 0.99). The model never supplies the score.
- [ ] `VERIFIED` (≥ 0.85 with a primary source) applies automatically; `PROBABLE` is a proposal; `POSSIBLE` is stored but never shown as a value; below 0.3 is not stored.
- [ ] Contradicting evidence holds the fact as unresolved regardless of score.
- [ ] A value a human set is never overwritten by an agent; the agent can only propose.
- [ ] Sensitive fields (email, organization link) need one band higher.
- [ ] Accept / dismiss from the record and from a tenant-wide proposals inbox; every decision is audited (BC-20-01).
**Source:** `ContactFact`, `docs/plan/contact-intelligence-agent.md` §5.

### BC-12-13 — Contact and organization enrichment agent
**As a** seller, **I want** new contacts and organizations researched in the background, **so that** titles, industry, size and links fill in without manual research.
- [ ] Triggered by `ContactCreated` / `OrganizationCreated` via the work queue; also on demand ("Re-enrich").
- [ ] Runs in `ai-service` on the current Azure gpt-4o / Spring AI 0.8.1 runtime; no AI in `crm-service`.
- [ ] Writes only through the fact ledger API; never writes record fields directly.
- [ ] All outbound fetches go through an SSRF guard (deny private, loopback and link-local addresses; re-check after redirects).
- [ ] Per-task research budget; stops when spent and records why.
- [ ] Evaluation harness with a labelled set reports precision per band before the agent is enabled for any tenant.
**Source:** `docs/agent.md`, `docs/plan/people-enrichment-agent.md`, `packages/db/src/safe-fetch.ts`.

### BC-12-09 — Typed custom fields with agent-fill brief
**As a** tenant admin, **I want** to add my own fields to organizations, contacts and deals, **so that** the CRM fits how we sell without code changes.
- [ ] Types: text, long text, number, date, checkbox, select, URL, email, phone, user.
- [ ] Values in typed columns (one per storage class), not JSON, so lists filter and sort server-side with indexes.
- [ ] Select options are rows; renaming an option does not orphan records.
- [ ] `key` is immutable; `label` can change. Archive, never hard delete, a field with values.
- [ ] Standard fields appear in the same list and can be hidden or reordered, not deleted or retyped.
- [ ] Per field: `agent_filled` flag and `agent_brief` text the enrichment agent reads; agent-filled values go through the fact ledger.
**Source:** `docs/plan/dynamic-fields.md` §§2–5.

### BC-12-10 — Saved views with URL-portable list state
**As a** seller, **I want** to save a filtered, sorted, column-selected list and share it by link, **so that** my team works from the same view.
- [ ] Views are private or shared within the tenant; owner can rename and delete.
- [ ] Filter grammar is validated server-side against standard and custom field types; unknown fields are rejected, not ignored.
- [ ] List state lives in the URL, so refresh, back and share keep it (Angular router + signals).
**Source:** `SavedView` model, `record-stack.ts` pattern.

### BC-12-11 — Archive, retention, purge and suppression list
**As a** privacy officer, **I want** CRM records archived, then purged after a retention period, and purged people kept out, **so that** we meet erasure requests and they stay met.
- [ ] Archive hides a record and keeps it restorable; replaces today's bare `is_deleted` flag semantics.
- [ ] Purge after a tenant retention period, or on demand for erasure; blocked when BC-20-05 legal hold applies.
- [ ] Purge writes the email (normalized and hashed) or domain to a suppression list; import, auto-capture and enrichment refuse suppressed identities.
- [ ] Purge removes dependent facts, activities and custom-field values; the audit record keeps that a purge happened, not the content.
**Source:** `apps/api/src/archive/*`, `SuppressedContact`, `SuppressedDomain`.

### BC-12-16 — Duplicate detection feeding account merge
**As a** sales ops user, **I want** likely duplicate organizations and contacts flagged, **so that** BC-12-04 merge has candidates.
- [ ] Organization match on normalized domain; free-mail domains (gmail.com etc.) never imply an organization.
- [ ] Contact match on normalized email, then name within the same organization.
- [ ] Candidates are suggestions; merge stays an explicit BC-12-04 action.
**Source:** `apps/api/src/companies/domain.ts`.

### BC-13-06 — Deal contacts with buying roles
**As a** seller, **I want** to attach contacts to a deal with a role (decision maker, champion, economic buyer, billing contact, technical evaluator, blocker), **so that** I can see the buying committee.
- [ ] Roles are a closed, tenant-configurable option list, not free text.
- [ ] A contact can be on many deals with different roles.
- [ ] BC-13-02 playbooks can require roles per stage.
**Source:** `DealContact.role`, `docs/crm-plan.md` l.621.

### BC-19-05 — Scoped machine API keys
**As a** tenant admin, **I want** API keys scoped to resources and actions, **so that** integrations and agents call the CRM API without a user session.
- [ ] Keys are shown once, stored hashed, prefixed for identification, expiring, revocable, rate-limited.
- [ ] Scopes map to BC-19-03 permissions; a key can never exceed its creator's permissions.
- [ ] Calls through the gateway carry a machine actor; recorded in audit.
- [ ] Needs a short amendment note to ADR-016 (machine principal type).
**Source:** `Apikey` model.

### BC-12-06 — Activity timeline
**As a** seller, **I want** notes, tasks, emails and meetings on one timeline per organization, contact and deal, **so that** I see the whole relationship.
- [ ] Gated: ADR-030 Accepted and the Activities hold lifted in ARCHITECTURE.md §9.
- [ ] Activity types: note, task (due, completed), email (thread link), meeting (event link).
- [ ] `/api/v1/activities/**` owned by `crm-service`; replaces the "Activities (deferred)" section of `docs/CRM_API_PARITY.md`.
**Source:** `Activity` model.

### BC-12-07 — Mailbox and calendar connection with incremental sync
**As a** seller, **I want** to connect my Google or Microsoft mailbox and calendar, **so that** my conversations and meetings land on the timeline without copy-paste.
- [ ] Per-user OAuth grant, separate from Entra sign-in; least scopes; tokens encrypted at rest; disconnect deletes tokens.
- [ ] Incremental sync with a stored cursor, `retry_after` on provider throttling, and a visible last-error.
- [ ] Personal mail is dropped at ingest (not stored and hidden): only threads with at least one known contact or business domain are kept.
- [ ] Other users in the tenant see metadata only by default; bodies need explicit sharing (ADR-030 D-05).
- [ ] Depends on BC-19-04 consent records for the people whose mail is processed.
**Source:** `MailboxSync`, `EmailThread`, `CalendarEvent`, `docs/plan/gmail-calendar-plan.md`.

### BC-12-08 — Contact auto-capture from mail and calendar
**As a** seller, **I want** new business correspondents and meeting attendees offered as contacts, **so that** the CRM stays complete.
- [ ] Off by default per mailbox (`auto_create` flag); when off, candidates are suggestions.
- [ ] Free-mail domains never create organizations; suppressed emails and domains are skipped.
- [ ] Organization inferred from domain; reuses BC-12-16 matching.
**Source:** `MailboxSync.autoCreate`, `apps/api/src/mailbox/participants.ts`.

### BC-20-06 — Agent run ledger and cost metering
**As a** tenant admin, **I want** every agent run and action recorded with tokens, cost and outcome, **so that** I can audit and cap AI spend.
- [ ] Run, action and audit rows with idempotency key and request hash per action.
- [ ] Token and cost totals per run, per agent, per tenant; monthly cap that parks new tasks when reached.
**Source:** `AgentRun`, `AgentAction`, `AgentAuditEvent`.

### BC-12-17 — Workspace selling profile as agent context
**As a** tenant admin, **I want** a short profile of what we sell and to whom, **so that** agents rank and write with our context.
- [ ] Profile drafted from the tenant's website, then edited by a human; versioned.
- [ ] Read-only input to agents; never used for authoritative decisions.
**Source:** `WorkspaceProfile`.

### BC-12-15 — Team agents: versioned definitions, triggers, deployment gate
**As a** sales ops user, **I want** to define small agents ("when a deal goes stale, draft a follow-up task"), **so that** routine work runs itself under control.
- [ ] Definition → immutable numbered versions (instructions, model, manifest of allowed tools, sandbox policy).
- [ ] Triggers: manual, schedule, CRM event, webhook; disabled until a version is deployed.
- [ ] Deploying a version is the human approval; it records who approved and when.
- [ ] Each action is typed, idempotent and permission-checked against the deployer's permissions; destructive actions are not in the tool set.
- [ ] Per-agent record scope (which entities and fields it may read and write).
**Source:** `AgentDefinition`, `AgentVersion`, `AgentTrigger`, `AgentAction`.
