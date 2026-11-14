import { CRM_STATUS_TONES } from './status-tokens';

/**
 * Catalog product domain models.
 * Aligned to product-service (`com.opencrm.product.catalog.*`).
 *
 * NOTE: Catalog ProductType values differ from pricing-service ProductType.
 * Use {@link CatalogProductType} here; use {@link PricingProductType} from
 * `pricing.model.ts` for pricing DTOs. Do not merge the two enums.
 */

/** Mirrors: com.opencrm.product.catalog.ProductType */
export const CATALOG_PRODUCT_TYPES = [
  'SAAS_SEAT_NAMED',
  'SAAS_SEAT_CONCURRENT',
  'SAAS_USAGE_METERED',
  'SAAS_FLAT_RATE',
  'ONPREM_PERPETUAL',
  'ONPREM_CORE',
  'ONPREM_SUBSCRIPTION',
  'HYBRID_PORTABLE',
  'SUPPORT_BASIC',
  'SUPPORT_PREMIUM',
  'SUPPORT_ENTERPRISE',
  'PROFESSIONAL_SERVICES',
  'TRAINING',
  'ADD_ON_API',
  'ADD_ON_STORAGE',
  'ADD_ON_ANALYTICS',
  'ADD_ON_SECURITY',
  'ADD_ON_INTEGRATION',
  'HARDWARE',
  'CUSTOM',
] as const;

export type CatalogProductType = (typeof CATALOG_PRODUCT_TYPES)[number];

export const CATALOG_PRODUCT_TYPE_LABELS: Record<CatalogProductType, string> = {
  SAAS_SEAT_NAMED: 'SaaS Named Seat',
  SAAS_SEAT_CONCURRENT: 'SaaS Concurrent Seat',
  SAAS_USAGE_METERED: 'SaaS Usage Metered',
  SAAS_FLAT_RATE: 'SaaS Flat Rate',
  ONPREM_PERPETUAL: 'On-Prem Perpetual',
  ONPREM_CORE: 'On-Prem Core',
  ONPREM_SUBSCRIPTION: 'On-Prem Subscription',
  HYBRID_PORTABLE: 'Hybrid Portable',
  SUPPORT_BASIC: 'Support Basic',
  SUPPORT_PREMIUM: 'Support Premium',
  SUPPORT_ENTERPRISE: 'Support Enterprise',
  PROFESSIONAL_SERVICES: 'Professional Services',
  TRAINING: 'Training',
  ADD_ON_API: 'Add-on API',
  ADD_ON_STORAGE: 'Add-on Storage',
  ADD_ON_ANALYTICS: 'Add-on Analytics',
  ADD_ON_SECURITY: 'Add-on Security',
  ADD_ON_INTEGRATION: 'Add-on Integration',
  HARDWARE: 'Hardware',
  CUSTOM: 'Custom',
};

export function isCatalogProductType(value: unknown): value is CatalogProductType {
  return typeof value === 'string' && (CATALOG_PRODUCT_TYPES as readonly string[]).includes(value);
}

/**
 * Mirrors: com.opencrm.product.catalog.ProductCategory
 * Values currently match pricing-service ProductCategory; kept on catalog model
 * as the catalog DTO source of truth.
 */
export type ProductCategory =
  | 'SUBSCRIPTION'
  | 'LICENSE'
  | 'SUPPORT'
  | 'SERVICES'
  | 'ADD_ON';

export const PRODUCT_CATEGORY_LABELS: Record<ProductCategory, string> = {
  SUBSCRIPTION: 'Subscription',
  LICENSE: 'License',
  SUPPORT: 'Support',
  SERVICES: 'Services',
  ADD_ON: 'Add-on',
};

/** Mirrors: com.opencrm.product.catalog.ProductStatus */
export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'RETIRED';

export const PRODUCT_STATUS_LABELS: Record<ProductStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  DEPRECATED: 'Deprecated',
  RETIRED: 'Retired',
};

export const PRODUCT_STATUS_COLORS: Record<ProductStatus, string> = {
  DRAFT: CRM_STATUS_TONES.muted.ink,
  ACTIVE: CRM_STATUS_TONES.success.ink,
  DEPRECATED: CRM_STATUS_TONES.warning.ink,
  RETIRED: CRM_STATUS_TONES.muted.ink,
};

/** Mirrors: com.opencrm.product.catalog.BillingFrequency */
export type BillingFrequency = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL' | 'ONE_TIME';

export const BILLING_FREQUENCY_LABELS: Record<BillingFrequency, string> = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  ANNUAL: 'Annual',
  ONE_TIME: 'One-time',
};

/**
 * Mirrors: com.opencrm.product.catalog.ProductDto
 * Returned by GET /api/v1/products, GET /api/v1/products/:id
 */
export interface ProductDto {
  id: number;
  uuid: string;
  sku: string;
  name: string;
  description?: string;
  productType: CatalogProductType;
  category: ProductCategory;
  status: ProductStatus;
  listPrice?: number;
  currency?: string;
  billingFrequency?: BillingFrequency;
  unitOfMeasure?: string;
  configurable: boolean;
  bundle: boolean;
  metadata?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.product.catalog.ProductCreateRequest
 * Sent to POST /api/v1/products
 */
export interface ProductCreateRequest {
  sku: string; // @NotBlank, max 100
  name: string; // @NotBlank, max 255
  description?: string;
  productType: CatalogProductType; // @NotNull
  category: ProductCategory; // @NotNull
  listPrice?: number;
  currency?: string; // max 3
  billingFrequency?: BillingFrequency;
  unitOfMeasure?: string; // max 50
  configurable?: boolean;
  bundle?: boolean;
  metadata?: string;
}

/**
 * Mirrors: com.opencrm.product.catalog.ProductUpdateRequest
 * Sent to PUT /api/v1/products/:id
 */
export interface ProductUpdateRequest {
  name?: string;
  description?: string;
  productType?: CatalogProductType;
  category?: ProductCategory;
  status?: ProductStatus;
  listPrice?: number;
  currency?: string;
  billingFrequency?: BillingFrequency;
  unitOfMeasure?: string;
  configurable?: boolean;
  bundle?: boolean;
  metadata?: string;
}
