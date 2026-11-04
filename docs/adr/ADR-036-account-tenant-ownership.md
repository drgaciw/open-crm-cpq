# ADR-036: crm-service owns the account-to-tenant mapping

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
**Does not authorize:** any code, table, endpoint or flag. This proposal is incomplete and does not establish a trusted customer-tenant authority.

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
- No tenant service exists, because BC-19-02 / DRG-306 is in Backlog. An admin-entered identifier alone is not evidence of a verified customer tenant or of the caller’s access to it.

## Candidate decision (not yet accepted)

The user delegated the product choices for account identity and merge authorization. The recommended boundaries below are recorded to guide this proposal, but they cannot substitute for the missing authoritative tenant source or a verified consumer implementation. Until those dependencies exist, tenant-sensitive capabilities introduced by this proposal must remain held; existing operations require a separate audit.

### D-01: Proposed ownership of account → tenant
CRM would own a link from an account (`organizations`, by public UUID) to at most one current tenant id; other services would consume it without writing it. This proposed link is not itself proof of the tenant, of the account-to-tenant relationship, or of a caller’s access. No verified authority is established by this ADR.

### D-02: Proposed audited write path
The following is a candidate design, not an existing endpoint or store. A role check alone is insufficient; scoped authorization and independent verification remain prerequisites.
- The mapping is set or changed only by an admin-scoped command, `PUT /api/v1/organizations/{id}/tenant`, role-gated per ADR-016 decision 7.
- It is never taken from the caller's `tid`, from a request body on ordinary organization create or update, or as a side effect of any other command.
- Every change writes an audit row to **`organization_tenant_assignment_history`**: organization id, old tenant, new tenant, actor `oid`, time and reason. This table is the interim store until BC-20-01 exists.
- The current mapping lives in **`organization_tenant_assignments`**, following the V9 `organization_territory_assignments` pattern.

### D-03: Proposed published language
Once its missing revision and authority contracts are specified, every change would emit **`AccountTenantAssigned` v1** through the crm outbox (ADR-018), in the same transaction as the mapping change.

Illustrative fields for a future registry entry (`EventContractRegistry`); this incomplete list is not an implemented or accepted event schema:

| Field | Type | Notes |
|---|---|---|
| `organizationUuid` | string (uuid) | required |
| `tenantId` | string, or null | null means unmapped |
| `previousTenantId` | string, or null | |
| `assignedAt` | timestamp (UTC) | required |
| `assignedBy` | string | actor `oid` |

- Topic: per the existing crm `KafkaTopics` convention.
- The payload tolerates unknown properties (ADR-018 D5).
- The proposal also includes a read-only CRM lookup, `{organizationUuid, tenantId, assignedAt}`, in the Open Host Service style used for GetSegment (DRG-713). An unmapped account returns `tenantId: null`.

### D-04: How consumers learn the tenant (option for drg)
- **(a) Local projection: recommended by CPQ Decision.**
  - Consumers (quote-service first) keep a small table keyed by account uuid, fed by `AccountTenantAssigned` through the inbox (ADR-018 D6).
  - Draft capture could avoid a synchronous CRM dependency only where its unresolved state cannot cause tenant-sensitive effects; consequential writes still need an authoritative, revision-bound check.
  - Cost: one table per consumer, and eventual consistency (an account mapped a moment ago may read as null).
- **(b) Synchronous read.**
  - The consumer calls the crm lookup on quote create and clone.
  - No projection table; the response is current only at the instant it is read. A subsequent write still needs a revision-bound concurrency protocol.
  - Adds a runtime dependency on the write path, needs service-token auth (ADR-016 D5), a timeout and fail-closed handling for tenant-sensitive operations.

Under either option:
- An unresolved or unverified mapping must not authorize quote acceptance, contract transitions, account merges, or other tenant-scoped effects. Harmless draft capture may retain an explicit unresolved state only if it cannot cause those effects; there is no fail-open switch for tenant-sensitive operations.
- If an account-source mode is implemented and enabled, it must not fall back to `tid`. The named mode is proposed, not an existing configuration switch.

### D-05: Delegated merge and hierarchy boundaries
- **Merge across tenants: block.** Both accounts must have verified current assignments to the same tenant; unknown, conflicting, or changing assignments block the merge. Check and bind the mapping revisions under a defined concurrency protocol, and record an audited reason.
- **Hierarchy: no inheritance in v1.** A child account gets no tenant from its parent; each account must have its own verified assignment. Enforce this at every entry point.

### D-06: Relation to ADR-016 and the `tid` interim (DRG-632)
- ADR-016 is unchanged. `tid` keeps identifying the issuer's tenant for authentication only.
- This proposal does not supersede the 2026-10-23 interim. A separately accepted decision and verified implementation are required before replacing it as the customer-tenant source.
- A future design should record the provenance of quote tenant values (`TID`, `ACCOUNT`, or unknown for older rows), so downstream consumers can treat unverified values as unresolved. This provenance is not claimed to exist today.
- Any future switch from `tid` to a verified account source requires an accepted rollout plan and validation; no such switch is authorized by this proposal.

### D-07: Path to BC-19 (DRG-306)
- A tenant id is an opaque reference. CRM can own the account-to-tenant link, but it cannot treat an arbitrary entered reference as verified. The authority, evidence, and validation mechanism are unresolved until a trustworthy source is available.
- When BC-19 lands, crm-service may validate assignments against provisioned tenants. The event and consumer contracts must be reviewed for compatibility with that authority before enablement.
- Moving ownership of tenants themselves to BC-19 would require a later decision; this proposal recommends retaining the account → tenant link in CRM.

## Open decisions and prerequisites

Before acceptance, specify a trusted tenant authority and scoped administrative and service authorization; a transactional per-account revision and tombstones; snapshot/bootstrap and replay; duplicate, gap and out-of-order handling; and an atomic consumer inbox/projection transaction. Specify how revisions are bound to consequential writes, how existing TID-derived rows are reconciled without rewriting history, and how chained merges and late events preserve lineage. The proposed event and lookup schemas above are incomplete until these are resolved.

1. **Tenant id format.** A UUID we mint, or an external customer id? Open; there is no recommendation.
2. **Merge rule.** Block mismatched or unresolved verified tenants; define the atomic concurrency protocol and lineage.
3. **Inheritance.** No inheritance in v1; enforce it at every entry point.
4. **Consumption.** Design revisioned projections and an authoritative write-boundary check; specify failure and recovery semantics before selecting an implementation.
5. **Acceptance of this ADR.** It remains Proposed and authorizes no code until the prerequisites above are resolved and accepted.

## Consequences
- **Potential benefit:** one audited write path for the link alongside the CRM account aggregate, if the proposal is accepted and implemented.
- **Potential benefit:** consumers could use a verified tenant binding after its authority and delivery guarantees are implemented; the mapping alone cannot supply verification.
- **Cost:** two crm tables and a new event, plus a quote-side projection (option a) or a runtime dependency (option b).
- **Risk:** accounts would remain unresolved until a verified source and an authorized, audited mapping or backfill process exist. An administrator entering a value does not establish its validity.
- **Risk:** the `tid` values already stored on quotes are per-deployment only and must not be read as customer tenants.

## Alternatives considered
- **BC-19 identity/tenancy service owns the mapping.** Rejected for now: BC-19-02 is in Backlog and no tenant service exists. D-07 keeps this path open.
- **quote-service owns the mapping.** Rejected: it would duplicate account master data in CPQ, cannot see account merges, and would need a second copy for CAM.
- **Keep `tid`, or use a per-user or per-group claim.** Rejected: ADR-016's single issuer cannot tell customers apart, and a user's group describes the employee, not the customer account.
- **A static configuration map.** Rejected: no audit, no events, and a redeploy for every change.
