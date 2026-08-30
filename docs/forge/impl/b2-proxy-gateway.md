---
status: done
id: b2-proxy-gateway
---

# b2-proxy-gateway

## Summary

W1-11 / PR-FE-7: Point the Angular CRM dev proxy at the API gateway (`:8080`) so `nx serve crm` can reach CRM and CPQ APIs through one origin. Document `:8081` as a temporary CRM-direct override only.

## Files

| Path | Change |
|------|--------|
| `frontend/proxy.conf.json` | `/api` target `http://localhost:8080` (was `:8081`) |
| `frontend/README.md` | Default gateway `:8080`; table + JSON for `:8081` CRM-direct override; links to CRM gateway docs |
| `docs/forge/impl/b2-proxy-gateway.md` | This status note |

## Acceptance

- [x] Default proxy target is gateway `:8080`
- [x] README documents `:8081` override for CRM-only DX and that committed config stays on `:8080`
- [x] `apps/crm` serve uses `proxyConfig: "proxy.conf.json"` (unchanged)

## Notes

- GitNexus MCP tools were unavailable in this worktree; change is config/docs only (**LOW** risk — local `nx serve` fails if gateway is down unless override is used).
- Forge docs under `docs/forge/02-crm-frontend.md` / `05-fe-cpq-ui.md` still describe the pre-cutover `:8081` default in narrative sections; runtime config is `:8080`.
