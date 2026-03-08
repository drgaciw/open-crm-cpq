---
name: bundle-assembly
description: Create product bundles, manage their composition, and validate bundle constraints for a quote.
---

# Bundle Assembly

Use this skill when you need to assemble a product bundle on a quote, validate bundle composition constraints, or modify an existing bundle's components.

## Scope

- Discover available bundle definitions and their required/optional components.
- Assemble a bundle on a quote, selecting components that satisfy all constraints.
- Validate minimum/maximum quantity rules and component compatibility.
- Do not create or modify bundle definitions in the product catalog.

## Inputs

- Quote ID to which the bundle will be added
- Bundle definition ID or search criteria
- Desired component selections and quantities

## Workflow

1. Retrieve available bundle definitions from the product-bundles endpoint.
2. Present bundle structure including required components, optional components, and quantity constraints.
3. Accept component selections and quantities from the user.
4. Validate selections against bundle constraints (min/max quantities, required components, exclusions).
5. POST the assembled bundle to the quote.
6. Return the finalized bundle summary with component breakdown and pricing impact.

## Minimum Checks

- All required bundle components are included in the assembly.
- Component quantities fall within defined min/max bounds.
- No excluded component combinations are present.
- The bundle is successfully persisted to the quote.

## Useful Commands

```bash
curl -s GET /api/v1/product-bundles
curl -s GET /api/v1/product-bundles/{bundleId}
curl -s POST /api/v1/quotes/{quoteId}/bundles -d '{"bundleId":"...","components":[...]}'
```
