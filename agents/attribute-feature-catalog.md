# Agent PRD: cpq-agent-014

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-014                                  |
| **Name**          | Attribute & Feature Catalog Agent              |
| **Group**         | Product Catalog Management                     |
| **Primary Service** | product-service (8083)                        |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-013 (Product SKU Management Agent)   |

---

## Purpose

The Attribute & Feature Catalog Agent manages the complete lifecycle of configurable product attributes, option groups, and option-level price adjustments within the CPQ product catalog. It governs attribute definitions across all supported data types (STRING, INTEGER, DECIMAL, BOOLEAN, ENUM, DATE), ensures consistent attribute naming and validation rules, and manages how attributes are assigned to products with appropriate default values and constraints. The agent also creates and maintains option groups with their associated selection types (SINGLE, MULTI, OPTIONAL) and configures price adjustment rules (FIXED, PERCENTAGE, OVERRIDE) that apply when specific options are selected. By maintaining a well-structured attribute and option catalog, the agent ensures that downstream configuration agents (cpq-agent-015, cpq-agent-016) operate against a complete and internally consistent product metadata foundation.

The agent performs continuous catalog completeness analysis, identifying products that lack required attributes for their product type, option groups with missing or misconfigured price adjustments, and attributes with inconsistent validation rules across product families. It cross-references attribute definitions against ERP system attributes to ensure that configurable features in CPQ map correctly to fulfillment-relevant parameters in the ERP system. Operating at an Advisory autonomy level, the agent prepares all attribute definitions, option group configurations, and price adjustment payloads but requires explicit human approval before creating or modifying any catalog metadata. This prevents accidental introduction of invalid attribute schemas that could break downstream configuration validation or pricing calculations, and ensures that all catalog changes are reviewed by a subject-matter expert before becoming available to sales teams.

---

## Key Skills / Capabilities

- **Attribute Definition Management**: Creates and maintains product attribute definitions using `POST /api/v1/product-attributes` with full schema specification including attribute name, data type (STRING, INTEGER, DECIMAL, BOOLEAN, ENUM, DATE), required/optional flag, default values, and validation constraints (min/max for numeric types, regex patterns for strings, allowed values for enums). Retrieves existing attribute definitions via `GET /api/v1/product-attributes` to ensure naming consistency and prevent redundant attribute creation across product families.

- **Option Group Creation & Configuration**: Assembles option group structures via `POST /api/v1/product-options/groups` specifying the group name, description, selection type (SINGLE for radio-button-style choices, MULTI for checkbox-style multi-select, OPTIONAL for entirely skippable groups), and display ordering. Populates groups with individual options using `POST /api/v1/product-options/groups/{groupId}/options`, defining each option's name, description, default selection state, and any associated SKU or external identifier for ERP mapping.

- **Price Adjustment Configuration**: Configures option-level price adjustments that apply when a customer selects specific options during product configuration. Supports all three adjustment types: FIXED (add/subtract a fixed monetary amount), PERCENTAGE (increase/decrease the base price by a percentage), and OVERRIDE (replace the base price entirely with a specified amount). Validates that adjustment values are within acceptable business ranges and that OVERRIDE adjustments do not result in negative pricing.

- **Completeness Analysis**: Performs systematic catalog audits by retrieving all products via `GET /api/v1/products` and their attribute assignments via `GET /api/v1/product-attributes/products/{productId}/values` to identify gaps. Detects products missing required attributes for their product type (e.g., a SAAS_SEAT_NAMED product without a "max_users" attribute), option groups without any options, options without price adjustments where adjustments are expected, and attributes with inconsistent validation rules across products of the same category.

- **ERP Attribute Synchronization**: Validates that CPQ product attributes map correctly to ERP system parameters by comparing attribute definitions and assigned values against ERP product data retrieved from `GET /api/v1/erp/costs/{productExternalId}`. Identifies attributes that exist in CPQ but have no ERP counterpart (potential fulfillment gap), and ERP fields that lack a corresponding CPQ attribute (potential configuration gap). Generates mapping reconciliation reports with recommended corrective actions.

- **Attribute Value Assignment**: Manages the assignment of attribute values to specific products using `POST /api/v1/product-attributes/products/{productId}/values`, ensuring that assigned values conform to the attribute's data type and validation constraints. Retrieves current attribute value assignments via `GET /api/v1/product-attributes/products/{productId}/values` to support update operations and prevent conflicting or overwriting value assignments.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Product ID               | String (UUID)     | Product record from cpq-agent-013 or catalog UI  |
| Attribute Name           | String            | Catalog administrator input                      |
| Attribute Data Type      | String (Enum)     | Selection from AttributeDataType enum (STRING, INTEGER, DECIMAL, BOOLEAN, ENUM, DATE) |
| Attribute Constraints    | JSON object       | Administrator input (min, max, pattern, allowedValues, required flag) |
| Option Group Name        | String            | Administrator input                              |
| Selection Type           | String (Enum)     | Selection from SelectionType enum (SINGLE, MULTI, OPTIONAL) |
| Option Details           | JSON array        | Administrator input (name, description, default, externalId per option) |
| Price Adjustment Type    | String (Enum)     | Selection from AdjustmentType enum (FIXED, PERCENTAGE, OVERRIDE) |
| Price Adjustment Value   | Decimal           | Administrator input                              |
| Product Type Context     | String (Enum)     | From ProductType enum, used for completeness validation |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Attribute Definition Payload  | JSON object (complete POST /api/v1/product-attributes request body) | Administrator must review and approve attribute definition before creation |
| Option Group Configuration    | JSON object (group definition with nested options and selection type) | Administrator must approve option group structure before submission via POST /api/v1/product-options/groups |
| Price Adjustment Specification | JSON object (optionId, adjustmentType, adjustmentValue, effectivePrice preview) | Administrator must review price impact and approve before adjustment is applied |
| Completeness Analysis Report  | JSON array of objects (productId, missingAttributes, incompleteOptionGroups, inconsistentValidations) | Informational report; administrator decides which gaps to address and in what priority |
| ERP Attribute Mapping Report  | JSON array of objects (cpqAttribute, erpField, mappingStatus, recommendation) | Administrator reviews mapping gaps and approves recommended corrections |
| Attribute Value Assignment Payload | JSON object (complete POST /api/v1/product-attributes/products/{productId}/values request body) | Administrator must confirm attribute value assignments before they are written to the product record |

---

## Typical Workflow

1. **Receive Attribute Management Request**: Catalog administrator initiates an attribute creation, option group setup, or completeness audit from the catalog management UI. The agent receives the target product ID (if product-specific) and the requested operation type. For new products recently created by cpq-agent-013, the agent may be triggered automatically to populate standard attributes for the product type.

2. **Analyze Existing Attribute Schema**: Agent retrieves the current attribute catalog via `GET /api/v1/product-attributes` and the target product's existing attribute assignments via `GET /api/v1/product-attributes/products/{productId}/values`. Identifies which attributes are already defined, which are missing for the product's type, and which have values that need updating.

3. **Prepare Attribute Definitions**: For new attributes, the agent constructs the complete attribute definition payload for `POST /api/v1/product-attributes`, including data type, validation constraints, and default values. Validates that the attribute name follows naming conventions and does not conflict with existing attributes. For ENUM-type attributes, validates that the allowed values list is complete and consistent.

4. **Configure Option Groups and Options**: When configurable options are needed, the agent assembles the option group payload for `POST /api/v1/product-options/groups` with the appropriate selection type. Populates individual options via `POST /api/v1/product-options/groups/{groupId}/options` and configures price adjustments (FIXED, PERCENTAGE, or OVERRIDE) for each option that affects pricing. Validates that adjustment values produce reasonable effective prices.

5. **Run Completeness Validation**: Agent performs a cross-product completeness check, comparing each product's attribute assignments against the required attribute template for its ProductType and ProductCategory. Identifies products with missing required attributes, option groups with no options defined, and options lacking expected price adjustments.

6. **Cross-Reference ERP Attributes**: Agent retrieves ERP product data via `GET /api/v1/erp/costs/{productExternalId}` and compares available ERP fields against CPQ attribute definitions. Generates a mapping report highlighting unmapped fields in both directions and recommending corrective actions.

7. **Present for Approval**: Agent displays all proposed changes -- attribute definitions, option group configurations, price adjustments, completeness findings, and ERP mapping gaps -- to the administrator with clear explanations and impact assessments. Each change requires individual approval.

8. **Execute Approved Changes**: Upon receiving explicit approval for each change, the agent submits the API calls in the correct dependency order (attributes before values, groups before options, options before price adjustments). Verifies each creation was successful by retrieving the created records and confirming field accuracy.

---

## Edge Cases & Exceptions

- **Attribute Name Collision**: When a proposed attribute name matches an existing attribute with a different data type or different validation constraints, the agent blocks creation and presents the conflict details. Suggests either reusing the existing attribute (if semantically identical), renaming the new attribute to distinguish it, or updating the existing attribute's constraints if they are outdated. Never silently overwrites or creates a confusingly similar attribute.

- **ENUM Attribute Value List Evolution**: When an existing ENUM attribute needs additional allowed values added (e.g., a new region code), the agent validates that adding the new value does not invalidate existing product configurations. Queries all products with that attribute assigned via `GET /api/v1/product-attributes/products/{productId}/values` to assess backward compatibility before recommending the schema update.

- **Price Adjustment Producing Negative Price**: When a FIXED adjustment with a negative value or a PERCENTAGE adjustment exceeding -100% would result in a negative effective price, the agent blocks the configuration and presents a warning with the calculated effective price. Suggests alternative adjustment values that maintain a positive price floor.

- **Orphaned Option Groups**: When an option group exists but is not referenced by any product configuration (detected during completeness analysis), the agent flags it as orphaned and recommends either assigning it to relevant products or archiving it. Does not auto-delete orphaned groups.

- **Bulk Attribute Assignment Across Product Family**: When the administrator needs to assign the same attribute with the same value to many products in a category, the agent validates each target product individually via `GET /api/v1/products/{id}` to confirm the product's type and status support the attribute, then presents a batch assignment plan for approval rather than requiring individual approval for each product.

- **Data Type Migration Request**: When an administrator requests changing an existing attribute's data type (e.g., STRING to ENUM), the agent analyzes all current values assigned to that attribute across all products to determine if the migration is lossless. If any existing values would be invalid under the new data type, the agent presents a migration impact report listing affected products and values that require manual remediation before the type change can proceed.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Product attribute completeness rate (required attributes populated) | >= 98% |
| Option group configuration accuracy (valid selection types and price adjustments) | >= 99% |
| ERP-CPQ attribute mapping coverage   | >= 95%         |
| Catalog completeness audit finding resolution rate within 30 days | >= 85% |
| Average attribute/option configuration workflow completion time | < 10 minutes |
