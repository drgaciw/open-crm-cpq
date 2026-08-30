# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave C EXECUTING** — Safe CPQ/CRM polish  
**Plan:** `docs/forge/12-wave-c-plan.md`  
**Herdr:** `forge-wave-c` (6× Grok 4.5)  
**Sources:** GitHub `gh issue list` + forge backlog (GitNexus MCP offline; CLI reindex in progress)

## Wave C agents

| Agent | Task |
|-------|------|
| `wc-quotes` | Quotes list/detail stub |
| `wc-data` | CPQ data-access HTTP clients |
| `wc-ui` | Shared-ui on CRM leads/contacts |
| `wc-sim` | Pricing simulator stub |
| `wc-tests` | FE test hardening |
| `wc-n1` | PriceBook N+1 (gh #456 family) |

## Still held
- Activities API, S2B-11, S2C-12  
- CORS/CSRF wildcard cluster (#458…)  
- Auth bypass CRITICAL (#451)  
