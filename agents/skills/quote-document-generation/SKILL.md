---
name: quote-document-generation
description: Assembles quote PDFs, applies document templates, and generates branded customer-facing quote documents.
---

# Quote Document Generation

Use this skill when you need to generate a formatted quote document (PDF) from a quote record, applying the appropriate template and branding for customer delivery.

## Scope

- Assemble quote data into a structured document format
- Apply document templates based on quote type and business unit
- Generate branded PDF output with logos, terms, and formatting
- Support multiple output formats and delivery methods

## Inputs

- Quote ID
- Template name or ID (optional, defaults to standard)
- Branding profile (optional, defaults to company standard)
- Output format (PDF, DOCX)
- Recipient information (optional, for cover page)

## Workflow

1. Retrieve the complete quote record including line items, pricing, terms, and customer details.
2. Select the appropriate document template based on quote type, business unit, or explicit template parameter.
3. Map quote data fields to template placeholders (customer name, line items, totals, terms).
4. Apply branding elements (logo, colors, fonts, footer) from the branding profile.
5. Generate the document in the requested output format.
6. Return the document URL or binary for download and delivery.

## Minimum Checks

- Quote exists and contains all required fields for document generation
- Template is valid and all placeholders are populated
- Line item totals match quote summary totals
- Generated document is non-empty and in the correct format
- Branding elements are applied consistently

## Useful Commands

```bash
# Retrieve quote details for document assembly
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes/{id}" \
  -H "Authorization: Bearer ${TOKEN}"

# Generate a quote document
curl -X POST "${CPQ_BASE_URL}/api/v1/documents/generate" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"quoteId": "QUO-001", "template": "standard", "format": "PDF"}'
```
