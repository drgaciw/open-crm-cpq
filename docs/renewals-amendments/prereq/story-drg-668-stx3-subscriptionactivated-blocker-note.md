# DRG-668 — S-TX3 blocker inventory / Decision note

**Status:** **Proposed.** Documentation only. Authorizes no code, migration, flag, topic, registry edit, or deploy.

**Story:** [DRG-668](https://linear.app/drgamtd-workspace/issue/DRG-668/s-tx3-proposed-add-subscriptionmodel-and-tenantid-to-the) — *S-TX3 (Proposed): Add subscriptionModel and tenantId to the SubscriptionActivated event (once ADR-026 delivers it)*.

**Reading base:** `main` at `0a3f0f8e` (2026-11-06). Evidence claims below were checked against that SHA.

**Scoring:** S-TX3 · CPQ Decision option **(b) re-scope** · **85%** · CPQ Decision 2026-10-29.

This note records why product work on DRG-668 **cannot start yet**, what must land first, the scope that becomes valid once unblocked, and open owner forks. It does **not** implement S-TX3. Linear status stays Backlog / In Progress; this note does not mark Done.

---

## 1. Verdict

**NO-GO for implementation.** The target event does not exist on `main`. ADR-026 names `SubscriptionActivated` as a reply example and is **Accepted as documentation only** — it still authorizes no registry entry, topic, or producer. The ADR-026 **implementing story is TBD**. Field sources for S-TX3 (`subscriptions.tenant_id`, `subscriptions.subscription_model`) are not on `main` either; they ride draft PRs for DRG-662 and DRG-666.

---

## 2. Blocker inventory

| Blocker | Current state (2026-11-06) | Why it blocks S-TX3 |
|---|---|---|
| **ADR-026 acceptance (docs)** | [ADR-026](../../adr/ADR-026-command-messages.md) **Accepted** 2026-10-17 — *documentation only*; catalog row in [docs/adr/README.md](../../adr/README.md): "authorizes no code, topic or registry entry" | Docs acceptance alone does not create `SubscriptionActivated`. S-TX3 adds fields to an **existing** event only. |
| **ADR-026 implementing story** | **TBD.** Linear search recorded on the ticket (2026-10-29): none. [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577) (Done) reconciled ADR-024..027 with the amendment spine; it is **related**, not the implementer. | Until a story registers and produces `SubscriptionActivated`, there is no DTO / registry contract / outbox publish site for S-TX3 to extend. |
| **`SubscriptionActivated` on `main`** | **Absent.** See §5 evidence. | Option (b) re-scope: this story does **not** invent the event; it only adds `subscriptionModel` + `tenantId` once the event exists. |
| **[DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661)** taxonomy ADR | **Accepted** (drg, 2026-10-29). Parent of S-TX3. | Schema-registry registration of taxonomy-bearing fields waits on Accepted taxonomy; still open who performs first registration (owner fork below). |
| **[DRG-662](https://linear.app/drgamtd-workspace/issue/DRG-662)** S-T1 `tenant_id` | **Accepted** story; implementation **draft** [#806](https://github.com/drgaciw/open-crm-cpq/pull/806) (not merged). `main` still has no `subscriptions.tenant_id` (V1_8 header comment: "subscriptions has no tenant_id"). | `tenantId` on the event is read from `subscriptions.tenant_id`, never from a request body. |
| **[DRG-666](https://linear.app/drgamtd-workspace/issue/DRG-666)** S-TX1 `subscription_model` | **Accepted** story; implementation **draft** [#808](https://github.com/drgaciw/open-crm-cpq/pull/808) (not merged). | `subscriptionModel` on the event comes from S-TX1 classification. |

**Depends on (merge order for field sources):** DRG-661 Accepted → DRG-662 (#806) + DRG-666 (#808) on `main` → ADR-026 implementing story delivers `SubscriptionActivated` → then S-TX3 may start under a separate owner Decision.

---

## 3. Scope once unblocked (option (b) — not authorized yet)

When every blocker in §2 is clear **and** owner Decision authorizes build:

1. **Additive only** on the **existing** `SubscriptionActivated` payload: `subscriptionModel` and `tenantId`.
2. **0 migrations** in the S-TX3 diff.
3. Publish through the CAM outbox in the **same transaction** as activation (ticket AC P05).
4. Schema-registry **BACKWARD** compatibility for the added fields; **no event version bump** (additive fields keep the version the implementing story already registered).
5. Null `tenantId` / `subscriptionModel` emitted as **absent** (`NON_NULL` / omit), not JSON null; tolerant readers accept payloads missing both fields.
6. Allowlist (ticket): `backend/services/contract-asset-management/**` (outbox/event publish + tests) and the SubscriptionActivated DTO path delivered by the ADR-026 implementing story (exact path recorded at launch). Forbidden: other services' migrations, workflow edits, flags, consumer-service product changes.

This section is a **reminder of the scored brief**, not a launch authorization.

---

## 4. Explicit NO-GO checklist

Do **not** start S-TX3 product code, registry edits, schema registration, migrations, or flags until **all** of the following are true:

- [ ] ADR-026 implementing story exists in Linear, is Accepted/authorized for build, and has delivered `SubscriptionActivated` (registry + topic + DTO + CAM outbox producer) on `main`.
- [ ] `EventContractRegistry` / `KafkaTopics` on `main` contain the SubscriptionActivated contract (today: **missing** — §5).
- [ ] DRG-662 (#806) merged: `subscriptions.tenant_id` available as the sole event source for `tenantId`.
- [ ] DRG-666 (#808) merged: `subscriptions.subscription_model` available as the sole event source for `subscriptionModel`.
- [ ] DRG-661 remains Accepted; first schema registration of taxonomy fields follows the owner choice in §6 (fork 4).
- [ ] Separate owner Decision / CPQ launch gate for **this** story's build (draft PR only until then).

Until then: docs and Decision notes only.

---

## 5. Code facts (evidence on `main` @ `0a3f0f8e`)

| Claim | Evidence | How checked |
|---|---|---|
| ADR-026 names `SubscriptionActivated` on `cpq.subscription.activated` as the success reply example | [ADR-026 D-01](../../adr/ADR-026-command-messages.md) ("Replies" bullet) | Read |
| ADR-026 is Accepted docs-only; does not authorize registry/topic/code | ADR-026 status line; [adr/README.md](../../adr/README.md) ADR-026 row | Read |
| No `SubscriptionActivated` (or `cpq.subscription.activated`) in the common event package | `rg` over `backend/libs/common` for `SubscriptionActivated`, `subscription.activated`, `ActivateSubscription` → **zero hits** | Search |
| Registry subscription contracts on `main` are renewal-eligible + ramp only | `EventContractRegistry.java`: `SUBSCRIPTION_RENEWAL_ELIGIBLE_V1` (~L112–114), `RAMP_APPLIED_V1` on `KafkaTopics.SUBSCRIPTION_RAMP_APPLIED` (~L121–122). No activated contract. | Read |
| Kafka topic constants: no activated topic | `KafkaTopics.java` L42–43: `cpq.subscription.ramp.applied`, `cpq.subscription.renewal.eligible` only | Read |
| Additive fields are non-breaking / no version bump precedent | `EventContractRegistry` class javadoc (~L45–46): adding a field is not breaking; breaking needs a new `eventVersion` | Read |
| `subscriptions.tenant_id` not on `main` | `V1_8__contract_header_backfill.sql` header: "subscriptions has no tenant_id"; no later CAM migration on `main` adds it (latest CAM Flyway on tree: V1_15) | Read + list |
| Field-source drafts | [#806](https://github.com/drgaciw/open-crm-cpq/pull/806) DRG-662 (draft); [#808](https://github.com/drgaciw/open-crm-cpq/pull/808) DRG-666 (draft) | `gh pr view` |

Paths (absolute from repo root):

- `docs/adr/ADR-026-command-messages.md`
- `docs/adr/README.md`
- `backend/libs/common/src/main/java/com/opencrm/common/event/EventContractRegistry.java`
- `backend/libs/common/src/main/java/com/opencrm/common/event/KafkaTopics.java`
- `backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql`

---

## 6. Open owner forks

These remain for **drg** (or delegated Decision). This note does not choose them.

| # | Fork | Default / notes | Blocks S-TX3? |
|---|---|---|---|
| 1 | **PERPETUAL end_date** (DRG-661 OQ) | Default Proposed: document contract end_date as maintenance coverage end; no `chk_contracts_dates` change unless Accepted with dry-run | Indirect (taxonomy clarity); not an S-TX3 code gate |
| 2 | **On-prem transactional minimum / ARR** (DRG-661 OQ) | Open | Indirect |
| 3 | **S-T2 customer account uuid source** ([DRG-663](https://linear.app/drgamtd-workspace/issue/DRG-663)) | Default: block apply when typed key missing | Related taxonomy cluster; not in S-TX3 allowlist |
| 4 | **Who registers** the first `SubscriptionActivated` schema, and later the S-TX3 field additions | Open on DRG-661: drg, or a CI step drg approves — **not agent work** | **Yes** for schema steps after the event exists |
| 5 | **File the ADR-026 implementing story** (owner, AC, allowlist, topic/DTO names) | Currently **TBD** | **Yes** — hard prerequisite |

---

## 7. Governance

- Docs only under `docs/renewals-amendments/prereq/` (+ ADR cross-links). **No** Java/TS, migrations, `EventContractRegistry` edits, or flags in this delivery.
- Draft PR only; never merge from this note; never mark Linear Done.
- Product implementation of S-TX3 stays **Proposed / NO-GO** until §4 clears.

**Cross-links:** [ADR-026](../../adr/ADR-026-command-messages.md) · [ADR catalog](../../adr/README.md) · [DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661) · [DRG-662](https://linear.app/drgamtd-workspace/issue/DRG-662) · [DRG-666](https://linear.app/drgamtd-workspace/issue/DRG-666) · [DRG-668](https://linear.app/drgamtd-workspace/issue/DRG-668)
