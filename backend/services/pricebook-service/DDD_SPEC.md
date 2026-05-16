# Price Book Service DDD Specification

## Aggregate Boundary

`PriceBook` is the aggregate root for regional, default, and customer-facing price publications. `PriceBookEntry` is an entity inside the `PriceBook` aggregate because entry identity is meaningful only within a single price book and product external ID.

Entries inherit the parent price book publication window. Resolution only uses a price book when `effectiveDate <= today <= expirationDate`; a missing start or end is open-ended.

## Publication Rules

Once a price book reaches its effective date it is published. Published price books and their entries are immutable: callers must create a future-dated replacement price book instead of mutating the published aggregate.
