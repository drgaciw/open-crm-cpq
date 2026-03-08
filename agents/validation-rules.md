# Agent PRD: cpq-agent-016

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-016                                  |
| **Name**          | Validation Rules Agent                         |
| **Group**         | Product Catalog Management                     |
| **Primary Service** | product-service (8083), pricing-service (8082) |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-002 (Product Configuration Agent), cpq-agent-015 (Compatibility Rules Agent) |

---

## Purpose

The Validation Rules Agent provides comprehensive error checking and data quality validation across the entire CPQ workflow, acting as the final quality gate before configurations proceed to pricing calculation, quote generation, and approval routing. It operates across both product-service and pricing-service to validate configuration completeness, product status eligibility, pricing rule syntax (DRL/Drools Rule Language), quote structural integrity, and inventory availability. Unlike cpq-agent-015 which focuses specifically on product-to-product compatibility constraints, this agent validates the broader data quality and business rule compliance of the entire quote configuration -- ensuring that every field is populated, every referenced entity exists and is in a valid state, every pricing rule can be parsed and executed, and every line item meets minimum data quality thresholds for downstream processing.

The agent operates at an Automated with constraints autonomy level, meaning that all validation checks execute automatically without requiring human intervention, but the results are subject to a strict severity classification: errors are hard blockers that cannot be overridden and must be resolved before the workflow can proceed, while warnings are soft blockers that can be acknowledged and overridden by authorized users with a documented justification. This two-tier approach ensures that genuinely invalid data (e.g., adding a product with END_OF_LIFE status to a new quote, or a DRL rule with syntax errors that would cause pricing engine failure) is always caught and blocked, while edge cases that may be legitimate business exceptions (e.g., a quote with an unusually high discount that triggers a threshold warning) can be escalated and overridden by users with appropriate authority. The agent maintains a validation audit trail recording every check performed, every result returned, and every override granted.

---

## Key Skills / Capabilities

- **Configuration Validation**: Performs end-to-end validation of product configurations by submitting them to `POST /api/v1/configuration/validate` and augmenting the compatibility-focused results from cpq-agent-015 with additional data quality checks. Validates that all required product attributes have values assigned (via `GET /api/v1/product-attributes/products/{productId}/values`), that attribute values conform to their defined data types and constraints, that option selections respect their group's selection type (SINGLE, MULTI, OPTIONAL), and that quantity values are within acceptable ranges.

- **Product Status Verification**: Automatically checks the lifecycle status of every product referenced in a configuration or quote by retrieving product records via `GET /api/v1/products/{id}`. Blocks the addition of products with status DEPRECATED or END_OF_LIFE to new quotes (hard error). Generates warnings for products in DRAFT status (not yet generally available) and for ACTIVE products that have been flagged for upcoming deprecation. Cross-references bundle membership via `GET /api/v1/product-bundles/product/{productId}` to ensure bundle components are all in valid statuses.

- **DRL Syntax Validation**: Validates the syntax and logical integrity of Drools Rule Language (DRL) pricing rules before they are activated in the pricing engine. Submits rule definitions to `POST /api/v1/rules/{id}/validate` to catch syntax errors, undefined variable references, type mismatches, infinite loop risks, and rule conflict conditions. Prevents activation of malformed pricing rules that would cause runtime failures or incorrect price calculations. Returns detailed error messages with line numbers and suggested corrections.

- **Quote Completeness Check**: Validates that quotes contain all structurally required elements before they can proceed to pricing or approval. Retrieves quote data via `GET /api/v1/quotes` and line items via `GET /api/v1/quotes/{quoteId}/line-items` to verify: at least one line item exists, every line item has a valid product reference, quantities are positive integers, required configuration options are selected, customer and deal references are valid (via `GET /api/v1/pricing-customers/{id}` and `GET /api/v1/deals/{id}`), and currency and effective dates are specified.

- **Availability Validation**: Checks real-time inventory and supply status for hardware and physical products by querying `GET /api/v1/erp/inventory/{productExternalId}`. Validates that requested quantities do not exceed available inventory (hard error for zero-stock items, warning for low-stock items). For SAAS and subscription products, validates that the customer's requested seat count or capacity does not exceed platform-level maximums defined in product attributes. Reports estimated fulfillment timelines when inventory is constrained.

- **Cross-Service Data Integrity Check**: Validates referential integrity across CPQ microservices by confirming that customer IDs referenced in quotes exist in CRM (via `GET /api/v1/contacts`), that deal IDs are valid and in appropriate pipeline stages (via `GET /api/v1/deals/{id}`), that product external IDs map to valid ERP records (via `GET /api/v1/erp/costs/{productExternalId}`), and that pricing customer records exist and are current (via `GET /api/v1/pricing-customers/{id}`). Catches dangling references that would cause downstream processing failures.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Configuration Payload    | JSON object       | Quote builder UI or cpq-agent-002 output (selected products, options, quantities) |
| Quote ID                 | String (UUID)     | Quote record for completeness validation         |
| Product IDs              | Array of String (UUID) | Products to validate status for                |
| DRL Rule Definition      | String (DRL text) | Pricing rule editor / cpq-agent-003 output       |
| DRL Rule ID              | String (UUID)     | Existing rule to validate via POST /api/v1/rules/{id}/validate |
| Customer External ID     | String            | Customer reference for cross-service validation  |
| Deal ID                  | String (UUID)     | Deal reference for pipeline stage validation     |
| Validation Scope         | String (Enum)     | CONFIGURATION_ONLY, QUOTE_COMPLETE, FULL_PREFLIGHT |
| Override Request         | JSON object       | User ID, warning ID, justification text (for warning overrides) |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Validation Result Summary     | JSON object (overallStatus: PASS/WARN/FAIL, errorCount, warningCount, checkResults: array) | Automated execution; errors block progression, no override possible |
| Error Details                 | JSON array of objects (checkId, severity: ERROR, category, message, affectedEntity, remediationSteps) | Errors must be resolved by the user; agent provides specific remediation guidance |
| Warning Details               | JSON array of objects (checkId, severity: WARNING, category, message, affectedEntity, overrideEligible: boolean) | Warnings displayed to user; authorized users can override with documented justification |
| DRL Validation Report         | JSON object (syntaxValid: boolean, errors: array of {line, column, message, suggestion}) | Automated check; rules with syntax errors cannot be activated |
| Availability Report           | JSON array of objects (productId, requestedQty, availableQty, status, estimatedFulfillmentDate) | Automated check; zero-stock is hard error, low-stock is warning |
| Validation Audit Trail        | JSON array of objects (timestamp, checkId, result, overrideGranted, overrideBy, justification) | Audit log maintained automatically; available for compliance review |

---

## Typical Workflow

1. **Receive Validation Trigger**: Validation is triggered automatically at key workflow transitions: when a product is added to a configuration, when a configuration is submitted for pricing, when a quote is submitted for approval, or when a pricing rule is saved. The agent receives the validation scope (CONFIGURATION_ONLY, QUOTE_COMPLETE, or FULL_PREFLIGHT) and the relevant payload.

2. **Execute Product Status Checks**: Agent retrieves the status of every product in the configuration via `GET /api/v1/products/{id}`. Immediately flags DEPRECATED and END_OF_LIFE products as hard errors. Flags DRAFT products as warnings. Checks bundle component statuses via `GET /api/v1/product-bundles/product/{productId}` to ensure no bundle contains an invalid component.

3. **Validate Configuration Data Quality**: Agent retrieves attribute definitions via `GET /api/v1/product-attributes` and current values via `GET /api/v1/product-attributes/products/{productId}/values` for each configured product. Validates that required attributes have values, values match expected data types (STRING, INTEGER, DECIMAL, BOOLEAN, ENUM, DATE), numeric values fall within defined min/max ranges, and ENUM values are in the allowed set.

4. **Run Compatibility Validation**: Agent delegates product-to-product compatibility checking to the rule engine via `POST /api/v1/configuration/validate`, incorporating the results from cpq-agent-015's rule set. Merges compatibility violations into the overall validation result, classifying REQUIRES and EXCLUDES violations as errors and RECOMMENDS violations as warnings.

5. **Check Quote Completeness** (for QUOTE_COMPLETE and FULL_PREFLIGHT scopes): Agent retrieves the full quote structure via `GET /api/v1/quotes/{quoteId}/line-items` and validates structural requirements: line item count, quantity values, customer reference validity (via `GET /api/v1/pricing-customers/{id}`), deal reference validity (via `GET /api/v1/deals/{id}`), and required date fields.

6. **Validate Availability** (for FULL_PREFLIGHT scope): Agent checks inventory for hardware and physical products via `GET /api/v1/erp/inventory/{productExternalId}`. Validates seat/capacity limits for SAAS products against product attribute constraints. Generates availability status for each line item.

7. **Compile and Classify Results**: Agent aggregates all validation results into a unified report, classifying each finding as ERROR (hard block, no override) or WARNING (soft block, override eligible). Calculates the overall validation status (PASS if zero errors and zero warnings, WARN if zero errors but one or more warnings, FAIL if one or more errors).

8. **Return Results and Record Audit Trail**: Agent returns the validation result to the triggering workflow. If the result is PASS, the workflow proceeds automatically. If WARN, the user can review warnings and submit override requests with justification. If FAIL, the workflow is blocked until errors are resolved. All results and any override actions are recorded in the validation audit trail.

---

## Edge Cases & Exceptions

- **Cascading Validation Failures**: When a single root cause (e.g., a deprecated product) triggers multiple downstream validation failures (compatibility rule violations, bundle integrity errors, pricing rule inapplicability), the agent performs root cause analysis and groups related errors under the primary finding. Prevents overwhelming the user with dozens of errors when fixing one root cause would resolve them all.

- **Cross-Service Timeout During Validation**: When one of the downstream services (CRM, ERP, pricing-service) is unavailable or times out during validation, the agent completes all checks that can be performed with available services and marks the unreachable checks as INCONCLUSIVE rather than failing them. Displays a warning that partial validation was performed and recommends re-running full validation when all services are available.

- **Warning Override Abuse Detection**: When the same warning is repeatedly overridden across multiple quotes by the same user without being addressed (e.g., consistently overriding low-stock warnings), the agent escalates the pattern to the administrator. After a configurable threshold (default: 5 overrides of the same warning type), the warning is promoted to an error for that user until the underlying issue is resolved.

- **DRL Rule with Valid Syntax but Logical Error**: When `POST /api/v1/rules/{id}/validate` passes syntax validation but the agent's static analysis detects potential logical issues (e.g., a condition that can never be true, overlapping rule conditions that create ambiguous pricing), the agent classifies these as warnings rather than errors since the rule is technically executable. Provides detailed explanations of the potential logical issues.

- **Validation During Bulk Quote Operations**: When validation is triggered for bulk operations (e.g., mass renewal of 100+ quotes), the agent runs validations in parallel batches to avoid overwhelming downstream services. Produces a consolidated batch validation report rather than individual results, highlighting common failure patterns across the batch with aggregated counts.

- **Product Status Change During Active Quote Session**: When a product's status changes from ACTIVE to DEPRECATED while a sales rep has an open quote session containing that product, the agent detects the status change on the next validation trigger and presents a warning (not an immediate error) since the product was valid when initially selected. Provides a grace period recommendation and suggests replacement products.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Invalid configuration escape rate (errors reaching pricing/approval) | < 0.5% |
| Validation execution latency (p95, CONFIGURATION_ONLY scope) | < 300ms |
| DRL syntax error detection rate (malformed rules caught before activation) | 100% |
| False positive rate (valid configurations incorrectly flagged as errors) | < 1% |
| Warning override audit trail completeness (all overrides documented) | 100% |
