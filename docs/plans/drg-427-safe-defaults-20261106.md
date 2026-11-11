# DRG-427 safe configuration defaults

Implementation on `feat/parallel-story-wave-20261106`, preserving the prior uncommitted wave. Sources: [Linear DRG-427](https://linear.app/drgamtd-workspace/issue/DRG-427), [Notion BC-02-04](https://app.notion.com/p/3e3f7cb4e4df81889eacc19abe5b6c68). Both require constraint-safe defaults with visible default provenance. Live discovery found Backlog, no assignee, blockers or implementation PR; the Notion staffing scout found no competing owner.

## Behavior and scope

`POST /api/v1/configurations/{id}/defaults` applies catalog-declared defaults to missing **required** option-group selections. It uses the authenticated actor and refuses a different owner, deleted/expired session or non-IN_PROGRESS state. Product IDs must identify an existing numeric catalog product. The existing product-options read API supplies group cardinality, selection type and `defaultOption` flags; no product API, shared DTO, gateway or configuration changes are introduced.

Seller choices are preserved. Required groups already satisfied receive no additional choice. All declared missing defaults for an unsatisfied required group form one candidate batch; insufficient defaults, ambiguous single-choice defaults, exceeded cardinality, malformed catalog identity, unavailable catalog and collisions with a seller key fail closed. The client first confirms the root product through the existing product GET, checking success and exact identity: the groups endpoint alone returns an empty list even for an unknown parent. Each newly inserted default target is also confirmed through that non-deleted product lookup before managed mutation; option rows can outlive a deleted child product. Existing manual choices are not subjected to a new lifecycle policy, and this change adopts no ACTIVE-only rule. The service does not select an arbitrary unmarked option or try alternatives until one happens to pass. Non-product attribute defaults are refused because the existing session constraint contract cannot validate their identity.

The complete detached candidate passes existing REQUIRES/EXCLUDES/VALIDATES evaluation before a managed selection changes; RECOMMENDS remains a visible nonblocking warning. A dedicated `ConstraintSolver.evaluateExplicitSelections` seam excludes implicit DEFAULT expansion for this check. Otherwise the old core could validate an unpersisted target as though selected. Existing evaluate paths keep their behavior; this does not implement the held full solver or a new rule language.

Generated selection keys are `option:<groupId>:<optionId>` with the option's product ID as value, following the session's existing numeric-product-value convention. New DTO field `defaultedSelectionKeys` marks these values, including after normal GET/reload. Explicit add/update/remove of one key clears only its marker. Existing selections remain manual after upgrade. This is API-visible provenance; no frontend button or new wizard is added.

## Persistence and ownership

The orchestrator allocated **configuration-engine-only** `V1_5__configuration_default_provenance.sql` after inspecting V1_1–V1_4. It creates `configuration_default_selections`, keyed by session/key with cascading session deletion. No existing migration is edited and no CRM migration number is claimed. ApplyDefaults runs transactionally; Hibernate's existing optimistic versioning remains authoritative for concurrent session writes. No event/registry or runtime flag is added.

Before an environment rolls back the new application, remove defaults traffic. Old code ignores the additive table; retain it to preserve provenance during application rollback. A separately reviewed destructive schema rollback would drop only the new table and lose its markers, not session selections. No rollback, migration execution or deployment is authorized or performed by this document.

Owned production files are limited to configuration-engine:

- New `product/ProductOptionGroupSnapshot.java`, `product/ProductOptionDefaultsClient.java`.
- New `session/ConfigurationDefaultsService.java`, `session/ConfigurationDefaultsController.java`.
- Existing `session/ConfigurationSession.java`, `dto/ConfigurationSessionDto.java`, and new method in `constraint/ConstraintSolver.java`.
- New migration `V1_5__configuration_default_provenance.sql`.

Tests add six focused classes and update only configuration-engine's migration count in `ServiceSchemaIsolationIntegrationTest`. Prior activity, product, quote and CRM files are outside this lane.

## Verification evidence

Pre-edit CLI impact was bound to `C:/Users/dgorn/open-crm-cpq`. Entity/DTO/class reports were LOW; entity results explicitly carry the BaseEntity dispatch lower bound. Entity `addSelection` and `removeSelection` each have the service then controller as two upstream symbols, LOW, no indexed processes. `ConstraintSolver` has one importing service, LOW, no indexed processes. Source searches confirmed the DI/controller paths where class reports showed no callers. No zero-caller result is used as proof of disuse.

The failing-first compile used the new `ConfigurationDefaultsServiceTest` before production classes existed:

```text
backend/mvnw.cmd -B -Punit-tests -pl services/configuration-engine -am -Dtest=ConfigurationDefaultsServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Observed RED: seven missing-production-symbol compilation errors, exit 1, 17.398 seconds; `.gitnexus/drg427-red.log`. This is compilation-level failing-first evidence, not a claim that behavior tests executed red. The command's PowerShell `-D` arguments were quoted and Maven heap capped at 768 MB.

| Requirement | Test coverage / observed status |
| --- | --- |
| Declared defaults, manual preservation, insufficient/ambiguous defaults, atomic conflicting batch, replay-stable markers, actor/state checks, missing/deleted target and null selection type | `ConfigurationDefaultsServiceTest`: 12 passed. |
| No phantom DEFAULT target | `ExplicitDefaultsConstraintTest` uses the actual core with DEFAULT plus REQUIRES; preserves old evaluate behavior and refuses missing explicit selection: 1 passed. |
| Cross-group EXCLUDES conflict | `ConfigurationDefaultsRulesTest` uses the actual solver/core and asserts no managed mutation or repository save: 1 passed. |
| Authenticated actor, DTO markers, failed HTTP response | `ConfigurationDefaultsControllerTest`: 2 passed. |
| Existing catalog JSON projection, unavailable upstream, malformed success envelope, unknown/mismatched root product, existing/deleted default target | `ProductOptionDefaultsClientTest`: 7 passed. |
| Real Flyway upgrade from V1_4, existing manual rows, Hibernate validation, marker reload/override, after-flush rollback | `ConfigurationDefaultProvenanceIntegrationTest`, tagged `integration`; requires Docker and has not run. |

Final full unit regression passed after review fixes: `backend/mvnw.cmd -B -Punit-tests -pl services/configuration-engine -am test`, heap capped at 768 MB, 42.787 seconds, exit 0. Configuration-engine ran **114 tests, zero failures/errors/skips**, including 23 new unit tests. The upstream common and security modules also passed. Log: `.gitnexus/drg427-final-unit.log`. The earlier `.gitnexus/drg427-unit.log` records the pre-review 110-test baseline. `git diff --check` passed after source/test edits. The Docker-tagged integration classes were excluded by the unit profile and have not run; this result does not certify the migration or ORM persistence. Concurrency and browser UAT remain unverified. Browser/chaos scripts are outside CI. Exact-head CI, designated CPQ Decision and owner merge remain separate delivery gates; no designated PASS, merge, external tracker update or Done state is claimed.

Independent review follow-up: the new-target existence guard above has missing/deleted-target atomic-refusal tests and a known-target client test. The existing explicit null-selection-type guard has a 422 regression test. New 422 code uses Spring's nondeprecated `UNPROCESSABLE_CONTENT` name. The Docker fixture's JDBC URL now appends the schema with `&` when Testcontainers already supplies a query string. The final unit run above covers these production fixes; Docker fixture execution remains pending. Follow-up impact for the newly created, not-yet-indexed `getGroups`, `applyDefaults`, `validateGroup` and JDBC URL helper returned UNKNOWN; confirming source searches bound callers to this lane's controller, service and tests. That result is unresolved graph coverage, not a clean impact certification.
