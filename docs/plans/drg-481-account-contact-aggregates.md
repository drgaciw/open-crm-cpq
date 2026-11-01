# DRG-481 — Account and contact aggregates

An account is the CRM organization. `AccountId` in `com.opencrm.common.valueobject` wraps that row's existing `Long` id (`of(Long)`, `value()`, null and non-positive rejected). It is additive: `Money` is unchanged, and `Percentage` stays out of crm-service.

Organization and contact services and mappers take or produce `AccountId` internally. `GET/POST /api/v1/organizations` and `/api/v1/contacts` are unchanged: JSON `id` and `organizationId` remain `Long`. There is no `/api/v1/accounts` alias.

`OrganizationCreated` is the AccountCreated event. DRG-510 / #711 already writes it through the outbox. This story does not add a publisher, a topic, or a migration. `AccountReplayFromOrganizationCreatedTest` replays that stream into a projection keyed by `AccountId` and checks it against `GET /api/v1/organizations`. Applying the stream twice does not add accounts.
