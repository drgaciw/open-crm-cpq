# ADR-031: Extensible field registry and saved views for CRM records

**Status:** Accepted on 2026-11-03 by the project owner ([delegation log](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82)). Proposed on 2026-10-15.
**Date:** 2026-10-15
**Deciders:** Project owner (drg amtd), 2026-11-03.
**Relates to:** [ADR-006](ADR-006-entities-never-at-api-boundary.md) (DTOs), [ADR-008](ADR-008-postgres-schema-per-service.md) (schema per service), [ADR-028](ADR-028-evidence-priced-fact-ledger.md) (agent-filled values).
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `FieldDefinition`, `FieldOption`, `FieldValue`, `SavedView` and `docs/plan/dynamic-fields.md`, at `6d4793d`.
**Does not authorize:** any code or migration.

---

## Context

Every tenant sells differently. Without custom fields, tenants either misuse description text or ask for code changes. A JSON column is the usual shortcut, but it cannot be indexed or sorted honestly, which breaks the rule that lists filter, sort and paginate in the database.

## Decision

### D-01 — Typed values, not JSON
Tables in `crm-service`'s schema: `field_definition` (tenant, entity, key, label, type, required, show_on_record, show_on_list, position, agent_filled, agent_brief, archived_at), `field_option` (definition, label, position, archived_at), `field_value` (definition, one nullable FK per entity, and one column per storage class: text, number `numeric(24,4)`, date, bool, option id, user id). Unique (definition, entity id). Indexes per storage class.

### D-02 — Entities and types
Entities: organization, contact, deal. Types: text, long text, number, date, checkbox, select, URL, email, phone, user. Multi-select is out of scope for v1.

### D-03 — Stable keys, archived not deleted
`key` is immutable and is what the API and agents use; `label` may change. A field with values is archived, never hard-deleted. Options are rows so a rename does not orphan values. Changing type is allowed only between compatible storage classes.

### D-04 — Standard fields in the same registry view
Standard fields are listed with custom fields; they can be hidden and reordered, not deleted or retyped.

### D-05 — Agent fill
`agent_filled` and `agent_brief` tell the enrichment agent what to look for. Agent-written values go through the fact ledger (ADR-028); the registry never lets an agent write `field_value` directly.

### D-06 — Saved views
`saved_view` (tenant, entity, owner, name, visibility private|tenant, filter, sort, columns). The filter is a small validated grammar over standard and custom field keys; unknown keys are rejected. List state is in the URL so links are shareable.

### D-07 — API shape
Records return custom values in a `fields` map keyed by `key` inside the DTO. Definitions have admin endpoints under `/api/v1/field-definitions/**` and views under `/api/v1/saved-views/**`, both owned by `crm-service` and added as gateway predicates and contract-matrix rows.

## Consequences
- Good: tenants extend without code; lists stay fast.
- Cost: dynamic query building in the repository layer; needs a whitelist-based builder, never string concatenation.

## Alternatives considered
- **JSONB per record.** Rejected: indexing and ordering by arbitrary keys is weak and type checks move to the application.
- **One table per entity's values.** Rejected: triples the code for no gain.
- **EAV with string values only.** Rejected: numbers and dates sort wrongly.
