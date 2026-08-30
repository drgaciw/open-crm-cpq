# Wave J architect review

**Date:** 2026-08-30  
**Reviewer:** `/opencrm-architect`  
**Plan:** `/home/username01/Projects/open-crm-cpq/docs/forge/19-wave-j-architecture.md`  
**Baseline:** Wave I COMPLETE on `main` @ `9292e9e`

## Verdict

**APPROVE** — forge with binding notes below. PR DAG remains **J1 ∥ J2 ∥ J3 ∥ J4 ∥ J5 → J6**.

## Evidence (re-checked)

| Source | Finding |
|--------|---------|
| GitNexus status | Index **stale ~51 commits** (`c5075a8` → `9292e9e`). Re-analyze before symbol edits. |
| `PricingServiceClient` (quote, `--file …/client/PricingServiceClient.java`) | **LOW** — 1 direct IMPORTS → `QuoteService.java` |
| `QuoteService` (BE, `--file …/quote/quote/QuoteService.java`) | **LOW** graph (stale); **text:** `QuoteController` owns REST lifecycle/`calculate` → treat **MED product** |
| `ProductServiceClient` (quote) | **UNKNOWN** (0 edges + riskNote) — text: `validateConfiguration` used; UNKNOWN ≠ unused |
| `ProductRuleEvaluationCore` | **UNKNOWN** (Target not found / missing from index) — text: evaluator + ConstraintSolver + tests |
| FE `QuoteService` (`--file …/quotes/.../quote.service.ts`) | **LOW** — barrel → routes |
| GitHub issues | Open **#238**, **#241** only — correctly **HOLD** |
| GitHub PRs | Open Bolt **#412** (superseded by I3 — close), **#430** WhatsApp — **OUT** |

## Binding constraints (forge agents)

1. **J1 MUST NEVER** `RestClient.body(JsonNode.class)`. I2 proved Jackson 3 cannot construct `com.fasterxml.jackson.databind.JsonNode`. Quote `PricingServiceClient` **still** does `.body(JsonNode.class)` on `/execute` and `/simulate` — fix via **String + Jackson 2 `ObjectMapper`** like AI `CpqAgentToolClients`. Do not re-edit AI clients.
2. **J5 docs-only**; `approvals.legacy-local-read-enabled` default **stays `true`**. Flip = **HIGH** dual-store cutover — out of wave.
3. Do **not** add Activities, #238, #241, product-validate delete, gateway domain rules, WhatsApp #430, full SpEL, or Kafka S2B-9.
4. Disambiguate ambiguous names with `--file` / `--uid`. Wrong BE `QuoteService` path (`…/service/…`) yields Target not found.

## HIGH risks

| Risk | Wave action |
|------|-------------|
| Flip `approvals.legacy-local-read-enabled` → `false` | **HOLD** — J5 checklist only |
| Stale GitNexus (~51) / UNKNOWN symbols | Re-analyze; confirm with `rg`; UNKNOWN ≠ unused |
| J1 apply-path decimal / `PRICING_*` regressions | **MED product** — fixture-lock totals |

## Plan deltas applied this review

- Staleness **~42 → ~51**; survey tip → `9292e9e`
- Strengthened J1 **NEVER** `body(JsonNode.class)` + I2 String/Jackson-2 proof; confirmed live quote client still broken
- BE `QuoteService` `--file` path `…/quote/quote/…`; text-confirm `QuoteController`
- J-T5: removed “optional Java probe” ambiguity — docs-only / default stays true
- Removed obsolete “I panes still landing” language

## Holds confirmed OUT

Activities · #238 · #241 · product-validate delete · gateway domain rules · WhatsApp #430 · full SpEL · Kafka S2B-9 · dual-read default flip
