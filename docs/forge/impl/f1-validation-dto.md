---
status: done
id: f1-validation-dto
---

# f1-validation-dto

## Summary

Added S2B-11 shared DTOs in `backend/libs/common`: `ConfigurationValidationResult` (`valid` + `violations[]`) and `ConfigurationViolation` (`ruleId`, `ruleType`, `description`, `remediation`).

## Files

| Path | Change |
|------|--------|
| `backend/libs/common/.../dto/ConfigurationValidationResult.java` | New result DTO + `ok()` / `invalid()` factories |
| `backend/libs/common/.../dto/ConfigurationViolation.java` | New violation DTO |
| `backend/libs/common/.../dto/ConfigurationValidationResultTest.java` | Jackson round-trip unit test |
| `docs/forge/impl/f1-validation-dto.md` | This note |

## Acceptance

- [x] Types compile in `libs/common`
- [x] Fields cover agent-contract shape from Wave F architecture
- [x] No service wiring yet (F2/F3)
