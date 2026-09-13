# Wave W architecture — ADR-006 remaining entity leaks (BE DTO)

**Status:** COMPLETE on main 2026-09-13 — W1 `e6d3b23` (#503), W2 `29cac5b` (#501), W3 `0a3d9a4` (#502) (W4 this closeout Accept ADR-006); **R2** still **HELD**; **Wave T skipped**  
**Architect:** `opencrm-architect` (Herdr `w8`) — **plan only; do not spawn implementers**  
**Evidence date:** 2026-09-13  
**Baseline:** `origin/main` @ `5e9c28d` (V closeout `6831672`; TDD ITs #498–#500; Boot **4.0.6**); plan `a7017bf`; FORGING `77e4b66`  
**Survey tip:** Day-1 tip `origin/main` @ `d9b8c8f` (W1 merge #503; W1–W3 landed)  
**Sources:** `rg` + **read controllers/entities**. GitNexus **skip analyze**. UNKNOWN ≠ unused; `-r /home/username01/Projects/open-crm-cpq --file`. Linear/Notion Open CRM: **none**.  
**Do not** edit `docs/forge/00-orchestrator-status.md`.  
**ADR-006 Accepted** in W4 after W1+W2+W3 on `main`.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Reopen N/S/U/V / N′′ / named T** | **REJECT** | Locked |
| **R2** list-row delete | **HOLD** | Do not un-hold |
| **Accept ADR-006 now** | **REJECT until W1–W3 merge** | Three controllers still return entities |
| **Subscriptions FE / ERP rewrite** | **HOLD** | O3 DTO landed; activate still ERP |
| **Delete product `POST /validate`** | **HOLD** | Dual-run retained |
| **Azure 7.x BOM** | **SKIP** | Optional LOW; parent `pom.xml` serializes all BE — not disjoint from W1–W3. Keep Boot **4.0.6**; Azure 5.8 verifier excluded (#500) |
| **`listLineItems` / bookHttpErrorMessage two-codes** | **SKIP** | No UI caller / N1 copies |
| **#238 / #241 / NgRx / dual-read flip** | **HOLD** | Standing |
| **✅ W1 CE session DTO** | **IN** | `ConfigurationSessionController` → `ApiResponse<ConfigurationSession>` |
| **✅ W2 approval-routing DTO** | **IN** | routing `ApprovalController` → `ApiResponse<ApprovalRequest>` entity. Quote BFF **already** `ApprovalRequestDto` |
| **✅ W3 document-generation job DTO** | **IN** | `DocumentController` → `ApiResponse<DocumentGenerationJob>` |
| **✅ W4 docs + Accept ADR-006** | **IN** | After W1–W3 on `main`; dual-read J–W; `newgrp docker` note |

**Chosen theme:** BE DTO-wrap the **three** ADR-006 leaks. **No FE. Zero new portal. Zero chrome.** Pattern: Wave **O3** (`SubscriptionDto` + MapStruct + MockMvc).

---

## 1. Problem / evidence (read files)

### Git / GitHub / dual-read / Boot

| Item | State |
|------|--------|
| Open issues | **#238**, **#241** — **HOLD** |
| Open PRs | **none** |
| Dual-read | `legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |
| Boot | Parent **4.0.6** — **do not downgrade** |
| Azure | `spring-cloud-azure.version` **5.8.0**; verifier **excluded** (#500) |

ADR-006 **Proposed** (`docs/adr/ADR-006-entities-never-at-api-boundary.md`): O3 subscription leak gone; remaining three entity returns.

### W-T1 — ConfigurationSession (confirmed)

`ConfigurationSessionController` `@RequestMapping("/api/v1/configurations")`:

| Method | Returns |
|--------|---------|
| POST `/` create | `ApiResponse<ConfigurationSession>` |
| GET `/{id}` | entity |
| GET `/` listByUser | `List<ConfigurationSession>` |
| POST `/{id}/selections` | entity |
| DELETE `/{id}/selections/{optionKey}` | entity |
| POST `/{id}/validate` | entity (**session** validate — **not** product `POST /api/v1/configuration/validate`) |
| POST complete / cancel | entity |

Entity extends `BaseEntity` (`id`, `uuid`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `version`, **`deleted`**). Domain: `productId`, `userId`, `status`, EAGER `selections` map, `valid`, `validationErrors`, `completedAt`, `expiresAt`.

Gateway: `Path=/api/v1/configurations/**`. FE `rg configurations` in `frontend/**/*.ts`: **zero**. MapStruct already on `configuration-engine/pom.xml`.

### W-T2 — approval-routing (confirmed; BFF already DTO)

**Leak:** `backend/services/approval-routing/.../ApprovalController.java` `/api/v1/approvals` returns `ApprovalRequest` entity (submit, getById, pending, pending/tier, approve, reject).

**Not a leak:** quote-service BFF `com.opencrm.quote.approval.ApprovalController` already returns `ApprovalRequestDto`. FE inbox calls **`/api/v1/quotes/approvals/**` only** (`approval.service.ts` + spec forbids `/api/v1/approvals`).

Quote `RoutingApprovalDto` (`JsonIgnoreProperties.ignoreUnknown = true`) already parses: `id`, `uuid`, `quoteId` (**String**), `approvalType`, `status`, `requesterId`, `approverId`, `approvalTier`, `reason`, `rejectionReason`, `submittedAt`, `approvedAt`, `rejectedAt`, `createdAt`.

Entity extra vs that DTO: `lineId`, discounts, `currencyCode`, `deleted`, `version`, audit users. Jackson currently serializes **`deleted`**.

MapStruct already on `approval-routing/pom.xml`.

### W-T3 — DocumentGenerationJob (confirmed)

`DocumentController` `/api/v1/documents`: getJobById, getJobsByQuote, getJobsByStatus, retryJob → entity. Retry **mutates repository in the controller**. FE quote documents use **quote-service** documents API, not this controller (`rg /api/v1/documents` in FE: **zero**). Gateway routes `/api/v1/documents/**`. MapStruct already on `document-generation/pom.xml`.

---

## 2. Decisions + rejected

### Shared (all DTO tracks)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Pattern | O3: `*Dto` + MapStruct `componentModel = "spring"` `toDto` / `toDtoList`; controller returns DTO only; omit **`deleted`** | Returning entity; Accept ADR-006 in W1/W2/W3 |
| JSON | Keep **camelCase** field names so existing RestClient/`ignoreUnknown` clients keep working | Renaming `quoteId` / status enums on the wire |
| FE | **None** | Subscriptions portal; approvals calling routing directly |
| Java | `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2`. ITs: wrap Maven in `newgrp docker` | Boot 3 revert |

### Track 1 — configuration-engine

| Topic | Decision | Rejected |
|-------|----------|----------|
| DTO | `ConfigurationSessionDto`: id, uuid, productId, userId, status, selections, valid, validationErrors, completedAt, expiresAt, createdAt, updatedAt, createdBy, updatedBy, version | Exposing `deleted` |
| Validate | Session `POST /{id}/validate` stays; returns DTO | Deleting product-service `POST /configuration/validate` |

### Track 2 — approval-routing

| Topic | Decision | Rejected |
|-------|----------|----------|
| DTO name | `com.opencrm.approval.dto.ApprovalRequestDto` (routing package — **not** quote BFF class) | Editing quote-service BFF / FE |
| Wire | **Must include** every field `RoutingApprovalDto` reads. Extra fields OK (`ignoreUnknown`). **Omit `deleted`** | Changing quoteId String→Long |
| Tests | MockMvc + keep `ApprovalRoutingClientTest` green (update fixtures if `deleted` disappears — client ignores unknown, missing `deleted` is fine) | Dual-read YAML flip |

### Track 3 — document-generation

| Topic | Decision | Rejected |
|-------|----------|----------|
| DTO | `DocumentGenerationJobDto`: id, uuid, quoteId, customerId, documentType, status, idempotencyKey, documentUrl, errorMessage, retryCount, startedAt, completedAt, failedAt, createdAt, updatedAt, version — **no `deleted`** | Kafka payload rewrite unless tests require |
| Retry | Move retry to **service**; controller stays thin | New generate HTTP (agents mention `/generate` — **out**) |

### Track 4 — docs

After W1+W2+W3 on `main`: **Accept ADR-006** (entities never at API boundary). Update `docs/adr/README.md`, AGENT/ARCHITECTURE. Dual-read hold **J–W**. Note `newgrp docker` for ITs (`docs/forge/impl/tdd-be-it-reactor.md`). **Do not** flip dual-read. **Do not** Accept ADR-005.

---

## 3. PR DAG

Disjoint services: **configuration-engine** ∥ **approval-routing** ∥ **document-generation**.

```text
W1 CE session DTO              W2 routing ApprovalRequest DTO       W3 document job DTO
──────────────────             ──────────────────────────────       ──────────────────
ConfigurationSessionDto         ApprovalRequestDto (routing pkg)     DocumentGenerationJobDto
MapStruct + controller          MapStruct + controller               MapStruct + thin retry
        │  no FE / no gateway           │  do not edit quote BFF             │  no FE
        └───────────────────────────────┴────────────────────────────────────┘
                                           ▼
                          W4 docs: Accept ADR-006 + dual-read J–W
                             (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **W1** | `feat(ce): configuration session DTO at API boundary` | CE dto/mapper/controller/service tests; `docs/forge/impl/w1-be-config-session-dto.md` | **MED** | No `ConfigurationSession` in controller signatures; MockMvc JSON has no `deleted`; session validate retained; product validate **untouched**; `JAVA_HOME=…/java/21.0.2`; `newgrp docker ./mvnw -pl services/configuration-engine -am test` |
| **W2** | `feat(approval-routing): ApprovalRequest DTO at API boundary` | routing dto/mapper/controller tests; `docs/forge/impl/w2-be-approval-request-dto.md` | **MED** | No entity in routing controller; JSON keeps `RoutingApprovalDto` fields; **no** quote-service / FE edits; **no** dual-read YAML; Maven `-pl services/approval-routing -am test` under `newgrp docker` |
| **W3** | `feat(document-generation): job DTO at API boundary` | document dto/mapper/controller/service retry; `docs/forge/impl/w3-be-document-job-dto.md` | **LOW–MED** | No entity in `DocumentController`; retry still PENDING→async; Maven `-pl services/document-generation -am test` under `newgrp docker` |
| **W4** | `docs(adr): Accept ADR-006 after entity leaks gone` | ADR-006 **Accepted**; adr README; AGENT/ARCHITECTURE; `docs/forge/impl/w4-wave-w-docs.md` | **LOW** | Accept **only** if W1–W3 on `main`; dual-read still true; **no** `00-orchestrator-status` |

**Day-1 max parallel:** **W1 ∥ W2 ∥ W3** → **W4**.

| Pair | Rule |
|------|------|
| W1 vs W2 vs W3 | Parallel (three Maven modules) |
| W2 vs quote BFF | W2 **must not** edit `quote-service` |
| W4 vs Accept | Serial after three merges |
| Dual-read / product validate / FE | **Forbidden** |

---

## 4. Blast radius

GitNexus **skip analyze**. Controllers typically **UNKNOWN** (HTTP). **`rg` SSOT.**

| Change | `rg` | Risk |
|--------|------|------|
| W1 session JSON | Gateway `/configurations/**`; **no FE** | **MED** (wire change; omit `deleted`) |
| W2 routing JSON | Quote `ApprovalRoutingClient` + tests; FE **does not** call routing | **MED** (keep `RoutingApprovalDto` fields) |
| W3 job JSON | Gateway `/documents/**`; quote FE uses quote-service docs | **LOW–MED** |
| Dual-read YAML | Flip = **HIGH** — out | — |
| Azure 7.x parent | **Not in wave** | — |

**WARN:** UNKNOWN ≠ unused. Disambiguate two `ApprovalController` / two `ApprovalRequest` with `--file`. Do not flip dual-read. Do not Accept ADR-006 in W1–W3. Do not downgrade Boot 4.0.6.

---

## 5. Herdr layout (orchestrator spawns — architect does not)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `ww-be-config-session` | `feat/forge-w1-be-config-session-dto` | W1 |
| 2 | `ww-be-approval-dto` | `feat/forge-w2-be-approval-request-dto` | W2 |
| 3 | `ww-be-document-job` | `feat/forge-w3-be-document-job-dto` | W3 |
| 4 | `ww-docs` | `docs/forge-w4-wave-w-closeout` | W4 |

**Chrome / FE owner:** **none.**  
**Model:** Grok 4.5, worktree-isolated. Overlay ≠ merge SSOT.  
**Java:** `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2`  
**ITs:** `newgrp docker <<'EOF'` then `./mvnw -B -pl services/<mod> -am test` (or `-DskipITs` if module has no IT and Docker blocked).  
**Vitest:** none expected.

---

## 6. Out of scope

- Activities / `/api/v1/activities/**`  
- CI #238; Kafka #241  
- Dual-read YAML flip; ADR-005 Accept  
- Product validate deletion; gateway domain validation  
- Full SpEL; Kafka S2B-9; WhatsApp #430 undo  
- NgRx; subscriptions FE; ERP adapter rewrite  
- N′′; **R2**; named Wave T / T1 / T2  
- New portal; `app.routes` / sidebar / shell  
- Boot 3 revert; Azure 7.x BOM this wave  
- Editing quote-service BFF `ApprovalController`  
- Accept ADR-006 **before** W1–W3 on `main`  

---

## 7. Goal text

```text
Implement Open CRM/CPQ Wave W per docs/forge/35-wave-w-architecture.md in three parallel BE tracks (O3 pattern): (1) configuration-engine ConfigurationSessionDto + MapStruct; controller returns DTO only; omit deleted; session validate stays; do not delete product POST /configuration/validate; JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2; newgrp docker ./mvnw -pl services/configuration-engine -am test; (2) approval-routing ApprovalRequestDto in com.opencrm.approval.dto; keep JSON fields RoutingApprovalDto already reads; omit deleted; do not edit quote-service or FE; Maven -pl services/approval-routing -am test under newgrp docker; (3) document-generation DocumentGenerationJobDto; thin controller; retry via service; Maven -pl services/document-generation -am test under newgrp docker. Day-1 W1∥W2∥W3; W4 Accept ADR-006 only after all three on main; dual-read YAML untouched. No FE. No Java Boot downgrade. Do not flip dual-read, un-hold R2, name T1/T2, invent Activities, start #238/#241, subscriptions FE, Azure 7.x, or edit 00-orchestrator-status.md. Evidence: docs/forge/impl/w*.md done, tests green, no entity in those three controllers.
```

---

## 8. Checklist

- [x] Even with `origin/main` @ `5e9c28d`  
- [x] Three leaks **read** in tree; quote BFF already DTO; FE does not call routing/CE sessions/doc-gen jobs  
- [x] Dual-read still true; Boot 4.0.6; R2 held; T skipped  
- [x] Do **not** Accept ADR-006 until W1–W3 merge  
- [x] Zero portal / chrome; no FE  
- [x] GitNexus analyze skipped; `rg` used  
- [x] Architect **does not spawn** implementers  
- [x] Orchestrator → Wave W FORGING (`00-*`) — `77e4b66`  
- [x] W1–W4 COMPLETE (W4 this PR); ADR-006 Accepted; R2 still held; T skipped  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). Do **not** Accept ADR-005. Do **not** edit quote BFF. Do **not** downgrade Boot 4.0.6.

**WAVE W — COMPLETE.**
