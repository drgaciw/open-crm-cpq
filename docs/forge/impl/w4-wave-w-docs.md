---
status: done
id: w4-wave-w-docs
wave: W
pr: W4
---

# w4-wave-w-docs

## Summary

Wave W forge closeout (docs only). **Accept ADR-006** — W1+W2+W3 on `main` removed the remaining three entity leaks (`ConfigurationSession`, approval-routing `ApprovalRequest`, `DocumentGenerationJob`). Dual-read default still `${APPROVALS_LEGACY_LOCAL_READ:true}` — YAML/env **untouched**. Did **not** Accept ADR-005. Product-service `POST /configuration/validate` still retained. WhatsApp #430 not undone. Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** start price-book list-row delete (**R2** still **HELD**). Did **not** name Wave T / T1 / T2. Did **not** reopen N/S/U/V. No Java, NgRx, Activities, `app.routes` / sidebar / shell, subscriptions FE. Boot **4.0.6** held.

## Landed (Day-1 already on `main`)

| ID | Result | Tip SHA |
|----|--------|---------|
| W1 | configuration-engine `ConfigurationSessionDto` + MapStruct; controller DTO-only; omit `deleted`; session validate retained; product validate **untouched** | `e6d3b23` (#503) |
| W2 | approval-routing `ApprovalRequestDto` (`com.opencrm.approval.dto`); wire keeps `RoutingApprovalDto` fields; omit `deleted`; **no** quote BFF / FE | `29cac5b` (#501) |
| W3 | document-generation `DocumentGenerationJobDto`; thin controller; retry via service; omit `deleted` | `0a3d9a4` (#502) |

## ITs / Docker note

Backend module tests for W1–W3 used Docker-backed Testcontainers where applicable. Prefer wrapping Maven in `newgrp docker` so the process group can reach `docker.sock` (see `docs/forge/impl/tdd-be-it-reactor.md`):

```bash
newgrp docker <<'EOF'
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 \
  ./mvnw -B -pl services/<module> -am test
EOF
```

## Residuals / holds noted (this closeout)

| Residual | Disposition |
|----------|-------------|
| **R2** price-book list-row delete | **Still HELD** — do not implement; not named a Wave R track |
| **Wave T** / T1 / T2 | **Skipped** — do not name or reopen |
| Dual-read flip | Hold **J–W** — YAML untouched; still `true` |
| ADR-005 | Still **Proposed** — do **not** Accept |
| ADR-006 | **Accepted** (this PR) — remaining three entity leaks gone |
| Subscriptions FE / ERP rewrite | **HELD** |

## Docs closeout (this PR)

| Artifact | Change |
|----------|--------|
| `docs/adr/ADR-006-entities-never-at-api-boundary.md` | Status **Accepted** (W1+W2+W3 on `main`) |
| `docs/adr/README.md` | ADR-006 **Accepted** |
| `AGENT.md` / `ARCHITECTURE.md` | Waves **A–V COMPLETE**; Wave **W** this closeout (W1∥W2∥W3); dual-read hold **J–W**; ADR-006 Accepted |
| Matrix | Dual-read flip **not authorized in Waves J–W**; Wave W BE DTO note; YAML untouched |
| `35-wave-w-architecture.md` | Status COMPLETE; R2 still held; T skipped; W1–W4 checkbox |
| `docs/forge/impl/w4-wave-w-docs.md` | This note |

## Holds (unchanged)

- Activities API / `/api/v1/activities/**`
- CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**); Accept ADR-005
- Full SpEL; Kafka S2B-9; subscriptions FE; NgRx
- **R2** price-book list-row delete; named Wave T / T1 / T2; WhatsApp #430 undo
- Boot 3 revert / Azure 7.x BOM this wave
