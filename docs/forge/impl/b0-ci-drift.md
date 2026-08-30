# impl status — b0-ci-drift

| Field | Value |
|-------|-------|
| **your-id** | `b0-ci-drift` |
| **Batch** | Batch 0 / B0-5 (PR-D) |
| **status** | done |
| **branch** | `ci/forge-b0-contract-drift-gate` |
| **date** | 2026-08-30 |

## Intent

Run CI contract drift verify in the non-AI toolchain path as a **blocking** gate. Preserve ADR-003 (no LLM / AI assist on GHA).

## Files touched

| File | Change |
|------|--------|
| `scripts/ci/toolchain-check.sh` | After tool versions, run `verify-drift.py` (fail closed) |
| `scripts/ci/run-core.sh` | Usage text notes contract drift on toolchain |
| `docs/ci-contract.yaml` | `last_reviewed: 2026-08-30`; toolchain + GHA shared-libraries notes; drift_prevention rule |
| `.github/workflows/ci.yml` | Manual-fallback mirror step on `shared-libraries` (non-AI) |
| `docs/forge/impl/b0-ci-drift.md` | This status file |

## Tests run

| Command | Result |
|---------|--------|
| `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` | OK |
| `bash scripts/ci/run-core.sh toolchain` (docker client stubbed locally; host `docker.sock` denied) | OK including drift gate |
| Negative: GHA `backend-integration` needs include `frontend` → `verify-drift.py` and `run-core.sh toolchain` | both exit 1 |
| Restored workflow + re-run `verify-drift.py` | OK |
| `rg` for `ai-assist` / `run-ai-stage` / `AI_CI` in GHA workflow | none (ADR-003) |

## Blast radius

GitNexus MCP unavailable in this session. Static callers of `toolchain-check.sh` / `run-core.sh toolchain`:

- Jenkins `stage('Toolchain')`
- `infra/jenkins/scripts/quickstart.sh` host smoke
- Contract `verify_commands` / docs

**Risk:** Low — fail-closed on real drift; no stage graph reorder; AI Assist `drift_sentinel` unchanged (advisory).

## Notes

- Did **not** enable AI_CI on GHA; did **not** invent microservices.
- Jenkins AI Assist `drift_check` / `drift_sentinel.py` left as advisory when `AI_CI_ENABLED=true`.
- Local full `docker version` fails without docker.sock access; CI agents with Docker remain the intended runtime. Stub used only for local acceptance of the new drift step.
