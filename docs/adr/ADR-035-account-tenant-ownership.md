# ADR-035: crm-service owns the account-to-tenant mapping

**Status:** Proposed on 2026-11-03. Not accepted.
**Date:** 2026-11-03
**Deciders:** Project owner (pending). Acceptance stays with drg.
**Relates to:**
- [ADR-016](ADR-016-identity-authentication-service-trust.md) (single tenant issuer, `tid`)
- [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (outbox, inbox, event registry)
- [ADR-017](ADR-017-synchronous-inter-service-communication.md) (synchronous calls, for option b)
- DRG-632 (`tid` interim on Quote, V1_13)
- DRG-630 (this decision's story)
- DRG-631 and DRG-662 (tenant on QuoteAccepted and on Subscription)
- DRG-306 / BC-19-02 (tenant provisioning)

**Source:** DRG-630 brief, which CPQ Decision passed with 86 on 2026-11-03 and ruled to be an ADR.
**Does not authorize:** any code, table, endpoint or flag.

---

## Context

Quote tenant assignment today uses the caller's Entra `tid`:
- DRG-632 added `quotes.tenant_id VARCHAR(128)` in quote-service V1_13.
- It is set on create and clone through `QuoteTenantResolver`, is immutable, and is null for service tokens and dev mode.

ADR-016 makes `tid` weak as a tenant: each deployment trusts one tenant-specific issuer, and multi-tenant issuers are refused. So `tid` is one value per deployment, not one per customer. drg chose it on 2026-10-23 as a deliberate interim, and CPQ Decision ratified it.

Three things depend on a real per-customer tenant:
- DRG-631 (tenant on QuoteAccepted);
- DRG-662 (Subscription tenant, which must equal the contract tenant);
- per-tenant S6 routing.

Today:
- The customer account is a crm-service aggregate (`organizations`, with hierarchy, territory/segment and merge). It has no tenant.
- quote-service knows the account only by `quotes.customer_external_id`.
- No tenant service exists, because BC-19-02 / DRG-306 is in Backlog.

## Decision

### D-01: crm-service is the system of record for account → tenant
crm-service owns a mapping from an account (`organizations`, by uuid) to at most one current tenant id. Other services never write it. They read it as downstream consumers.

### D-02: One audited write path
- The mapping is set or changed only by an admin-scoped command, `PUT /api/v1/organizations/{id}/tenant`, role-gated per ADR-016 decision 7.
- It is never taken from the caller's `tid`, from a request body on ordinary organization create or update, or as a side effect of any other command.
- Every change writes an audit row to **`organization_tenant_assignment_history`**: organization id, old tenant, new tenant, actor `oid`, time and reason. This table is the interim store until BC-20-01 exists.
- The current mapping lives in **`organization_tenant_assignments`**, following the V9 `organization_territory_assignments` pattern.

### D-03: Published language
Every change emits **`AccountTenantAssigned` v1** through the crm outbox (ADR-018), in the same transaction as the mapping change.

Schema registry entry (`EventContractRegistry`):

| Field | Type | Notes |
|---|---|---|
| `organizationUuid` | string (uuid) | required |
| `tenantId` | string, or null | null means unmapped |
| `previousTenantId` | string, or null | |
| `assignedAt` | timestamp (UTC) | required |
| `assignedBy` | string | actor `oid` |

- Topic: per the existing crm `KafkaTopics` convention.
- The payload tolerates unknown properties (ADR-018 D5).
- crm-service also offers a read-only lookup, `{organizationUuid, tenantId, assignedAt}`, in the Open Host Service style used for GetSegment (DRG-713). An unmapped account returns `tenantId: null`.

### D-04: How consumers learn the tenant (option for drg)
- **(a) Local projection: recommended by CPQ Decision.**
  - Consumers (quote-service first) keep a small table keyed by account uuid, fed by `AccountTenantAssigned` through the inbox (ADR-018 D6).
  - Quote creation does not depend on crm-service being up.
  - Cost: one table per consumer, and eventual consistency (an account mapped a moment ago may read as null).
- **(b) Synchronous read.**
  - The consumer calls the crm lookup on quote create and clone.
  - No projection table, and always current.
  - Adds a runtime dependency on the write path, needs service-token auth (ADR-016 D5), a timeout and a fail-open path.

Under either option:
- An unresolved tenant stores `NULL` and is made visible with a warning log and a counter. Creation does not fail, unless an optional reject flag is set (default off).
- `tid` is never used as a fallback once `opencrm.tenant.source=account` is selected.

### D-05: Merge and hierarchy (recommended options)
- **Merge across tenants: recommended to block.** An account merge whose source and survivor carry different tenants is refused, with an audited reason. The alternative, letting the survivor's tenant win, is open for drg.
- **Hierarchy: recommended no inheritance in v1.** A child account gets no tenant from its parent; each account is mapped explicitly. The alternative is default inheritance from the parent.

### D-06: Relation to ADR-016 and the `tid` interim (DRG-632)
- ADR-016 is unchanged. `tid` keeps identifying the issuer's tenant for authentication only.
- This ADR supersedes the 2026-10-23 interim **as a source of customer tenant**.
- Quote rows record where their tenant came from (`TID`, `ACCOUNT`, or NULL for older rows), so downstream consumers (DRG-631, DRG-662) can treat non-`ACCOUNT` values as unresolved.
- The switch from `tid` to the account source is an owner-run configuration change, and it is off by default.

### D-07: Path to BC-19 (DRG-306)
- The tenant id is an opaque reference owned by crm-service until BC-19-02 provides ProvisionTenant and TenantProvisioned.
- When BC-19 lands, crm-service validates assignments against provisioned tenants. Consumers and the event contract do not change.
- Moving ownership of tenants themselves to BC-19 would be a later ADR. Ownership of the account → tenant link stays with crm-service.

## Open decisions for drg
1. **Tenant id format.** A UUID we mint, or an external customer id? Open; there is no recommendation.
2. **Merge rule.** Block cross-tenant merges (recommended), or let the survivor win?
3. **Inheritance.** No inheritance in v1 (recommended), or a child inherits from its parent?
4. **Consumption.** Option (a) local projection (recommended by CPQ Decision), or (b) synchronous read?
5. **Acceptance of this ADR.** Until drg accepts it, it stays Proposed and authorizes no code.

## Consequences
- **Good:** one write path and one audit trail for tenant, next to the account aggregate that already handles merge and hierarchy.
- **Good:** consumers get the tenant without inventing their own source; DRG-631 and DRG-662 can follow.
- **Cost:** two crm tables and a new event, plus a quote-side projection (option a) or a runtime dependency (option b).
- **Risk:** accounts start unmapped. Tenants stay null until drg runs a backfill (owner-gated, S7-class, G04) or admins map accounts.
- **Risk:** the `tid` values already stored on quotes are per-deployment only and must not be read as customer tenants.

## Alternatives considered
- **BC-19 identity/tenancy service owns the mapping.** Rejected for now: BC-19-02 is in Backlog and no tenant service exists. D-07 keeps this path open.
- **quote-service owns the mapping.** Rejected: it would duplicate account master data in CPQ, cannot see account merges, and would need a second copy for CAM.
- **Keep `tid`, or use a per-user or per-group claim.** Rejected: ADR-016's single issuer cannot tell customers apart, and a user's group describes the employee, not the customer account.
- **A static configuration map.** Rejected: no audit, no events, and a redeploy for every change.
