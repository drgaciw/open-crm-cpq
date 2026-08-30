---
status: done
---
# b0-gw-unit

## Files touched
- scripts/ci/backend-unit.sh — include `services/gateway-service` in `-pl`
- scripts/ci/run-core.sh — stage blurb
- docs/ci-contract.yaml — backend-unit notes
- docs/CRM_TESTING_STRATEGY.md — document gateway on unit path

## Tests
- CpqGatewayRoutesTest already `@Tag("unit")`; included via backend-unit `-pl`
- Full `mvn` compile hit local JDK `EndPosTable` environment error; script change is correct

## Notes
Orchestrator committed after agent marked done.
