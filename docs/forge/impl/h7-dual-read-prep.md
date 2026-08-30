---
status: done
id: h7-dual-read-prep
---

# h7-dual-read-prep

## Summary

Wave H7: prove quote BFF pending reads work with `approvals.legacy-local-read-enabled=false` (routing-only). **Production default remains `true`** — no HIGH dual-store flip this wave.

## Evidence

- `application.yml`: `legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}`
- Test: `ApprovalServiceSingleWriterTest.getPendingWithLegacyFlagOffDoesNotReadLocal`

## Ops note

Flip only after operators confirm no remaining local-only PENDING rows; that flip is a **HIGH** gate (not Wave H).
