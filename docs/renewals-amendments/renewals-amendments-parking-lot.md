# Renewals and amendments: parking lot (not active, future scope)

Status: **PARKED. None of the items below is an active development story.** They are not in the first slice (S1 to S8), have no estimate, and must not be started without a new decision. Recorded 2026-10-16 from a product discussion. Tracked as RA-53 / DRG-587 (one parent item, "[PARKED]").

## Context
ACI's on-premise customers are largely financial institutions that will not allow usage-monitoring connectivity, so they self-report usage. Today there is no systematic way to see on-premise usage in the product. The self-report workflow idea is a manual usage-number entry plus an attestation step by a rep or customer-ops.

Relevant existing content: the P2 capacity-meter policy and the Scenario 2 capacity true-up in `cpq-pricing-policy-sanity-check.md` (gaps G3 to G6: measurement method, provenance, frozen snapshot, backdating), MeterReading and TrueUp intent (RA-23), and the hash-chained audit log from S2 (`amendment-technical-approach-and-dependencies.md`).

## Parked items

### PL-1 Usage self-report entry record
- **Description:** a record per customer, contract line and period holding the metric (for example cores, users, transactions), the reported number, unit, period start and end, and who entered it.
- **Dependencies:** MeterReading model (RA-23); P2 measurement-method decision (sanity check G3); contract and line identifiers from the contract header (S7).
- **Not an active dev story.**

### PL-2 Rep or customer-ops attestation step
- **Description:** a reported number is not usable until an attester confirms it. Captures attester, date, and an evidence note (for example a customer email or signed certificate reference). Open choice: attester role and whether a customer-side sign-off is also needed.
- **Dependencies:** PL-1; role model; provenance field on meter readings (sanity check G6).
- **Not an active dev story.**

### PL-3 Usage to true-up and capacity-meter link
- **Description:** an attested report becomes a frozen MeterReading snapshot that feeds the P2 capacity true-up (Scenario 2) and renewal sizing.
- **Dependencies:** PL-1, PL-2; P2 policy definition (measurement method, tolerance, rounding, backdating); TrueUp intent (RA-23); the typed-policy split proposed in the sanity check.
- **Not an active dev story.**

### PL-4 Audit trail for usage reports
- **Description:** every create, edit and attestation of a usage report is appended to the existing hash-chained audit log, so a self-reported number can be shown unchanged later.
- **Dependencies:** S2 audit log; OQ14 chain scope; PL-1, PL-2.
- **Not an active dev story.**

### PL-5 Usage-based renewal uplift justification
- **Description:** show attested self-reported usage next to a proposed renewal uplift so a rep can justify it to the customer. Where no attested report exists, the uplift cannot cite usage.
- **Dependencies:** PL-1 to PL-3; renewal policy (RA-34 area); see OQ21.
- **Not an active dev story.**

## Entry criteria to unpark
First slice shipped; P2 policy defined; product confirms attester roles; Legal confirms the evidence retention rule (relates to OQ8).
