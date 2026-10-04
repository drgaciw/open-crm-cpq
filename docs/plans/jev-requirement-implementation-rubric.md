# Jev requirement-vs-implementation gate

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put [Jev](https://glama.ai/mcp/servers/minhgv/jev-mcp) (`jev-mcp`) in the development loop so every non-trivial change is judged against stated requirements, with GitNexus evidence in the packet Jev scores.

**Architecture:** GitNexus is the evidence collector (graph, blast radius, route shape). Jev is the typed judge (`covered` / `partial` / `not_covered` / `not_verifiable`, plus review scores and `safe_to_apply`). This repo owns policy: thresholds, forge holds, and whether a judgment means ship, revise, or escalate. Jev does not write code, read git, or authorize a merge.

**Tech Stack:** Cursor MCP `jev-mcp` (TypeSafe Jev recipes), GitNexus CLI `node .gitnexus/run.cjs` bound to `/home/username01/Projects/open-crm-cpq`, existing contract matrix and harvest index.

## Global Constraints

- Jev returns judgments. Agents and humans own edits, commits, and merges.
- A higher score never authorizes scope expansion, a forge-hold lift, or a second rules engine.
- `risk: UNKNOWN` from GitNexus is unresolved. Confirm with `rg` before treating a symbol as unused.
- `detect_changes` with `partial: true` or `truncated: true` is not a clean check.
- Dual-read default stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. R2 price-book list-row delete, full SpEL, Activities API, and NgRx stay held.
- ADR-006: DTOs at the API boundary. Do not send entity classes as the requirement proof.
- Do not paste secrets, `.env` values, or tokens into Jev `diff`, `tests`, or `repository_context`. Set `redacted: true` when the packet was scrubbed.
- GitNexus repo flag: `-r /home/username01/Projects/open-crm-cpq` (or `--repo` as the CLI skill documents).

---

## Why this shape

Jev’s host must supply the diff, the requirements, and the verification evidence. The server does not walk this monorepo. Without GitNexus, “blast radius” and “spec match” are guesses. With GitNexus, the packet names callers, processes, and route consumers before Jev classifies coverage.

Use separate recipes. Do not collapse them into one chat prompt.

| Recipe | When | What it returns |
|--------|------|-----------------|
| `jev_check_requirement` | After a slice has a diff and tests | Per criterion: `covered`, `partial`, `not_covered`, `not_verifiable` |
| `jev_review` | Before declaring the slice done | Correctness, spec-match, test-gap, blast-radius, `safe_to_apply` |
| `jev_assess_change_risk` | Auth, gateway, schema, or cross-service changes | Risk dimensions; incomplete evidence never returns `auto` |
| `jev_evaluate` | Only when no recipe fits | Named Choice / Score / Noul questions in one parallel call |

`jev_verify` checks a claim. `jev_classify_issue` and `jev_coding_loop` stay out of this rubric until a later wave.

---

## Decision rubric

Policy lives here, not inside a rewritten Jev prompt. Apply rows in order. First match wins.

### R1 — Requirement coverage (`jev_check_requirement`)

| Status | Meaning | Next action |
|--------|---------|-------------|
| `covered` | Diff + tests show the criterion | Eligible for R2 |
| `partial` | Some of the criterion is present | Revise the smallest missing piece; do not ship |
| `not_covered` | Criterion absent from diff and tests | Implement or explicitly drop the criterion in the plan before re-score |
| `not_verifiable` | Packet lacks a fact that could change the call | Stop. Fill GitNexus evidence (below). Do not mark covered |

`evidence_complete: false` forces treat-as-`not_verifiable` even if a criterion looks covered.

### R2 — Done gate (`jev_review`)

Call only when every in-scope criterion is `covered` or explicitly deferred in the plan.

| Signal | Policy |
|--------|--------|
| `safe_to_apply` false, or spec-match / correctness weak | Revise. Do not commit as “done” |
| `safe_to_apply` true and GitNexus risk is LOW with a clean `detect_changes` | Agent may proceed to the normal commit path |
| `safe_to_apply` true but GitNexus risk is HIGH, CRITICAL, or UNKNOWN | Human review. Do not auto-merge |
| Test output missing for a behavior change | `tests` empty → treat test-gap as blocking |

### R3 — Change risk (`jev_assess_change_risk`)

Required when the diff touches gateway routes, `SecurityConfig`, Flyway, pricing execute, `resolve-batch`, or approval BFF.

| Jev action | Policy |
|------------|--------|
| `auto` | Allowed only if GitNexus risk is LOW and evidence is complete |
| `review` | Human looks at the named dimension |
| `escalate` | Stop the slice. Do not keep editing to “get a better score” |

High-risk or incomplete evidence must not be overridden by `auto_accept`.

### R4 — Forge and architecture veto (local, not Jev)

If the diff would flip dual-read, unhold R2, add Easy Rules or SpEL-as-platform, put Spring AI or Drools inside the wrong service, or invent `/api/v1/activities/**`, the slice is **rejected** regardless of Jev scores.

---

## Evidence packet (GitNexus → Jev)

Build this before any `jev_*` call. Jev’s `repository_context` is this packet, not a repo dump.

### Bind and freshness

```bash
node .gitnexus/run.cjs status --repo /home/username01/Projects/open-crm-cpq
```

If the index is stale, `node .gitnexus/run.cjs analyze --index-only` from the repo root before impact. Record: repo name `open-crm-cpq`, path, index commit, how far behind HEAD.

### Map the requirement to code

| Requirement kind | GitNexus call | Put in `repository_context` |
|------------------|---------------|-----------------------------|
| “How does X work?” | `query({search_query})` or `node .gitnexus/run.cjs query "…" --repo …` | Process names and the symbols that implement them |
| A symbol the slice will edit | `context({name})` | Callers, callees, processes |
| Before the edit | `impact({target, direction: "upstream"})` | Direct callers, risk, `riskNote` if UNKNOWN |
| API / contract criterion | `route_map` then `api_impact` for the path; `shape_check` if response keys matter | Consumers, middleware, MISMATCH keys |
| Security-sensitive path | `explain({target})` after `analyze --pdg` | Source→sink findings, or “PDG not built” |

CLI fallbacks (MCP missing):

```bash
node .gitnexus/run.cjs impact "<Symbol>" --direction upstream --repo /home/username01/Projects/open-crm-cpq
node .gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

### After the edit, before `jev_review`

```bash
node .gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Paste the symbol/process summary into `repository_context`. If `partial` or `truncated`, set `evidence_complete: false` and `truncated: true`. Re-run; do not treat a zero as “unaffected.”

### Requirement list shape

One bullet per acceptance criterion, copied from the plan or contract row. Example for a price-book coverage slice:

```text
1. GET /api/v1/price-books/{id}/coverage returns missing productExternalIds for ACTIVE catalog products.
2. Unpublished-only writes stay enforced (ensureNotPublished).
3. No new path under /api/v1/pricing/price-books.
4. Contract matrix §4b updated.
```

`diff` is the focused patch (`git diff`), not the whole tree. `tests` is the relevant Maven or Vitest output. `request` for `jev_review` is the user ask plus the criterion list.

---

## Files

| File | Responsibility |
|------|----------------|
| `.cursor/mcp.json` | Register `jev-mcp` stdio server next to `angular-cli` |
| `docs/plans/jev-requirement-implementation-rubric.md` | This rubric (policy) |
| `.cursor/rules/jev-requirement-gate.mdc` | Agent-requestable rule: when to call Jev and which GitNexus commands fill the packet |
| `docs/plans/00-harvest-index.md` | One line pointing harvest implementers at this gate |

Do not add Jev calls inside Java services. This is a development gate, not a runtime dependency.

---

## Task 1: Register Jev MCP

**Files:** `.cursor/mcp.json`

- [ ] Read current `mcpServers` (today: `angular-cli` only).
- [ ] Add a `jev` (or `jev-mcp`) stdio entry using the upstream install from [minhgv/jev-mcp](https://glama.ai/mcp/servers/minhgv/jev-mcp). Pin the package version in the args once the install command is confirmed; do not float `latest` in the committed file.
- [ ] Restart MCP / confirm `GetDynamicTools` lists `jev_check_requirement`, `jev_review`, and `jev_assess_change_risk`.
- [ ] If the server needs an API key, document the env var name only. Do not commit the key.

**Done when:** a no-op `jev_evaluate` Choice question against a one-line fixture state returns a typed answer, not a transport error.

---

## Task 2: Agent rule that builds the packet

**Files:** `.cursor/rules/jev-requirement-gate.mdc`

- [ ] Write `description` so the rule applies when implementing or reviewing a feature against acceptance criteria.
- [ ] `alwaysApply: false` (requestable). Harvest and forge agents opt in.
- [ ] Steps, in order: GitNexus status → `query`/`context` for the requirement → `impact` upstream before edits → implement → tests → `detect-changes` → `jev_check_requirement` → if all covered, `jev_review` → if gateway/security/schema, also `jev_assess_change_risk`.
- [ ] Copy the R1–R4 table in short form. State that Jev `auto` loses to GitNexus HIGH / CRITICAL / UNKNOWN and to forge holds.
- [ ] Point CLI fallbacks at `node .gitnexus/run.cjs` and `--repo /home/username01/Projects/open-crm-cpq`.

**Done when:** a fresh agent session given only this rule can name the three Jev tools and the impact command without opening this plan.

---

## Task 3: Pilot the rubric on one landed contract

**Files:** none in production code. Notes append to this plan under “Pilot log”.

Pick a **read-only** comparison first so the rubric is calibrated before it gates new harvest PRs.

- [ ] Requirement source: `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §4b price-book `resolve-batch` row (internal, pricing-service only).
- [ ] GitNexus: `context` on `PriceBookInternalResolutionService`; `impact` upstream; `route_map` / `api_impact` if the route is indexed.
- [ ] Diff: `git diff` empty is valid. Use `git show` of the commit that added resolve-batch, or a file excerpt, and set `review_at` to that SHA. `tests`: cite `PriceBookInternalResolutionServiceTest` result if run.
- [ ] Call `jev_check_requirement` with 3–5 criteria taken verbatim from the matrix row and AGENT holds (no `/api/v1/pricing/price-books`, DTO not entity).
- [ ] Record statuses. If `not_verifiable`, add the missing GitNexus fact and call once more. Do not loop to chase a score.

**Done when:** the pilot log shows one requirement table and the policy action (ship-equivalent “already landed”, revise, or escalate) with the GitNexus risk line.

---

## Task 4: Attach the gate to the harvest index

**Files:** `docs/plans/00-harvest-index.md` (one subsection, no queue rewrite)

- [ ] Under global gates, add: implementers of Queues C3, E3, and F run this rubric before calling a slice done.
- [ ] Queue A stays closed. Do not reopen T1.
- [ ] Queue B stays HOLD. Jev must not be used to argue a Spring AI upgrade.

**Done when:** the index links this file and does not authorize new code by itself.

---

## Out of scope

- Runtime calls from `ai-service` to Jev.
- Replacing GitHub review, Jenkins, or Bugbot.
- Auto-applying patches from `safe_to_apply`.
- Tuning Jev question packs (`pack-requirement`) until the pilot shows a systematic false covered/not_covered.

## Open point

Confirm the `jev-mcp` install command and whether TypeSafe calls leave the machine. The plan assumes a local MCP process that sends decision state to Jev; do not enable it on a slice that still contains secrets.
