# Architecture artifacts improvement — steering docs (not Wave P)

**Status:** READY — implement immediately in this worktree after this file commits  
**Architect:** `opencrm-architect` (Herdr `w8`)  
**Evidence date:** 2026-08-31  
**Baseline:** `origin/main` @ `9e1df5b` (Wave O FORGING; hygiene N′ COMPLETE)  
**GitNexus:** `-r /home/username01/Projects/open-crm-cpq`; index typically matches `main`; CLI stale from untracked `.worktrees/` is **false stale**. Markdown edits — no Java/FE symbols. **UNKNOWN ≠ unused** if mentioning `SubscriptionController` / `SecurityConfig`.

---

## 0. Selection

**Not a product wave.** Highest-leverage work is **steering artifacts** new forge panes actually read (`AGENT.md` is `always_applied`). Wave O code (catalog, rule-sets, CRM shell, contract-asset Java) is **exclusive** to implementer worktrees — **do not touch**.

| Track | Verdict |
|-------|---------|
| **A1** `AGENT.md` current-status + file reference + landed-vs-target ADR table | **IN** — highest steering |
| **A2** `docs/adr/README.md` freshness (O3 forging; config API stays backlog path) | **IN** |
| **A3** `MODERNIZATION_PLAN.md` short landed-vs-target banner only | **IN** — do not rewrite 1600 lines |
| **A4** `07-wave-backlog.md` historical + `docs/ARCHITECTURE.md` index | **IN** |
| **A5** `24-adr-gitnexus-review.md` addendum (hygiene + Wave O plan) | **IN** |
| Dual-read flip / Accept ADR-006 / GLM-5 runtime / NgRx / Activities / #238/#241 | **REJECT** |

**One shell owner:** none (docs). No FE/Java.

---

## 1. Problem / evidence

| Artifact | Drift |
|----------|--------|
| `AGENT.md` L16 | “Early stage… No controllers, services, repositories, or tests yet… No frontend scaffolding” — **false**. Waves A–N COMPLETE; hygiene COMPLETE; Wave O forging |
| `AGENT.md` File Reference | “5 services + 2 libs”, gateway bootstrap-only, `cpq-service` (does not exist), no Angular — **false**. `backend/pom.xml` modules: common, security, rules-engine, crm, gateway, pricing, pricebook, product, quote, ai, configuration-engine, approval-routing, document-generation, contract-asset |
| `AGENT.md` Key ADRs table | **Target-state** as if landed: NgRx 19+ (**forge holds** NgRx); Azure OpenAI + Claude (ADR-002 **Accepted** GLM-5, **runtime** still Azure `gpt-4o`); Elasticsearch 8; GraphQL optional; “Activity Service” while Activities API **held** |
| `AGENTS.md` | GitNexus block only — no pointer to `AGENT.md` / adr catalog after `<!-- gitnexus:end -->` |
| `MODERNIZATION_PLAN.md` | 16-month target; no landed-vs-target banner |
| `docs/forge/07-wave-backlog.md` | “Ready for PM approval of implementation wave 1” |
| ADR-006 | **Proposed**; O3 **forging** DTO+list — **do not Accept** |
| ADR-005 | **Proposed**; YAML `${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |
| GitHub | Open issues **#238**, **#241** only |

---

## 2. Decisions + rejected

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | Docs-only steering | Wave P product; editing O1/O2/O3 files |
| AGENT.md | Replace false “early stage” + file reference; split **Landed vs target** in ADR table; point at `docs/adr/` | Delete AGENT.md; rewrite all phase agent tables |
| AGENTS.md | After `gitnexus:end`, 8-line pointer (survives GitNexus inject of the start/end block) | Editing inside `gitnexus:start`…`end` |
| Config API ADR | Keep `docs/backlog/ADR-configuration-api-reconciliation.md`; catalog row only (no new ADR-007 file) | Moving that ADR during Wave O |
| ADR-006 | Note **O3 forging**; stay **Proposed** | Accept “leak gone” |
| MODERNIZATION_PLAN | ~15-line banner at top | Rewrite the 16-month plan |
| 07-wave-backlog | Historical banner + pointer to forge 23–26 | Delete the file |

---

## 3. PR DAG

Single worktree, sequential docs commits (no Herdr implementer panes, no shell).

| ID | Commit | Files | Risk |
|----|--------|-------|------|
| **P0** | `docs(forge): architecture artifacts plan` | this file | LOW |
| **A1–A5** | `docs(agent): landed-vs-target steering for AGENT.md and ADR catalog` | AGENT.md, AGENTS.md (after gitnexus:end), adr README + ADR-006 note, MODERNIZATION_PLAN banner, 07-wave-backlog, ARCHITECTURE.md, 24-adr addendum | LOW |

Acceptance: a new forge pane reading `AGENT.md` sees Wave N COMPLETE / Wave O FORGING, lists real services, **does not** start NgRx / Activities / GLM-5 runtime / dual-read flip.

---

## 4. Blast radius

Docs only. `SubscriptionController` / `SecurityConfig` mentioned in prose — GitNexus **UNKNOWN** for those Java symbols; **no Java edits**. Dual-read YAML **untouched**.

---

## 5. Herdr layout

**None.** Architect implements in `w8` / `.worktrees/architect`. Do not dual-write O1/O2/O3 worktrees.

---

## 6. Out of scope

Activities API; #238/#241; product validate deletion; gateway domain validation; dual-read flip; WhatsApp #430; SpEL; Kafka S2B-9; contract-asset FE; NgRx; GLM-5 runtime cutover; Accept ADR-006; Wave O exclusive files (`frontend/libs/cpq/catalog`, new rule-sets lib, `frontend/apps/crm` shell, `contract-asset-management` Java).

---

## 7. Goal text

```text
Improve Open CRM/CPQ steering docs per docs/forge/27-architecture-artifacts-plan.md: AGENT.md current-status + file reference + landed-vs-target ADR table pointing at docs/adr/; AGENTS.md pointer after gitnexus:end; ADR catalog notes O3 forging / dual-read hold / GLM-5 not runtime; MODERNIZATION_PLAN.md short banner only; 07-wave-backlog.md historical; docs/ARCHITECTURE.md index; 24-adr-gitnexus-review addendum. Docs only. Do not edit 00-orchestrator-status.md, Wave O implementer surfaces, or dual-read YAML. Do not Accept ADR-006.
```

---

## 8. Checklist

- [x] Rebased `docs/forge-architect` onto `origin/main` @ `9e1df5b`  
- [x] GitHub holds #238/#241  
- [x] Dual-read YAML stays true  
- [x] ADR-006 stays Proposed  
- [x] No Wave O code files  
- [x] A1–A5 implemented in this worktree after this plan commits  
