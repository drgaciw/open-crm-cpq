---
name: product-sku-management
description: SKU creation, naming validation, and lifecycle state transitions for products.
---

# Product SKU Management

Use this skill when the user needs to create new product SKUs, validate SKU naming conventions, or transition a product through lifecycle states (e.g., draft, active, deprecated, retired).

## Scope

- Create and register new product SKUs with validated naming patterns
- Enforce SKU uniqueness and naming convention rules
- Manage product lifecycle state transitions (draft -> active -> deprecated -> retired)
- Query existing products by SKU, status, or category

## Inputs

- Product name and description
- SKU identifier (or auto-generate per naming convention)
- Product category and type
- Target lifecycle status for transitions
- Product ID (for status updates)

## Workflow

1. Validate the requested SKU against naming conventions (prefix, length, allowed characters).
2. Check for SKU uniqueness by searching existing products.
3. Create the product record via POST or update status via PUT.
4. Confirm the new state and return the product details.
5. If a state transition is invalid (e.g., retired -> active), report the error with allowed transitions.

## Minimum Checks

- SKU matches the organization naming pattern (e.g., uppercase alphanumeric with hyphens)
- SKU is unique across all products
- State transition follows the allowed lifecycle graph
- Required fields (name, SKU, category) are present before creation

## Useful Commands

```bash
# List all products
curl -s GET '/api/v1/products'

# Create a new product
curl -s -X POST '/api/v1/products' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Product Name","sku":"PRD-001","category":"SOFTWARE","status":"DRAFT"}'

# Transition product status
curl -s -X PUT '/api/v1/products/{id}/status' \
  -H 'Content-Type: application/json' \
  -d '{"status":"ACTIVE"}'
```
