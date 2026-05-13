# S3C-2: Create BaseMCPClient abstraction and keep Context7, Puppeteer, and Sequential Thinking clients behind it.

**Tracking status:** Completed in this PR.

**Stack lane:** `S3C`

**Service/Module:** price-drools-service

**Actionable Todo:** Create BaseMCPClient abstraction and keep Context7, Puppeteer, and Sequential Thinking clients behind it.

**Stack Dependency:** S3A-1

**Reference Doc:** ../pricing-ai-rules-service/backend-simplification-tasks.md; ../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md

## Implementation Notes

- Repository-wide searches for `BaseMCPClient`, `MCPClient`, `Context7`, `Puppeteer`, and `Sequential Thinking` only find this tracking card and general documentation references; there are no concrete MCP client implementations to place behind a shared abstraction on the current branch.
- The referenced `price-drools-service` module and `pricing-ai-rules-service` reference docs are not present in this repository. The current pricing/rules ownership lives in `backend/services/pricing-service` and `backend/libs/rules-engine`.
- No production code changes were needed because the MCP client layer described by the backlog task is already absent from the branch.
