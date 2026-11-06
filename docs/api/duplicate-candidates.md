# Duplicate candidates (Open Host Service, v1)

Published read-only duplicate suggestions for organizations and contacts. Version is `v1`. These endpoints suggest possible duplicates. A suggestion is not a merge and does not change the row.

Merge is `POST /api/v1/organizations/merge` (BC-12-04). A user calls that command explicitly. This API never calls it.

## Requests

`GET /api/v1/organizations/{id}/duplicate-candidates`

`GET /api/v1/contacts/{id}/duplicate-candidates`

`{id}` is the CRM row id used by the existing organization and contact APIs. The gateway already routes `/api/v1/organizations/**` and `/api/v1/contacts/**` to crm-service.

Both responses are `ApiResponse<List<DuplicateCandidateDto>>`.

## Response fields

Each element of `data` has only these fields:

| Field | JSON | Meaning |
| --- | --- | --- |
| candidateId | number | Row id of the other organization or contact |
| candidateType | string | `ORGANIZATION` or `CONTACT` |
| reason | string | `DOMAIN`, `EMAIL`, or `NAME_SAME_ORG` |
| matchedValue | string | Normalized domain, email, or `first last` name |

An unknown id returns HTTP 404 with `RESOURCE_NOT_FOUND`. A soft-deleted organization or contact, including an organization already merged away, also returns HTTP 404. BC-12-04 closes the losing organization with `organizations.is_deleted`. There is no separate merged flag.

No candidates returns HTTP 200 and `data: []`. The source row is never an element of its own list.

A contact whose status is not `ACTIVE` (including `ARCHIVED`, `INACTIVE`, and `DO_NOT_CONTACT`) returns HTTP 200 and an empty list. It is not a candidate of an active contact either.

At most 50 candidates are returned.

## Match rules

Organization candidates use reason `DOMAIN`. The compared value is the normalized `websiteDomain`. When `websiteDomain` is null or blank, the normalized `website` is used instead. `https://www.Acme.com/about` and `acme.com` match. Values are normalized when read. Create and update still store the raw website and domain.

Free-mail and consumer domains never imply an organization. They never produce an organization candidate and never count as a same-company signal for contacts. Matching is the normalized domain, exact or as a subdomain suffix (`mail.yahoo.com` is free-mail; `notgmail.com` and `gmail.company.com` are not).

The fixed list is:

gmail.com, googlemail.com, outlook.com, hotmail.com, hotmail.co.uk, live.com, msn.com, passport.com, yahoo.com, yahoo.co.uk, ymail.com, rocketmail.com, icloud.com, me.com, mac.com, aol.com, aim.com, proton.me, protonmail.com, pm.me, gmx.com, gmx.net, gmx.de, mail.com, yandex.com, yandex.ru, zoho.com, zohomail.com, fastmail.com, hey.com, tutanota.com, tuta.io, qq.com, 163.com, 126.com, mail.ru, web.de, comcast.net, verizon.net, att.net, sbcglobal.net

Contact candidates prefer reason `EMAIL`: active contacts with the same trimmed, lowercased email, including contacts in different organizations. Plus-tags and Gmail dot rules are not applied.

When that email search finds nobody, reason `NAME_SAME_ORG` suggests active contacts with the same normalized first and last name and the same `organization_id`. The same name in a different organization is not a candidate. A free-mail address does not turn that into a match.

Candidates require `is_deleted = false`. Contact candidates also require status `ACTIVE`.

## Query bound

Organization lookup is one page of 50 rows, ordered by id. The pattern is the full normalized domain, with LIKE wildcards escaped, compared to `website_domain`, or to `website` only when the domain is blank. Exact equality is then checked in memory. Contact email lookup is exact `LOWER(TRIM(email))` equality, also capped at 50. Same-organization name lookup loads at most 50 active contacts in that organization and compares names in memory. There is no migration and no new index.
