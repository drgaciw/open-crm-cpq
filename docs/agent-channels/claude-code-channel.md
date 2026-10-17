# Claude Code channel

Cursor cloud agents stay the default implementation channel.

Claude sessions work only from an allowlist validated by CPQ Decision. One story per session. The session ends at a draft PR. Draft PRs only, base `main`. When the allowlist is insufficient, or a spec conflicts with the code, the session stops and reports. The session does not widen the allowlist.

Existing root agent files (CLAUDE.md, AGENTS.md, AGENT.md) take precedence on conflict.

Routing table approved by owner via PM on 2026-10-17.

| Story type | Channel | Why |
| --- | --- | --- |
| Docs-only, ADR/spec, DoD status PRs, body edits | Cursor | Cheap, parallel, already working |
| Small single-module feature or bugfix, well specified | Cursor | Fits the cloud VM; parallel |
| Migration-heavy (Flyway/CAM V1_x, rollback, backfill code) | Claude (preferred) | Needs a database and Testcontainers loop, with careful ordering. No apply to any real environment |
| Multi-module refactor (quote, price book, CAM, gateway) | Claude, with GitNexus impact | Impact analysis where an index exists. Cloud agents have no GitNexus index |
| Long test-first loops (many red/green cycles, flaky integration tests) | Claude | Repeated red/green cycles. CI run ids remain required |
| Security-sensitive (authz, tenant isolation) implementation | Either; Claude reviews when Cursor implemented | Second-model check |
| Independent code review of a draft PR (read-only; findings to CPQ Decision) | Claude | Different model |
| Anything touching S3 (V1_10+) migrations | Claude only after S3 merges; file collisions checked | Avoid number collisions |

Routing criteria: a build or database loop of more than about three iterations, or two or more modules, or migration or backfill code, or a need for real impact analysis. CPQ Decision validates the routing per story before launch. The same story does not run in both channels.

Stories on this channel score [CPQ Definition of Done v0.3](https://app.notion.com/p/3fcf7cb4e4df8133860dcc8613c620eb).

TEST-FIRST (owner-approved 2026-10-17 7:45 AM CT, validated by CPQ Decision): for each new behavior, write a failing test first, then implement until it passes, before opening or updating the draft PR. Show the failing-first evidence as a commit or CI run id, not just a statement. In the PR body, map each test to its AC and DoD v0.3 item (3 Tests, 4 Quote and pricing correctness, 16 Authorization, 17 Concurrency and idempotency, 18 State-machine coverage) and cite real CI run ids. Say honestly which tests were not written test-first. Docs-only stories: N/A. This is a process rule inside the existing allowlist and widens no scope.

A session statement that tests passed is not evidence. The draft PR cites CI run ids.

Human gates: no merge, no ready-marking, no flag changes, no deploy, no S7 apply or compensate, no workflow or branch-protection edits, no external messages.

Independent of Aegis.
