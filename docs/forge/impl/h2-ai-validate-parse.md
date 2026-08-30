---
status: done
id: h2-ai-validate-parse
---

# h2-ai-validate-parse

## Summary

Wave H2 / F6: `CpqAgentToolClients.validateConfiguration` parses gateway JSON
`ApiResponse<ConfigurationValidationResult>` (`valid` + `violations[]`) into the
existing `CpqToolResponse` envelope. Pricing and quote line-item tool paths stay
opaque `JsonNode` via `postJson` / `getJson`.

## Changes

| Area | Detail |
|------|--------|
| `CpqAgentToolClients` | Typed `ParameterizedTypeReference<ApiResponse<ConfigurationValidationResult>>`; `data` is the result tree (`valid` / `violations`); envelope failure → `clientError` |
| `CpqAgentToolClientsTest` | Structured valid / invalid fixtures; envelope + transport failure cases |
| Constructors | Spring ctor takes `ObjectMapper` for `valueToTree`; test ctor unchanged (`RestClient` only) |

## Acceptance

- [x] Unit fixture with structured gateway payload green (`valid` + `violations` asserted)
- [x] `valid=false` still tool-success with structured violations in `data` (LLM-readable)
- [x] Pricing / line-item paths unchanged
- [x] `CpqToolResponse` envelope retained
- [x] JDK 21 unit tests

## Risk note

GitNexus: `CpqAgentToolClients` upstream **LOW** (1 direct → `CpqAgentTools`). AI
`validateConfiguration` **LOW**. Wave contract risk **LOW–MED**. Index was 28
commits behind at edit time; callers confirmed via `rg` (tools + unit tests only).
