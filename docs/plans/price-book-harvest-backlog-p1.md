# Track P1 harvest backlog (E2)

**Parent plan:** [price-book-reuse-gap-analysis.md](./price-book-reuse-gap-analysis.md) §P1-Pilot.  
**Capability matrix:** [price-book-capability-matrix-p1.md](./price-book-capability-matrix-p1.md).  
**Source pin:** `1349a190a6a1f7c6e0396fe333a13d811e427744` @ `/home/username01/Projects/price-book-service`.  
**Dual-read:** leave `${APPROVALS_LEGACY_LOCAL_READ:true}` — do not flip.  
**Track 2:** **HOLD** — no AI / Spring AI / agent implementation PRs in this wave.  
**P2:** scored ideas stay in [price-book-harvest-backlog-p2.md](./price-book-harvest-backlog-p2.md). **Do not** start F1 / P2-1 until E-GATE.

Ranked serial PRs below. **Do not** start P1-1…P1-3 code until E1–E2 merge (this docs PR) and a new architect un-hold of **E3**. **Do not** put Drools/AI in `pricebook-service`. **Do not** un-hold R2 list-row delete.

---

## Maven env (every **code** PR — E3 later, not now)

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
# Unit tests (no docker):
cd backend && ./mvnw -B -pl services/pricebook-service,services/pricing-service -am test -DskipITs
# ITs when Testcontainers needed and login `id` lacks docker:
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -B -pl services/pricebook-service,services/pricing-service -am test
EOF
```

P1-1 Flyway lives in **`pricing-service`** (schema owner; `pricebook-service` has `flyway.enabled: false`). Always include `-pl services/pricing-service` when adding `V4__…`.

**GitNexus (every code PR):**

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "<Symbol>" --direction upstream \
  --repo /home/username01/Projects/open-crm-cpq --file path/to/File.java
# UNKNOWN ≠ unused — confirm with rg
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all \
  --repo /home/username01/Projects/open-crm-cpq
```

Docs-only (this PR): skip impact; still run `detect_changes` before commit. Do not edit `docs/forge/00-orchestrator-status.md` or `docs/plans/00-harvest-index.md`.

---

## DAG overview

```
E0 pin (this PR, parent §P0) → E1 matrix + E2 backlog (this PR)
                              ↘
                                P1-1 (entry dating + overlap reject)
                                  → P1-2 (resolve-batch as-of + pricing regression)
                                P1-3 (publish tests)  ∥ P1-1
                                P1-4 selective header — residual / won't-do now
```

| ID | Status | Owner | Branch (suggested) | Depends |
|----|--------|-------|--------------------|---------|
| **E0** | This PR (parent pin) | docs + local clone | `docs/harvest-e-docs` | — |
| **P1-D1** | This PR (E1+E2) | docs | `docs/harvest-e-docs` | E0 pin recorded first |
| **P1-1** | Queued — **HOLD** until E1–E2 merge | `pricebook-service` + pricing Flyway | `feat/pricebook-entry-effective-windows` | P1-D1 merge + E3 un-hold |
| **P1-2** | Queued — **HOLD** | `pricebook-service` + `pricing-service` tests | `feat/pricebook-resolve-batch-as-of` | P1-1 |
| **P1-3** | Queued — **HOLD** | `pricebook-service` (+ optional FE DTO) | `test/pricebook-publish-immutability` | P1-D1 (∥ P1-1) |
| **P1-4** | Residual | — | — | product confirm; **not** in E3 first slice |

**Rejected shapes:** dating without overlap reject; `salesPrice`; `asOf` query param on resolve-batch; Drools in pricebook; Mongo/Eureka; React/Supabase; R2 un-hold; dual-read flip; new external `/api/v1/price-books/**` siblings in P1.

---

## E0 — Pin & controller inventory (this deliverable)

| Field | Value |
|-------|-------|
| Acceptance | Clone @ `1349a19…`; parent §P0 pin + secret-scan + `/api/v1` remap inventory |
| Evidence | Local `/home/username01/Projects/price-book-service`; parent plan §P0 pin |
| Exclusive | Local clone path; `docs/plans/price-book-reuse-gap-analysis.md` (**pin + inventory + secret-scan only**) |

Source build (optional): host `mvn` missing; `backend/mvnw -f …/price-book-service/pom.xml -DskipTests package` with `JAVA_HOME` **21.0.2** → **BUILD SUCCESS** (Boot 3.2.5). No `newgrp docker`.

---

## P1-D1 — Matrix + backlog (this deliverable)

| Field | Value |
|-------|-------|
| Owner | docs |
| Branch | `docs/harvest-e-docs` |
| Exclusive files | `docs/plans/price-book-capability-matrix-p1.md`, `docs/plans/price-book-harvest-backlog-p1.md`, parent `docs/plans/price-book-reuse-gap-analysis.md` (P0 pin only) |
| Depends | E0 pin recorded on parent **before** matrix claims the SHA |
| Acceptance | Matrix rows for header / entry dating / resolve-batch / publish only; bulk/coverage/UX/Cost/Drools/AI/SpEL not “do now”; pin SHA recorded; backlog ranked P1-1…P1-3 + P1-4 residual |
| Out of scope | Harvest code, Flyway, FE, forge status edits, P2 rewrite, harvest index |

---

## P1-1 — Entry effective windows + overlap reject (rank 1)

| Field | Value |
|-------|-------|
| Owner | `pricebook-service` + `pricing-service` Flyway |
| Branch | `feat/pricebook-entry-effective-windows` |
| Exclusive files (approx.) | `backend/services/pricing-service/src/main/resources/db/migration/V4__price_book_entry_effective_windows.sql` (next free version if V4 taken); `PriceBookEntry.java`; entry DTOs/create/update requests; `PriceBookMapper`; `PriceBookService` uniqueness/overlap; `PriceBookServiceTest`; FE `pricing.model.ts` entry types |
| Depends | P1-D1 merged + E3 un-hold |
| Mode | B — port source window + **reject overlaps** (do not copy Mongo unique-on-from-date hole) |

### Behavior spec

- Add nullable `effective_from` / `effective_to` `DATE` on `price_book_entries`.
- Existing rows: both NULL = open window (always valid) — today’s one-entry-per-product data keeps working.
- Create/update: if both dates set, `effectiveTo >= effectiveFrom` (`INVALID_DATE_RANGE`).
- Overlap: two non-deleted entries for the same `(priceBookId, productExternalId)` whose inclusive windows intersect → `OVERLAPPING_PRICE_BOOK_ENTRY` (or reuse `DUPLICATE_PRICE_BOOK_ENTRY` with a clear message). Null end = +∞; null start = −∞.
- Still enforce `floorPrice ≤ listPrice` and `ensureNotPublished` on the **book**.
- Do **not** add `salesPrice`, `isActive`, `priceStatus`, `type`, Mongo DBRefs.
- ADR-006: DTOs only. No new external path. Agent skill curl may add `effectiveFrom`/`effectiveTo` (today it already sends `effectiveDate` which the API ignores).

### Acceptance

- [ ] Flyway applied via **pricing-service** (`flyway.enabled: true`, schema `pricing`); `pricebook-service` `ddl-auto: validate` still passes.
- [ ] Create two non-overlapping dated entries for the same product on an unpublished book — both persist.
- [ ] Create/update that overlaps an existing window — `BusinessException`, no row.
- [ ] Open-window (null,null) still unique per product (treat as overlapping any other open or dated window that covers “all time”).
- [ ] Published book still immutable (`PRICE_BOOK_PUBLISHED_IMMUTABLE`).
- [ ] FE models accept optional dates; **no** R2 list-row delete.
- [ ] `@Tag("unit")` green under Maven env above. **No** Drools. **No** dual-read YAML.

### GitNexus / risk

- Impact `PriceBookService` upstream **before** edit (`--file` the Java service). Warn if HIGH/CRITICAL.
- Confirm `UNKNOWN` with `rg`.
- Blast: pricebook-service, pricing Flyway, FE models, agent skill curl. Gateway/matrix unchanged if no new path.

---

## P1-2 — `resolve-batch` as-of selection (rank 2)

| Field | Value |
|-------|-------|
| Owner | `pricebook-service` + `pricing-service` tests |
| Branch | `feat/pricebook-resolve-batch-as-of` |
| Exclusive files (approx.) | `PriceBookInternalResolutionService.java`; `PriceBookEntryRepository` (dated finder or in-memory filter after IN query); `PriceBookInternalResolutionServiceTest.java`; `RestPriceBookResolutionClientTest.java`; pricing execute goldens if list-price vectors change |
| Depends | P1-1 |
| Mode | B — as-of = today; keep request DTO `{ region, productExternalIds }` |

### Behavior spec

After book pick (`isEffective` unchanged):

1. Keep the **single IN** query for N products (do not regress C6 / #456).
2. For each `productExternalId`, among non-deleted entries whose inclusive window contains `LocalDate.now()`, pick **latest `effectiveFrom`** (null from sorts as lowest / open-past; prefer the dated row if both an open and a dated window would match — **should not happen** after P1-1 overlap reject).
3. Future `effectiveFrom` or expired `effectiveTo` → `entryMatched=false`.
4. Do **not** add `asOf` to `ResolveBatchRequest`. Do **not** return `salesPrice`.

Inclusive window (same as book `isEffective`):

```
(effectiveFrom == null || !effectiveFrom.isAfter(asOf))
&& (effectiveTo == null || !effectiveTo.isBefore(asOf))
```

### Acceptance

- [ ] Unit tests: scheduled future entry misses; expired misses; current window hits; open-window (nulls) hits; book future/expired still `NO_PRICE_BOOK`.
- [ ] Duplicate-product IN result does not first-wins blindly when two dated rows exist (dirty data) — latest `effectiveFrom` ≤ as-of wins.
- [ ] Still **one** `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` (or successor) per batch.
- [ ] `RestPriceBookResolutionClient` still posts `/internal/v1/price-books/resolve-batch`; client tests green.
- [ ] Pricing execute goldens that resolve list price still pass (`./mvnw -B -pl services/pricing-service -am test -DskipITs`).
- [ ] **No** new gateway path; **no** contract-matrix row unless DTO fields on the **internal** response change (they should not).

### GitNexus / risk

- Impact `PriceBookInternalResolutionService` and `RestPriceBookResolutionClient` upstream. HIGH is expected (pricing execute). **Warn the user** before editing; do not skip.
- Confirm repository finders with `rg` if impact is `UNKNOWN`.

---

## P1-3 — Publish / immutability lock tests (rank 3)

| Field | Value |
|-------|-------|
| Owner | `pricebook-service` (+ optional FE `published` on DTO) |
| Branch | `test/pricebook-publish-immutability` |
| Exclusive files (approx.) | `PriceBookServiceTest.java`; optional `PriceBookDto.published`; optional FE `isPriceBookPublished` still sourced from the same rule |
| Depends | P1-D1 (may land ∥ P1-1; must not change resolve-batch) |
| Mode | C — target already enforces; lock + document |

### Behavior spec

Keep `ensureNotPublished`: `effectiveDate != null && !effectiveDate.isAfter(today)`. Expiration does **not** re-open writes. Null `effectiveDate` remains writable.

Optional (small): expose derived `published` on `PriceBookDto` so FE does not re-implement the formula. If added, FE `isPriceBookPublished` should prefer the flag when present.

### Acceptance

- [ ] Tests: published book rejects header update/delete and entry create/update/delete with `PRICE_BOOK_PUBLISHED_IMMUTABLE`.
- [ ] Tests: expired book (expirationDate < today) still rejects writes; resolve-batch still `NO_PRICE_BOOK` (may live in P1-2).
- [ ] Tests: unpublished (`effectiveDate` null or future) still writable.
- [ ] **No** R2 list-row delete. **No** new path.

### GitNexus / risk

- Prefer test-only. If `PriceBookDto` gains `published`, impact FE models + `isPriceBookPublished` callers; browser-verify price-book list/detail/edit/entry dialogs.

---

## P1-4 — Selective book metadata (residual — not E3 first slice)

| Field | Value |
|-------|-------|
| Owner | `pricebook-service` + FE if ever |
| Decision | **Won't-do in P1** unless product names a CPQ use beyond region/default |
| Would have been | `type` enum, persisted `status`, `customerGroup`, `attributes` map, `description` |
| Why not | Matrix H5–H9: no contract-matrix row, no FE field, hierarchy already `region` + `isDefault`. Persisted `status` would duplicate derived published. |

If product later asks: new ticket **after E-GATE**, Flyway + DTOs + matrix row. Not bulk/coverage (those are P2).

---

## Track P1 done gate (after E3)

Close when **all** are true:

- [ ] Entry dating + overlap reject landed **or** explicitly won't-do (P1-1).
- [ ] `resolve-batch` as-of selection landed **or** explicitly won't-do (P1-2), with pricing client regression.
- [ ] Publish/immutability either locked with tests or signed off as already sufficient (P1-3).
- [ ] Selective header either residual-ticketed or landed (P1-4).
- [ ] Dual-read still `true`. R2 still held. No Drools in pricebook.

Then Queue F (P2-1 coverage, P2-2 bulk, …) may start **only** after a new architect un-hold.

---

## Out of scope for this backlog

- Implementing P1-1…P1-3 in this PR.  
- Queue F / P2 code. Queue B Path B ADR. Queue D TCV.  
- Editing `docs/plans/00-harvest-index.md` or `docs/forge/00-orchestrator-status.md`.  
- Rewriting [price-book-harvest-backlog-p2.md](./price-book-harvest-backlog-p2.md).
