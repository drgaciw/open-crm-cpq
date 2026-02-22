---
name: phase0-compatibility-spike
description: Run or refresh the Phase 0 pricing-rule compatibility spike by inventorying the target DRL catalog and seeded rules, reconciling them against a legacy PricingRules.drl source, and updating the phase0 reconciliation evidence doc.
---

# Phase 0 Compatibility Spike

Use this skill when you need to repeat the rule catalog reconciliation spike for the pricing service in an assigned worktree.

## Scope

- Work only in the assigned worktree.
- Do not edit other worktrees or shared repos.
- Treat the legacy `PricingRules.drl` source as read-only input.

## Inputs

- Target worktree containing `backend/services/pricing-service`
- Legacy source file path to `PricingRules.drl` (usually from the base repo worktree)

## Workflow

1. Confirm the assigned worktree and check for unrelated changes (`git status --short`).
2. Inventory target DRL resources under `backend/services/pricing-service/src/main/resources/rules` and extract rule names/salience.
3. Inventory seeded rules from `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql` (rule set metadata + rule name/type/priority/explanation code).
4. Read the legacy `PricingRules.drl` and list all source rule names.
5. Map every legacy source rule exactly once into `overlap`, `additive`, or `defer` with a short rationale tied to the Phase 0 catalog.
6. Update `docs/phase0/phase0-rule-catalog-reconciliation.md` with inventories, parity notes, mapping table, and a Phase 2 recommendation.
7. Verify the doc references the exact file paths used as evidence and that counts still match (target DRLs, seeded rules, source rules).

## Minimum Checks

- 8 target DRL files inventoried (or explicitly note count changes)
- 8 seeded rules inventoried (or explicitly note count changes)
- Every source `PricingRules.drl` rule classified once
- Phase 2 recommendation calls out ordering/precedence risks for price-writing rules

## Useful Commands

```powershell
rg --files -g "*.drl" backend/services/pricing-service/src/main/resources/rules
Select-String -Path backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql -Pattern "INSERT INTO rules|SELECT 'rule-"
Select-String -Path <legacy PricingRules.drl path> -Pattern '^rule\s+"([^"]+)"'
```
