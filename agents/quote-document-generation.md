# Agent PRD: cpq-agent-031

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-031                                  |
| **Name**          | Quote Document Generation Agent                |
| **Group**         | Integration & Operations                       |
| **Primary Service** | quote-service (8084), document-generation service |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-024 (Multi-Level Approval Routing Agent) |

---

## Purpose

The Quote Document Generation Agent produces professional, branded quote proposal documents in PDF format from approved quotes. It assembles comprehensive quote data -- including product configuration, pricing waterfall, discount summary, terms and conditions, and approval status -- into customizable document templates that match the organization's branding and formatting standards. The agent supports multiple template types (standard proposal, executive summary, technical specification, renewal offer) and handles multi-currency formatting, localized content, and digital signature integration.

The agent operates at a Semi-autonomous level: standard document generation from approved quotes using default templates is fully automatic, but custom template selection, non-standard content modifications, and document regeneration after pricing changes require sales representative confirmation. The agent integrates with the document-generation microservice (using OpenHTML + Thymeleaf for template rendering) and stores generated documents linked to their source quotes for audit trail compliance.

---

## Key Skills / Capabilities

- **Quote Data Assembly**: Retrieves the complete quote including all line items, pricing details, discount waterfall, approval history, and customer information via `GET /api/v1/quotes/{quoteId}` and related endpoints. Structures the data for template rendering.

- **Template Selection and Rendering**: Selects the appropriate document template based on quote type, customer segment, and deal characteristics. Renders the template using Thymeleaf with the assembled quote data. Supports standard proposal, executive summary, and renewal offer templates.

- **Multi-Currency Formatting**: Formats all monetary values according to the quote's currency, including proper symbol placement, decimal formatting, and thousand separators. Handles dual-currency display (local + base currency) when configured.

- **Digital Signature Integration**: Prepares documents for digital signature workflows by inserting signature blocks, initials fields, and date fields at configured positions. Supports integration with DocuSign and Adobe Sign APIs.

- **Document Versioning**: Maintains document versions linked to quote versions. When a quote is modified after document generation, tracks the delta and enables regeneration with change highlighting.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Approved quote reference                         |
| Template Type                | String (Enum)     | STANDARD_PROPOSAL, EXECUTIVE_SUMMARY, RENEWAL_OFFER |
| Customer Branding Overrides  | JSON object       | Optional customer-specific branding              |
| Include Sections             | List of String    | Optional section selection (pricing, terms, technical) |
| Output Format                | String (Enum)     | PDF (default), DOCX                              |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Generated Document            | PDF/DOCX file           | Rep reviews before sending to customer            |
| Document Metadata             | JSON object (documentId, version, generatedAt) | Logged for audit trail |
| Signature-Ready Document      | PDF with signature fields | Rep confirms before initiating signature workflow |

---

## Typical Workflow

1. **Receive Generation Request**: Triggered automatically when a quote is approved or manually by the sales representative.

2. **Assemble Quote Data**: Retrieves all quote data, customer information, and pricing details.

3. **Select Template**: Determines the appropriate template based on quote type and customer segment.

4. **Render Document**: Generates the document using the Thymeleaf template engine via `POST /api/v1/documents/generate`.

5. **Apply Branding**: Applies organizational branding and any customer-specific overrides.

6. **Store and Return**: Stores the generated document linked to the quote and returns the document reference.

---

## Edge Cases & Exceptions

- **Quote Modified After Generation**: When the source quote is modified, the agent marks the existing document as stale and offers to regenerate.

- **Template Not Found**: When the requested template type is unavailable for the customer's locale, falls back to the default template.

- **Large Quote (>100 line items)**: For very large quotes, generates paginated pricing tables with subtotals per section.

- **Multi-Language Quote**: When the customer requires a document in a non-default language, uses the localized template if available.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Document generation success rate     | >= 99%          |
| Document generation latency (p95)    | < 10 seconds    |
| Template rendering accuracy          | 100%            |
| Pricing accuracy in documents        | 100%            |
