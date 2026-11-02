/**
 * Pricing domain models.
 * Aligned to pricing-service / pricebook-service (`com.opencrm.pricing.*`, `com.opencrm.pricebook.*`).
 *
 * NOTE: Pricing ProductType values differ from catalog ProductType.
 * Use {@link PricingProductType} here; use {@link CatalogProductType} from
 * `product.model.ts` for catalog DTOs. Do not merge the two enums.
 */

/** Mirrors: com.opencrm.pricing.product.ProductType */
export const PRICING_PRODUCT_TYPES = [
  'SAAS_SEAT_NAMED',
  'SAAS_SEAT_CONCURRENT',
  'SAAS_MAU',
  'SAAS_USAGE',
  'SAAS_CREDITS',
  'SAAS_TIERED',
  'SAAS_BASE_OVERAGE',
  'SAAS_COMMITTED',
  'SAAS_OUTCOME',
  'ONPREM_PERPETUAL',
  'ONPREM_CORE',
  'ONPREM_SERVER',
  'ONPREM_CAPACITY',
  'ONPREM_SITE',
  'ONPREM_MODULE',
  'ONPREM_MIGRATION',
  'ONPREM_SUPPORT',
  'HYBRID_BUNDLE',
  'HYBRID_BRIDGE',
  'HYBRID_MIGRATION_RAMP',
] as const;

export type PricingProductType = (typeof PRICING_PRODUCT_TYPES)[number];

export const PRICING_PRODUCT_TYPE_LABELS: Record<PricingProductType, string> = {
  SAAS_SEAT_NAMED: 'SaaS Named Seat',
  SAAS_SEAT_CONCURRENT: 'SaaS Concurrent Seat',
  SAAS_MAU: 'SaaS MAU',
  SAAS_USAGE: 'SaaS Usage',
  SAAS_CREDITS: 'SaaS Credits',
  SAAS_TIERED: 'SaaS Tiered',
  SAAS_BASE_OVERAGE: 'SaaS Base + Overage',
  SAAS_COMMITTED: 'SaaS Committed',
  SAAS_OUTCOME: 'SaaS Outcome',
  ONPREM_PERPETUAL: 'On-Prem Perpetual',
  ONPREM_CORE: 'On-Prem Core',
  ONPREM_SERVER: 'On-Prem Server',
  ONPREM_CAPACITY: 'On-Prem Capacity',
  ONPREM_SITE: 'On-Prem Site',
  ONPREM_MODULE: 'On-Prem Module',
  ONPREM_MIGRATION: 'On-Prem Migration',
  ONPREM_SUPPORT: 'On-Prem Support',
  HYBRID_BUNDLE: 'Hybrid Bundle',
  HYBRID_BRIDGE: 'Hybrid Bridge',
  HYBRID_MIGRATION_RAMP: 'Hybrid Migration Ramp',
};

export function isPricingProductType(value: unknown): value is PricingProductType {
  return typeof value === 'string' && (PRICING_PRODUCT_TYPES as readonly string[]).includes(value);
}

/**
 * Mirrors: com.opencrm.pricing.product.ProductCategory
 * Same literals as catalog ProductCategory today; separate alias documents package drift risk.
 */
export type PricingProductCategory =
  | 'SUBSCRIPTION'
  | 'LICENSE'
  | 'SUPPORT'
  | 'SERVICES'
  | 'ADD_ON';

/** Mirrors: com.opencrm.pricing.customer.CustomerSegment */
export type CustomerSegment = 'STARTUP' | 'SMB' | 'MID_MARKET' | 'ENTERPRISE' | 'STRATEGIC';

export const CUSTOMER_SEGMENT_LABELS: Record<CustomerSegment, string> = {
  STARTUP: 'Startup',
  SMB: 'SMB',
  MID_MARKET: 'Mid-Market',
  ENTERPRISE: 'Enterprise',
  STRATEGIC: 'Strategic',
};

/** Mirrors: com.opencrm.pricing.customer.CustomerRegion */
export type CustomerRegion = 'NA' | 'EMEA' | 'APAC' | 'LATAM';

export const CUSTOMER_REGION_LABELS: Record<CustomerRegion, string> = {
  NA: 'North America',
  EMEA: 'EMEA',
  APAC: 'APAC',
  LATAM: 'LATAM',
};

/** Mirrors: com.opencrm.pricing.rule.RuleType */
export type RuleType = 'PRICING' | 'CONFIGURATION' | 'APPROVAL' | 'RENEWAL' | 'RISK';

/** Mirrors: com.opencrm.pricing.rule.RuleStatus */
export type RuleStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED';

export const RULE_STATUS_LABELS: Record<RuleStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  DISABLED: 'Disabled',
};

/** Mirrors: com.opencrm.pricing.ruleset.RuleSetStatus */
export type RuleSetStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'ARCHIVED';

export const RULE_SET_STATUS_LABELS: Record<RuleSetStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  DEPRECATED: 'Deprecated',
  ARCHIVED: 'Archived',
};

/**
 * Subscription type on PricingContext (Java field is free string; common values documented).
 */
export type SubscriptionType = 'NEW' | 'AMENDMENT' | 'RENEWAL';

/** Mirrors: com.opencrm.pricing.waterfall.WaterfallStepType */
export type WaterfallStepType =
  | 'LIST_PRICE'
  | 'BASE_PRICE'
  | 'DISCOUNT'
  | 'SURCHARGE'
  | 'PARTNER_ADJUSTMENT'
  | 'PRORATION'
  | 'FX_CONVERSION'
  | 'TAX'
  | 'NET_PRICE'
  | 'APPROVAL';

/**
 * Pricing UI approval severity (PRICING_IA_BACKLOG).
 * Wire `PricingResult.approvalState` is currently a free string from pricing-service.
 */
export type PricingApprovalState =
  | 'COMPLIANT'
  | 'DEAL_DESK_REQUIRED'
  | 'CFO_REQUIRED'
  | 'HARD_BLOCK';

export const PRICING_APPROVAL_STATE_LABELS: Record<PricingApprovalState, string> = {
  COMPLIANT: 'Compliant',
  DEAL_DESK_REQUIRED: 'Deal Desk Required',
  CFO_REQUIRED: 'CFO Required',
  HARD_BLOCK: 'Hard Block',
};

export const PRICING_APPROVAL_STATE_COLORS: Record<PricingApprovalState, string> = {
  COMPLIANT: '#4caf50',
  DEAL_DESK_REQUIRED: '#ff9800',
  CFO_REQUIRED: '#f44336',
  HARD_BLOCK: '#b71c1c',
};

/** Rules engine type (RuleSetDto.engineType; default DROOLS). */
export type RulesEngineType = 'DROOLS' | 'OPENL';

/**
 * Mirrors: com.opencrm.pricing.product.PricingProduct (lightweight ref DTO)
 */
export interface PricingProductDto {
  id: number;
  uuid: string;
  externalId: string;
  name: string;
  productType: PricingProductType;
  category?: PricingProductCategory;
  listPrice?: number;
  currency?: string;
  metadata?: string;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.pricing.customer.CustomerDto
 * Returned by GET /api/v1/pricing-customers
 */
export interface PricingCustomerDto {
  id: number;
  uuid: string;
  externalId: string;
  name: string;
  type?: string;
  segment?: CustomerSegment;
  region?: CustomerRegion;
  industry?: string;
  churnScore?: number;
  annualRevenue?: number;
  metadata?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.pricing.customer.CustomerCreateRequest
 * Sent to POST /api/v1/pricing-customers
 */
export interface PricingCustomerCreateRequest {
  externalId: string; // @NotBlank
  name: string; // @NotBlank
  type?: string;
  segment?: CustomerSegment;
  region?: CustomerRegion;
  industry?: string;
  churnScore?: number;
  annualRevenue?: number;
  metadata?: string;
}

/**
 * Mirrors: com.opencrm.pricing.customer.CustomerUpdateRequest
 * Sent to PUT /api/v1/pricing-customers/:id
 */
export interface PricingCustomerUpdateRequest {
  name?: string;
  type?: string;
  segment?: CustomerSegment;
  region?: CustomerRegion;
  industry?: string;
  churnScore?: number;
  annualRevenue?: number;
  metadata?: string;
}

/**
 * Mirrors: com.opencrm.pricebook.PriceBookDto
 * Returned by GET /api/v1/price-books
 */
export interface PriceBookDto {
  id: number;
  uuid: string;
  name: string;
  region?: string;
  currency?: string;
  isDefault?: boolean;
  effectiveDate?: string;
  expirationDate?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.pricebook.PriceBookCreateRequest
 * Sent to POST /api/v1/price-books
 */
export interface PriceBookCreateRequest {
  name: string; // @NotBlank
  region?: string;
  currency?: string;
  isDefault?: boolean;
  effectiveDate?: string;
  expirationDate?: string;
}

/**
 * Mirrors: com.opencrm.pricebook.PriceBookUpdateRequest
 * Sent to PUT /api/v1/price-books/:id
 */
export interface PriceBookUpdateRequest {
  name?: string;
  region?: string;
  currency?: string;
  isDefault?: boolean;
  effectiveDate?: string;
  expirationDate?: string;
}

/**
 * Mirrors: com.opencrm.pricebook.PriceBookEntryDto
 */
export interface PriceBookEntryDto {
  id: number;
  uuid: string;
  priceBookId: number;
  productExternalId: string;
  listPrice: number;
  floorPrice?: number;
  currency?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.pricebook.PriceBookEntryCreateRequest
 */
export interface PriceBookEntryCreateRequest {
  productExternalId: string; // @NotBlank
  listPrice: number; // @NotNull
  floorPrice?: number;
  currency?: string;
}

/**
 * Mirrors: com.opencrm.pricebook.PriceBookEntryUpdateRequest
 */
export interface PriceBookEntryUpdateRequest {
  productExternalId?: string;
  listPrice?: number;
  floorPrice?: number;
  currency?: string;
}

/**
 * Mirrors: com.opencrm.pricing.ruleset.RuleSetDto
 * Returned by GET /api/v1/rule-sets
 */
export interface RuleSetDto {
  id: number;
  uuid: string;
  name: string;
  description?: string;
  status: RuleSetStatus;
  ruleSetVersion?: number;
  effectiveDate?: string;
  expirationDate?: string;
  engineType?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.pricing.ruleset.RuleSetCreateRequest
 * Sent to POST /api/v1/rule-sets
 */
export interface RuleSetCreateRequest {
  name: string; // @NotBlank
  description?: string;
  engineType?: string; // default DROOLS
  effectiveDate?: string;
  expirationDate?: string;
}

/**
 * Mirrors: com.opencrm.pricing.ruleset.RuleSetUpdateRequest
 * Sent to PUT /api/v1/rule-sets/:id
 */
export interface RuleSetUpdateRequest {
  name?: string;
  description?: string;
  effectiveDate?: string;
  expirationDate?: string;
}

/**
 * Mirrors: com.opencrm.pricing.rule.RuleDto
 * Returned by GET /api/v1/rules
 */
export interface RuleDto {
  id: number;
  uuid: string;
  ruleSetId: number;
  name: string;
  description?: string;
  ruleType: RuleType;
  priority?: number;
  status: RuleStatus;
  content?: string;
  conditionGroup?: string;
  actionSet?: string;
  conflictPolicy?: string;
  explanationCode?: string;
  metadata?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.pricing.rule.RuleCreateRequest
 * Sent to POST /api/v1/rules
 */
export interface RuleCreateRequest {
  ruleSetId: number; // @NotNull
  name: string; // @NotBlank
  description?: string;
  ruleType: RuleType; // @NotNull
  priority: number; // @NotNull, 100–1000
  content: string; // @NotBlank (DRL)
  conditionGroup?: string;
  actionSet?: string;
  conflictPolicy?: string;
  explanationCode?: string;
  metadata?: string;
}

/**
 * Mirrors: com.opencrm.pricing.rule.RuleUpdateRequest
 * Sent to PUT /api/v1/rules/:id
 */
export interface RuleUpdateRequest {
  name?: string;
  description?: string;
  ruleType?: RuleType;
  priority?: number;
  content?: string;
  conditionGroup?: string;
  actionSet?: string;
  conflictPolicy?: string;
  explanationCode?: string;
  metadata?: string;
}

/**
 * Mirrors: com.opencrm.pricing.rule.RuleImpactDto
 */
export interface RuleImpactDto {
  ruleId: number;
  ruleName?: string;
  ruleType?: RuleType;
  ruleStatus?: RuleStatus;
  priority?: number;
  ruleSetId?: number;
  ruleSetStatus?: RuleSetStatus;
  activeRuleSet?: boolean;
  totalRulesInRuleSet?: number;
  activeRulesInRuleSet?: number;
  pricingExecutionsWithRuleSet?: number;
  pricingExecutionsWithRuleSetLast30Days?: number;
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.PricingLineRequest
 */
export interface PricingLineRequest {
  productExternalId: string;
  lineItemType?: string;
  quantity?: number;
  listPriceOverride?: number;
  metadata?: Record<string, unknown>;
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.PricingContext
 * Sent to POST /api/v1/pricing/execute and /api/v1/pricing/simulate
 */
export interface PricingContext {
  customerExternalId: string;
  quoteExternalId?: string;
  lineItems: PricingLineRequest[];
  region?: string;
  currency?: string;
  durationInMonths?: number;
  subscriptionType?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.PricingLineResult
 */
export interface PricingLineResult {
  productExternalId: string;
  lineItemType?: string;
  quantity?: number;
  listPrice?: number;
  unitPrice?: number;
  discountRate?: number;
  discountAmount?: number;
  surchargeAmount?: number;
  finalPrice?: number;
  explanationCodes?: string[];
  metadata?: Record<string, unknown>;
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.PricingWaterfallStep
 */
export interface PricingWaterfallStep {
  stepType: WaterfallStepType;
  description?: string;
  amount?: number;
  runningTotal?: number;
  ruleId?: string;
  ruleName?: string;
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.PricingWaterfall
 */
export interface PricingWaterfall {
  listPrice?: number;
  basePrice?: number;
  netPrice?: number;
  steps?: PricingWaterfallStep[];
  approvalState?: string;
  explanationCodes?: string[];
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.FinancialMetrics
 */
export interface FinancialMetrics {
  arr?: number;
  tcv?: number;
  acv?: number;
  /** Final-period run rate × 12. Omitted on a flat quote (Java NON_NULL). */
  exitArr?: number;
  clv?: number;
  churnScore?: number;
  discountRate?: number;
  totalDiscount?: number;
  totalSurcharge?: number;
  priceMultiplier?: number;
  estimatedMarginPct?: number;
}

/**
 * Mirrors: com.opencrm.pricing.waterfall.PricingResult
 */
export interface PricingResult {
  success: boolean;
  lineResults?: PricingLineResult[];
  waterfall?: PricingWaterfall;
  financialMetrics?: FinancialMetrics;
  explanationCodes?: string[];
  approvalState?: string;
  approvalTier?: string;
  executionTimeMs?: number;
  errorMessage?: string;
}

/**
 * Mirrors: com.opencrm.pricing.execution.BulkPricingSimulationRequest
 * Sent to POST /api/v1/pricing/simulate/bulk
 */
export interface BulkPricingSimulationRequest {
  contexts: PricingContext[];
}

/**
 * Mirrors: com.opencrm.pricing.execution.BulkPricingSimulationResponse
 */
export interface BulkPricingSimulationResponse {
  totalRequests?: number;
  successfulRequests?: number;
  failedRequests?: number;
  totalExecutionTimeMs?: number;
  results: PricingResult[];
}

/**
 * Mirrors: com.opencrm.pricing.execution.PricingAuditLog (DTO projection)
 * Returned by GET /api/v1/pricing/audit/:quoteExternalId
 */
export interface PricingAuditLogDto {
  id: number;
  uuid: string;
  quoteExternalId?: string;
  customerExternalId?: string;
  ruleSetId?: number;
  inputSnapshot: string;
  outputSnapshot: string;
  waterfallTrace?: string;
  rulesFired?: string;
  executionTimeMs?: number;
  executedAt: string;
  executedBy?: string;
}
