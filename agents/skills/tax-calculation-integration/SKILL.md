---
name: tax-calculation-integration
description: Calculates tax rates, validates exemptions, and determines tax jurisdictions via Avalara AvaTax or Vertex O Series.
---

# Tax Calculation Integration

Use this skill when you need to calculate taxes on a quote, validate tax exemption certificates, or determine the correct tax jurisdiction for a transaction.

## Scope

- Calculate applicable tax rates for quote line items by jurisdiction
- Validate customer tax exemption certificates and apply exemptions
- Determine the correct tax jurisdiction based on ship-to and bill-to addresses
- Integrate with external tax engines (Avalara AvaTax, Vertex O Series)

## Inputs

- Quote ID
- Ship-to address (street, city, state, postal code, country)
- Bill-to address (if different from ship-to)
- Tax exemption certificate ID (optional)
- Product tax category codes

## Workflow

1. Retrieve the quote with line items, customer addresses, and product details.
2. Determine the tax jurisdiction based on ship-to address (or bill-to where applicable).
3. Check for valid tax exemption certificates for the customer.
4. Submit the transaction to the configured external tax engine (Avalara AvaTax or Vertex O Series).
5. Receive calculated tax amounts per line item and jurisdiction.
6. Apply tax amounts to the quote and update totals.

## Minimum Checks

- Ship-to address is complete and valid for jurisdiction determination
- Tax exemption certificates are validated and not expired
- External tax engine returns a successful response
- Tax amounts are applied at the line-item level
- Total tax matches the sum of line-item taxes

## Useful Commands

```bash
# Calculate taxes for a quote
curl -X POST "${CPQ_BASE_URL}/api/v1/quotes/{id}/tax-calculation" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"shipToAddress": {"street": "123 Main St", "city": "Austin", "state": "TX", "postalCode": "78701", "country": "US"}}'
```
