# Ramp lifecycle acceptance evidence, 2026-10-10

The R1/R2 lifecycle gate passed through the actual gateway and domain services twice. A fresh second run also exercised schedule editing in the browser while the quote was actually `PENDING_APPROVAL`. Authentication used an ephemeral synthetic RSA JWT/JWKS/client-token fixture; configuration evaluation, product validation, pricing, pricebook lookup, PostgreSQL, transactional outbox, Kafka and canonical approval processing used real implementations.

## Recorded runs

| Run | UTC interval | Synthetic database | Result |
| --- | --- | --- | --- |
| Core API | 2026-10-10 04:18:03.570–04:21:46.350 | `ramp_next_acceptance_20261010041803` | 9 assertion groups, 44 gateway HTTP requests, passed, no cleanup errors |
| Browser-assisted lifecycle | 2026-10-10 04:22:11.611–04:25:56.806 | `ramp_next_acceptance_20261010042211` | Same 9 groups and 44 runner HTTP requests, plus 2 browser checks, passed, no cleanup errors |

The second run's browser requests are additional to the 44 runner requests. Its page errors and failed quote API responses were both empty. Each run recorded 16 JWKS reads and successful local client-token acquisition by pricing and quote services. Both runs bound source evidence to integration HEAD `d719f456f9588aedfd969c54617a0b128d7119ee`.

Final harness review tightened three reusable-source guards to compare against `HEAD`, so staged changes are rejected as well as unstaged changes. A scratch-repository regression showed the old guard returning 0 and the corrected guard returning 1 for staged-only changes in backend, gateway and frontend scopes. Node syntax and PowerShell AST checks passed. This guard correction changes admission checks only; the successful runs' source files and copied JARs were rehashed and still match their provenance. The lifecycle was not rerun for this correction.

The nine assertion groups establish:

1. Missing, wrong-audience and expired bearer identities receive HTTP 401.
2. Real quote→pricing→pricebook calculation preserves the verified author and yields AC01 TCV 81,000, ACV 27,000, ARR 24,000 and exit ARR 30,000.
3. The quote's current ramp binding reaches canonical approval through the transactional outbox, owned Kafka broker and canonical inbox.
4. Submit invokes authenticated configuration-engine/product preflight and enters actual `PENDING_APPROVAL`.
5. An identical pending schedule save preserves the priced snapshot, current approval and outbox binding count.
6. Editing the second period from 45 to 40 returns the quote to stale `DRAFT`, supersedes the old canonical approval, and refuses stale submit (422) and late decisions through both approval paths (409).
7. Recalculation changes the pricing fingerprint even though the governing discount remains 32%; unauthorized approval is 403 and the verified Sales VP identity can approve the replacement.
8. Submit→canonical approval→send→accept preserves the priced periods and amounts. Accepted authoring/calculation are refused; `accepted_at` is persisted and the accepted outbox event is acknowledged as published.
9. Process-only ramp flag-off rejects scheduled authoring with 422 without writing a line, while real flat quote calculation still succeeds.

After the commercial edit, both accepted snapshots retain TCV 74,400, ACV 24,800, ARR 20,400, exit ARR 30,000 and totalAmount 1,700. Canonical approval identities advance from 1 to 3. The browser checks save/reload the identical schedule, then save/reload the changed period: the first preserves Pending Approval and priced preview; the second displays Draft/stale state, hides old priced preview/governance, and disables Submit, Send and Accept.

## Source and artifact provenance

The launcher byte-compared 1,065 tracked backend files against the selected artifact-source worktrees and copied seven executable JARs with recorded SHA256 values. Six JARs came from the prior audit worktree at `834410a2f7ebdee8027df26220c479a0f65d9bc8`, whose Docker clean Maven verify/unit-tests run passed 16 modules and 1,661 unit tests. The gateway came from the integration worktree: implementation commit `95110df`, unchanged backend content at run HEAD `d719f45`, with 437 common/security/gateway unit tests passing after the targeted repair. This provenance relies on those observed builds; a source-byte comparison and JAR hash alone do not independently prove that an arbitrary supplied JAR was compiled from those sources.

| Service | Copied JAR SHA256 |
| --- | --- |
| gateway-service | `7738E0C2D452C48EE444E9D22A592EBD4DF71D9C73A5149FABCD40FF1277CF5B` |
| product-service | `922612C8560EB3453B49A2268C3722A0D2510210030403ACCEBE78CBCDD283CB` |
| pricing-service | `4E0791425D0F2CAADC075204E52D5B950AD3335E9B079312D28843A775BFCD5D` |
| pricebook-service | `F502488AF4DA281CFDB67ADE732D4058209CD153BA95FD698CAC819A359FAD5D` |
| quote-service | `1556A6F60B4DD2D0F3084A704DD34018C78E0C6E44451F9C34034D90240EA68E` |
| configuration-engine | `C595F9BA1D51B9BE0DAE411FFC6158576021B1FEDB9A97763B4399FA6BA05663` |
| approval-routing | `3EB92ABCF4F2F46CC97A532459812107824ACBBE58BD9DD34DDF9ED49F6C5CFC` |

The browser used the audit's previously built CRM application. All 533 tracked frontend source files matched the integration tree; 120 compiled files were hashed into the result. Browser interception supplied the synthetic author bearer only to the local API proxy. External origins were blocked, including externally hosted font/icon assets, so the screenshots establish workflow state rather than visual polish or external asset availability.

Local evidence lives in the retained integration worktree's ignored `.acceptance-artifacts/lifecycle/` directory:

- `result-core-pass.json`, `processes-core-pass.json`, `artifact-provenance-core-pass.json`: first successful run.
- `result.json`, `processes.json`, `artifact-provenance.json`: browser-assisted successful run and current empty ownership manifest.
- `pending-identical.png`, `pending-edit.png`: browser state evidence.
- Timestamped per-service logs, `broker.log`, `redis.log`, `memory.jsonl`: diagnostics and resource observations. The generic broker/Redis logs contain the latest run; per-service logs retain their unique launch timestamps.
- `result-attempt3.json`, `processes-attempt3.json`, `artifact-provenance-attempt3.json`: retained pre-fix HTTP 504 reproduction, explicitly failed.

Preserve/copy this ignored evidence before removing the worktree. The committed document records results and provenance but does not embed all local logs, built artifacts or screenshots.

## Containment and prerequisites

This is a Windows, named-wave harness, deliberately restricted to:

- Integration: `C:\Users\dgorn\.codex-worktrees\open-crm-cpq\ramp-next-20261010\integration`.
- Browser/audit source: `C:\Users\dgorn\.codex-worktrees\open-crm-cpq\quality-audit-20261009`.

Retain those worktrees and verified artifacts to reproduce this wave. A clone at an arbitrary path will be refused; broadening the allowlist is a separate impact-reviewed change. These restrictions prevent accidental use of the other checkout, its `:4200` server or its GitNexus MCP.

Required locally: PowerShell 7 (`pwsh.exe`), Node with built-in fetch, Java capable of running the verified JARs, Git and Docker on PATH. The observed host used Node 24 and JDK 25. The existing `postgres` Docker container must expose the intended PostgreSQL database server at `127.0.0.1:5432`, permit the `postgres` user to create a new synthetic database through container-local psql, and have its matching password supplied via `DB_PASSWORD` in the launching process environment. No password is committed or written into provenance/results.

Cached Docker images `apache/kafka:4.3.1` and `redis:8` are required: launches use `--pull=never`. The seven executable `backend/services/<service>/target/<service>-1.0.0-SNAPSHOT.jar` files must exist in the verified source worktrees. Optional browser checks also require audit `frontend/dist/apps/crm/browser`, its installed Playwright package and Chromium executable; the harness does not build or download them.

Reserved loopback ports are gateway 19080, pricing 19082, product 19083, quote 19084, approval 19085, pricebook 19087, configuration-engine 19088, JWT fixture 19090, browser static/API proxy 19091, owned Redis 19379 and owned Kafka 19392. Existing listeners are not reused or killed. The committed frontend proxy remains unchanged.

Initial broker launch requires at least 2.5 GB measured free host RAM, each JVM launch 1.5 GB, post-readiness reserve 0.8 GB, and each owned browser launch 1 GB. Do not lower guards to bypass a resource shortage. The launcher starts bounded services sequentially and stops only PIDs whose start time, executable, JAR and port match the ownership manifest. Container cleanup checks exact IDs and wave/database labels. An interrupted host/process can require explicitly inspecting that manifest and invoking its owned cleanup commands.

Both successful runs finished with zero owned JVMs, no owned Kafka/Redis containers, and no listeners on the acceptance ports. The fixture server, static server and browser closed. Synthetic databases were retained as evidence. Existing services were preserved. Jev remained disabled, the legacy approval read default remained enabled, and ramp/outbox overrides existed only in these owned test processes.

## Practical reproduction

Start from the retained integration worktree, with source/JAR build provenance verified and sufficient measured memory. The launcher refuses to overwrite an existing manifest. Before preparing a new run, inspect the manifest and ensure `processes` is empty and `broker`/`redis` are null. Archive the prior `processes.json`, `result.json` and `artifact-provenance.json` under new unique names; never discard evidence or hide a live manifest.

```powershell
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\dgorn\.codex-worktrees\open-crm-cpq\ramp-next-20261010\integration'
$auditSource = 'C:\Users\dgorn\.codex-worktrees\open-crm-cpq\quality-audit-20261009'
& ./scripts/verification/ramp-lifecycle-processes.ps1 -Action prepare `
  -IntegrationRoot (Get-Location).Path -ArtifactSource $auditSource `
  -GatewayArtifactSource (Get-Location).Path
if ($LASTEXITCODE -ne 0) { throw 'Preparation failed.' }

# Enter the matching PostgreSQL password without putting it in command history.
$databaseCredential = Read-Host 'Synthetic database password' -AsSecureString
$env:DB_PASSWORD = ConvertFrom-SecureString $databaseCredential -AsPlainText
try {
  node scripts/verification/ramp-lifecycle-acceptance.mjs
  $acceptanceExit = $LASTEXITCODE
} finally {
  Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
  Remove-Variable databaseCredential -ErrorAction SilentlyContinue
}
if ($acceptanceExit -ne 0) { throw 'Acceptance failed; inspect result.json and owned logs.' }
```

The core run must pass first. For a browser-assisted second run, inspect/archive its completed manifest/results/provenance, prepare again to obtain a fresh synthetic database, and set `$env:RAMP_BROWSER_ARTIFACTSOURCE = $auditSource` before the same Node command. Remove that environment variable in the `finally` block. Do not reuse the first database/manifest; the runner requires a prepared, never-run manifest.

For interrupted-run recovery, invoke the launcher with `-Action stop -IntegrationRoot (Get-Location).Path`, then `-Action stop-broker` with the same root. It refuses to stop unrelated PIDs/containers. Readiness is application readiness, not proof of every dependency; HTTP 503/504 or fallback envelopes never count as feature acceptance.

## Explicit limits

- No real Entra browser authorization-code/PKCE/session login, customer notification, or downstream `QuoteAccepted` CRM/subscription consumer was exercised.
- No rollback fault injection, historical distinct-event replay ordering, broad page/field UAT or chaos run is claimed. The browser checks cover the two pending schedule workflows described above.
- This local harness is not wired into CI. Browser UAT/chaos are not implied by a green unit-test gate.
- The earlier backend clean `unit-tests` profile excluded Docker-tagged `*IntegrationTest` classes; it did not execute every Docker integration suite. This lifecycle run used real Docker services for its own stated assertions.
- Frontend CI uses an allowlist, currently including cpq-quotes but excluding other CPQ projects/shared-ui; it does not prove every frontend project.
- GitNexus framework bindings/dynamic dispatch can remain `UNKNOWN`, and broad process enumeration has reported a cap. Empty graph results are not an all-clear; actual forwarding/lifecycle checks supply the stated additional evidence.
- Other gateway services' malformed CircuitBreaker shortcuts remain a separate follow-up. These passes certify only the exercised paths with the scoped quote/pricing correction.
- R3/R4 product implementation and production rollout are not established by this R1/R2 acceptance evidence. Frontend dependency advisories remain a separate upgrade task.
