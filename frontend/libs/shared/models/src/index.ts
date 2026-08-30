// API Response
export * from './lib/api-response.model';

// Domain Models — CRM
export * from './lib/organization.model';
export * from './lib/contact.model';
export * from './lib/lead.model';
export * from './lib/deal.model';

// Domain Models — CPQ (catalog / quote / pricing)
// CatalogProductType ≠ PricingProductType — keep enums separate (see forge PR-FE-CPQ-02).
export * from './lib/product.model';
export * from './lib/quote.model';
export * from './lib/pricing.model';
