// API Response
export * from './lib/api-response.model';
export * from './lib/jev-suggestion.model';

// Domain Models — CRM
export * from './lib/organization.model';
export * from './lib/contact.model';
export * from './lib/lead.model';
export * from './lib/deal.model';

// Domain Models — CPQ (catalog / quote / pricing / approvals)
// CatalogProductType ≠ PricingProductType — keep enums separate (see forge PR-FE-CPQ-02).
export * from './lib/product.model';
export * from './lib/product-option.model';
export * from './lib/product-attribute.model';
export * from './lib/quote.model';
export * from './lib/pricing.model';
export * from './lib/approval.model';
