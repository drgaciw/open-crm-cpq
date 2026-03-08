---
name: validation-rules
description: Configuration validation, product status checks, and DRL syntax validation for pricing and product rules.
---

# Validation Rules

Use this skill when the user needs to validate a product configuration, check product status constraints, or verify DRL (Drools Rule Language) syntax for pricing and business rules.

## Scope

- Validate product configurations against business rules and constraints
- Check product status eligibility (e.g., only ACTIVE products are quotable)
- Validate DRL rule syntax before deployment
- Run pricing validation to detect rule conflicts or missing inputs

## Inputs

- Product ID and configuration selections
- DRL rule source code (for syntax validation)
- Pricing context (currency, customer segment, effective date)
- Quote line items (for pricing validation)

## Workflow

1. Identify the validation type: configuration, pricing, or DRL syntax.
2. For configuration validation, submit the product ID and selections to the validation endpoint.
3. For pricing validation, submit the quote context and line items.
4. For DRL syntax validation, parse the rule file and check for compilation errors.
5. Collect all validation errors, warnings, and informational messages.
6. Return a structured validation report with pass/fail status and actionable details.

## Minimum Checks

- All referenced product IDs resolve to existing, ACTIVE products
- Configuration selections satisfy all REQUIRES and do not violate EXCLUDES rules
- DRL files compile without syntax errors and salience values do not conflict
- Pricing validation confirms all required pricing inputs are present
- Validation errors include field-level detail for resolution

## Useful Commands

```bash
# Validate a product configuration
curl -s -X POST '/api/v1/products/validate-configuration' \
  -H 'Content-Type: application/json' \
  -d '{"productId":"prod-001","selections":[{"optionId":"opt-a","quantity":2}]}'

# Validate pricing
curl -s -X POST '/api/v1/pricing/validate' \
  -H 'Content-Type: application/json' \
  -d '{"quoteId":"q-001","lineItems":[{"productId":"prod-001","quantity":1}]}'
```
