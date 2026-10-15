# trycompai/crm — capability matrix (Track CC1)

**Status:** Documentation only. Drafted 2026-10-15.
**Source pin:** [trycompai/crm](https://github.com/trycompai/crm) @ `6d4793dd6d7aeea91aa6a034e00b17d7408a2d08`.
**Target pin:** `open-crm-cpq` `main` @ `f58a77f`.
**Parent:** [trycompai-crm-reuse-gap-analysis.md](./trycompai-crm-reuse-gap-analysis.md).

Legend — **Have**: implemented in Open-CRM-CPQ. **Partial**: some of it exists. **Gap**: nothing exists. **Out**: deliberately not taken.

| # | Capability | Source evidence | Open-CRM-CPQ today | Status | Backlog item | ADR |
|---|---|---|---|---|---|---|
| 1 | Company / organization CRUD | `Company` model, `apps/api/src/companies` | `crm-service` `organization` package | **Have** | — | — |
| 2 | Contact CRUD | `Contact` model | `crm-service` `contact` package | **Have** | — | — |
| 3 | Lead capture + advisory score | (leads are contacts + intelligence) | `lead` + `lead/scoring` (JEV advisory, ADR-015) | **Have** | — | — |
| 4 | Deal with stage and currency | `Deal`, `docs/currency.md` | `deal` package, `currency` column | **Partial** — no FX, totals mix currencies | BC-13-05 | ADR-032 |
| 5 | Deal buying roles (closed list) | `DealContact.role`, `Contact.buyingRole`, `docs/crm-plan.md` l.621 | none | **Gap** | BC-13-06 | — |
| 6 | Domain events from CRM writes | agent `EVENT` triggers | consumes `QuoteAccepted` only; no outbox | **Gap** | BC-12-05 | — |
| 7 | Activity timeline (note, task, email, meeting) | `Activity` model (links `EmailThread`, `CalendarEvent`), gmail-calendar plan | none; Activities API **held** | **Gap (held)** | BC-12-06 | ADR-030 |
| 8 | Mailbox + calendar sync (cursor, retry-after, incremental) | `MailboxSync`, `calendar-sync.service.ts`, gmail-calendar plan | none | **Gap (held)** | BC-12-07 | ADR-030 |
| 9 | Contact auto-capture, personal mail dropped at ingest, free-mail blocklist | gmail-calendar plan l.92, l.104 | none | **Gap (held)** | BC-12-08 | ADR-030 |
| 10 | Typed custom fields (options as rows, immutable key) | `docs/plan/dynamic-fields.md` §3 | none | **Gap** | BC-12-09 | ADR-031 |
| 11 | Field-level agent fill (`agentFilled`, `agentBrief`) | same | none | **Gap** | BC-12-09 | ADR-031, ADR-028 |
| 12 | Saved views, URL-portable list state | `SavedView`, `record-stack.ts` | none | **Gap** | BC-12-10 | ADR-031 |
| 13 | Archive → retention → purge | `apps/api/src/archive/*` | `is_deleted` soft delete only | **Partial** | BC-12-11 | ADR-033 |
| 14 | Suppression list (purged stays purged) | `SuppressedContact`, `SuppressedDomain` | none | **Gap** | BC-12-11 | ADR-033 |
| 15 | Evidence-priced facts, bands, proposals, human-wins | `ContactFact`, contact-intelligence §5 | none (lead score only) | **Gap** | BC-12-12 | ADR-028 |
| 16 | Enrichment agent (web research, SSRF-safe fetch) | `docs/agent.md`, `safe-fetch.ts`, people-enrichment plan | none | **Gap** | BC-12-13 | ADR-028, ADR-029 |
| 17 | Durable work queue (lease, budget, dedupe, retry) | `AgentTask`, `lib/tasks.ts` (README l.88–93) | none; agent Kafka **held** (#241) | **Gap** | BC-12-14 | ADR-029 |
| 18 | Team agents: versions, triggers, deployment as approval, action ledger | `AgentDefinition`, `AgentVersion`, `AgentTrigger`, `AgentAction` | none | **Gap** | BC-12-15 | ADR-034 |
| 19 | Duplicate detection via `domainFromEmail` + name match | `apps/api/src/companies/domain.ts` | BC-12-04 merge is planned; no detection | **Gap** | BC-12-16 | — |
| 20 | Selling profile as agent context | `WorkspaceProfile`, `docs/agent.md` | none | **Gap** | BC-12-17 | — |
| 21 | Scoped machine API keys (hashed, rate-limited, expiring) | `Apikey` model | user JWT only (ADR-016) | **Gap** | BC-19-05 | — |
| 22 | Agent run cost / budget metering | `AgentTask.budget`, research budget in `docs/agent.md` | none | **Gap** | BC-20-06 | ADR-029 |
| 23 | Website visitor tracking + forms | `docs/tracking.md` | none | **Out (deferred)** | — | needs BC-19-04 |
| 24 | Product telemetry allowlist | `packages/telemetry` | none | **Out (deferred)** | — | reserved ADR-020 |
| 25 | Next.js / tRPC / Prisma / Better Auth | stack | Angular / Spring / Flyway / Entra | **Out** | — | ARCHITECTURE §2, §6 |
| 26 | MCP server over CRM procedures | `dynamic-fields-build.md` l.161 ("Do not build MCP now") | none | **Out (source also defers)** | — | — |
