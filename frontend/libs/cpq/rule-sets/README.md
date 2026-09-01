# cpq-rule-sets

Pricing rule-set admin portal (`@frontend/cpq-rule-sets`).

## Pattern

Uses existing `RuleSetService` / `RuleService` from `@frontend/cpq-pricing-data-access` (signal-state). List (optional status filter), detail with Activate/Deprecate ConfirmDialog, nested rules via `listByRuleSet`, create/edit rule with DRL textarea + `validateSyntax`, per-rule **Impact** dialog via `analyzeImpact`. Standalone + OnPush; no NgRx.

## HTTP

| Surface | Path |
|---------|------|
| Rule sets list/get/create/activate/deprecate | `/api/v1/rule-sets` |
| Rules by set / CRUD / validate / impact | `/api/v1/rules` (`listByRuleSet` only — no bare list; impact `GET /{id}/impact`) |

**Never** call `/api/v1/configuration/rules` or `/api/v1/pricing/rule-sets`.

```ts
import { RULE_SETS_ROUTES } from '@frontend/cpq-rule-sets';
import { RuleSetService, RuleService } from '@frontend/cpq-pricing-data-access';
```

## Running unit tests

```bash
NX_DAEMON=false npx nx test cpq-rule-sets --skip-nx-cache
```
