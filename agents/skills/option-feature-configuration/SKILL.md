---
name: option-feature-configuration
description: Manage option groups and enforce configuration rules (REQUIRES/EXCLUDES/RECOMMENDS) for a quote line item.
---

# Option & Feature Configuration

Use this skill when a line item needs its options and features configured, or when you need to validate that selected options satisfy all configuration rules.

## Scope

- Retrieve available option groups and their rules for a given product.
- Apply REQUIRES, EXCLUDES, and RECOMMENDS rule logic to a configuration.
- Persist valid configurations to quote line items; reject invalid combinations.
- Do not modify the product catalog or rule definitions.

## Inputs

- Product ID for which options are being configured
- Quote ID and line-item ID to receive the configuration
- Desired option selections from the user

## Workflow

1. Fetch the product's option groups and associated rules via the product options endpoint.
2. Present available options organized by group, noting any default selections.
3. Accept the user's option selections and evaluate them against all REQUIRES and EXCLUDES rules.
4. Surface any RECOMMENDS suggestions that complement the current selection.
5. If all rules pass, POST the configuration to the quote line item.
6. If any rule fails, report the specific violation and suggest a corrective action.

## Minimum Checks

- All REQUIRES dependencies are satisfied before configuration is persisted.
- No EXCLUDES conflicts exist in the final selection.
- RECOMMENDS suggestions are surfaced but do not block configuration.
- The configured line item reflects the exact options selected.

## Useful Commands

```bash
curl -s GET /api/v1/products/{productId}/options
curl -s POST /api/v1/quotes/{quoteId}/line-items/{lineItemId}/configure -d '{"options":[...]}'
```
