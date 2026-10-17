# Documentation acceptance — 2026-10-17

**Status:** Accepted as documentation by the project owner on 2026-10-17.

The owner wrote **“I accept the documentation proposals”** after the processing and merge of PRs #648–#661. This record captures that acceptance against `main` at `7352d4227c33f75bc7eadb5f9ba5ce589596f572`, the merge of [closeout #661](https://github.com/drgaciw/open-crm-cpq/pull/661).

## Accepted documentation

The acceptance covers these decision frames and their documentation corrections in #661:

| PR | Accepted documentation |
|---|---|
| [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) | [Cluster C: true-up and meters](cluster-c-trueup-and-meters-decision-frame.md) |
| [#649](https://github.com/drgaciw/open-crm-cpq/pull/649) | [Cluster D: renewal automation](cluster-d-renewal-automation-decision-frame.md) |
| [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) | [Cluster A: amendment modes and policy](cluster-a-amendment-modes-and-policy-decision-frame.md) |
| [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) | [Cluster E: LifecycleChange model](cluster-e-lifecyclechange-model-decision-frame.md) |
| [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) | [Cluster B: on-prem to SaaS and maintenance](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) |
| [#653](https://github.com/drgaciw/open-crm-cpq/pull/653) | [DRG-616: OQ6 design-contract gaps](story-drg-616-oq6-design-contract-gaps.md) |
| [#654](https://github.com/drgaciw/open-crm-cpq/pull/654) | [DRG-572: one-time and retroactive charges](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) |
| [#655](https://github.com/drgaciw/open-crm-cpq/pull/655) | [Cluster F: fulfilment](cluster-f-fulfilment-decision-frame.md) |
| [#656](https://github.com/drgaciw/open-crm-cpq/pull/656) | [Cluster G: visibility, queues and migration](cluster-g-visibility-queues-migration-decision-frame.md) |
| [#657](https://github.com/drgaciw/open-crm-cpq/pull/657) | [S3 reconciliation and owner-decision frame](s3-reconciliation-and-owner-decision-frame.md) |
| [#658](https://github.com/drgaciw/open-crm-cpq/pull/658) | [DRG-549: renewal pricing resolution](drg-549-renewal-pricing-resolution-design-frame.md) |
| [#659](https://github.com/drgaciw/open-crm-cpq/pull/659) | [DRG-576: product relationships and eligibility](story-drg-576-product-relationship-and-eligibility-decision-frame.md) |
| [#660](https://github.com/drgaciw/open-crm-cpq/pull/660) | [DRG-575: ARR bridge per change line](story-drg-575-arr-bridge-per-change-line-decision-frame.md) |

It also covers the draft OpenAPI, field-schema addendum and requirements reconciliation delivered with DRG-616 and corrected by #661:

- [Draft amendments OpenAPI](DRAFT-openapi-amendments.yaml).
- [Amendment entity field schema](../amendment-entity-field-schema.md), including the DRG-616 addendum.
- [First-slice requirements and acceptance criteria](../amendment-first-slice-requirements-and-acceptance.md), including the DRG-616 status reconciliation.

This is acceptance of the documentation batch. The document-level “Proposed, unaccepted” headers are superseded by this record. Historical source quotations and reading-base observations retain their original dates and wording.

## Decision and delivery boundaries

The frames document alternatives, recommendations and defaults for later decisions. Documentation acceptance does not select every recommended default or resolve every fork: per-fork “Proposed” labels and explicit unresolved questions remain decision requirements. In particular, a default that defers a choice is not a choice of one of its alternatives.

The unresolved CAM pricing synchronization and delivery protocol, digest-at-close and race contract, durable commercial-envelope storage contract, renewal trigger timing, fulfilment state/transport choices and ARR computation writer remain unresolved where the accepted frames say so. AC-S3-9 through AC-S3-11, AC-S5-7 and AC-S8-6 remain proposed contract criteria until their specified gaps are resolved.

OQ6 retains the rule recorded under delegation in [its canonical decision](oq6-revision-model-owner-decision.md). OQ18 remains subject to its separate confirmation; the competing CAM-local interim and shared-library proposals are not both adopted by this documentation acceptance. ADR-017 and ADR-024 through ADR-027, the separate DRG-609 design and the sibling policy/specification proposals retain their existing statuses. No ADR number is assigned.

Acceptance authorizes recording these documents as accepted documentation. It authorizes no implementation story, code, schema or migration, feature flag, topic, gateway route, data backfill or deploy. The [S3–S8 authorization gate](story-s3-s4-authorization-gate.md), [S5 readiness gate](story-s5-lifecyclechange-readiness.md) and the holds in [AGENT.md](../../../AGENT.md) continue to apply. No Linear issue transition or story completion is claimed.
