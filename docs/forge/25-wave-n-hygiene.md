# Wave N hygiene (N′) — quote errorCode banner ∥ matrix completeness

**Status:** APPROVED FOR FORGE — PM picked architect option **A** (hygiene before Wave O) 2026-08-31  
**Architect:** `opencrm-architect` (Herdr `w8` verdict)  
**Baseline:** Wave N COMPLETE `main` @ `5f126ca`

## Tracks

| ID | Title | Scope | Risk |
|----|-------|-------|------|
| **H1** | Quote delete banner uses real `errorCode` | `quotes-detail` `httpErrorMessage` + spec; no shell | **LOW** (GitNexus `httpErrorMessage` **MEDIUM** — 7 same-file callers; warn) |
| **H2** | Matrix N3 DELETE row + N1 PUT/DELETE notes + dual-read J–N | `CPQ_API_CONTRACT_MATRIX.md`; optional n4 addendum | **LOW** docs |
| **H3** | TestBed hookTimeout | Only if already in price-books/pricing-customers | **optional, not blocker** |

**Parallel:** H1 ∥ H2. No Wave O. Dual-read YAML stays true.

## Holds

Activities, #238, #241, product validate deletion, gateway domain validation, dual-read flip, WhatsApp #430, contract-asset FE, NgRx, SpEL, Kafka S2B-9.
