# ADR-039: Asset package stays in contract-asset-management for this sprint

**Status:** Proposed on 2026-11-24. Not accepted. Acceptance and merge are the owner's gate.
**Date:** 2026-11-24
**Deciders:** Project owner (drg). Requested by Projects Manager (drg's asset-mgmt workstream, 2026-11-23), task W0-4.
**Backlog:** [DRG-652](https://linear.app/drgamtd-workspace/issue/DRG-652) (epic). Task W0-4 of the asset-management sprint.
**Relates to:** [ADR-037](ADR-037-asset-installed-base.md) (Accepted; Asset / installed base; placement vs CAM stays open), [ADR-035](../renewals-amendments/adr-035-amendment-document-placement.md) (amendment document and contract header in contract-asset-management; does not place the Asset), [ADR-008](ADR-008-postgres-schema-per-service.md) (no new schema), [ADR-038](ADR-038-account-service-extraction.md) (a keep-in-place decision for a different aggregate).
**Does not authorize:** any extraction, move, new module, schema, migration, registry entry, flag change, code change or deploy. Leaves [ADR-037](ADR-037-asset-installed-base.md) in effect. Does not close placement vs CAM (DRG-561).

---

## Context

The Asset package is already in the contract-asset-management module. On `main` at `db8ae627`:

- Maven module: `backend/pom.xml` lists `services/contract-asset-management`. The service artifact is `contract-asset-management` (`backend/services/contract-asset-management/pom.xml`).
- Java package: `com.opencrm.contract.asset` under `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/`. `Asset.java` is in that package. Its class comment says the entity "Lives in contract-asset-management until placement versus CAM (DRG-561) is decided."
- Tests for the same package live in `backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/`. There is no second Asset package elsewhere in the repo.

[ADR-037](ADR-037-asset-installed-base.md) records the DRG-653 aggregate, API, events and outbox. It leaves placement open: the asset context may later become its own service or schema, or stay a package in contract-asset-management. ADR-035 placed the amendment document in contract-asset-management. It did not place the Asset. Sprint work on epic [DRG-652](https://linear.app/drgamtd-workspace/issue/DRG-652) needs an explicit instruction not to extract or move the package while that question is still open.

## Decision

For this asset-management sprint, the Asset package stays in the contract-asset-management module and service. No extraction. No move.

The package that stays is `com.opencrm.contract.asset` (main and test) inside `backend/services/contract-asset-management`.

### Scope

- In scope: where that package lives for this sprint.
- Out of scope: revising ADR-037; the lasting placement question (DRG-561); amendment documents (ADR-035); subscriptions and entitlements; a new service or schema; asset holds, read roles, migrations and event shapes. Those stay as ADR-037 and their own stories left them.

### Time-box

This sprint only (epic DRG-652, task W0-4). It does not outlive the sprint.

**Revisit trigger:** the sprint ends, or a story proposes to extract the Asset package into its own module or service or to move it out of contract-asset-management, whichever comes first. That revisit is the open placement question ADR-037 leaves to DRG-561. This record does not answer it.

## Consequences

- Sprint stories keep the Asset package in `backend/services/contract-asset-management`. They do not add a module, rename the package, or move the files.
- A later extraction or move needs its own decision after a revisit trigger. ADR-008 still authorizes no new schema.
- ADR-037 was accepted on 2026-11-23. Acceptance of this sprint placement proposal is a separate owner decision.
- While this record is Proposed it authorizes nothing. Accepting it still authorizes no code, migration, flag or deploy. It only records that this sprint does not extract or move the package.

## Alternatives considered

- Extract the Asset package to its own module or service in this sprint. Not for this sprint: the package is already in contract-asset-management, ADR-037 still has placement open, and ADR-008 authorizes no new schema. Revisit at the trigger above.
- Leave the sprint placement unrecorded and rely on ADR-037. ADR-037 says the question is open and that the package may move later. Without this record, sprint work has no explicit "do not extract or move" decision.

## References

- Linear epic [DRG-652](https://linear.app/drgamtd-workspace/issue/DRG-652). Task W0-4 (asset-management sprint, 2026-11-23).
- [ADR-037](ADR-037-asset-installed-base.md), including the open placement item (DRG-561).
- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/Asset.java` on `db8ae627`.
