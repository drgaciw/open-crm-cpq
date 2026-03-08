---
name: perpetual-license
description: Core pack rounding, perpetual license fee calculation, and topology validation for on-premises products.
---

# Perpetual License

Use this skill when the user needs to calculate perpetual license fees with core-based packing (16-core packs), validate deployment topology for on-premises products, or resolve pricing for ONPREM_PERPETUAL and ONPREM_CORE product types.

## Scope

- Round core counts up to 16-core pack boundaries for ONPREM_CORE licensing
- Calculate one-time perpetual license fees based on core pack quantity
- Validate deployment topology (server count, core-per-server, cluster config)
- Apply the core-license-pack.drl rule (salience 590) for pack rounding logic

## Inputs

- Product ID (type: ONPREM_PERPETUAL or ONPREM_CORE)
- Total core count or server/core topology details
- Per-core-pack license fee
- Deployment environment metadata (optional)

## Workflow

1. Retrieve the product definition and confirm it is type ONPREM_PERPETUAL or ONPREM_CORE.
2. For ONPREM_CORE, collect the total core count from the topology input.
3. Round the core count up to the nearest 16-core pack boundary (e.g., 20 cores becomes 32 cores, i.e., 2 packs).
4. Execute the pricing engine, which triggers core-license-pack.drl (salience 590).
5. Calculate the total license fee: number of packs multiplied by the per-pack price.
6. Validate that the topology is supported (e.g., minimum core count, valid cluster size).
7. Return the pack count, rounded core total, per-pack price, and total license fee.

## Minimum Checks

- Product type is ONPREM_PERPETUAL or ONPREM_CORE
- Core count is a positive integer
- Core count rounds up to 16-core boundaries (never rounds down)
- The core-license-pack.drl rule (salience 590) fires during pricing execution
- Topology validation passes (cores per server within supported range)
- License fee is a one-time charge, not recurring

## Useful Commands

```bash
# Execute perpetual license pricing
curl -s -X POST '/api/v1/pricing/execute' \
  -H 'Content-Type: application/json' \
  -d '{"productId":"prod-onprem-001","type":"ONPREM_CORE","coreCount":48,"topology":{"servers":3,"coresPerServer":16}}'

# Retrieve on-prem product details
curl -s GET '/api/v1/products/{id}'
```
