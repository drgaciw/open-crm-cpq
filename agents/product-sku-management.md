# Agent PRD: cpq-agent-013

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-013                                  |
| **Name**          | Product SKU Management Agent                   |
| **Group**         | Product Catalog Management                     |
| **Primary Service** | product-service (8083)                        |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | None                                           |

---

## Purpose

The Product SKU Management Agent is responsible for assisting catalog administrators and product managers in creating, maintaining, and governing the lifecycle of product SKUs and master data within the CPQ platform. It enforces standardized SKU naming conventions (prefix-category-sequence format), detects and prevents duplicate SKU entries before they pollute the catalog, and ensures that every product record contains the minimum required metadata fields before transitioning out of DRAFT status. The agent monitors the full product lifecycle state machine (DRAFT -> ACTIVE -> DEPRECATED -> END_OF_LIFE) and proactively recommends lifecycle transitions based on usage analytics -- for example, recommending deprecation for products that have appeared on zero quotes in the trailing twelve months or flagging ACTIVE products whose associated ERP cost records show discontinued supplier status.

Because the agent operates at an Advisory autonomy level, every product creation, update, activation, deprecation, and end-of-life transition requires explicit human approval before execution. The agent prepares the complete API payload, validates it against business rules, and presents the proposed change with a detailed impact summary (e.g., number of active quotes referencing the product, open deals in pipeline, existing bundle memberships) so the approver can make an informed decision. This design prevents accidental catalog corruption, ensures auditability of all catalog changes, and maintains synchronization between the CPQ product catalog and the upstream ERP system of record. The agent also supports bulk operations by validating batch import files against the SKU schema and flagging rows that violate naming conventions, contain duplicates, or reference invalid product types or categories.

---

## Key Skills / Capabilities

- **SKU Generation & Validation**: Generates compliant SKU codes following the organization's naming convention (e.g., `{ProductType}-{Category}-{Sequence}`) by analyzing existing SKU patterns in the catalog retrieved via `GET /api/v1/products`. Validates proposed SKUs against naming rules, checks for uniqueness across the entire catalog using `GET /api/v1/products/search?sku={proposedSku}`, and suggests corrections for non-compliant entries.

- **Duplicate Detection**: Performs multi-field similarity analysis before any product creation to identify potential duplicates. Searches by SKU, name, and attribute combination using `GET /api/v1/products/search` with various query parameters. Compares product attributes via `GET /api/v1/product-attributes/products/{productId}/values` across candidate matches to distinguish true duplicates from legitimately similar products. Presents a similarity score and side-by-side comparison when potential duplicates are found.

- **Product Creation Assistance**: Guides users through creating well-formed product records by assembling the complete payload for `POST /api/v1/products` including all required fields (name, SKU, productType from the ProductType enum, category from ProductCategory enum, description, and initial status of DRAFT). Validates that the selected productType and category combination is valid and that all mandatory attributes for that product type are populated before recommending submission.

- **Lifecycle Transition Recommendation**: Monitors product usage patterns by cross-referencing quote line items via `GET /api/v1/quotes` and deal pipeline data via `GET /api/v1/deals/pipeline` to identify lifecycle transition candidates. Recommends activation via `POST /api/v1/products/{id}/activate` for fully configured DRAFT products, deprecation via `POST /api/v1/products/{id}/deprecate` for zero-activity products, and flags products approaching end-of-life based on configurable business rules. Each recommendation includes a full impact analysis.

- **ERP Master Data Synchronization**: Validates that product master data remains consistent between the CPQ catalog and the ERP system by comparing product records retrieved via `GET /api/v1/products/{id}` against ERP cost and inventory data obtained from `GET /api/v1/erp/costs/{productExternalId}` and `GET /api/v1/erp/inventory/{productExternalId}`. Detects discrepancies in product names, categories, active/inactive status, and cost figures. Generates reconciliation reports and recommends corrective updates.

- **Bulk Import Validation**: Processes batch product import files (CSV/JSON) by validating each row against the product schema, SKU naming conventions, ProductType and ProductCategory enums, and duplicate detection rules. Produces a validation report with row-level pass/fail status, error descriptions, and suggested corrections before any records are submitted to `POST /api/v1/products`.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Product Name             | String            | Catalog administrator input                      |
| Proposed SKU             | String            | Auto-generated or administrator input            |
| Product Type             | String (Enum)     | Selection from ProductType enum (SAAS_SEAT_NAMED, SAAS_SEAT_CONCURRENT, SAAS_FLAT_RATE, SAAS_USAGE_METERED, ONPREM_PERPETUAL, ONPREM_CORE, ONPREM_CAPACITY, SUPPORT_BASIC, SUPPORT_PREMIUM, SUPPORT_ENTERPRISE, PROFESSIONAL_SERVICES, TRAINING, HARDWARE) |
| Product Category         | String (Enum)     | Selection from ProductCategory enum (PLATFORM, MODULE, ADD_ON, SERVICE, SUPPORT, BUNDLE) |
| Product Description      | String            | Administrator input                              |
| Product External ID      | String            | ERP system reference identifier                  |
| Bulk Import File         | File (CSV/JSON)   | Administrator file upload                        |
| Lifecycle Transition Target | String (Enum)  | Target status: ACTIVE, DEPRECATED, END_OF_LIFE  |
| Activity Lookback Period | Integer (months)  | Configuration parameter, default 12 months       |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Generated SKU Suggestion      | JSON object (proposedSku, namingConventionBreakdown, uniquenessCheck) | Administrator must review and approve SKU before product creation proceeds |
| Duplicate Detection Report    | JSON array of objects (candidateProductId, similarityScore, matchedFields, sideBySideComparison) | Administrator must confirm the product is not a duplicate before creation is allowed |
| Product Creation Payload      | JSON object (complete POST /api/v1/products request body) | Administrator must review and explicitly approve the product creation; agent does not auto-create |
| Lifecycle Transition Recommendation | JSON object (productId, currentStatus, recommendedStatus, impactAnalysis, affectedQuotes, affectedBundles) | All lifecycle transitions require explicit administrator approval before execution |
| ERP Sync Discrepancy Report   | JSON array of objects (productId, field, cpqValue, erpValue, recommendedAction) | Administrator reviews discrepancies and approves individual correction actions |
| Bulk Import Validation Report | JSON array of objects (rowNumber, status, errors, suggestions) | Administrator reviews validation results and approves batch submission |

---

## Typical Workflow

1. **Receive Product Management Request**: Catalog administrator initiates a product creation, update, or lifecycle review from the catalog management UI. The agent receives the request type and any provided product details including name, type, category, and description.

2. **Generate and Validate SKU**: For new product creation, the agent analyzes existing SKU patterns by querying `GET /api/v1/products` filtered by the target product type and category. Generates a compliant SKU following the naming convention and validates uniqueness via `GET /api/v1/products/search?sku={proposedSku}`.

3. **Execute Duplicate Detection**: Agent performs a multi-dimensional similarity search using `GET /api/v1/products/search` with name, category, and type parameters. For candidate matches, retrieves full attribute sets via `GET /api/v1/product-attributes/products/{productId}/values` to compute similarity scores. Presents results to the administrator with a clear duplicate/no-duplicate recommendation.

4. **Assemble Product Record**: Agent constructs the complete product creation payload for `POST /api/v1/products`, validating that all required fields are populated, the productType and category values are valid enum members, and any referenced external IDs exist in the ERP system via `GET /api/v1/erp/costs/{productExternalId}`.

5. **Present for Approval**: Agent displays the assembled product record, SKU validation results, duplicate detection report, and ERP cross-reference status to the administrator. Highlights any warnings or issues that require attention before approval.

6. **Execute Approved Action**: Upon receiving explicit administrator approval, the agent submits the product creation via `POST /api/v1/products` or lifecycle transition via `POST /api/v1/products/{id}/activate` or `POST /api/v1/products/{id}/deprecate`. Records the approval metadata (approver, timestamp, rationale) for audit trail purposes.

7. **Validate Post-Creation State**: Agent verifies the product was created/updated successfully by retrieving the record via `GET /api/v1/products/{id}` and confirming all fields match the approved payload. Checks ERP synchronization status and flags any post-creation discrepancies.

8. **Schedule Lifecycle Monitoring**: Agent registers the product for ongoing lifecycle monitoring. Periodically queries quote activity via `GET /api/v1/quotes` and deal pipeline via `GET /api/v1/deals/pipeline` to detect usage patterns that warrant lifecycle transition recommendations.

---

## Edge Cases & Exceptions

- **SKU Naming Convention Conflict**: When the auto-generated SKU following the standard convention conflicts with an existing SKU, the agent increments the sequence number and re-validates. If multiple collisions occur (e.g., during bulk import), the agent switches to a timestamp-suffixed naming variant and alerts the administrator that the standard sequence pool may need re-indexing.

- **ERP System Unavailable**: When calls to `GET /api/v1/erp/costs/{productExternalId}` or `GET /api/v1/erp/inventory/{productExternalId}` time out or return errors, the agent proceeds with product creation/validation using CPQ-only data and flags the record as "ERP Sync Pending." Queues a reconciliation check for when the ERP system becomes available.

- **Deprecation of Bundled Product**: When a deprecation recommendation targets a product that is a component of one or more active bundles (detected via `GET /api/v1/product-bundles/product/{productId}`), the agent escalates the recommendation with a detailed bundle impact analysis, listing all affected bundles and their current quote activity. Suggests either deprecating the entire bundle or replacing the component before proceeding.

- **Bulk Import with Mixed Valid/Invalid Rows**: When a batch import file contains a mixture of valid and invalid product records, the agent generates a line-by-line validation report and offers the administrator the choice to submit only valid rows (skipping invalid ones) or reject the entire batch for correction. Never partially submits without explicit approval of the partial strategy.

- **Concurrent Modification Conflict**: When two administrators attempt to modify the same product simultaneously, the agent detects the version conflict on the `PUT /api/v1/products/{id}` call (HTTP 409 Conflict) and presents both versions side-by-side, allowing the current administrator to merge changes or override with their version.

- **Product with Active Quotes Targeted for Lifecycle Change**: When an administrator requests deprecation or end-of-life for a product that appears on open/active quotes (detected via `GET /api/v1/quotes`), the agent blocks the transition and presents a list of affected quotes with deal owners and expected close dates, requiring the administrator to either wait for quote resolution or obtain override approval.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| SKU naming convention compliance rate | >= 99%         |
| Duplicate product creation prevention rate | >= 98%    |
| Product lifecycle transition recommendation accuracy | >= 90% |
| ERP-CPQ master data synchronization discrepancy rate | < 2% |
| Average product creation workflow completion time | < 5 minutes |
