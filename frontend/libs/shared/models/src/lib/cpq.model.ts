import { PageRequest } from './api-response.model';

export type ProductType =
  | 'SAAS_SEAT_NAMED'
  | 'SAAS_SEAT_CONCURRENT'
  | 'SAAS_USAGE_METERED'
  | 'SAAS_FLAT_RATE'
  | 'ONPREM_PERPETUAL'
  | 'ONPREM_CORE'
  | 'ONPREM_SUBSCRIPTION'
  | 'HYBRID_PORTABLE'
  | 'SUPPORT_BASIC'
  | 'SUPPORT_PREMIUM'
  | 'SUPPORT_ENTERPRISE'
  | 'PROFESSIONAL_SERVICES'
  | 'TRAINING'
  | 'ADD_ON_API'
  | 'ADD_ON_STORAGE'
  | 'ADD_ON_ANALYTICS'
  | 'ADD_ON_SECURITY'
  | 'ADD_ON_INTEGRATION'
  | 'HARDWARE'
  | 'CUSTOM';
export type ProductCategory = 'SUBSCRIPTION' | 'LICENSE' | 'SUPPORT' | 'SERVICES' | 'ADD_ON';
export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'RETIRED';
export type BillingFrequency = 'ONE_TIME' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';

export interface CpqPageRequest extends PageRequest {
  [param: string]: string | number | boolean | undefined;
}

export interface ProductDto {
  id: number;
  uuid?: string;
  sku: string;
  name: string;
  description?: string;
  productType: ProductType;
  category: ProductCategory;
  status: ProductStatus;
  listPrice?: number;
  currency?: string;
  billingFrequency?: BillingFrequency;
  unitOfMeasure?: string;
  configurable: boolean;
  bundle: boolean;
  metadata?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

export interface ProductCreateRequest {
  sku: string;
  name: string;
  description?: string;
  productType: ProductType;
  category: ProductCategory;
  listPrice?: number;
  currency?: string;
  billingFrequency?: BillingFrequency;
  unitOfMeasure?: string;
  configurable?: boolean;
  bundle?: boolean;
  metadata?: string;
}

export type ProductUpdateRequest = Partial<ProductCreateRequest> & {
  status?: ProductStatus;
};

export type PriceBookStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface PriceBookDto {
  id: number;
  name: string;
  description?: string;
  currency: string;
  region?: string;
  status?: PriceBookStatus;
  effectiveFrom?: string;
  effectiveTo?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PriceBookCreateRequest {
  name: string;
  description?: string;
  currency: string;
  region?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
}

export type PriceBookUpdateRequest = Partial<PriceBookCreateRequest> & {
  status?: PriceBookStatus;
};

export interface PriceBookEntryDto {
  id: number;
  priceBookId: number;
  productId: number;
  sku?: string;
  listPrice: number;
  currency: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface PriceBookEntryCreateRequest {
  productId: number;
  listPrice: number;
  currency?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
}

export type PriceBookEntryUpdateRequest = Partial<PriceBookEntryCreateRequest> & {
  active?: boolean;
};

export type QuoteStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'SENT'
  | 'ACCEPTED'
  | 'EXPIRED'
  | 'CANCELLED';
export type QuoteDealType = 'NEW' | 'AMENDMENT' | 'RENEWAL' | 'EXPANSION';
export type ApprovalState = 'NOT_REQUIRED' | 'REQUIRED' | 'PENDING' | 'APPROVED' | 'REJECTED';

export interface QuoteDto {
  id: number;
  uuid?: string;
  quoteNumber: string;
  name: string;
  description?: string;
  status: QuoteStatus;
  dealType?: QuoteDealType;
  customerExternalId?: string;
  dealExternalId?: string;
  subscriptionType?: string;
  currency?: string;
  region?: string;
  durationInMonths?: number;
  startDate?: string;
  endDate?: string;
  subtotal?: number;
  totalDiscount?: number;
  totalTax?: number;
  totalAmount?: number;
  arr?: number;
  tcv?: number;
  acv?: number;
  approvalState?: ApprovalState;
  approvalTier?: string;
  notes?: string;
  lineItems?: QuoteLineItemDto[];
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

export interface QuoteCreateRequest {
  name: string;
  description?: string;
  customerExternalId: string;
  dealExternalId?: string;
  dealType?: QuoteDealType;
  subscriptionType?: string;
  currency?: string;
  region?: string;
  durationInMonths?: number;
  startDate?: string;
  endDate?: string;
  notes?: string;
}

export type QuoteUpdateRequest = Partial<QuoteCreateRequest>;

export interface QuoteLineItemDto {
  id: number;
  quoteId: number;
  productExternalId: string;
  productName?: string;
  productType?: string;
  lineItemType?: string;
  quantity: number;
  unitOfMeasure?: string;
  listPrice?: number;
  billingFrequency?: string;
  totalPrice?: number;
  sortOrder?: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LineItemCreateRequest {
  productExternalId: string;
  productName?: string;
  productType?: string;
  lineItemType?: string;
  quantity?: number;
  unitOfMeasure?: string;
  listPrice: number;
  billingFrequency?: string;
  sortOrder?: number;
  notes?: string;
}

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ApprovalRequestDto {
  id: number;
  quoteId: number;
  status: ApprovalStatus;
  approverId?: string;
  requestedBy?: string;
  reason?: string;
  comments?: string;
  createdAt?: string;
  decidedAt?: string;
}

export interface ApprovalSubmitRequest {
  reason?: string;
  approverId?: string;
}

export interface ApprovalResponseRequest {
  comments?: string;
}

export type ConfigurationStatus = 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED';

export interface ConfigurationSessionDto {
  id: number;
  productId: string;
  userId: string;
  status: ConfigurationStatus;
  selections?: Record<string, string>;
  validationErrors?: string[];
  valid?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export type DocumentJobStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
export type DocumentType = 'QUOTE_PDF' | 'CONTRACT_PDF' | 'ORDER_FORM' | 'INVOICE';

export interface DocumentGenerationJobDto {
  id: number;
  quoteId: string;
  documentType?: DocumentType;
  status: DocumentJobStatus;
  outputUrl?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PricingLineRequest {
  productId: string;
  sku?: string;
  quantity: number;
  listPrice?: number;
  attributes?: Record<string, string | number | boolean>;
}

export interface PricingContext {
  quoteExternalId?: string;
  customerExternalId?: string;
  currency?: string;
  lines: PricingLineRequest[];
  attributes?: Record<string, string | number | boolean>;
}

export interface PricingLineResult {
  productId?: string;
  sku?: string;
  quantity: number;
  listPrice?: number;
  netPrice?: number;
  discountAmount?: number;
  totalPrice?: number;
}

export interface PricingResult {
  quoteExternalId?: string;
  currency?: string;
  subtotal?: number;
  discountTotal?: number;
  taxTotal?: number;
  grandTotal?: number;
  lines: PricingLineResult[];
}

export interface BulkPricingSimulationRequest {
  contexts: PricingContext[];
}

export interface BulkPricingSimulationResponse {
  results: PricingResult[];
}

export interface PricingAuditLogDto {
  id: number;
  quoteExternalId: string;
  createdAt?: string;
  eventType?: string;
  message?: string;
  payload?: unknown;
}
