# S3A-3: Replace deprecated @MockBean/@SpyBean test usage with Spring Boot 4-compatible test annotations where applicable.

**Tracking status:** Complete in this PR; repository test sources were audited and no deprecated `@MockBean` or `@SpyBean` usage remains to replace.

**Stack lane:** `S3A`

**Service/Module:** pricing-ai-rules-service/price-drools-service

**Actionable Todo:** Replace deprecated @MockBean/@SpyBean test usage with Spring Boot 4-compatible test annotations where applicable.

**Stack Dependency:** S3A-1

**Reference Doc:** ../pricing-ai-rules-service/compilation-errors-inventory.md; ../pricing-ai-rules-service/dependency-compatibility-report.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Verified there are no `org.springframework.boot.test.mock.mockito`, `@MockBean`, or `@SpyBean` references outside this tracking card.
- No code changes were required because the target test sources already avoid the deprecated Spring Boot test annotations.
