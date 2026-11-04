# GetSegment (Open Host Service, v1)

Published read for the segment assigned to an account. Version is `v1`. The path and the response field names are the contract. Consumers DRG-422 (BC-01 eligibility) and DRG-441 (BC-04 commercial policy) must use only this endpoint. They must not read `crm-service` entities or tables.

## Request

`GET /api/v1/organizations/{id}/segment`

`{id}` is the CRM organization row id. The response `organizationId` is the account UUID, not that row id.

The published type is `com.opencrm.crm.api.v1.segment.OrganizationSegmentResponse`, wrapped in `ApiResponse`.

## Response fields

| Field | JSON | Meaning |
| --- | --- | --- |
| organizationId | string | Account UUID |
| territory | string or null | Territory code |
| segment | string or null | Segment code |
| ruleId | number or null | Rule that produced the assignment |
| assigned | boolean | Whether a segment is assigned |

An assigned organization returns HTTP 200. `assigned` is `true` and `segment` is populated.

An existing organization with no current assignment returns HTTP 200. `assigned` is `false` and `segment` is null. `territory` and `ruleId` are null as well.

An unknown organization returns HTTP 404.

While the territory-assignment hold is in force, this route returns HTTP 503 with `TERRITORY_ASSIGNMENT_NOT_READY`, as described in `docs/runbooks/territory-assignment-hold.md`. That hold is unchanged.
