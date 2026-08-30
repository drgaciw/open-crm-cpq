---
status: done
---
# b0-crm-search

## Summary
Align Lead/Deal search to `ApiResponse<List<T>>` + `PageInfo` (Contact/Org already matched).
FE services tolerate `T[]` or legacy `{content}` via `p.content ?? p`. Lead convert navigation hardened.

## Files (representative)
- LeadController / DealController search return types
- Lead/Deal/Contact/Org controller ITs for search shape
- FE lead/deal/contact/organization services + lead-detail
- shared api-response.model + interceptor/lead service specs
- docs/CRM_API_PARITY.md

## Tests
Local `mvn` hit JDK EndPosTable environment error in this sandbox; changes mirror existing Contact/Org pattern.
