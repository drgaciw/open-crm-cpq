---
status: done
id: d1-gateway-cors-csrf
---

# d1-gateway-cors-csrf

## Summary

Removed `https://*.azurewebsites.net` from gateway CORS and CSRF allow-lists. Origins are now env-driven (`FRONTEND_URL` / `CORS_ALLOWED_ORIGINS`) with local default `http://localhost:4200`. `CsrfProtectionFilter` rejects any allow-list entry containing `*` at bind time and matches Origin/Referer with exact scheme/host/port only (no subdomain wildcards).

## Files

| Path | Change |
|------|--------|
| `backend/services/gateway-service/src/main/resources/application.yml` | Replace Azure wildcards with `${CORS_ALLOWED_ORIGINS:${FRONTEND_URL:http://localhost:4200}}` for CORS + CSRF |
| `backend/services/gateway-service/.../CsrfProtectionFilter.java` | `sanitizeAllowedOrigins`; remove `*.` matching; exact origin only |
| `backend/services/gateway-service/.../CsrfProtectionFilterTest.java` | Unit tests: wildcard reject, Azure subdomain forbid, localhost allow, yaml clean |
| `backend/docker-compose.yml` | `FRONTEND_URL: http://localhost:4200` on gateway-service |
| `docs/CRM_GATEWAY_ENV.md` | Document FRONTEND_URL / CORS_ALLOWED_ORIGINS |
| `microsite-prompt.xml` | Align CORS note with explicit env allow-list |
| `docs/forge/impl/d1-gateway-cors-csrf.md` | This status note |

## Acceptance

- [x] No `*.azurewebsites.net` in gateway CORS/CSRF config (yml + filter)
- [x] Env-driven allow-list (`FRONTEND_URL` / `CORS_ALLOWED_ORIGINS`); localhost:4200 default retained
- [x] `CsrfProtectionFilter` rejects `*.` patterns at construction
- [x] Gateway unit tests prove wildcard origins rejected
- [x] `docs/CRM_GATEWAY_ENV.md` updated
- [x] This impl note status done

## Blast radius (GitNexus + grep)

- Repository: `open-crm-cpq` (main index; worktree `d1-cors-csrf`). Index was 17 commits behind HEAD at analysis time.
- `CsrfProtectionFilter` upstream impact: **UNKNOWN** (Spring `@Component` WebFilter — no graph callers). Grep: gateway-only; not referenced outside `gateway-service`.
- `matchesPattern` → replaced by `matchesExactOrigin`: **LOW** (same-class callers only: `matchesAllowedOrigin` → `filter` / `matchesAllowedReferer`).
- Risk for this change: **MED** (security boundary; browser FE + cookie CSRF path). No HIGH/CRITICAL unresolved callers beyond filter UNKNOWN (confirmed unused outside gateway via text search).

## Verify

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/21.0.2
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -pl services/gateway-service -am test \
  -Dtest=CsrfProtectionFilterTest,CpqGatewayRoutesTest \
  -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false

rg -n 'azurewebsites|\*\.' backend/services/gateway-service docs/CRM_GATEWAY_ENV.md microsite-prompt.xml
```

## Notes

- When `CORS_ALLOWED_ORIGINS` is set, it **replaces** the `FRONTEND_URL` default; include every allowed origin explicitly.
- Wave D siblings: D2 (`security.open` removal), D3 (issue closeout) are out of scope for this PR.
