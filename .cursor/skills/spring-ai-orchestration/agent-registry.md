# CPQ Functional Agent Registry

Authoritative handoff graph: `agents/HANDOFFS.md`. Parent index: `agent-prd.md` §3.

## Canonical 37 agents (cpq-agent-001 … cpq-agent-037)

| ID | Title | Java class | Package | PRD | Group |
|----|-------|------------|---------|-----|-------|
| 001 | Guided Product Selection | `GuidedProductSelectionAgent` | `agent.product` | `agents/guided-product-selection.md` | Configure |
| 002 | Option & Feature Configuration | `OptionFeatureConfigurationAgent` | `agent.product` | `agents/option-feature-configuration.md` | Configure |
| 003 | Bundle Assembly | `BundleAssemblyAgent` | `agent.product` | `agents/bundle-assembly.md` | Configure |
| 004 | List Price Application | `ListPriceApplicationAgent` | `agent.pricing` | `agents/list-price-application.md` | Price |
| 005 | Pricing Method | `PricingMethodAgent` | `agent.pricing` | `agents/pricing-method.md` | Price |
| 006 | Markup & Cost Integration | `MarkupCostIntegrationAgent` | `agent.pricing` | `agents/markup-cost-integration.md` | Price |
| 007 | Volume Discount | `VolumeDiscountAgent` | `agent.pricing` | `agents/volume-discount.md` | Price |
| 008 | Customer Segment Discount | `CustomerSegmentDiscountAgent` | `agent.pricing` | `agents/customer-segment-discount.md` | Price |
| 009 | Promotional Campaign | `PromotionalCampaignAgent` | `agent.pricing` | `agents/promotional-campaign.md` | Price |
| 010 | Deal-Specific Discount | `DealSpecificDiscountAgent` | `agent.pricing` | `agents/deal-specific-discount.md` | Price |
| 011 | Minimum Margin Enforcement | `MinimumMarginEnforcementAgent` | `agent.pricing` | `agents/minimum-margin-enforcement.md` | Price (guardrail) |
| 012 | Profitability Alert | `ProfitabilityAlertAgent` | `agent.pricing` | `agents/profitability-alert.md` | Price (advisory) |
| 013 | Product SKU Management | `ProductSkuManagementAgent` | `agent.product` | `agents/product-sku-management.md` | Catalog |
| 014 | Attribute & Feature Catalog | `AttributeFeatureCatalogAgent` | `agent.product` | `agents/attribute-feature-catalog.md` | Catalog |
| 015 | Compatibility Rules | `CompatibilityRulesAgent` | `agent.product` | `agents/compatibility-rules.md` | Catalog |
| 016 | Validation Rules | `ValidationRulesAgent` | `agent.product` | `agents/validation-rules.md` | Catalog |
| 017 | Recommendation & Upsell | `RecommendationUpsellAgent` | `agent.intelligence` | `agents/recommendation-upsell.md` | Quote (advisory) |
| 018 | Dynamic Bundle Pricing | `DynamicBundlePricingAgent` | `agent.pricing` | `agents/dynamic-bundle-pricing.md` | Price |
| 019 | SaaS Tier Pricing | `SaasTierPricingAgent` | `agent.subscription` | `agents/saas-tier-pricing.md` | Subscription |
| 020 | Term & Co-Terming | `TermCotermingAgent` | `agent.subscription` | `agents/term-coterming.md` | Subscription |
| 021 | Usage-Based Pricing | `UsageBasedPricingAgent` | `agent.subscription` | `agents/usage-based-pricing.md` | Subscription |
| 022 | Perpetual License | `PerpetualLicenseAgent` | `agent.subscription` | `agents/perpetual-license.md` | Subscription |
| 023 | Maintenance & Support Fee | `MaintenanceSupportFeeAgent` | `agent.subscription` | `agents/maintenance-support-fee.md` | Subscription |
| 024 | Multi-Level Approval Routing | `MultiLevelApprovalRoutingAgent` | `agent.governance` | `agents/multilevel-approval-routing.md` | Approve |
| 025 | Exception Handling | `ExceptionHandlingAgent` | `agent.governance` | `agents/exception-handling.md` | Approve |
| 026 | Win-Probability Analysis | `WinProbabilityAnalysisAgent` | `agent.intelligence` | `agents/win-probability-analysis.md` | Quote (advisory) |
| 027 | Margin Optimization | `MarginOptimizationAgent` | `agent.intelligence` | `agents/margin-optimization.md` | Quote (advisory) |
| 028 | Renewal Opportunity Detection | `RenewalOpportunityDetectionAgent` | `agent.intelligence` | `agents/renewal-opportunity-detection.md` | Renew |
| 029 | Upgrade/Downgrade Path | `UpgradeDowngradePathAgent` | `agent.intelligence` | `agents/upgrade-downgrade-path.md` | Renew |
| 030 | Contract Lifecycle Management | `ContractLifecycleManagementAgent` | `agent.governance` | `agents/contract-lifecycle-management.md` | Fulfill |
| 031 | Quote Document Generation | `QuoteDocumentGenerationAgent` | `agent.governance` | `agents/quote-document-generation.md` | Quote |
| 032 | Currency & FX Management | `CurrencyFxManagementAgent` | `agent.governance` | `agents/currency-fx-management.md` | Price |
| 033 | Tax Calculation Integration | `TaxCalculationIntegrationAgent` | `agent.governance` | `agents/tax-calculation-integration.md` | Price |
| 034 | Competitive Pricing Intelligence | `CompetitivePricingIntelligenceAgent` | `agent.governance` | `agents/competitive-pricing-intelligence.md` | Quote (advisory) |
| 035 | Price Book Management | `PriceBookManagementAgent` | `agent.governance` | `agents/price-book-management.md` | Price |
| 036 | Audit & Compliance Reporting | `AuditComplianceReportingAgent` | `agent.governance` | `agents/audit-compliance-reporting.md` | Approve |
| 037 | Deal Desk Workflow Orchestration | `DealDeskWorkflowOrchestrationAgent` | `agent.governance` | `agents/deal-desk-workflow-orchestration.md` | Approve (primary orchestrator) |

All paths under `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/`.

## Deal-desk orchestration cluster (non-001 numbering)

These use **two-digit** IDs in code (distinct from zero-padded 001–037 PRD IDs):

| ID | Title | Java class | Package |
|----|-------|------------|---------|
| cpq-agent-01 | Deal Strategist | `DealStrategistAgent` | `agent.orchestration` |
| cpq-agent-02 | Constitutional Guardian | `ConstitutionalGuardianAgent` | `agent.orchestration` |
| cpq-agent-03 | Commercial Architect | `CommercialArchitectAgent` | `agent.orchestration` |

## Extended implementation agents (beyond PRD-37)

| ID | Title | Java class | Package |
|----|-------|------------|---------|
| cpq-agent-A-1 | Intent Recognition | `IntentRecognitionAgent` | `agent.engineering` |
| cpq-agent-A-2 | NeRF Visualization | `NerfVisualizationAgent` | `agent.engineering` |
| cpq-agent-A-3 | Generative Engineer | `GenerativeEngineerAgent` | `agent.engineering` |
| cpq-agent-A-4 | Bundle Optimizer | `BundleOptimizerAgent` | `agent.engineering` |
| cpq-agent-B-1 | RL Pricing Actor | `RlPricingActorAgent` | `agent.commerce` |
| cpq-agent-B-2 | Counter-Party Modeler | `CounterPartyModelerAgent` | `agent.commerce` |
| cpq-agent-B-3 | Margin Discount Router | `MarginDiscountRouterAgent` | `agent.commerce` |
| cpq-agent-C-1 | Dynamic Composer | `DynamicComposerAgent` | `agent.execution` |
| cpq-agent-C-2 | AI Counsel | `AiCounselAgent` | `agent.execution` |
| cpq-agent-C-3 | Version Audit | `VersionAuditAgent` | `agent.execution` |
| cpq-agent-D-1 | Real-Time LCA | `RealTimeLcaAgent` | `agent.esg` |
| cpq-agent-D-2 | Circular Economy Advisor | `CircularEconomyAdvisorAgent` | `agent.esg` |
| cpq-agent-E-1 | Supply Chain Synthesizer | `SupplyChainSynthesizerAgent` | `agent.postsignature` |
| cpq-agent-E-2 | Transition Manager | `TransitionManagerAgent` | `agent.postsignature` |
| cpq-agent-F-1 | Master Data Steward | `MasterDataStewardAgent` | `agent.masterdata` |
| cpq-agent-F-2 | Deal Registration Authority | `DealRegistrationAuthorityAgent` | `agent.masterdata` |

## Cursor project specialists (non-Spring beans)

| Agent | Scope | Invoke when |
|-------|-------|-------------|
| `cpq-lifecycle-tracer` | Cross-microservice revenue path | Quote accept → CRM WON, LeadToDealLifecycleTest, gateway/event contract breaks outside ai-service |
| `spring-ai-orchestration-specialist` | ai-service federation hub | This agent — routing, tools, workflows, functional agent implementation |

## Key graph facts

- **Entry point:** cpq-agent-001
- **Primary orchestrator:** cpq-agent-037 (+ deal-desk workflow)
- **Final pricing guardrail:** cpq-agent-011 (salience 930)
- **Event channel:** `quote:accepted` → CRM listener + ai-service `QuoteAcceptedListener`
- **Planned channels:** `quote:status-changed`, `product:updated`, `pricing:executed` (see `agent-prd.md` §2.6)

## Invoke API

```
POST /api/v1/ai/agents/{agentId}/invoke
POST /api/v1/ai/agents/{agentId}/stream
POST /api/v1/ai/orchestrations/{workflowId}/invoke   # e.g. deal-desk
```

Session state: Redis `ai:session:{sessionId}` (24h TTL) via `RedisStateManager`.
