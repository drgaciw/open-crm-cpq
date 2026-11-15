# Contracts and Assets screens: narrow hold exception (DRG-761)

**Owner decision, 2026-11-15.** The forge hold on "contract-asset FE" (waves K, M and N) is lifted for read-only screens only. Everything else in that hold stays held.

## Why

The Jev Playwright MCP UAT (DRG-753) found that the webapp has no Assets or Contracts screens, so installed-base workflows cannot be run in the browser (DRG-761). Wave M deferred this FE until the API had a DTO and a paginated list. Both exist now: `SubscriptionDto` and `GET /api/v1/subscriptions` (paginated).

## In scope

- `frontend/libs/cpq/contracts` (`@frontend/cpq-contracts`):
  - `/contracts`: a paginated contract (subscription) list from `GET /api/v1/subscriptions`.
  - `/contracts/:id`: contract detail from `GET /api/v1/subscriptions/{id}`, with its assets from `GET /api/v1/assets?subscriptionId=`.
  - `/assets`: the installed base from `GET /api/v1/assets`.
- Sidebar links for Contracts and Assets.
- Asset reads require `InstalledBase.Read` (see `docs/runbooks/asset-read-access.md`). In dev mode the API answers 403 to anonymous requests by design. The screens say so plainly instead of showing an error or an empty list.

## Still held

- Subscription lifecycle actions: activate (which syncs ERP), suspend and cancel. Renew is retired (DRG-543).
- Amendments, asset history and supersede-chain views, and asset reports.
- Any write to contract-asset-management from the browser.
- Any change to asset access rules or a dev bypass for `InstalledBase.Read` (ADR-016).

## How contracts appear in a local stack

A subscription is created only when contract-asset-management consumes `cpq.quote.status.accepted`. The outbox relay is off by default (`opencrm.outbox.relay.enabled`). Locally, follow `docs/runbooks/event-operations.md` section 2:

1. Skip the test-data backlog (step 5).
2. Start quote-service with `OPENCRM_OUTBOX_RELAY_ENABLED=true`.

An accepted quote then becomes a PENDING contract.
