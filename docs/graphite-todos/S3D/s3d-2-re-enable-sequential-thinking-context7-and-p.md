# S3D-2: Re-enable Sequential Thinking, Context7, and Puppeteer clients behind flags once dependency compatibility is proven.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S3D`

**Service/Module:** price-drools-service

**Actionable Todo:** Re-enable Sequential Thinking, Context7, and Puppeteer clients behind flags once dependency compatibility is proven.

**Stack Dependency:** S3D-1, S3C-2

**Reference Doc:** ../pricing-ai-rules-service/AI_SPEC.md; ../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md; ../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md

## Implementation Notes

- Added default-off MCP client feature flags for Sequential Thinking, Context7, and Puppeteer in `ai-service`.
- Added a small registry that exposes only enabled MCP client descriptors, keeping client activation behind configuration.
- Narrow Maven verification is still blocked by the known Spring AI `ChatClient` compile mismatch in `AbstractCpqAgent`.
