# ADR-038: Standalone account-service, extract vs keep Account in crm-service

**Status:** Proposed on 2026-11-11. Not accepted.
**Date:** 2026-11-11
**Deciders:** Project owner (pending). Acceptance stays with drg.
**Backlog:** [DRG-692](https://linear.app/drgamtd-workspace/issue/DRG-692) / [BC-12-19](https://app.notion.com/p/40bf7cb4e4df813fb8dfeabed1a92cb3), **scope item 1 (ADR) only**. Parent epic DRG-285 (BC-12).
**Relates to:**
- [ADR-036](ADR-036-account-tenant-ownership.md) (Proposed; crm-service would own the account-to-tenant link)
- [ADR-008](ADR-008-postgres-schema-per-service.md) (schema per service; authorizes no new schema)
- [ADR-009](ADR-009-gateway-edge-only.md) (gateway edge-only), [ADR-016](ADR-016-identity-authentication-service-trust.md) (security), [ADR-017](ADR-017-synchronous-inter-service-communication.md) (sync calls), [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (events)
- DRG-481 (BC-12-01), DRG-482 (BC-12-02 hierarchy), DRG-483 (BC-12-03 territory), DRG-484 (BC-12-04 merge), DRG-691 (BC-12-18 FE), DRG-687 (BC-13-08 opportunity-service, the parallel pattern)

**Code stays held.** This ADR authorizes no code, module, schema, migration, gateway route, topic, registry entry, flag or deploy. DRG-692 says "Hold: do not start extract until ADR is decided." DRG-692 scope items 2 to 6 stay held until this ADR is Accepted (or Rejected with a keep-in-crm rationale), and each then needs its own story and PR.

---

## Context

DRG-692 asks for a decision on whether the customer Account moves out of `crm-service` into its own `account-service`. Its stated reasons are clearer BC-12 ownership, account APIs that evolve independently of Leads and Deals, and public naming (`Account` vs `Organization`) for CPQ consumers that already use `AccountId`.

What exists on `main` (`ae74f83d0d7489f634da2ac3d6fecb2d99fd38f0`):

- **Aggregate.** Account is `Organization` in crm-service, table `crm.organizations`: `backend/services/crm-service/src/main/java/com/opencrm/crm/organization/Organization.java:18`. It has a parent link (`parent_organization_id`, `:91-92`), soft delete (`@SQLRestriction("is_deleted = false")`, `:95`) and a contact count computed from `crm.contacts` (`:102`).
- **API.** `/api/v1/organizations` (`OrganizationController.java:23`) supports create, list, get, update, set parent, hierarchy, delete, search and contacts (`:30-132`). Merge is at `/api/v1/organizations/merge` (`merge/AccountMergeController.java:19`). Territory and segment are under `/api/v1/organizations` (`territory/TerritoryAssignmentController.java:26-61`).
- **Same-schema neighbours.** Contacts (`crm/contact/`), Deals, Leads, buying roles and playbooks all live in crm-service. Organization's contact count reads `crm.contacts` directly (`Organization.java:102`).
- **Migrations.** Account-related crm Flyway versions are V8 (hierarchy), V9 (territory/segment), V10 (merge), V11 (organization reference guards) and V16 (hierarchy audit), all in `crm-service/src/main/resources/db/migration/`.
- **Events.** crm-service publishes `OrganizationCreated`, `HierarchyChanged`, `TerritoryAssigned` and `AccountsMerged` (`backend/libs/common/src/main/java/com/opencrm/common/event/EventContractRegistry.java:152-153,172-173,196-201`). No `Account*`-named event exists. No backend service other than crm-service references these event classes (repo grep, 2026-11-11).
- **Gateway.** A single `crm-service` route carries `/api/v1/leads/**,/api/v1/deals/**,/api/v1/contacts/**,/api/v1/organizations/**,...` (`backend/services/gateway-service/src/main/resources/application.yml:46-49` and `:450-453`).
- **Frontend.** `frontend/libs/crm/organizations` (`organization.service.ts`, `organizations.routes.ts`, list, detail and form components).
- **Consumers.** quote-service stores the account only as `quotes.customer_external_id` (`backend/services/quote-service/src/main/java/com/opencrm/quote/quote/Quote.java:22,41`). CAM assets store `account_id` from `QuoteAcceptedEvent.customerId` (`contract-asset-management/.../asset/Asset.java:40-44`).
- **Topology.** Ports 8080 to 8090 are assigned ([ARCHITECTURE.md](../ARCHITECTURE.md) §4). There is no `account-service` module. ADR-008 "does not authorize new schemas".
- **ADR-036.** That ADR (Proposed) puts the account-to-tenant link (`organization_tenant_assignments`, `AccountTenantAssigned`) in crm-service, next to the `organizations` aggregate. Whatever this ADR decides about the Account also decides where ADR-036's link would live.

## Questions to decide (DRG-692 scope item 1)

The options below each come from DRG-692 or from the repo facts above. Each cost is the repo fact behind it. This ADR records no recommendation. CPQ Decision scores the draft, and drg decides.

### Q-01: Extract or keep in crm
- **(a) Keep in crm-service** and settle naming only (Q-02). Nothing to move. Hierarchy, territory, merge and contact counts stay same-schema.
- **(b) Extract to `account-service`.** This needs a new module, a new schema (amending ADR-008, as ADR-024 did for `orders`), a new port, a gateway route, and a data migration out of `crm.organizations` and its V8 to V11 and V16 tables.

### Q-02: Public API shape
- `/api/v1/accounts` as a new path, or an alias of `/api/v1/organizations`, or both during a cutover. The gateway already rewrites CPQ aliases (ARCHITECTURE.md §4, gateway row). Under ADR-009, any alias is routing only.

### Q-03: Contacts
- Contacts stay in crm-service unless this ADR moves them (DRG-692, "Out of scope (initial)"). If Accounts move and Contacts stay, `Organization.java:102` (contact count from `crm.contacts`) becomes a cross-service read, which ADR-008 rules out within one database.

### Q-04: Data ownership, migration, dual-read and cutover
- DRG-692 requires "no long-lived dual write without an explicit dual-read plan" and "no silent dual-write". An extraction must name one writer at every step and include a cutover runbook (DRG-692 AC).

### Q-05: Events
- Map `OrganizationCreated` to `AccountCreated` (DRG-692, BC-12-05). Under ADR-018, a rename is a new contract or version, not an in-place change. Any dual-emit period needs an end date.

### Q-06: Relation to ADR-036
- If (b): ADR-036's tenant link and its events would be owned by account-service, so ADR-036 needs an amendment before either is accepted.
- If (a): ADR-036 stands as written.

### Q-07: Security
- Any new service follows ADR-016 (per-service JWT, `/internal` service tokens). Authorization must match the current Organization policy in `dev` and JWT modes (DRG-692 AC).

## Out of scope

Taken from DRG-692: PartnerAccount (BC-16), tenant and identity admin (BC-19; Account is the customer account, not the login tenant), and moving Lead or Deal out of crm-service. DRG-692 scope items 2 to 6 are covered only as consequences of Q-01.

## Consequences (by option, not decided)

- **(a) Keep:** no migration. Naming drift (`Organization` in code, `Account` in product language) stays unless Q-02 adds an alias.
- **(b) Extract:** one more service, schema, port and route. One-time data migration and cutover runbook. Event versioning work under ADR-018. ADR-036 must be amended. Delivery then follows the DRG-692 ACs (Flyway and health in CI, gateway authz parity, BC-12-01 behaviour, FE cutover, Testcontainers ITs).

## Acceptance

This ADR stays **Proposed**. Acceptance needs drg's explicit decision on Q-01, plus answers to Q-02 to Q-06 for the chosen option. Until then, no DRG-692 implementation PR may open.

## References

- Linear [DRG-692](https://linear.app/drgamtd-workspace/issue/DRG-692), read 2026-11-11.
- Notion [BC-12-19](https://app.notion.com/p/40bf7cb4e4df813fb8dfeabed1a92cb3) in Open-CRM-CPQ Backlog (Status "Not started"; "Hold: Do not start extraction until the ADR is decided").
- Notion hub [Project OPEN-CRM-CPQ](https://app.notion.com/p/3e3f7cb4e4df8197abf7d804245c5df4).
- Repo files cited above at `d3256ae4`; [ADR-036](ADR-036-account-tenant-ownership.md); [ARCHITECTURE.md](../ARCHITECTURE.md) §4.
