# DRG-481 — Account and contact aggregates

An account is the CRM organization. **Decision:** its existing public UUID is the canonical cross-service `AccountId`; the numeric database primary key is a local legacy identifier. `AccountId` wraps a canonical UUID string. `Money` is unchanged, and `Percentage` stays out of crm-service.

Organization and contact services resolve the existing numeric request IDs to the UUID-backed `AccountId`; DTO `id` and `organizationId` fields and numeric routes stay unchanged. Existing `uuid` fields expose the shared ID. There is no new `/api/v1/accounts` alias or database-ID migration.

UUID-backed organization reads bypass the prior cache so a case-collision cannot return a cached different account; case-insensitive lookup may need a functional index if production traffic warrants it.

`OrganizationCreated` is the AccountCreated event. DRG-510 / #711 already publishes the existing organization UUID as `organizationId`; `ContactCreated` uses the same UUID. This change does not alter those event contracts. The replay test reconstructs its `AccountId` directly from each event, without reading the producer's organizations table, and compares the projection with the public API UUIDs. Applying the stream twice does not add accounts.

Before rollout, verify every existing `organizations.uuid` is a UUID and that no two values differ only by case. The database column is a string and its case-sensitive unique constraint does not guarantee either property. Lookup accepts uppercase legacy UUIDs, but ambiguous case variants fail closed. Invalid or ambiguous rows must be reconciled with downstream consumers before enabling UUID-backed lookups; silently replacing identifiers would break existing references and event history.
