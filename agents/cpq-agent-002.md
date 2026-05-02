# Agent PRD: cpq-agent-002

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-002                                  |
| **Name**          | Option & Feature Configuration Agent           |
| **Group**         | Product Configuration                          |
| **Primary Service** | product-service (8083)                       |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-001 (Guided Product Selection Agent) |

---

## Purpose

The Option & Feature Configuration Agent manages the full lifecycle of configurable product options once a product has been selected by cpq-agent-001. Enterprise products typically expose multiple axes of configurability -- editions (Standard, Professional, Enterprise), functional modules (Reporting, Analytics, Integrations), add-on capabilities (Premium Support, Advanced Security, API Access), SLA tiers (Bronze, Silver, Gold, Platinum), and attribute-level parameters (user count, storage capacity, retention period). This agent retrieves the complete option group structure and attribute schema for each selected product, presents valid choices to the sales representative, and enforces the configuration rule engine in real time as selections are made.

The agent operates at a Semi-autonomous level, meaning it can automatically apply non-commercial defaults (DEFAULT rules) and enforce hard constraints (REQUIRES, EXCLUDES, VALIDATES rules) without human approval, but must surface commercial recommendations (RECOMMENDS rules) as suggestions requiring rep confirmation. The configuration rule engine supports five rule types defined in the ConfigRuleType enum: REQUIRES (mandatory co-selection), EXCLUDES (mutual exclusivity), RECOMMENDS (soft suggestion with rationale), DEFAULT (auto-applied initial values), and VALIDATES (custom validation expressions). The agent evaluates these rules incrementally as each option is selected, calling `POST /configuration/validate` to ensure the running configuration remains valid. Attribute values are type-checked against the AttributeDataType enum (STRING, INTEGER, DECIMAL, BOOLEAN, ENUM, DATE) and selection constraints are enforced per the SelectionType enum (SINGLE, MULTI, OPTIONAL). Price adjustments from option selections (AdjustmentType: FIXED, PERCENTAGE, OVERRIDE) are calculated and displayed in real time to give reps immediate pricing visibility.

---

## Key Skills / Capabilities

- **Configuration Rule Evaluation**: Retrieves the complete rule set for a product via `GET /api/v1/configuration/rules/product/{productId}` and evaluates REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, and VALIDATES rules incrementally as the rep makes selections. Maintains a dependency graph to detect circular rule conflicts and cascading requirements.

- **Option Group Management**: Fetches the hierarchical option group structure via `GET /api/v1/product-options/groups/product/{productId}` and renders it as a navigable configuration tree. Enforces SelectionType constraints -- SINGLE (radio-button style, exactly one), MULTI (checkbox style, zero or more), OPTIONAL (zero or one) -- and validates minimum/maximum selection counts per group.

- **Attribute Value Management**: Retrieves product attribute definitions and permissible values via `GET /api/v1/product-attributes/products/{productId}/values`. Validates user-entered values against the AttributeDataType (STRING with regex patterns, INTEGER with min/max ranges, DECIMAL with precision constraints, BOOLEAN, ENUM with allowed value lists, DATE with range bounds). Displays contextual help text and unit labels.

- **Default Auto-Application**: On initial product configuration load, automatically applies all DEFAULT rules without requiring rep interaction. This includes setting default edition selections, pre-selecting mandatory modules, initializing attribute values to their defaults, and applying standard SLA tiers. The rep sees the pre-configured state and can override any default.

- **RECOMMENDS Suggestion Engine**: When RECOMMENDS rules fire (e.g., selecting the Enterprise edition triggers a recommendation for the Advanced Security add-on), the agent surfaces these as non-blocking suggestions with business rationale. Tracks recommendation acceptance rates to improve suggestion relevance over time. Recommendations include estimated price impact using AdjustmentType calculations.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Product ID                   | String (UUID)     | Output from cpq-agent-001 (product selection)   |
| Current Configuration State  | JSON object       | Running state maintained in session              |
| Option Selection Event       | JSON object (groupId, optionId, action: SELECT/DESELECT) | Sales rep UI interaction |
| Attribute Value Entry        | JSON object (attributeId, value, dataType) | Sales rep form input              |
| Customer Segment             | String (Enum)     | Passed from cpq-agent-001 customer context       |
| Quote Line Item ID           | String (UUID)     | Optional -- when reconfiguring an existing line item |
| Configuration Template ID    | String (UUID)     | Optional -- when applying a saved configuration template |

---

## Outputs

| Output                           | Format                  | Human-in-the-Loop Checkpoint                     |
|----------------------------------|-------------------------|--------------------------------------------------|
| Configuration State              | JSON object (selectedOptions, attributeValues, appliedRules, validationStatus) | Continuously updated; rep sees real-time state |
| Validation Errors                | JSON array of objects (ruleId, ruleType, message, severity, affectedOptions) | REQUIRES/EXCLUDES violations block progression until resolved |
| RECOMMENDS Suggestions           | JSON array of objects (ruleId, suggestedOption, rationale, priceImpact) | Advisory only; rep decides whether to accept |
| Auto-Applied Defaults Log        | JSON array of objects (ruleId, appliedOption, appliedValue) | Informational; rep can override any default |
| Price Impact Summary             | JSON object (basePrice, optionAdjustments[], runningTotal) | Displayed in real time; no approval gate |
| Validated Configuration Payload  | JSON object (productId, selectedOptions, attributeValues, adjustments) | Final configuration must pass `POST /configuration/validate` before submission to quote |

---

## Typical Workflow

1. **Receive Product for Configuration**: After cpq-agent-001 confirms a product selection, this agent receives the product ID and initiates configuration loading. Retrieves the product's full configuration schema in parallel.

2. **Load Configuration Schema**: Agent executes three parallel calls: `GET /api/v1/configuration/rules/product/{productId}` for rules, `GET /api/v1/product-options/groups/product/{productId}` for option groups, and `GET /api/v1/product-attributes/products/{productId}/values` for attribute definitions. Assembles the complete configuration model.

3. **Apply DEFAULT Rules**: Agent evaluates all rules with `ruleType = DEFAULT` and automatically applies them to the configuration state. This sets initial edition selections, pre-checks mandatory options, and populates attribute default values. Logs all auto-applied defaults for transparency.

4. **Present Configuration Interface**: Agent renders the option groups as a structured configuration form, showing available options with their selection types, current selections (including defaults), attribute input fields with validation constraints, and price adjustment indicators.

5. **Process Selection Events**: As the rep selects/deselects options or enters attribute values, the agent incrementally evaluates the affected rules. REQUIRES rules surface mandatory co-selections. EXCLUDES rules disable mutually exclusive options. VALIDATES rules run custom validation expressions. Price adjustments (FIXED, PERCENTAGE, OVERRIDE) are recalculated.

6. **Surface RECOMMENDS Suggestions**: When a selection triggers RECOMMENDS rules, the agent presents non-blocking suggestion cards with the recommended option, business rationale, and estimated price impact. Rep can accept, dismiss, or defer each recommendation.

7. **Validate Complete Configuration**: When the rep signals configuration is complete, the agent calls `POST /api/v1/configuration/validate` with the full configuration payload. If validation passes, the configuration is marked as valid and ready for pricing. If validation fails, specific errors are highlighted with corrective guidance.

8. **Emit Validated Configuration**: The validated configuration is packaged as a structured payload containing all selected options, attribute values, and calculated adjustments. This payload is passed to cpq-agent-003 (Bundle Assembly) if bundling applies, or directly to cpq-agent-004 (List Price Application) for pricing waterfall initiation.

---

## Edge Cases & Exceptions

- **Circular Rule Dependencies**: When REQUIRES rules create circular dependencies (A requires B, B requires C, C requires A), the agent detects the cycle during rule graph construction and flags it as a configuration model error. Presents all options in the cycle to the rep with a manual override prompt and logs the issue for product management review.

- **Conflicting REQUIRES and EXCLUDES**: When selecting an option triggers both a REQUIRES rule (must include X) and an EXCLUDES rule (cannot include X) on another option, the agent halts incremental evaluation, presents the conflict with both rule explanations, and asks the rep to choose which constraint to honor. The overridden rule is logged.

- **Attribute Value Out of Range**: When a rep enters an attribute value that violates the defined constraints (e.g., user count of 50,000 when max is 10,000), the agent immediately displays the validation error with the allowed range and prevents the value from being committed to the configuration state. Suggests the nearest valid value.

- **Configuration Template Mismatch**: When applying a saved configuration template to a product that has been updated (new options added, options removed, rules changed), the agent performs a diff between the template and current schema. Applies compatible selections, flags incompatible ones, and asks the rep to resolve each mismatch individually.

- **Mid-Configuration Product Deprecation**: If a product's status changes to DEPRECATED while a rep is actively configuring it, the agent displays an immediate warning with the replacement product (if available) and offers to migrate the current configuration to the replacement product's schema, mapping compatible options automatically.

- **Large Option Group Rendering (>100 options)**: For products with extensive option catalogs, the agent implements lazy loading with search/filter capabilities within each option group. Groups are collapsed by default with summary indicators showing selection count and pending recommendations.

---

## Success Metrics

| Metric                                        | Target          |
|------------------------------------------------|-----------------|
| Configuration validation pass rate (first attempt) | >= 85%      |
| Average configuration completion time          | < 4 minutes     |
| DEFAULT rule auto-application accuracy         | 100%            |
| RECOMMENDS suggestion acceptance rate          | >= 25%          |
| Configuration error resolution time (avg)      | < 30 seconds    |
