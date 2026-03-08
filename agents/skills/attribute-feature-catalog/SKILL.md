---
name: attribute-feature-catalog
description: Attribute definition management, option group creation, and price adjustments for product features.
---

# Attribute & Feature Catalog

Use this skill when the user needs to define product attributes, create option groups for configurable features, or set price adjustments tied to specific attribute selections.

## Scope

- Define and manage product attributes (e.g., color, size, region)
- Create option groups with selectable values for each attribute
- Configure price adjustments (absolute or percentage) per option selection
- Query existing attributes and options for a given product

## Inputs

- Product ID
- Attribute name, type, and constraints (required, default value)
- Option group name and selectable values
- Price adjustment amount and type (FIXED, PERCENTAGE) per option
- Display order and visibility settings

## Workflow

1. Retrieve the target product to confirm it exists and is in a configurable state.
2. List existing attributes to avoid duplicates.
3. Create or update attribute definitions on the product.
4. Create option groups with their selectable values and price adjustments.
5. Validate that all required attributes have at least one option defined.
6. Return the complete attribute and option catalog for the product.

## Minimum Checks

- Product exists and is in DRAFT or ACTIVE status
- Attribute names are unique within the product
- Option values are unique within their option group
- Price adjustments are non-negative and use a valid adjustment type
- Required attributes have a default value specified

## Useful Commands

```bash
# List attributes for a product
curl -s GET '/api/v1/products/{id}/attributes'

# Create an attribute
curl -s -X POST '/api/v1/products/{id}/attributes' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Region","type":"STRING","required":true}'

# List option groups for a product
curl -s GET '/api/v1/products/{id}/options'

# Create an option group
curl -s -X POST '/api/v1/products/{id}/options' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Support Level","values":["Basic","Premium"],"priceAdjustments":{"Premium":{"amount":500,"type":"FIXED"}}}'
```
