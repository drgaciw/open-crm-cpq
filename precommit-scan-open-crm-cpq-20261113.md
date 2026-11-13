# Pre-Commit Security Scan — open-crm-cpq

**Date:** 2026-11-13
**Verdict:** SAFE TO COMMIT (0 new Critical/High on staged changes)

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 0 |
| Medium | 17 |
| Low | 0 |
| **Total** | **17** |
| New (staged) | 0 |
| Existing | 134 |

**Risk score:** 55.8/100 (High Risk) — driven by pre-existing findings, not this commit.

## Coverage notes

- gitleaks staged: clean; full-tree hits are `.gitnexus/*`, `.env`, tests, untracked `lo` (excluded from commit)
- semgrep: tool failed (opentelemetry import error) — not counted as clean
- checkov: 17 medium Dockerfile/GHA findings (existing)
- hadolint: ran; no blocking findings for gate

## Existing medium findings (sample)

- 🟡 MEDIUM `CKV_DOCKER_2` @ `/infra/jenkins/jenkins/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/document-generation/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/document-generation/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/contract-asset-management/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/contract-asset-management/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/configuration-engine/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/configuration-engine/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/pricebook-service/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/pricebook-service/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/approval-routing/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/approval-routing/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/product-service/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/product-service/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_DOCKER_2` @ `/backend/services/quote-service/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-healt
- 🟡 MEDIUM `CKV_DOCKER_3` @ `/backend/services/quote-service/Dockerfile:1` — https://docs.prismacloud.io/en/enterprise-edition/policy-reference/docker-policies/docker-policy-index/ensure-that-a-use
- 🟡 MEDIUM `CKV_GHA_7` @ `/cpq_beta/.github/workflows/release_notes.yml:12` — The build output cannot be affected by user parameters other than the build entry point and the top-level source locatio
- 🟡 MEDIUM `CKV2_GHA_1` @ `/cpq_beta/.github/workflows/on_release.yml:0` — Ensure top-level permissions are not set to write-all
