# crm-cpq

Angular feature library for CPQ user journeys owned by the CRM microsite.

## Boundaries

- Catalog: product and bundle discovery used while building quotes.
- Configuration: guided option selection, constraints, and compatibility flows.
- Pricing: price breakdowns, adjustments, discount context, and approval signals.
- Quotes: quote drafting, review, approval submission, and document handoff.

Service clients and backend contracts stay in shared data-access or generated API libraries. This library owns route composition and CPQ feature UI only.
