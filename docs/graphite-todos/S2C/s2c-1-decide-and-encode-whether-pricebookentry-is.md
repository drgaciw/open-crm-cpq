# S2C-1: Decide and encode whether PriceBookEntry is an entity under PriceBook; add effective-period and immutable-publication tests.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S2C`

**Service/Module:** backend/services/pricebook-service

**Actionable Todo:** Decide and encode whether PriceBookEntry is an entity under PriceBook; add effective-period and immutable-publication tests.

**Stack Dependency:** S2A-1

**Reference Doc:** backend/services/pricebook-service/DDD_SPEC.md; MODERNIZATION_PLAN.md

## Implementation Notes

- Encoded `PriceBook` as the aggregate root and `PriceBookEntry` as a child entity under the price book aggregate.
- Entries inherit the parent price book's effective publication window.
- Published price books and their entries are immutable; create a future-dated replacement price book for changes.
