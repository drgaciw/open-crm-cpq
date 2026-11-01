# Territory and segment assignment hold

Territory rule creation/listing, assignment and segment reads return HTTP 503 with
`TERRITORY_ASSIGNMENT_NOT_READY`. The same guard applies to direct service calls;
no rule, assignment or outbox row is read or written. There is no runtime switch to
release this hold. It is a temporary availability constraint, not completion of
DRG-483.

Before a code change releases the hold, deliver and verify all of the following:

- A server-enforced sales-operations capability and authoritative account/tenant
  scope for writes and reads, including direct-service and cross-scope tests.
- Durable recomputation or invalidation when organization facts or rules change,
  plus a durable, replayable unassignment contract for a previously assigned account.
- A decision on overlapping rule changes and concurrent updates, with PostgreSQL
  and outbox tests. The current serialization tests cover concurrent assignments
  and organization-row updates, not a concurrent rule change.
- A safe transaction boundary for callers. The future-ready assignment path refreshes
  a managed assignment row after locking the organization. Callers must not stage
  unflushed changes to that assignment in the same persistence context unless that
  behavior is explicitly guarded or isolated and tested; a refresh can discard them.
- Coordinated CRM Flyway ordering. Hierarchy uses V8, territory uses V9 and merge
  uses V10 on the corresponding branches; verify the actual deployment history and
  do not deploy a later version in a way that skips an earlier migration.

Before enabling traffic, drain old replicas that lack this hold and reconcile any
assignments or events they created. The hold neither retracts published events nor
repairs existing stale assignments. Review pending outbox rows under existing
operational controls.
