# trycompai/crm — reuse and gap analysis (harvest Track CC1)

**Status:** Plan ready, documentation only. Drafted 2026-10-15.
**Source:** [trycompai/crm](https://github.com/trycompai/crm) at `6d4793dd6d7aeea91aa6a034e00b17d7408a2d08` (MIT, © 2026 Comp AI).
**Target:** `crm-service`, `ai-service`, `frontend/libs/crm`, and the BC-12, BC-13, BC-19 and BC-20 epics in Notion and Linear (project P-DRG-6).
**Children:** [capability matrix](./trycompai-crm-capability-matrix-cc1.md) · [harvest backlog](./trycompai-crm-harvest-backlog-cc1.md) · ADR-028 to ADR-034 (all **Proposed**).
**Does not authorize:** any code, schema, topic, endpoint or UI. Every backlog item is gated on its ADR being Accepted.

**Naming:** harvest **Track CC1** (Comp CRM). Not forge Wave K, not ramp waves R1–R4, not pricing T1/T2.

---

## 1. Why this repo

Open-CRM-CPQ is CPQ-led. Its CRM half (BC-12 Customer & Account, BC-13 Opportunity) has four stories each and no design for how relationship data gets *into* the CRM or stays correct. `crm-service` has leads, contacts, organizations, deals and an advisory lead score, and nothing else.

trycompai/crm is the opposite: an agent-first CRM with no quoting at all. Its value to us is the parts we have never designed:

- how mail and calendar become contacts, companies and a timeline;
- how an AI writes facts about people and companies without inventing them;
- how background agents run reliably on PostgreSQL alone;
- how a workspace extends its own data model;
- how deletion, suppression and multi-currency work in a CRM.

It is also a disciplined codebase. Its `AGENTS.md` and `docs/api.md` are explicit about where logic lives, which matches our own boundary rules.

## 2. Source at a glance

| Area | What it is | Evidence |
|---|---|---|
| Stack | pnpm + Turborepo; Next.js 15 web, NestJS + tRPC API, Prisma on PostgreSQL, Better Auth, Vercel AI SDK agent runtime | `README.md`, `package.json`, `apps/*` |
| Tenancy | **Single tenant per install.** One Better Auth organization exists for SSO and membership, but it "is not a tenancy boundary"; CRM tables carry no tenant column. The agent "may read everything, including full email bodies" because it is an internal single-tenant tool | `docs/api.md` §"One organization, and it is not a tenancy boundary"; `docs/agent.md` l.285 |
| Domain | Company (+ CompanyEnrichment), Contact (+ ContactFact, ContactBrief), Deal, DealContact (role), ExchangeRate, Activity, MailboxSync, EmailThread/EmailMessage, CalendarEvent/Attendee, FieldDefinition/Option/Value, SavedView, SuppressedContact, SuppressedDomain, WorkspaceProfile, Apikey, Agent* (definition, version, trigger, run, action, audit), AgentTask, Tracked* and FormSubmission | `packages/db/prisma/schema.prisma` |
| Intelligence | Web research and enrichment agents; contact intelligence with an evidence-priced confidence model; selling-profile context | `docs/agent.md`, `docs/plan/contact-intelligence-agent.md`, `docs/plan/people-enrichment-agent.md` |
| Runtime | One dispatcher schedule that "decides nothing"; `lib/tasks.ts` leases due rows with `FOR UPDATE SKIP LOCKED`; expired leases free work | `README.md` lines 88–93 |
| Integrations | Google mail + calendar sync with watermarks, personal mail dropped at ingest, free-mail domain blocklist for company matching | `docs/plan/gmail-calendar-plan.md`, `apps/api/src/google/calendar-sync.service.ts` |
| Data lifecycle | Archive, retention controller, purge, suppression list | `apps/api/src/archive/*`, `SuppressedContact` model |
| Money | Per-deal currency with a frozen conversion to the workspace base currency | `docs/currency.md` |
| Extensibility | Typed custom fields (no JSON values), options as rows, immutable key, `agentFilled` + `agentBrief` per field | `docs/plan/dynamic-fields.md` §3 |
| Agent builder | Versioned agent definitions, triggers (schedule, event, manual, webhook), sandbox policy, approval and deployment, audit events | `AgentDefinition`, `AgentVersion`, `AgentTrigger` models |
| Website | Tracked domains, visitors, events, form submissions | `docs/tracking.md` |
| Safety | `safe-fetch.ts` SSRF guard on agent web fetches | `packages/db/src/safe-fetch.ts` |

## 3. Architecture gate (ARCHITECTURE.md alignment)

| Constraint | Verdict | How Track CC1 complies |
|---|---|---|
| Java 21 / Boot 4.0.6, PostgreSQL + Flyway | **PASS** | Patterns only. No TypeScript, Prisma or NestJS is ported. Schemas are re-expressed as Flyway migrations when an ADR is accepted. |
| Angular Nx only, no React/Next harvest | **PASS** | UI ideas (one field sheet, URL-portable views) become Angular signal stories. No component code is copied. |
| Runtime AI in `ai-service` only, Azure gpt-4o / Spring AI 0.8.1 | **PASS** | Enrichment and team agents live in `ai-service`. No AI in `crm-service`. No Spring AI upgrade is requested. |
| ADR-006 DTOs only | **PASS** | All new endpoints return DTOs in `ApiResponse<T>`. |
| ADR-015 JEV advisory | **PASS** | Agent output stays advisory; human-confirmed values win (ADR-028). |
| ADR-016 identity | **PASS** | Mailbox OAuth grants are per user, separate from Entra sign-in. Agent actions carry an agent actor plus the deploying human. |
| Activities API hold (§2, §9) | **HOLD — not lifted** | ADR-030 *proposes* lifting it. Until ADR-030 is Accepted, no `/api/v1/activities/**`. |
| Agent Kafka #241 | **PASS** | ADR-029 uses a PostgreSQL work queue. It does not add agent topics. |
| Multi-tenancy (BC-19-02) | **ADAPT** | The source is single-tenant and lets its agent read all mail. Every harvested table gains `tenant_id` and row-level isolation, and agent read scope is narrowed (ADR-030 D-05, ADR-034 D-04). |
| R2 list-row delete hold | **PASS** | ADR-033 covers CRM records, not price-book rows. It does not un-hold R2. |

**Overall:** aligns, as documentation. Code stays gated.

## 4. Comparison with Notion and Linear

The Notion **Open-CRM-CPQ Backlog** database and Linear project **P-DRG-6** carry the same 20-epic capability map. CRM coverage today:

| Epic | Stories in tracker | What `crm-service` already has | Gap the source fills |
|---|---|---|---|
| BC-12 Customer & Account (DRG-285) | 01 aggregates, 02 hierarchy, 03 territory, 04 merge | Organization, Contact, Lead CRUD; soft delete (`is_deleted`); lead score advisory | Activity timeline, mail/calendar capture, enrichment, custom fields, saved views, archive/purge/suppression, duplicate detection, import |
| BC-13 Opportunity (DRG-294) | 01 lifecycle, 02 playbooks, 03 guided selling, 04 recommended products | Deal CRUD with stage and a per-row `currency` | Multi-currency reporting, buying roles |
| BC-19 Identity (DRG-278) | 01 OIDC … 04 consent | Gateway + per-service JWT (ADR-016) | Scoped machine API keys for agents and integrations |
| BC-20 Audit (DRG-279) | 01 audit … 05 retention | Audit and decision log for CPQ | Agent run ledger and cost metering |

### 4.1 Drift found during the review

1. **Defect — mixed-currency pipeline totals.** `DealRepository` groups the pipeline summary by stage and sums `amount` across rows with different `currency` values (`backend/services/crm-service/src/main/java/com/opencrm/crm/deal/DealRepository.java`, the `SUM(d.amount) … GROUP BY d.stage` query). A EUR deal and a USD deal are added as if they were the same unit. The source documents this exact failure ("$2.0M silently") in `docs/currency.md`. Filed as BC-13-05.
2. **Tracker drift — BC-12-01.** Notion and Linear show BC-12-01 as Not started / Backlog, but `crm-service` implements organization and contact CRUD. It does not emit `AccountCreated` (no outbox in `crm-service`), so the story is partly done, not done. A comment on DRG-481 records this. No status was changed.
3. **No event emission from CRM.** `crm-service` consumes `QuoteAccepted` but publishes nothing. Every harvested story that triggers agents ("contact created", "deal moved") needs CRM domain events. This is a prerequisite, captured in the backlog's dependency column.

## 5. Dispositions

| # | Source capability | Disposition | Target | ADR |
|---|---|---|---|---|
| 1 | Evidence-priced fact ledger, human-wins, suggestion inbox | **Adopt pattern** | `crm-service` (ledger), `ai-service` (writer) | ADR-028 |
| 2 | Lease-based Postgres work queue, "dispatcher decides nothing" | **Adopt pattern** | `ai-service` | ADR-029 |
| 3 | Mail/calendar sync, contact auto-capture, activity timeline | **Adopt pattern, gated** | `crm-service` + new connector module | ADR-030 (lifts Activities hold) |
| 4 | Typed custom fields, options as rows, `agentFilled`/`agentBrief` | **Adopt pattern** | `crm-service`, `frontend/libs/crm` | ADR-031 |
| 5 | Saved views with URL-portable state | **Adopt pattern** | `crm-service`, `frontend/libs/crm` | ADR-031 |
| 6 | Frozen FX base amount per deal | **Adopt pattern + fix defect** | `crm-service` | ADR-032 |
| 7 | Archive → retention → purge, suppression list | **Adopt pattern** | `crm-service`, BC-20-05 | ADR-033 |
| 8 | Agent builder: versions, triggers, deployment as approval, audit | **Adopt pattern, later** | `ai-service` | ADR-034 |
| 9 | SSRF-safe fetch for agent web research | **Adopt as acceptance criterion** | `ai-service` | ADR-029 / ADR-034 |
| 10 | Free-mail domain blocklist and `domainFromEmail` matching | **Adopt as acceptance criterion** | BC-12 capture and dedupe stories | ADR-030 |
| 11 | Selling profile (ICP, offering, tone) as agent context | **Adopt, small** | `ai-service` | — |
| 12 | Buying role on deal contacts (closed option list) | **Adopt** | `crm-service` | — |
| 13 | Website visitor tracking and forms | **Defer** | — | Needs BC-19-04 consent first |
| 14 | Product telemetry allowlist | **Defer** | — | Covered by observability ADR (reserved ADR-020) |
| 15 | Next.js UI, tRPC, Prisma, Better Auth | **Reject** | — | Stack mismatch (ARCHITECTURE §2, §6) |
| 16 | Single-workspace assumptions | **Reject** | — | We are multi-tenant |

## 6. Sequencing

1. **CC1-G0 (docs):** merge this plan, the matrix, the backlog and ADR-028 to ADR-034 as Proposed.
2. **CC1-G1 (owner):** decide ADR-032 first. It fixes a live defect and has no dependency.
3. **CC1-G2:** CRM domain events from `crm-service` (prerequisite for anything agent-triggered).
4. **CC1-G3:** ADR-029 work queue, then ADR-028 ledger, then the enrichment agent.
5. **CC1-G4:** ADR-031 fields and views; ADR-033 lifecycle.
6. **CC1-G5:** ADR-030 activities and mailbox — only after the owner lifts the Activities hold.
7. **CC1-G6:** ADR-034 team agents, Phase 4.

## 7. Licence and attribution

The source is MIT. We port ideas and re-implement in Java and Angular; no source files are copied. If a future PR copies any non-trivial text or code, it must keep the MIT notice in `THIRD_PARTY_NOTICES`.
