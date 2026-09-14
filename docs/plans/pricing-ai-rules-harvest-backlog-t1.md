# Track 1 harvest backlog (T1-D1)

**Parent plan:** [pricing-ai-rules-reuse-gap-analysis.md](./pricing-ai-rules-reuse-gap-analysis.md) §10.  
**Capability matrix:** [pricing-ai-rules-capability-matrix-t1.md](./pricing-ai-rules-capability-matrix-t1.md).  
**Source pin:** `3aa45bf164469364389e096df07e591c81079f6a` @ `/home/username01/Projects/pricing-ai-rules-service`.  
**Dual-read:** leave `${APPROVALS_LEGACY_LOCAL_READ:true}` — do not flip.  
**Track 2:** **HOLD** — no AI / Spring AI / agent implementation PRs in this wave.

Ranked serial PRs below match plan §10. **Do not** start T1-1…T1-5 code until T1-G0 is done (merged) and prefer T1-D1 docs merged first. **Do not** bump Drools before T1-1…T1-4 green. **Do not** put Spring AI in `pricing-service`.

---

## Maven env (every code PR)

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
# Unit tests (no docker):
cd backend && ./mvnw -B -pl services/pricing-service -am test -DskipITs
# ITs when Testcontainers needed:
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -B -pl services/pricing-service -am test
EOF
# rules-engine-only (T1-4 / T1-5):
./mvnw -B -pl libs/rules-engine,services/pricing-service -am test -DskipITs
```

**GitNexus (every code PR):**

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "<Symbol>" --direction upstream --repo /home/username01/Projects/open-crm-cpq
# UNKNOWN ≠ unused — confirm with rg
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

---

## DAG overview

```
T1-G0 (done #505) → T1-D1 (this docs PR)
                 ↘
                   T1-1 → T1-2 → T1-3 → T1-4 → T1-5
```

| ID | Status | Owner | Branch | Depends |
|----|--------|-------|--------|---------|
| **T1-G0** | Done (merged #505) | docs + local clone | `chore/pricing-ai-rules-g0-pin` | — |
| **T1-D1** | This PR | docs | `docs/pricing-ai-rules-t1-matrix` | T1-G0 |
| **T1-1** | Queued | `pricing-service` | `test/pricing-golden-fixtures-harvest` | T1-G0 (+ prefer T1-D1) |
| **T1-2** | Queued | `pricing-service` | `feat/pricing-metrics-calculator-parity` | T1-1 |
| **T1-3** | Queued | `pricing-service` | `feat/pricing-metric-gated-drl` | T1-2 |
| **T1-4** | Queued | `rules-engine` + `pricing-service` | `feat/pricing-drools-validate-parity` | T1-3 |
| **T1-5** | Queued | parent BOM | `chore/drools-bom-10-2-0` | T1-4 |

**Rejected shapes:** Drools bump before fixtures; AI/agent PRs in this wave; renaming execute→evaluate; Keycloak; Spring AI in `pricing-service`.

---

## T1-G0 — Pin & fixture inventory (done)

| Field | Value |
|-------|-------|
| Acceptance | Clone @ `3aa45bf…`; matrix stub with SHA + fixture inventory |
| Evidence | PR #505 / commit on `main` |
| Exclusive (approx.) | Local clone path; optional stub `pricing-ai-rules-capability-matrix-t1.md` |

---

## T1-D1 — Matrix + backlog (this deliverable)

| Field | Value |
|-------|-------|
| Owner | docs |
| Branch | `docs/pricing-ai-rules-t1-matrix` |
| Exclusive files | `docs/plans/pricing-ai-rules-capability-matrix-t1.md`, `docs/plans/pricing-ai-rules-harvest-backlog-t1.md` |
| Depends | T1-G0 |
| Acceptance | Matrix rows for execute / simulate / metrics / DRL / validate only; no AI “do now”; hybrid §5.1a called out; pin SHA recorded; backlog ranked T1-1…T1-5 |
| Out of scope | Harvest code, Drools BOM, T1-1 fixtures, forge status edits |

---

## T1-1 — Golden fixtures harvest (rank 1)

| Field | Value |
|-------|-------|
| Owner | `pricing-service` |
| Branch | `test/pricing-golden-fixtures-harvest` |
| Exclusive files (approx.) | `backend/services/pricing-service/src/test/resources/fixtures/pricing/**`, `PricingExecuteGoldenFixtureTest.java`, optional `PricingDrulesGoldenFixtureTest.java` |
| Depends | T1-G0 (+ prefer T1-D1) |
| Mode | B — adapt source goldens to **execute** / simulate / drl shapes |
| Hybrid note | Fixtures may assert metrics fields; derivation fixes land in T1-2, not DRL |

### Acceptance

- [ ] New/adapted goldens map source `evaluate` → target **`POST /api/v1/pricing/execute`** (and simulate where appropriate).
- [ ] Existing `execute/01`–`03` + `drl/*` fixtures still pass.
- [ ] Prefer coverage from: `pricing-evaluate-001`, `error-pricing-missing-quote-001`, `calculate_waterfall`, `simulate_amendment`, `reprice_renewal`, `evaluate_approvals` (as execute/drl vectors).
- [ ] `@Tag("unit")` green under Maven env above.
- [ ] **No** new `/api/v1/**` path; **no** Drools BOM change; **no** AI.

### GitNexus / risk

- Prefer test-only touch; if production symbols change, impact first.
- Confirm any `UNKNOWN` with `rg`.

---

## T1-2 — MetricsCalculator parity (rank 2)

| Field | Value |
|-------|-------|
| Owner | `pricing-service` |
| Branch | `feat/pricing-metrics-calculator-parity` |
| Exclusive files (approx.) | `MetricsCalculator.java`, `FinancialMetrics.java` (only if fields needed), `MetricsCalculatorTest.java`, related execute goldens asserting metrics |
| Depends | T1-1 |
| Mode | B — Java derivation only (§5.1a) |
| Spec source | `PricingRules.drl` rules `Calculate ARR` / `TCV` / `ACV` / `CLV` + evaluate golden financial vectors |

### Acceptance

- [ ] ARR / TCV / ACV / CLV (+ existing churn/discount fields as needed) match source calculate-* **spec** vectors.
- [ ] **No** DRL calculator ports (`Calculate ARR/TCV/ACV/CLV` stay out of `resources/rules`).
- [ ] GitNexus impact on `MetricsCalculator` + `rg MetricsCalculator` (resolve `UNKNOWN`).
- [ ] Unit + execute golden metrics assertions green.
- [ ] Dual-read untouched; no AI; no path rename.

### GitNexus / risk

- `MetricsCalculator` historically `UNKNOWN` in index — **not** unused; confirm callers (`PricingExecutionService`, tests) via `rg` before edit.

---

## T1-3 — Metric-gated policy DRL (rank 3)

| Field | Value |
|-------|-------|
| Owner | `pricing-service` |
| Branch | `feat/pricing-metric-gated-drl` |
| Exclusive files (approx.) | `src/main/resources/rules/*.drl` (**additive only**), seed/Flyway **only if** rule-set seed required, `fixtures/pricing/drl/**` |
| Depends | T1-2 |
| Mode | B — policy DRL consuming precomputed metrics |
| Primary candidates | `TCV-based Pricing Tiers`, `ACV-based Minimum Commitment`; churn only if product gap vs renewal-uplift remains |

### Acceptance

- [ ] Policy DRL only — **no** Calculate ARR/TCV/ACV/CLV DRL.
- [ ] Phase 0 overlap skipped or explicitly reconciled (esp. ARR-gated volume vs named-seat qty discount — avoid double discount).
- [ ] Sports / vertical packs **excluded**.
- [ ] Preserve seeded Phase 0 rule names/explanation codes; new rules = additive catalog entries.
- [ ] Compile + drl goldens green.
- [ ] No new external API path.

### GitNexus / risk

- Impact any shared fact / session helper symbols before edit.
- Conflict resolution between quote-level and line-item writers documented in PR body.

---

## T1-4 — Drools validate / compile parity (rank 4)

| Field | Value |
|-------|-------|
| Owner | `rules-engine` + `pricing-service` |
| Branch | `feat/pricing-drools-validate-parity` |
| Exclusive files (approx.) | `backend/libs/rules-engine/src/main/java/**` (compiler/helpers), `RuleService.java` (+ tests); **no** parent `drools.version` yet |
| Depends | T1-3 |
| Mode | B/C — adapt source `DroolsService` validate/compile edge cases to JDK **21** |
| Spec / goldens | `rule-validate-001.json`, `error-rule-validate-invalid-001.json`, `rule-compile-001.json` |

### Acceptance

- [ ] Compile/validate edge cases adapted; `RuleServiceTest` + `rules-engine` tests green.
- [ ] Existing `POST /api/v1/rules/{id}/validate` behavior strengthened — **no** new `/api/v1/**` path.
- [ ] Target remains Java 21 (no Java 25 APIs from source).
- [ ] No Drools BOM bump in this PR (that is T1-5).
- [ ] No AI / Keycloak.

### GitNexus / risk

- Impact `RuleService` and rules-engine compile helpers before edit; HIGH/CRITICAL → warn and stop for architect.

---

## T1-5 — Drools BOM 10.2.0 (rank 5, last)

| Field | Value |
|-------|-------|
| Owner | parent BOM |
| Branch | `chore/drools-bom-10-2-0` |
| Exclusive files (approx.) | `backend/pom.xml` (`drools.version` **8.44.0.Final → 10.2.0**), `libs/rules-engine` tests, full pricing DRL corpus compile suite |
| Depends | T1-4 (and T1-1…T1-4 green) |
| Mode | B — version alignment only |
| Evidence | [phase0-drools-compatibility.md](../phase0/phase0-drools-compatibility.md) |

### Acceptance

- [ ] All **8** DRLs compile on 10.2.0.
- [ ] `rules-engine` + `pricing-service` test suites green.
- [ ] CI contract unchanged (no invented runners).
- [ ] PR links Phase 0 Drools evidence.
- [ ] Failure attribution: if red, revert/fix **this** bump — fixtures/metrics/DRL/validate already proven on 8.44.

### GitNexus / risk

- Parent BOM change → **HIGH** CI blast radius; run full pricing + rules-engine suites; warn before merge if detect-changes shows wide graph.

---

## Explicitly out of Track 1 backlog

| Item | Disposition |
|------|-------------|
| Spring AI 1.1.7 / Path B ports | **HOLD** until Path B ADR **Accepted** |
| RuleAuthor / Validator / Optimizer agents | **HOLD** |
| AI in `pricing-service` | Exclude |
| Execute → evaluate rename | Won't-do |
| Source Keycloak as IdP | Exclude |
| Sports / Omaha FE / vertical packs | Won't-do |
| CLI companion | Parked |
| Batch/recalculate new public paths | Defer / won't-do |
| Dual-read flip, NgRx, Activities, #238, #241, etc. | Forge holds (plan §11) |
| Edit `docs/forge/00-orchestrator-status.md` | Forbidden for this wave |

---

## Track 1 done gate (before any Track 2 code)

Residual deterministic gaps are either:

1. Closed by T1-1…T1-5, or  
2. Ticketed with owner, or  
3. Explicitly won't-do in the matrix.

Only then: Path B ADR **draft → Accepted** (Azure OpenAI only). **No** Cursor workers for Spring AI ports until Accept.
