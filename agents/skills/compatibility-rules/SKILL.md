---
name: compatibility-rules
description: Compatibility validation and rule management for product configuration constraints.
---

# Compatibility Rules

Use this skill when the user needs to define, query, or validate compatibility rules between products or product options, including REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, and VALIDATES rule types.

## Scope

- Create and manage compatibility rules between products or options
- Validate a proposed configuration against all applicable rules
- Support rule types: REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, VALIDATES
- Report conflicts and suggest compliant alternatives

## Inputs

- Product ID (source product for the rule)
- Rule type (REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, VALIDATES)
- Target product or option ID
- Configuration payload to validate
- Rule priority and error message

## Workflow

1. Retrieve existing compatibility rules for the source product.
2. If creating a rule, validate that both source and target products/options exist.
3. Check for conflicting rules (e.g., a product cannot both REQUIRE and EXCLUDE the same target).
4. Create the compatibility rule with the specified type, target, and metadata.
5. To validate a configuration, submit the full selection payload and review the rule evaluation results.
6. Return any violations, recommendations, or defaults applied.

## Minimum Checks

- No circular REQUIRES dependencies exist
- REQUIRES and EXCLUDES rules do not conflict for the same source-target pair
- All referenced product and option IDs are valid
- VALIDATES rules include a valid expression or DRL reference
- Configuration validation returns a clear pass/fail with violation details

## Useful Commands

```bash
# List compatibility rules for a product
curl -s GET '/api/v1/products/{id}/compatibility-rules'

# Validate a product configuration
curl -s -X POST '/api/v1/products/validate-configuration' \
  -H 'Content-Type: application/json' \
  -d '{"productId":"prod-001","selections":[{"optionId":"opt-a","quantity":1},{"optionId":"opt-b","quantity":1}]}'
```
