# Impl status: b0-cpq-docs (B0-6)

**Agent:** impl-docs  
**your-id:** `b0-cpq-docs`  
**Branch:** `docs/forge-b0-cpq-matrix-pricebooks`  
**Task:** Register price-books in contract matrix + fix agent skill path drift (forge track 04 PR-B)  
**status:** done

## Files touched

| File | Change |
|------|--------|
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | New §4b price-book rows (11); port map 8089; gateway predicates; §8 tension; Appendix A/B totals → **80** |
| `agents/skills/price-book-management/SKILL.md` | `/api/v1/pricing/price-books` → `/api/v1/price-books` |
| `agents/skills/list-price-application/SKILL.md` | Same path correction |
| `agents/price-book-management.md` | Path + primary service → `pricebook-service (8089)` |
| `docs/forge/impl/b0-cpq-docs.md` | This status file |

## Intentionally not changed

- Java controllers / services
- `gateway-service` `application.yml` (already routes `/api/v1/price-books/**`)
- `CpqGatewayRoutesTest` (out of docs/agents-only scope for B0-6)

## Tests run

Docs/agents-only change — no Maven/Nx suite applicable.

Verification commands:

```bash
rg -n 'pricing/price-books' agents -g '*.md'   # no call-site hits remaining
rg -n '@RequestMapping\("/api/v1/price-books"\)' backend/services/pricebook-service
rg -n '§4b|Total matrix rows' docs/backlog/CPQ_API_CONTRACT_MATRIX.md
```

Matrix prose still mentions the wrong `/api/v1/pricing/price-books` path once in §4b and §8 as an explicit **do-not-use** note.

## Blast radius / GitNexus

GitNexus MCP tools were **unavailable** in this session. Static grep: consumers of the corrected paths are agent docs/skills (004, 032, 035) and matrix readers; no Java symbols edited. Risk **LOW** (documentation only).

## Notes

- Canonical external path: `/api/v1/price-books/**` on **pricebook-service** (8089).
- Agent-contract GETs: list, get-by-id, list entries (agents 004 / 032 / 035).
- Mutations remain **stable** (035 advisory / human-gated).
- Internal resolve-batch documented as **internal**.
