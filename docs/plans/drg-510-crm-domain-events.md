# DRG-510 — CRM domain events

`crm-service` writes four domain events to `outbox_events` in the same transaction as the state change. The shared relay (`opencrm.outbox.relay.enabled`, off by default, ADR-018) is what sends them to Kafka. Domain code does not call `KafkaTemplate`.

| Event | Topic | When |
| --- | --- | --- |
| `OrganizationCreated` | `cpq.crm.organization.created` | `POST /api/v1/organizations`. This is the AccountCreated event of BC-12-01. |
| `ContactCreated` | `cpq.crm.contact.created` | `POST /api/v1/contacts` |
| `DealStageChanged` | `cpq.crm.deal.stage.changed` | The deal's stage changes, including a close |
| `DealClosed` | `cpq.crm.deal.closed` | The deal enters `WON` or `LOST` |

Contracts are appended to `EventContractRegistry` and `KafkaTopics` as PUBLISHED, owner `crm-service`, version 1. Existing constants are unchanged.

`DealClosed` carries `dealId`, `organizationId`, `outcome`, `previousStage`, `closedAt`, and `lostReason`. It does not carry amount, currency, discount, price, or an approval decision. Quote acceptance may set those fields on the deal row; they are not copied onto this event.

The `actor` column is the ADR-016 step 4 envelope value the relay copies to the Kafka `actor` header: `user:<oid>` for a person, `service:<client-id>` for an agent application (`idtyp=app`).

Open Host Service does not apply: there is no new REST endpoint. Audit of commercial decisions does not apply: these events are pipeline and CRM state, not pricing, discount, or approval decisions.
