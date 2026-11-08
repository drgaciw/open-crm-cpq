# DRG-593 — Renewal quote creation modes (Decision brief)

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593/renewal-quote-creation-modes-auto-generate-from-terms-or-start-from) (RA-59). Call decisions 2026-10-16 (#5, #9).

**Status:** Proposed Decision brief. This file records what Cluster D already adopted for creation modes, maps the landed writers on `main`, and surfaces **remaining owner forks as OQx**. **It authorizes no production code path, no schema change, no Flyway migration, no flag flip, no route, no Kafka consumer registration, and no deploy.** `opencrm.pricing.ramp.enabled` stays `false`. `opencrm.outbox.relay.enabled` stays `false`. No `opencrm.jev.*.enabled` flag is turned on. S3 / DRG-581 stays unstarted. First slice remains amendments-only; this renewal-modes work stays outside that slice until a separate implementation story is authorized.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (do not contradict)

| Artifact | Relation to this brief |
|---|---|
| [cluster-d-renewal-automation-decision-frame.md](cluster-d-renewal-automation-decision-frame.md) § DRG-593 | Historical forks **QM-A..QM-D**. Recommendations retained as rationale. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | **Adopted** QM-A, QM-B, QM-C, QM-D baseline defaults. Also keeps the single-line zero-uplift 12-month auto-generate boundary until delivered pricing contracts widen it. |
| [renewal-policy-spec.md](renewal-policy-spec.md) §1.8 / AC-RN-23..25 | Working proposal for modes + prior-term usage; RN1–RN5 still apply. Spec status remains a proposal (not an ADR). |
| [specs/renewal-automation.md](specs/renewal-automation.md) AC-593-1..4 | Proposed test criteria; do not re-decide QM forks. |
| [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) DRG-568 (draft) | Escalators/locks ACs + fixtures; `OF-568-*` stay Proposed; ramp flag off; does not widen `draftRenewal` or invent a creation mode. |
| [#812](https://github.com/drgaciw/open-crm-cpq/pull/812) DRG-664 (draft) | `subscriptions.renewal_mode` (`AUTO_ROLL` \| `MANUAL` \| NULL fail-safe). Quote-accepted renewal math unchanged; unattended auto-roll execution out of scope. PERPETUAL null-end CHECK remains Proposed / NO-GO. |
| DRG-555 brief in flight | Same Cluster D frame + [specs/renewal-automation.md](specs/renewal-automation.md) § DRG-555; [#800](https://github.com/drgaciw/open-crm-cpq/pull/800) records the renewal-automation checklist line (555/593/596). Term-end actions and clocks are RN-*; creation modes stay QM-* / OQ-593-*. This brief does not reopen RN-A..D. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Accepted / adopted / Proposed docs cited in Alignment. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had no usable local index (`list_repos` empty; `.gitnexus/run.cjs` absent). Graph impact for docs-only is **UNKNOWN**, not an all-clear. Text search confirms the writers below.

## Call decision (already on the issue)

`verified by reading the issue`.

1. A renewal quote is created two ways: **auto-generate from the contract's terms** (term, lines, renewal pricing policy) or **start from scratch**. The rep chooses at creation.
2. A renewal quote may **optionally include prior-term usage as context**. Opt-in per quote; informational; does not change pricing unless a pricing policy explicitly consumes it.

## Adopted (do not re-open as owner forks)

From the [delegated record](delegated-renewals-amendments-decisions-2026-10-17.md) Cluster D disposition index (`verified by reading docs`):

| Fork | Adopted disposition (summary) |
|---|---|
| **QM-A** | Rep chooses at manual or API create. Lead-time kickoff (when kickoff is on) always uses auto-generate. Start-from-scratch is never the job's mode. |
| **QM-B** | Keep the landed single-line, zero-uplift, 12-month draft as the interim auto-generate boundary. Do not widen `draftRenewal` in this brief. Multi-line copy and resolved RenewalPricingPolicy wait on delivered pricing contracts (DRG-549 / related). |
| **QM-C** | Prior-term usage is opt-in, off by default, context only; omitted from the pricing snapshot unless the resolved policy declares a usage input. On-prem self-reported usage stays parked ([DRG-587](https://linear.app/drgamtd-workspace/issue/DRG-587)). |
| **QM-D** | Manual and API create are the described entry points while the eligibility consumer stays unregistered. Ramp flag stays false. Renewal outbox relay stays disabled. DRG-611 quote-side backstop is not launched by this brief. |

Renewal-automation contract (same delegated record): versioned CAM settings; independent CAM daily kickoff scan at `endDate - leadTimeDays` when opted in; no unattended path to `EXECUTED` in this slice; no listener / relay / ramp / Jev flag enabled by those choices.

## What the tree does today (renewal quote paths)

`verified by reading code` at the base SHA.

1. **Eligibility writer.** `QuoteContinuationService.draftRenewal` builds one `RENEWAL` continuation from exactly one accepted recurring line, with null uplift and null term → `QuoteContinuationPolicy.plan` uses **0% uplift** and **12 months**, start the day after subscription end. A live renewal for that start date is returned unchanged (no second draft). On create it inserts one `commercial_decision_audit` row (`SYSTEM_RENEWAL_DRAFT` / `RENEWAL_EXIT_RATE`). There is **no creation-mode field** and **no start-from-scratch path**.
2. **Eligibility consumer.** `SubscriptionRenewalEligibleEventConsumer` is `@ConditionalOnProperty(name = "opencrm.pricing.ramp.enabled", havingValue = "true")`. Committed config is false → listener absent. Publication alone opens no draft (matches AC-593-4).
3. **Manual / API continuation.** `preview` / `author` require the continuation flag path and a typed request. When `fromActiveAssets(request)` is true, routing goes through `AssetRenewalDrafts` ([DRG-658](https://github.com/drgaciw/open-crm-cpq/pull/742) on `main`). That path is asset-sourced authoring; it is not yet a QM mode selector.
4. **Date-only renew.** `POST /api/v1/subscriptions/{id}/renew` remains 410 Gone; successor is `POST /api/v1/quotes/continuations`.
5. **Pricing.** `draftRenewal` does not call `RenewalUpliftResolver` or pricing-service. Escalator expansion (`RampEscalatorExpander`) is not on the renewal draft path while the ramp flag is off ([#807](https://github.com/drgaciw/open-crm-cpq/pull/807) encodes ACs; does not flip behavior).
6. **`usageBand`.** Copied into the eligibility audit inputs. It is **not** the prior-term usage context of QM-C / §1.8.

## Cross-brief contracts (hold these)

### With DRG-568 / #807

- Multi-year escalators and time-bounded locks are **pricing-shape** work. Creation mode does not select escalator semantics.
- While `opencrm.pricing.ramp.enabled` is false, production renewals stay **one flat period**. Auto-generate (QM-B interim) stays the single-line flat draft.
- `OF-568-1`..`OF-568-9` remain Proposed on that draft; this brief invents no competing escalator default.
- Widening auto-generate to emit segment schedules is **not** authorized here; see **OQ-593-3** and **OQ-593-7**.

### With DRG-664 / #812

- `subscriptions.end_date` = current committed period end (`NOT NULL`). Evergreen / auto-roll advances through the RENEWAL path; it does not null that column.
- `renewal_mode` NULL / unknown / `MANUAL` = fail-safe **no auto-roll** (today's behaviour). `AUTO_ROLL` records intent only in that slice; unattended term roll is out of scope there.
- Creation modes remain orthogonal: `MANUAL` (or null) still allows rep-chosen modes on manual/API create (QM-A). `AUTO_ROLL` does **not** by itself open a draft or force start-from-scratch. See **OQ-593-6**.
- PERPETUAL `chk_contracts_dates` null-end fork stays Proposed / NO-GO on #812; this brief does not pick it.

### With DRG-555 (Cluster D / automation spec)

- Term-end actions (`Renew`, `RenewOnce`, `Evergreen`, `Cancel`) run only when `auto_renew` is true (RN-A adopted). That is not a creation-mode choice.
- Kickoff (when `kickoff_opt_in` is true and inside the lead window) uses **auto-generate** (QM-A). Start-from-scratch stays manual/API only.
- Unattended path does not reach `EXECUTED` in this slice (RN-D). No contradiction with QM-D's "listener stays unregistered."
- This brief does not reopen RN-A..D, AS-A..D, or OQ-RN-DRIVE (superseded by the adopted CAM kickoff scan design; delivery still open).

## Remaining owner forks (OQx)

Every row below is **Proposed, unaccepted, owner decision required**. Alternatives are written beside the recommendation. Picking a row authorizes **no** code, migration, flag, or deploy. These are the open questions that survive the adopted QM-A..D baselines.

| ID | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|
| **OQ-593-1** | SaaS prior-term usage **payload shape** (policy [OQ-RN-8](renewal-policy-spec.md#4-open-questions)) | Leave shape unspecified until metering DTO is chosen; AC-RN-25 / AC-593-3 still hold (opt-in stores a reference; snapshot omits unless policy declares usage). | Define a closed DTO now (meter id, period, quantity, source); or bind to an existing MeterReading type before any opt-in UI. | Metering / capacity stories; not DRG-587 (parked). |
| **OQ-593-2** | Project an **open, unapplied mid-term amendment** into auto-generate? (policy [OQ-RN-7](renewal-policy-spec.md#4-open-questions)) | Copy the **applied term only** (policy proposal default). | Project the open amendment's commercial envelope into the draft; or refuse auto-generate while a non-terminal mid-term change exists. | S3 / amendment spine; digest contracts. |
| **OQ-593-3** | **When** does auto-generate leave the QM-B interim (single-line, 0% uplift, 12 months) for the call-text target (every renewable line + resolved RenewalPricingPolicy)? | Keep interim until DRG-549 remaining modes **and** a delivered pricing confirmation contract exist; then a separate implementation story widens the writer. | Widen `draftRenewal` immediately; or widen only the CAM kickoff writer and leave eligibility `draftRenewal` flat forever. | DRG-549, DRG-539 list bases, DRG-609 design acceptance, DRG-564 confirmation store. |
| **OQ-593-4** | Start-from-scratch **artifact**: Renew LifecycleChange with zero copied lines (policy AC-RN-24 / AC-593-1) vs empty quote-service `RENEWAL` draft (today's continuation model)? | Target = LifecycleChange on the amendment spine; interim may keep a quote-service draft with zero lines until S3 is authorized. Do not invent a second document kind. | Quote-only forever; or dual-write quote + LifecycleChange from day one. | S3 / DRG-581 separate authorization; OQ6 revision model. |
| **OQ-593-5** | **Wire shape** for mode + usage opt-in on manual/API create | Additive request fields on the continuation create (`creationMode`, optional `priorTermUsageRef` / opt-in flag). Kickoff/system paths omit mode and always auto-generate. No new public route in this brief. | Separate endpoints per mode; or store mode only on a CAM settings/kickoff operation and omit it from quote-service. | API / OpenAPI completion; not authorized here. |
| **OQ-593-6** | How creation modes interact with **`renewal_mode`** (DRG-664 / #812) | Orthogonal. NULL/`MANUAL`: rep chooses modes on manual/API create. `AUTO_ROLL`: still no draft from eligibility alone; kickoff (when opted in) uses auto-generate per QM-A; unattended EXECUTED stays refused (RN-D). | Treat `AUTO_ROLL` as implied kickoff+auto-generate; or refuse start-from-scratch when `AUTO_ROLL` is set. | #812 merge + settings row (DRG-596 delivery). |
| **OQ-593-7** | When ramp is later enabled, does auto-generate **emit DRG-568 segment schedules** or stay flat until a priced confirmation? | Stay flat in the draft writer; segment expansion belongs to the separately authorized, flag-gated pricing path (#807 ACs). Creation mode does not flip the ramp flag. | Expand segments inside auto-generate when an escalator is on the policy; or copy escalator intent as display-only without priced periods. | #807 owner Decision on `OF-568-*`; ramp flag stay false until that Decision. |
| **OQ-593-8** | Do **asset-sourced** renewals (`AssetRenewalDrafts`, DRG-658) expose the same two modes + usage opt-in? | Same QM-A choice at manual/API create; kickoff/system still auto-generate only; asset path must not invent a third mode. | Asset path auto-generate only; or asset path start-from-scratch only. | DRG-658 landed writer; renewal policy client on that path. |
| **OQ-593-9** | When term-end action is `Renew` / `RenewOnce` (DRG-555) and a system renewal is later authorized, which creation mode applies? | Always **auto-generate** (same as kickoff). Start-from-scratch remains rep-only. | Allow term-end to open start-from-scratch; or skip quote creation and write periods directly (rejected by RN-D / no unattended EXECUTED). | DRG-555 delivery; signature policy OQ-RN-3 (Legal); S3. |

### Parked (not an OQ to solve in this brief)

- **DRG-587** on-prem self-reported usage (PL-1..PL-5, OQ21). Stays parked. QM-C option C is not taken.

## Out of scope (explicit)

- Any Java/TypeScript change, Flyway migration, feature flag, workflow, or gateway route.
- Registering `SubscriptionRenewalEligibleEventConsumer` or enabling the renewal outbox relay.
- Flipping `opencrm.pricing.ramp.enabled` or any `opencrm.jev.*.enabled` flag.
- Accepting [renewal-policy-spec.md](renewal-policy-spec.md) as an ADR.
- Re-opening adopted QM-A..D, RN-A..D, or AS-A..D defaults.
- Implementing DRG-534 ramp amendments.
- Marking Linear DRG-593 Done.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-593-1..9** as the residual owner menu without re-litigating QM-A..D. Accepting / merging this file **does not** open an implementation story, does not unblock DRG-549 catalog work, does not enable kickoff, and does not change `draftRenewal`.

## References

- Linear: DRG-593, DRG-555, DRG-596, DRG-544, DRG-549, DRG-567, DRG-568, DRG-587, DRG-664, DRG-658.
- GitHub drafts: [#807](https://github.com/drgaciw/open-crm-cpq/pull/807), [#812](https://github.com/drgaciw/open-crm-cpq/pull/812), [#800](https://github.com/drgaciw/open-crm-cpq/pull/800).
- Code (read-only): `QuoteContinuationService.java` (`draftRenewal`, `author`, asset branch), `SubscriptionRenewalEligibleEventConsumer.java`, `AssetRenewalDrafts.java`, `QuoteContinuationPolicy.java`.
- Holds: [AGENT.md](../../../AGENT.md) forge holds; Cluster D holds table (relay, ramp, S3, S6, DRG-611).
