# DRG-549 renewal pricing resolution — security criteria

**Status:** Criteria for the DRG-549 code slice. This file authorizes no flag, no gateway route, no deploy, and no real customer data.

**Decision check (before code).** Compared with merged [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) (`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`) and with S1/S2.

- #663 adopts the baseline `SAME` semantics: carry the prior accepted price. The design frame says that price is the exit net named by D-01 of `adr-renewal-uplift-basis-and-precedence.md` (`Same`: the exit net; basis and percent are ignored). The request already carries `exitNet`. This slice uses that field and does not invent another source.
- #663 does not name an HTTP status for a missing exit net. The binding CPQ Decision amendment maps a missing `exitNet` to **422** and forbids a guessed price. That is a wire mapping of "no price", not a different price. No STOP.
- S1 (`docs/renewals-amendments/s1-v1-7-contract-documents.md`) stores policy slots as tokens only. It does not store an exit net or a renewal method. S2 (DRG-580 audit hash chain) does not define a renewal price. Neither design supplies a prior price other than the caller-carried exit net.

**Impact:** UNKNOWN. No GitNexus index is available in this tree (`impact` returned no indexed repository). A text search of `backend/` for `RenewalUpliftResolver` finds the class and `RenewalUpliftResolverTest` only.

## 1. Validation schema

`POST /internal/v1/pricing/renewals/resolve` (see Authorization for why this path is internal). The body maps to `RenewalUpliftRequest`. No `tenantId` field. An unknown property, including `tenantId`, is rejected.

| Field | Rule |
|---|---|
| `currency` | Required. Three uppercase letters. |
| `exitNet` | Required. Decimal ≥ 0, at most 18 integer digits and 6 fractional digits. Missing or null is **422**. The resolver does not substitute zero. |
| `quantity` | Required. ≥ 0. |
| `termMonths` | Required. Integer 1..120. |
| `pins` | Required object. The selected price-as-of date and price-book version are required for a priced result. `listUnit` is required only when `notAboveList` is true. |
| `rungs` | Size 0..5. At most one rung per kind. Kinds: `CLAUSE`, `LINE`, `AGREEMENT`, `ACCOUNT`, `RULES`. A duplicate kind is 400. |
| rung `mode` | `SAME` or `UPLIFT_PCT` only. `LIST`, `UPLIFT_AMT`, `OVERRIDE`, and `RENEGOTIATE` are **422**, matching `RenewalUpliftModeOutOfSliceException`, not 500. |
| `upliftPercentPoints` | 0..100 inclusive when present. Percent points, not a fraction. |
| `retentionMarker` | Opaque string, at most 128 characters. Never interpreted as a price. |

Bean validation on this service already returns 422. Malformed JSON on this path returns 400. Bodies carry an error code and no stack trace.

`RenewalResolveLimits.MAX_BODY_BYTES` is **65536**. A larger body is rejected with `REQUEST_TOO_LARGE` before the resolver runs. This constant is the size bound for the slice. It is not a gateway limit and it is not an application.yml change.

## 2. Tenant isolation

CAM stores `renewal_policy_record` with `tenant_id`. Every repository finder declared for this store takes `tenantId`. The service drops a row whose tenant does not match the caller. The read controller takes tenant from the verified token claim `tid` (the same claim `UserContext.tenantId` reads for a person). The query string and the body cannot set it. There is no HTTP write route.

**Cross-tenant negative test (mandatory).** A policy stored for tenant A is invisible to a read as tenant B that repeats A's account, agreement, and line keys. A write as tenant B does not insert or version a row under A's tenant. The quote-service client has no tenant argument and does not send a tenant query parameter.

Pricing resolve reads no store. Its response is derived from the request. A body `tenantId` is rejected.

## 3. Authentication and authorization

The existing service authority is `Internal.Invoke`, mapped to `ROLE_INTERNAL_INVOKE`. It already gates `/internal/**`. This slice uses that authority and grants no new role.

- Resolve is `POST /internal/v1/pricing/renewals/resolve` with method security `ROLE_INTERNAL_INVOKE`.
- CAM read is `GET /internal/v1/renewal-policies` on the same chain.
- Anonymous is 401. A user token (including CPQ.User, SalesVP, DealDesk, CFO, Admin) is 403. A service token without `Internal.Invoke` is 403.
- No gateway route is added. The gateway already publishes `/api/v1/pricing/**`. Mounting the resolver on that prefix would publish it through the existing wildcard. The internal path is not in the gateway configuration.

**Gap.** `Internal.Invoke` is not limited to the quote-service client id. Any application token that already holds it can call. No existing authority names quote-service alone, so this slice grants nothing narrower. There is no Legal or Owner app role; those names are not created.

## 4. Rate limit and abuse

No request-rate limiter is added. The only in-repo limiter found is the CAM ERP sync Resilience4j limiter. The gateway is not edited. Defense in this slice is the internal service authority, the 65536-byte cap, at most five rungs, and a pure computation with no store write on the resolve path.

**Gap for the owner (DoD v0.3 items 20 and 21).** There is no edge rate limit, no per-caller quota, and no latency SLO. Item 21 (p95) is report-only: no p95 target is set, and this slice records no latency metric. Request data must not be placed on metric labels if a later metric is added.

## 5. Read-only resolve and preview

The resolve handler calls `RenewalUpliftResolver` and returns. It opens no write transaction, writes no `PricingAuditLog`, no outbox row, and no event. The success body has `preview: true` and `apiVersion`. A preview is not a confirmed commercial price (DRG-564/612 stay out of this slice).

`SAME` returns the request `exitNet` at unit scale 4, one flat period, and does not clamp. A unit outside a normalized cap or floor is `PRICE_PROTECTION_CONFLICT`. `UPLIFT_PCT` behavior is unchanged.

## 6. CAM migration

One additive migration, `V1_11__create_renewal_policy_records.sql`. Checked against `main` (highest CAM version is `V1_8`) and remote branches (no CAM `V1_9`, `V1_10`, or `V1_11`). `V1_9` stays reserved for S4. `V1_10` is left for S3. The guarded rollback script is `db/rollback/V1_11__drop_renewal_policy_records.sql`. It is not a Flyway version. It drops the table only when the table is empty. No existing migration is edited. No real data is seeded.
