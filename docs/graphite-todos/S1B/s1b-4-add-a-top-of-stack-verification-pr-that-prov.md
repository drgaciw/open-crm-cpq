# S1B-4: Top-of-stack backend verification

**Status:** Implemented on branch `todo/s1b-4-add-a-top-of-stack-verification-pr-that-prov`.

## Deliverables

- Parent POM Surefire/Failsafe split (`skip.integration.tests`, `*IntegrationTest` naming)
- Maven profiles: `unit-tests`, `integration-tests` (alias `integration`)
- [backend/README.md](../../../backend/README.md) — verification command table
- [docs/CRM_TESTING_STRATEGY.md](../../../docs/CRM_TESTING_STRATEGY.md) — reactor commands

## Evidence (2026-05-16)

| Command | Exit code |
|---------|-----------|
| `verify -pl libs/common,libs/security,libs/rules-engine -am` | 0 |
| `verify -Punit-tests -Djacoco.skip=true -pl libs/...,services/crm-service,... (excl. ai-service, configuration-engine)` | 0 |

Full reactor may fail on `configuration-engine` (protobuf) or `ai-service` (Spring AI) — out of scope for Stack 1B.
