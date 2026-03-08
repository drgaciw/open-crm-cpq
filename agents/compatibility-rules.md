# Agent PRD: cpq-agent-015

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-015                                  |
| **Name**          | Compatibility Rules Agent                      |
| **Group**         | Product Catalog Management                     |
| **Primary Service** | product-service (8083)                        |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-013 (Product SKU Management Agent)   |

---

## Purpose

The Compatibility Rules Agent enforces technical and business compatibility constraints across the product catalog, ensuring that sales representatives can only create valid product configurations. It manages the full spectrum of configuration rule types -- REQUIRES (product A mandates inclusion of product B), EXCLUDES (product A cannot coexist with product B), RECOMMENDS (product A suggests product B but does not mandate it), DEFAULT (product A automatically pre-selects product B unless overridden), and VALIDATES (custom validation logic that evaluates complex multi-product conditions). The agent creates, maintains, and executes these rules in real-time during the quote configuration process, catching incompatible selections before they reach pricing or approval stages. By centralizing compatibility logic in a managed rule engine rather than relying on tribal knowledge, the agent eliminates configuration errors that historically resulted in undeliverable quotes, post-sale engineering change orders, and customer dissatisfaction.

The agent operates at a Semi-autonomous autonomy level, meaning that real-time validation of product configurations against existing rules executes automatically without human intervention, but the creation, modification, or deletion of compatibility rules themselves requires explicit human approval. This distinction is critical: sales representatives benefit from instant feedback when they select incompatible products (automated validation), while the underlying rule set that governs those validations remains under controlled change management. The agent also performs cross-product constraint analysis to identify rule conflicts (e.g., circular REQUIRES dependencies or contradictory REQUIRES/EXCLUDES pairs), rule coverage gaps (products with no compatibility rules defined), and rule impact assessments when new rules are proposed (how many existing quotes or active configurations would be affected). It maintains a technical compatibility matrix that maps hardware/software/service interrelationships and synchronizes with external sources of compatibility data such as vendor certification matrices and platform version support tables.

---

## Key Skills / Capabilities

- **Compatibility Validation**: Executes real-time validation of product configurations against the active rule set using `POST /api/v1/configuration/validate`. Accepts a proposed configuration (list of selected products and their options) and evaluates all applicable REQUIRES, EXCLUDES, and VALIDATES rules. Returns a validation result with pass/fail status, violated rules, and specific remediation guidance (e.g., "Product X requires Product Y -- add Product Y or remove Product X"). This validation runs automatically during quote configuration without requiring human approval.

- **Rule Creation Assistance**: Guides catalog administrators through defining new compatibility rules using `POST /api/v1/configuration/rules`. Constructs rule payloads specifying the source product, target product, rule type (from ConfigRuleType enum), rule conditions, and human-readable descriptions. Validates that proposed rules do not conflict with existing rules by retrieving the current rule set via `GET /api/v1/configuration/rules` and running conflict detection algorithms before presenting the rule for approval.

- **Cross-Product Constraint Analysis**: Performs deep analysis of the entire compatibility rule graph by retrieving all rules via `GET /api/v1/configuration/rules` and product-specific rules via `GET /api/v1/configuration/rules/product/{productId}`. Detects circular dependency chains (A requires B, B requires C, C requires A), contradictory rule pairs (A requires B and A excludes B), transitive implications (if A requires B and B requires C, then A transitively requires C), and orphaned rules referencing deprecated or end-of-life products. Generates a constraint health report with severity-ranked findings.

- **Configuration Impact Assessment**: When a new rule is proposed or an existing rule is modified, the agent evaluates the impact on the existing quote portfolio and active configurations. Queries open quotes via `GET /api/v1/quotes` and their line items via `GET /api/v1/quotes/{quoteId}/line-items` to identify configurations that would become invalid under the proposed rule change. Presents an impact report showing affected quote count, deal value at risk, and specific quote IDs for review.

- **Technical Compatibility Matrix Synchronization**: Maintains an internal compatibility matrix mapping product-to-product relationships across all dimensions (hardware-software compatibility, version dependencies, platform requirements, service prerequisites). Cross-references product metadata from `GET /api/v1/products/{id}` and product attributes from `GET /api/v1/product-attributes/products/{productId}/values` to identify relationships that should be encoded as formal compatibility rules but currently exist only as informal knowledge.

- **Rule Coverage Gap Detection**: Analyzes the product catalog to identify products that have no compatibility rules defined, products recently created by cpq-agent-013 that have not yet been assessed for compatibility relationships, and product categories where rule density is significantly lower than peer categories. Recommends priority areas for rule development based on quote volume and historical configuration error rates.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Configuration Payload    | JSON object       | Quote builder UI (list of selected productIds, optionIds, quantities) |
| Source Product ID        | String (UUID)     | Administrator input for rule creation            |
| Target Product ID        | String (UUID)     | Administrator input for rule creation            |
| Rule Type                | String (Enum)     | Selection from ConfigRuleType enum (REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, VALIDATES) |
| Rule Conditions          | JSON object       | Administrator input (conditional logic, attribute-based constraints) |
| Rule Description         | String            | Administrator input (human-readable explanation)  |
| Product ID for Analysis  | String (UUID)     | Target product for rule coverage or impact analysis |
| Impact Assessment Scope  | String (Enum)     | ALL_QUOTES, OPEN_QUOTES_ONLY, or SPECIFIC_DEAL   |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Validation Result             | JSON object (valid: boolean, violations: array of {ruleId, ruleType, description, remediation}) | Automated -- no approval needed; results displayed to sales rep in real-time during configuration |
| Rule Creation Payload         | JSON object (complete POST /api/v1/configuration/rules request body) | Administrator must review and approve before rule is created |
| Constraint Health Report      | JSON object (circularDependencies, contradictions, orphanedRules, transitiveChains, severityRanking) | Informational report; administrator prioritizes and addresses findings |
| Impact Assessment Report      | JSON object (affectedQuoteCount, totalDealValue, affectedQuoteIds, configurationBreakdowns) | Required review before any rule modification that affects existing quotes |
| Rule Coverage Gap Report      | JSON array of objects (productId, productName, ruleCount, categoryAverage, recommendation) | Informational; administrator decides which gaps to address |
| Compatibility Matrix Export   | JSON matrix (productId x productId, relationship type, rule reference) | Reference document; no action checkpoint |

---

## Typical Workflow

1. **Receive Validation or Rule Management Request**: The agent operates in two modes. In validation mode, it receives a configuration payload from the quote builder UI when a sales rep adds, removes, or modifies products in a quote. In rule management mode, a catalog administrator initiates a rule creation, modification, or analysis request from the compatibility management UI.

2. **Load Active Rule Set**: Agent retrieves the relevant rule set using `GET /api/v1/configuration/rules` for full rule analysis or `GET /api/v1/configuration/rules/product/{productId}` for product-specific validation. Caches the rule set for the duration of the configuration session to minimize API calls during iterative configuration changes.

3. **Execute Validation (Automated)**: For configuration validation requests, the agent submits the configuration payload to `POST /api/v1/configuration/validate` and processes the response. Categorizes validation results into hard failures (REQUIRES/EXCLUDES/VALIDATES violations that block progression) and soft recommendations (RECOMMENDS suggestions that the rep may optionally act on). Returns results to the UI immediately without human approval.

4. **Analyze Rule Proposal (Rule Management)**: For new rule creation requests, the agent constructs the proposed rule payload and runs it against the existing rule set to detect conflicts. Performs circular dependency analysis, contradiction detection, and transitive implication mapping. If conflicts are detected, presents them to the administrator before allowing the rule to proceed to approval.

5. **Assess Configuration Impact**: Before presenting a new or modified rule for approval, the agent evaluates the impact on existing quotes by querying `GET /api/v1/quotes` and `GET /api/v1/quotes/{quoteId}/line-items` for open quotes. Compiles an impact report showing which existing configurations would become invalid under the proposed rule change.

6. **Present Rule for Approval**: Agent displays the complete rule definition, conflict analysis results, and impact assessment to the administrator. Includes the human-readable description, affected product pairs, and a preview of how the rule would affect a sample configuration. Waits for explicit approval before proceeding.

7. **Execute Approved Rule Change**: Upon approval, the agent submits the rule creation via `POST /api/v1/configuration/rules`. Verifies successful creation by retrieving the rule and confirming it appears in subsequent `GET /api/v1/configuration/rules/product/{productId}` queries for both the source and target products.

8. **Update Constraint Analysis**: After a rule change, the agent re-runs the full constraint health analysis to detect any newly introduced conflicts or implications. Updates the compatibility matrix and notifies the administrator if the new rule created any transitive effects that may require additional rules or adjustments.

---

## Edge Cases & Exceptions

- **Circular Dependency Detection**: When a proposed REQUIRES rule would create a circular dependency chain (e.g., A requires B, B requires C, and the new rule C requires A), the agent blocks the rule creation and presents the full dependency cycle with a visual graph representation. Suggests alternative rule structures such as creating a bundle that includes all three products or using RECOMMENDS instead of REQUIRES for one link in the chain.

- **Contradictory Rule Pair**: When a proposed rule directly contradicts an existing rule (e.g., proposing A REQUIRES B when A EXCLUDES B already exists), the agent presents both rules side-by-side and requires the administrator to either withdraw the new rule or first delete/modify the conflicting existing rule. Never allows contradictory rules to coexist in the active rule set.

- **Rule Referencing Deprecated Product**: When a compatibility rule references a product with status DEPRECATED or END_OF_LIFE (detected by cross-referencing `GET /api/v1/products/{id}`), the agent flags the rule as potentially stale. For REQUIRES rules pointing to deprecated products, escalates immediately since the rule would force selection of an unavailable product. For EXCLUDES rules, downgrades to informational since excluding a deprecated product has minimal practical impact.

- **High-Impact Rule Change**: When the impact assessment reveals that a proposed rule would invalidate more than 10 active quotes or affect deals with aggregate pipeline value exceeding a configurable threshold, the agent applies an enhanced approval workflow requiring senior administrator sign-off. Generates a detailed remediation plan for affected quotes.

- **Bulk Product Addition Validation**: When a sales rep pastes or imports multiple products into a quote simultaneously, the agent validates the entire set as a single configuration rather than validating each product individually. This catches cross-product incompatibilities that would not surface in individual product validation (e.g., two mutually exclusive products both being added in the same batch).

- **Rule Evaluation Performance Degradation**: When the active rule set exceeds performance thresholds (e.g., >1000 rules or validation latency exceeding 500ms), the agent recommends rule consolidation strategies such as replacing multiple individual REQUIRES rules with a bundle definition, or archiving rules for product pairs where both products are in END_OF_LIFE status.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Real-time configuration validation latency (p95) | < 500ms |
| Incompatible configuration escape rate (invalid configs reaching pricing) | < 1% |
| Rule conflict detection accuracy (circular deps, contradictions found) | >= 99% |
| Rule coverage (products with at least one compatibility rule defined) | >= 90% |
| Impact assessment accuracy (predicted vs actual affected quotes) | >= 95% |
