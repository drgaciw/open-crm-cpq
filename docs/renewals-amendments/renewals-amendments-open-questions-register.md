# Renewals and amendments: consolidated open-questions register

*Date: 2026-10-16. Documentation only. Status values: BLOCKS DEV (cannot start the named story without it), DEFAULT PROPOSED (can start on the stated default; confirm by the date given), NOT BLOCKING (later work or go-live). Owner is a role proposal [I]. Source files: spike section 6.5 and decisions, spec, schema section 7, ADR-035, adjacent services assessment, transcript section 9.*

| ID | Question | Status | Proposed default and rationale | Blocks | Owner | Needed by |
|---|---|---|---|---|---|---|
| OQ1 | Accept ADR-035 (document in CAM contract-documents module, thin header, LifecycleChange stays in quote-service)? | **BLOCKS DEV** | Accept as written | S1 and everything after | Architect or user, Jev-routed Choice | Day 0 |
| OQ2 | Apply point | RESOLVED 2026-10-16 | Apply only after EXECUTED, single status column | none | n/a | done |
| OQ3 | Contract header enrichment vs DRG-458 | DEFAULT PROPOSED | Thin header now (id, number, tenant, customer ref, start, end, status, last_executed_seq, prior_contract_id), names aligned to DRG-458 | S1 | Architect | Day 0 |
| OQ4 | Numbering | RESOLVED 2026-10-16 | Provisional at DRAFT, final at EXECUTED, no renewal reset | none | n/a | done |
| OQ5 | Partial application | DEFAULT PROPOSED | One unit per subscription, atomic per unit, no auto rollback, document PARTIALLY_APPLIED, remedy by correcting amendment | S4 | Product plus architect | before S4 (Day 2) |
| OQ6 | Revision model | DEFAULT PROPOSED | New revision under the same amendment number; one EXECUTED revision (V11); drafts only edit | S3 | Architect | before S3 |
| OQ7 | Backfill scope | DEFAULT PROPOSED for slice | Slice: contract header only (S7), no synthetic history. History backfill stays DRG-566 | S7 history only | Product | before DRG-566 |
| OQ8 | Retention N years, legal hold, disposal | NOT BLOCKING dev; **blocks production go-live** | Column `retention_until` nullable; assume contract end + 7 years as placeholder, not used | go-live | Legal | before go-live |
| OQ9 | Does pricing-service support AmendmentPolicy modes and PolicyDecision trace ids? Unverified | **BLOCKS S5 until verified** | One day read-only spike; fallback RetainContracted with override flag | S5 | Pricing owner | Day 1 |
| OQ10 | Which signature policies ship in slice? | DEFAULT PROPOSED | NOT_REQUIRED, NOTICE_ONLY, CLICK_THROUGH only; DRG-457 adapter later | S3 | Product | Day 0 |
| OQ11 | Legacy path rollout flag granularity | DEFAULT PROPOSED | Per tenant flag, default off, on for internal tenant first | S6 | Platform | before S6 |
| OQ12 | Slice action codes | DEFAULT PROPOSED | Add, Update, Remove only; Swap, Upgrade, Downgrade wait for RA-42 / DRG-576 | S5 | Product | Day 0 |
| OQ13 | Who may VOID an executed document | NOT BLOCKING | Out of slice; role `contract-admin` plus Legal reason, proposal only | later | Legal, product | before VOID story |
| OQ14 | Hash chain scope | DEFAULT PROPOSED | Per document chain; periodic per-contract anchor deferred | S2 | Architect | before S2 |
| OQ15 | Price-lock intent: own intent vs Amend | NOT BLOCKING | Decide with RA-34 | later | Product | before DRG-568 |
| OQ16 | AMENDED_AND_RESTATED replaces or adds to governing terms; reseller signatures; merge intent | NOT BLOCKING | Out of slice | later | Legal | before restate story |
| OQ17 | Approval thresholds that trigger CONTRACT_MODIFICATION for non-override amendments | DEFAULT PROPOSED | Approval only for overrides, backdating, or total ARR delta above a configurable threshold; initial value set by Finance | S5 | Finance | before S5 end |
| OQ18 | Digest canonicalization library location | DEFAULT PROPOSED | `backend/libs/common` | S3, S5 | Architect | before S3 |
| OQ19 | Performance targets | DEFAULT PROPOSED | See requirements non-functional section | S8 | Platform | before S8 |
| OQ20 | Linear label naming: existing items carry both `renewals-amendments` and `Open-CRM-CPQ` | RESOLVED for new items | New items carry both | none | n/a | done |

## Summary
Blocking development start: **OQ1** (decision) and **OQ9** (verification, only gates S5). Everything else has a default that lets the first stories start. Needed at go-live only: OQ8. The deferred items (OQ13, 15, 16) belong to later policies.
